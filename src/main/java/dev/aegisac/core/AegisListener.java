package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;

public final class AegisListener implements Listener {
    private final AegisAC plugin;
    private final MovementAnalyzer movement;
    private final CombatAnalyzer combat;

    public AegisListener(AegisAC plugin) {
        this.plugin = plugin;
        this.movement = new MovementAnalyzer(plugin);
        this.combat = new CombatAnalyzer(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        movement.handle(event, plugin.data().get(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            PlayerData data = plugin.data().get(player.getUniqueId());
            data.combatEvents++;
            combat.handle(event, data);
        }
        if (event.getEntity() instanceof Player victim) {
            plugin.data().get(victim.getUniqueId()).lastDamageMillis = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.data().get(player.getUniqueId()).lastDamageMillis = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer().getUniqueId());
        data.lastVelocityMillis = System.currentTimeMillis();
        data.expectedVelocity = event.getVelocity().clone();
        data.velocityStart = event.getPlayer().getLocation().clone();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer().getUniqueId());
        data.lastTeleportMillis = System.currentTimeMillis();
        data.lastLocation = event.getTo() == null ? null : event.getTo().clone();
        data.airTicks = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer().getUniqueId());
        data.lastTeleportMillis = System.currentTimeMillis();
        data.airTicks = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aegis.bypass") || !plugin.getConfig().getBoolean("checks.autoclicker.enabled", true)) return;
        PlayerData data = plugin.data().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        data.swingTimes.addLast(now);
        while (!data.swingTimes.isEmpty() && now - data.swingTimes.peekFirst() > 1000L) data.swingTimes.removeFirst();

        int minSamples = plugin.getConfig().getInt("checks.autoclicker.min-samples", 30);
        int maxCps = plugin.getConfig().getInt("checks.autoclicker.max-cps", 22);
        if (data.swingTimes.size() >= Math.max(8, Math.min(minSamples, maxCps + 2))) {
            int cps = data.swingTimes.size();
            double threshold = plugin.getConfig().getDouble("checks.autoclicker.buffer-to-alert", 4.0);
            if (cps > maxCps) {
                double confidence = Math.min(0.93, 0.72 + (cps - maxCps) * 0.035);
                plugin.violations().flag(player, data, CheckType.AUTOCLICKER, 0.75, threshold, confidence,
                        "cps=" + cps);
            } else {
                data.decay(CheckType.AUTOCLICKER, 0.08);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("aegis.bypass") || !plugin.getConfig().getBoolean("checks.fastplace.enabled", true)) return;
        PlayerData data = plugin.data().get(player.getUniqueId());
        long now = System.currentTimeMillis();
        data.placeTimes.addLast(now);
        while (!data.placeTimes.isEmpty() && now - data.placeTimes.peekFirst() > 1000L) data.placeTimes.removeFirst();

        int rate = data.placeTimes.size();
        int maxRate = plugin.getConfig().getInt("checks.fastplace.max-places-per-second", 17);
        Location eye = player.getEyeLocation();
        Location center = event.getBlockPlaced().getLocation().clone().add(0.5, 0.5, 0.5);
        double distance = eye.getWorld() == center.getWorld() ? eye.distance(center) : 0.0;
        double maxDistance = plugin.getConfig().getDouble("checks.fastplace.max-eye-distance", 6.25);
        double threshold = plugin.getConfig().getDouble("checks.fastplace.buffer-to-alert", 3.0);

        if (rate > maxRate || distance > maxDistance) {
            double confidence = distance > maxDistance ? 0.90 : 0.76;
            plugin.violations().flag(player, data, CheckType.FASTPLACE, 1.0, threshold, confidence,
                    "rate=" + rate + "/s, distance=" + fmt(distance));
        } else {
            data.decay(CheckType.FASTPLACE, 0.12);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemoteBreak(BlockBreakEvent event) {
        if (remote(event.getPlayer(), event.getBlock(), false)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemotePlace(BlockPlaceEvent event) {
        if (remote(event.getPlayer(), event.getBlockPlaced(), true)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRemoteInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null
                && remote(event.getPlayer(), event.getClickedBlock(), false)) {
            event.setCancelled(true);
        }
    }

    private boolean remote(Player player, Block target, boolean placedBlock) {
        PlayerData data = plugin.data().get(player.getUniqueId());
        data.interactionEvents++;
        if (!plugin.getConfig().getBoolean("checks.remote-interact.enabled", true)
                || player.hasPermission("aegis.bypass") || player.getGameMode().name().equals("CREATIVE")
                || player.getGameMode().name().equals("SPECTATOR")) return false;
        Location eye = player.getEyeLocation();
        if (eye.getWorld() != target.getWorld()) return false;
        // Measure to the nearest point of the block, not its center.
        // Placed blocks can legitimately be one block past the clicked face.
        org.bukkit.util.BoundingBox box = target.getBoundingBox();
        double dx = eye.getX() - Math.max(box.getMinX(), Math.min(box.getMaxX(), eye.getX()));
        double dy = eye.getY() - Math.max(box.getMinY(), Math.min(box.getMaxY(), eye.getY()));
        double dz = eye.getZ() - Math.max(box.getMinZ(), Math.min(box.getMaxZ(), eye.getZ()));
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        AttributeInstance reach = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double limit = (reach == null ? 4.5 : reach.getValue())
                + plugin.getConfig().getDouble("checks.remote-interact.reach-margin", 1.25)
                + (placedBlock ? 1.5 : 0.0);
        if (distance <= limit) return false;
        data.remoteBlocks++;
        long now = System.currentTimeMillis();
        if (now - data.lastRemoteAuditMillis >= 5000L) {
            data.lastRemoteAuditMillis = now;
            AegisAudit.warning(plugin, "REMOTE_BLOCKED", player.getName() + " uuid=" + player.getUniqueId()
                    + " distance=" + fmt(distance) + " allowed=" + fmt(limit)
                    + " (further events are rate-limited)");
        }
        // Other plugins can teleport players or extend reach. Cancelling is safer than
        // treating this client-side symptom as proof of a specific Freecam mod.
        if (now - data.lastTeleportMillis >= 1400L
                && plugin.currentTps() >= plugin.getConfig().getDouble("minimum-tps", 18.0)
                && player.getPing() <= plugin.getConfig().getInt("maximum-ping-ms", 300)) {
            plugin.violations().flag(player, data,
                    CheckType.FREECAM_INTERACT, 1.0,
                    plugin.getConfig().getDouble("checks.remote-interact.buffer-to-alert", 2.0),
                    0.99, "blockDistance=" + fmt(distance));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLoginBan(AsyncPlayerPreLoginEvent event) {
        plugin.moderation().checkLogin(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLoginVpn(AsyncPlayerPreLoginEvent event) {
        plugin.vpnGate().checkLogin(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        AegisAudit.info(plugin, "QUIT", event.getPlayer().getName() + " uuid=" + event.getPlayer().getUniqueId());
        plugin.data().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.data().get(player.getUniqueId()).lastTeleportMillis = System.currentTimeMillis();
        AegisAudit.info(plugin, "JOIN", player.getName() + " [IP: " + AegisAudit.ip(player)
                + "] uuid=" + player.getUniqueId());
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
