package dev.aegisac.core;

/** Conservative horizontal bound using the current attributes and the preceding movement. */
final class MotionEnvelope {
    private MotionEnvelope() {}

    static double predictedAllowance(double attributeAllowance, double previousHorizontal, double tickFactor) {
        return Math.max(attributeAllowance,
                Math.min(Math.max(0.0, previousHorizontal), attributeAllowance) + 0.10 * tickFactor);
    }

    static boolean severe(double horizontal, double predictedAllowance, double multiplier, double absoluteDistance) {
        return horizontal > Math.max(absoluteDistance, predictedAllowance * multiplier);
    }
}
