package dev.aegisac.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sanctions are persisted by UUID; IP bans are separate because an address can be shared.
 * Only the server thread mutates records. Pre-login threads read immutable snapshots.
 */
public final class Moderation {
    private record Record(int stage, long until, boolean permanent, String lastReason, String ip) {}
    private final AegisAC plugin;
    private final File file;
    private final Map<UUID, Record> records = new ConcurrentHashMap<>();
    private final Map<String, Boolean> ipBans = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> evidence = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAction = new ConcurrentHashMap<>();

    Moderation(AegisAC plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "sanctions.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.isConfigurationSection("players")) {
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String path = "players." + key + ".";
                    records.put(uuid, new Record(yaml.getInt(path + "stage"), yaml.getLong(path + "until"),
                            yaml.getBoolean(path + "permanent"), yaml.getString(path + "reason", ""), yaml.getString(path + "ip", "")));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid sanctions UUID.");
                }
            }
        }
        for (String ip : yaml.getStringList("ip-bans")) ipBans.put(ip, true);
    }

    public void checkLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        String ip = event.getAddress().getHostAddress();
        if (ipBans.containsKey(ip)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "This IP is banned by AegisAC. Contact server staff to appeal.");
            AegisAudit.warning(plugin, "LOGIN_DENIED", event.getName() + " ip=" + ip + " reason=ip-ban");
            return;
        }
        Record record = records.get(event.getUniqueId());
        if (record == null) return;
        if (record.permanent()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "Account banned by AegisAC. Contact server staff to appeal.");
            AegisAudit.warning(plugin, "LOGIN_DENIED", event.getName() + " ip=" + ip + " reason=account-ban");
        } else if (record.until() > System.currentTimeMillis()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "Temporarily blocked by AegisAC until " + java.time.Instant.ofEpochMilli(record.until()) + ". Contact staff to appeal.");
            AegisAudit.warning(plugin, "LOGIN_DENIED", event.getName() + " ip=" + ip + " reason=temp-ban until=" + record.until());
        }
    }

    public void onAlert(Player player, CheckType type, double confidence, String detail) {
        if (!plugin.getConfig().getBoolean("enforcement.enabled", false) || player.hasPermission("aegis.bypass")) return;
        if (!plugin.getConfig().getStringList("enforcement.eligible-checks").contains(type.name())) return;
        if (confidence < plugin.getConfig().getDouble("enforcement.minimum-confidence", 0.98)) return;
        if (plugin.currentTps() < plugin.getConfig().getDouble("minimum-tps", 18.0)
                || player.getPing() > plugin.getConfig().getInt("maximum-ping-ms", 300)) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        if (now - lastAction.getOrDefault(uuid, 0L) < plugin.getConfig().getLong("enforcement.cooldown-ms", 600_000L)) return;
        Deque<Long> hits = evidence.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        long window = plugin.getConfig().getLong("enforcement.evidence-window-ms", 90_000L);
        while (!hits.isEmpty() && now - hits.peekFirst() > window) hits.removeFirst();
        hits.addLast(now);
        if (hits.size() < Math.max(3, plugin.getConfig().getInt("enforcement.alerts-required", 4))) return;
        hits.clear();
        lastAction.put(uuid, now);
        Record old = records.getOrDefault(uuid, new Record(0, 0, false, "", ""));
        int stage = old.stage() + 1;
        String reason = type.name() + ": " + detail;
        if (stage <= 3) {
            records.put(uuid, new Record(stage, 0, false, reason, ""));
            save();
            AegisAudit.warning(plugin, "SANCTION", player.getName() + " uuid=" + uuid + " stage=" + stage
                    + " action=kick reason=" + reason);
            player.kickPlayer("AegisAC: repeated suspicious interactions (" + stage + "/3). Contact staff if this is a mistake.");
        } else if (stage <= 5) {
            long duration = stage == 4 ? 3_600_000L : 86_400_000L;
            records.put(uuid, new Record(stage, now + duration, false, reason, ""));
            save();
            AegisAudit.warning(plugin, "SANCTION", player.getName() + " uuid=" + uuid + " stage=" + stage
                    + " action=temp-ban until=" + (now + duration) + " reason=" + reason);
            player.kickPlayer("AegisAC: temporary restriction until " + java.time.Instant.ofEpochMilli(now + duration)
                    + ". Contact staff to appeal.");
        } else if (plugin.getConfig().getBoolean("enforcement.permanent-ip-ban-enabled", false)) {
            InetSocketAddress address = player.getAddress();
            if (address == null || address.getAddress() == null) return;
            String ip = address.getAddress().getHostAddress();
            records.put(uuid, new Record(stage, 0, true, reason, ip));
            ipBans.put(ip, true);
            save();
            AegisAudit.warning(plugin, "SANCTION", player.getName() + " uuid=" + uuid + " ip=" + ip
                    + " stage=" + stage + " action=permanent-ip-ban reason=" + reason);
            player.kickPlayer("AegisAC: banned. Contact server staff to appeal.");
        } else {
            AegisAudit.warning(plugin, "REVIEW_REQUIRED", player.getName() + " uuid=" + uuid
                    + " permanent IP banning is disabled; reason=" + reason);
        }
    }

    public int stage(UUID uuid) { return records.getOrDefault(uuid, new Record(0, 0, false, "", "")).stage(); }
    public String reason(UUID uuid) { return records.getOrDefault(uuid, new Record(0, 0, false, "", "")).lastReason(); }

    public void reset(UUID uuid) {
        Record removed = records.remove(uuid);
        if (removed != null && !removed.ip().isEmpty()) ipBans.remove(removed.ip());
        evidence.remove(uuid);
        lastAction.remove(uuid);
        save();
    }

    public boolean unbanIp(String ip) {
        if (ipBans.remove(ip) == null) return false;
        // A related account ban remains until it is separately pardoned.
        save();
        return true;
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            String path = "players." + e.getKey() + ".";
            yaml.set(path + "stage", e.getValue().stage());
            yaml.set(path + "until", e.getValue().until());
            yaml.set(path + "permanent", e.getValue().permanent());
            yaml.set(path + "reason", e.getValue().lastReason());
            yaml.set(path + "ip", e.getValue().ip());
        }
        yaml.set("ip-bans", new java.util.ArrayList<>(ipBans.keySet()));
        try {
            File temp = new File(file.getParentFile(), "sanctions.yml.tmp");
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save sanctions: " + ex.getMessage());
        }
    }
}
