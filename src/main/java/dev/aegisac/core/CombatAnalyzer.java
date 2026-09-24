package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

public final class CombatAnalyzer {
    private final AegisAC plugin;

    public CombatAnalyzer(AegisAC plugin) {
        this.plugin = plugin;
    }

    public void handle(EntityDamageByEntityEvent event, PlayerData data) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (player.hasPermission("aegis.bypass")) return;
        if (!plugin.getConfig().getBoolean("checks.reach.enabled", true)) return;
        if (player.getGameMode().name().equals("CREATIVE") || player.getGameMode().name().equals("SPECTATOR")) return;

        // 1.21.11 spears can legitimately use an item-provided attack range.
        String itemName = player.getInventory().getItemInMainHand().getType().name();
        if (itemName.contains("SPEAR")) {
            data.decay(CheckType.REACH, 0.35);
            return;
        }

        Entity target = event.getEntity();
        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double distance = distanceToBox(eye.getX(), eye.getY(), eye.getZ(), box);
        double max = plugin.getConfig().getDouble("checks.reach.max-normal-reach", 4.25);
        if (player.getPing() > 140) {
            max += plugin.getConfig().getDouble("checks.reach.high-ping-extra", 0.45);
        }
        double threshold = plugin.getConfig().getDouble("checks.reach.buffer-to-alert", 2.0);
        if (distance > max) {
            double confidence = Math.min(0.99, 0.82 + (distance - max) * 0.12);
            plugin.violations().flag(player, data, CheckType.REACH, 1.0, threshold, confidence,
                    "distance=" + fmt(distance) + ", max=" + fmt(max));
        } else {
            data.decay(CheckType.REACH, 0.25);
        }
    }

    private static double distanceToBox(double x, double y, double z, BoundingBox b) {
        double cx = clamp(x, b.getMinX(), b.getMaxX());
        double cy = clamp(y, b.getMinY(), b.getMaxY());
        double cz = clamp(z, b.getMinZ(), b.getMaxZ());
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
