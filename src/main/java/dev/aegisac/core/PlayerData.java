package dev.aegisac.core;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

public final class PlayerData {
    Location lastLocation;
    long lastMoveNanos;
    long lastTeleportMillis;
    long lastVelocityMillis;
    long lastDamageMillis;
    int airTicks;
    int stableGroundTicks;
    double lastVerticalDelta;

    final Deque<Long> moveTimes = new ArrayDeque<>();
    final Deque<Long> swingTimes = new ArrayDeque<>();
    final Deque<Long> placeTimes = new ArrayDeque<>();
    final Map<CheckType, Double> buffers = new EnumMap<>(CheckType.class);
    final Map<CheckType, Long> lastAlertMillis = new EnumMap<>(CheckType.class);

    Vector expectedVelocity;
    Location velocityStart;

    double buffer(CheckType type) {
        return buffers.getOrDefault(type, 0.0);
    }

    void setBuffer(CheckType type, double value) {
        buffers.put(type, Math.max(0.0, value));
    }

    void decay(CheckType type, double amount) {
        setBuffer(type, buffer(type) - amount);
    }
}
