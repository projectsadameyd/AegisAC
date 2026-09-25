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
        // Only blocks touching the player's feet or body can alter movement.
        // A 3x3x3 scan exempted ordinary movement merely near stairs or ice.
        for (int x = floor(location.getX() - 0.32); x <= floor(location.getX() + 0.32); x++) {
            for (int y = floor(location.getY() - 0.12); y <= floor(location.getY()); y++) {
                for (int z = floor(location.getZ() - 0.32); z <= floor(location.getZ() + 0.32); z++) {
                    Block block = location.getWorld().getBlockAt(x, y, z);
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
        return movementExemptionReason(player, data, minTps, currentTps, maxPing) != null;
    }

    public static boolean basicMovementExempt(Player player, PlayerData data, double minTps, double currentTps,
                                              int maxPing, Location destination) {
        return movementExemptionReason(player, data, minTps, currentTps, maxPing, destination) != null;
    }

    public static String movementExemptionReason(Player player, PlayerData data, double minTps, double currentTps, int maxPing) {
        return movementExemptionReason(player, data, minTps, currentTps, maxPing, player.getLocation());
    }

    private static String movementExemptionReason(Player player, PlayerData data, double minTps,
                                                  double currentTps, int maxPing, Location destination) {
        if (player.hasPermission("aegis.bypass")) return "aegis.bypass permission";
        if (player.getGameMode().name().equals("CREATIVE") || player.getGameMode().name().equals("SPECTATOR")) return "creative/spectator mode";
        if (player.getAllowFlight() || player.isFlying() || player.isInsideVehicle()) return "flight permission/vehicle";
        if (player.isGliding() || player.isSwimming() || player.isRiptiding()) return "gliding/swimming/riptide";
        if (player.getPing() > maxPing) return "ping over " + maxPing + "ms";
        if (currentTps < minTps) return "TPS under " + minTps;
        long now = System.currentTimeMillis();
        if (now - data.lastTeleportMillis < 1400L) return "recent teleport/join";
        if (now - data.lastVelocityMillis < 900L) return "recent velocity";
        if (now - data.lastDamageMillis < 500L) return "recent damage";
        if (nearSpecialMovementBlock(destination)) return "touching special block";
        return null;
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
