package dev.aegisac.core;

import java.util.EnumMap;
import java.util.Map;

/** Explicit opt-in thresholds for checks with different score ranges. */
final class EnforcementProfiles {
    record Profile(double minimumScore, double maximumScore, int requiredAlerts, long minimumSpanMillis) {}

    private static final Map<CheckType, Profile> PROFILES = new EnumMap<>(CheckType.class);

    static {
        PROFILES.put(CheckType.FREECAM_INTERACT, new Profile(0.98, 0.99, 4, 0));
        PROFILES.put(CheckType.SPEED, new Profile(0.92, 0.98, 8, 5_000));
        PROFILES.put(CheckType.FLY, new Profile(0.83, 0.83, 16, 20_000));
        PROFILES.put(CheckType.TIMER, new Profile(0.96, 0.96, 16, 20_000));
        PROFILES.put(CheckType.REACH, new Profile(0.98, 0.99, 12, 12_000));
        PROFILES.put(CheckType.AUTOCLICKER, new Profile(0.93, 0.93, 16, 20_000));
        PROFILES.put(CheckType.FASTPLACE, new Profile(0.90, 0.90, 16, 20_000));
        PROFILES.put(CheckType.VELOCITY, new Profile(0.74, 0.74, 16, 20_000));
        if (PROFILES.size() != CheckType.values().length) throw new ExceptionInInitializerError("Missing check profile");
    }

    private EnforcementProfiles() {}

    static Profile get(CheckType type) { return PROFILES.get(type); }
}
