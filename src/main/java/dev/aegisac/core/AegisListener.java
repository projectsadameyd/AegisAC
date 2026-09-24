package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.data().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.data().get(event.getPlayer().getUniqueId()).lastTeleportMillis = System.currentTimeMillis();
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
