package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Set;

public final class WorldUtil {
    private static final Set<String> WEIRD_MOVEMENT_BLOCKS = Set.of(
            "ICE", "PACKED_ICE", "BLUE_ICE", "FROSTED_ICE",
            "SLIME_BLOCK", "HONEY_BLOCK", "SOUL_SAND", "SOUL_SOIL",
            "COBWEB", "POWDER_SNOW", "SCAFFOLDING", "LADDER", "VINE",
            "WATER", "LAVA", "BUBBLE_COLUMN"
    );

    private WorldUtil() {}

    public static boolean nearSpecialMovementBlock(Location location) {
        if (location == null || location.getWorld() == null) return true;
        int bx = floor(location.getX());
        int by = floor(location.getY());
        int bz = floor(location.getZ());
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = location.getWorld().getBlockAt(bx + x, by + y, bz + z);
                    if (isSpecial(block.getType())) return true;
                }
            }
        }
        return false;
    }

    public static boolean isGrounded(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Block below = location.getWorld().getBlockAt(
                floor(location.getX()), floor(location.getY() - 0.08), floor(location.getZ()));
        return !below.isPassable() && below.getType() != Material.AIR;
    }

    public static boolean basicMovementExempt(Player player, PlayerData data, double minTps, double currentTps, int maxPing) {
        if (player.hasPermission("aegis.bypass")) return true;
        if (player.getGameMode().name().equals("CREATIVE") || player.getGameMode().name().equals("SPECTATOR")) return true;
        if (player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()) return true;
        if (player.isGliding() || player.isSwimming() || player.isRiptiding()) return true;
        if (player.getPing() > maxPing) return true;
        if (currentTps < minTps) return true;
        long now = System.currentTimeMillis();
        if (now - data.lastTeleportMillis < 1400L) return true;
        if (now - data.lastVelocityMillis < 900L) return true;
        if (now - data.lastDamageMillis < 500L) return true;
        return nearSpecialMovementBlock(player.getLocation());
    }

    public static double horizontalDistance(Location a, Location b) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean isSpecial(Material material) {
        String name = material.name();
        if (WEIRD_MOVEMENT_BLOCKS.contains(name)) return true;
        return name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE") || name.endsWith("_WALL") || name.endsWith("_DOOR");
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
