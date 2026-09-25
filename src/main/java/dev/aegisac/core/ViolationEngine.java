package dev.aegisac.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViolationEngine {
    private final AegisAC plugin;
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();

    public ViolationEngine(AegisAC plugin) {
        this.plugin = plugin;
    }

    public void flag(Player player, PlayerData data, CheckType type, double weight, double threshold,
                     double confidence, String detail) {
        data.failedSamples.merge(type, 1L, Long::sum);
        double next = data.buffer(type) + weight;
        data.setBuffer(type, next);
        if (next < threshold) return;

        double minimumConfidence = plugin.getConfig().getDouble("alerts.minimum-confidence", 0.72);
        if (confidence < minimumConfidence) return;

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("alerts.cooldown-ms", 1250L);
        long previous = data.lastAlertMillis.getOrDefault(type, 0L);
        if (now - previous < cooldown) return;
        data.lastAlertMillis.put(type, now);

        int ping = player.getPing();
        double tps = plugin.currentTps();
        String message = "§8[§bAegis§8] §f" + player.getName()
                + " §7failed §c" + type.display() + " " + type.variant()
                + " §8(§7buffer=§f" + round(next)
                + "§7, confidence=§f" + Math.round(confidence * 100.0) + "%"
                + "§7, ping=§f" + ping + "ms"
                + "§7, tps=§f" + round(tps)
                + "§7, " + detail + "§8)";

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("aegis.alerts") && !alertsDisabled.contains(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }
        AegisAudit.warning(plugin, "ALERT", stripColors(message));
        plugin.moderation().onAlert(player, type, confidence, detail);
        data.setBuffer(type, Math.max(0.0, threshold * 0.55));
    }

    public boolean toggleAlerts(Player player) {
        UUID uuid = player.getUniqueId();
        if (alertsDisabled.remove(uuid)) return true;
        alertsDisabled.add(uuid);
        return false;
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String stripColors(String input) {
        return input.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }
}
