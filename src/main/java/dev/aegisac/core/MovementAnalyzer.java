package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.concurrent.TimeUnit;

public final class MovementAnalyzer {
    private final AegisAC plugin;

    public MovementAnalyzer(AegisAC plugin) {
        this.plugin = plugin;
    }

    public void handle(PlayerMoveEvent event, PlayerData data) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null || from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        boolean positional = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1.0E-7;
        if (!positional) return;

        Player player = event.getPlayer();
        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();
        long elapsedNanos = data.lastMoveNanos == 0L ? TimeUnit.MILLISECONDS.toNanos(50) : nowNanos - data.lastMoveNanos;
        data.lastMoveNanos = nowNanos;

        boolean grounded = WorldUtil.isGrounded(to);
        if (grounded) {
            data.airTicks = 0;
            data.stableGroundTicks++;
        } else {
            data.airTicks++;
            data.stableGroundTicks = 0;
        }

        timer(player, data, nowMillis);

        boolean exempt = WorldUtil.basicMovementExempt(player, data,
                plugin.getConfig().getDouble("minimum-tps", 18.0), plugin.currentTps(),
                plugin.getConfig().getInt("maximum-ping-ms", 300));

        if (!exempt) {
            speed(player, data, from, to, elapsedNanos, grounded);
            fly(player, data, dy, grounded);
            velocity(player, data, from, to, nowMillis);
        } else {
            data.decay(CheckType.SPEED, 0.40);
            data.decay(CheckType.FLY, 0.40);
            data.decay(CheckType.VELOCITY, 0.35);
        }

        data.lastVerticalDelta = dy;
        data.lastLocation = to.clone();
    }

    private void speed(Player player, PlayerData data, Location from, Location to, long elapsedNanos, boolean grounded) {
        if (!plugin.getConfig().getBoolean("checks.speed.enabled", true)) return;
        double horizontal = WorldUtil.horizontalDistance(from, to);
        double tickFactor = Math.max(1.0, Math.min(3.0, elapsedNanos / 50_000_000.0));
        double base = grounded
                ? plugin.getConfig().getDouble("checks.speed.max-ground-horizontal-per-tick", 0.62)
                : plugin.getConfig().getDouble("checks.speed.max-air-horizontal-per-tick", 0.78);
        double allowed = base * tickFactor;
        if (player.isSprinting()) allowed *= 1.08;
        allowed *= Math.max(1.0, player.getWalkSpeed() / 0.2f);

        double threshold = plugin.getConfig().getDouble("checks.speed.buffer-to-alert", 4.0);
        if (horizontal > allowed) {
            double over = horizontal / Math.max(0.001, allowed);
            double confidence = Math.min(0.98, 0.72 + (over - 1.0) * 0.22);
            plugin.violations().flag(player, data, CheckType.SPEED, Math.min(2.0, over - 0.75), threshold,
                    confidence, "h=" + fmt(horizontal) + ", max=" + fmt(allowed));
        } else {
            data.decay(CheckType.SPEED, 0.18);
        }
    }

    private void fly(Player player, PlayerData data, double dy, boolean grounded) {
        if (!plugin.getConfig().getBoolean("checks.fly.enabled", true)) return;
        int suspiciousAirTicks = plugin.getConfig().getInt("checks.fly.suspicious-air-ticks", 14);
        double threshold = plugin.getConfig().getDouble("checks.fly.buffer-to-alert", 5.0);

        if (!grounded && data.airTicks > suspiciousAirTicks) {
            boolean hovering = Math.abs(dy) < 0.012 && Math.abs(data.lastVerticalDelta) < 0.02;
            boolean risingTooLong = dy > 0.08 && data.lastVerticalDelta > 0.08 && data.airTicks > suspiciousAirTicks + 4;
            if (hovering || risingTooLong) {
                double confidence = hovering ? 0.83 : 0.77;
                plugin.violations().flag(player, data, CheckType.FLY, 1.0, threshold, confidence,
                        "airTicks=" + data.airTicks + ", dy=" + fmt(dy));
                return;
            }
        }
        data.decay(CheckType.FLY, 0.12);
    }

    private void timer(Player player, PlayerData data, long nowMillis) {
        if (!plugin.getConfig().getBoolean("checks.timer.enabled", true)) return;
        data.moveTimes.addLast(nowMillis);
        while (!data.moveTimes.isEmpty() && nowMillis - data.moveTimes.peekFirst() > 2000L) {
            data.moveTimes.removeFirst();
        }
        if (data.moveTimes.size() < 25) return;
        long span = Math.max(1L, data.moveTimes.peekLast() - data.moveTimes.peekFirst());
        double pps = (data.moveTimes.size() - 1) * 1000.0 / span;
        double max = plugin.getConfig().getDouble("checks.timer.max-moving-packets-per-second", 24.5);
        double threshold = plugin.getConfig().getDouble("checks.timer.buffer-to-alert", 3.0);
        if (pps > max && player.getPing() < plugin.getConfig().getInt("maximum-ping-ms", 300)) {
            double confidence = Math.min(0.96, 0.72 + (pps - max) / 25.0);
            plugin.violations().flag(player, data, CheckType.TIMER, 0.75, threshold, confidence,
                    "rate=" + fmt(pps) + "/s");
        } else {
            data.decay(CheckType.TIMER, 0.08);
        }
    }

    private void velocity(Player player, PlayerData data, Location from, Location to, long nowMillis) {
        if (!plugin.getConfig().getBoolean("checks.velocity.enabled", true)) return;
        if (data.expectedVelocity == null || data.velocityStart == null) return;
        long age = nowMillis - data.lastVelocityMillis;
        if (age < 150L || age > 750L) {
            if (age > 750L) {
                data.expectedVelocity = null;
                data.velocityStart = null;
            }
            return;
        }
        double expectedHorizontal = Math.sqrt(data.expectedVelocity.getX() * data.expectedVelocity.getX()
                + data.expectedVelocity.getZ() * data.expectedVelocity.getZ());
        if (expectedHorizontal < 0.18) return;
        double actual = WorldUtil.horizontalDistance(data.velocityStart, to);
        double ratio = actual / expectedHorizontal;
        double minimum = plugin.getConfig().getDouble("checks.velocity.minimum-horizontal-response-ratio", 0.06);
        double threshold = plugin.getConfig().getDouble("checks.velocity.buffer-to-alert", 4.0);
        if (age > 350L && ratio < minimum) {
            plugin.violations().flag(player, data, CheckType.VELOCITY, 0.75, threshold, 0.74,
                    "response=" + fmt(ratio) + ", age=" + age + "ms");
        } else if (ratio >= minimum) {
            data.decay(CheckType.VELOCITY, 0.30);
            data.expectedVelocity = null;
            data.velocityStart = null;
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
