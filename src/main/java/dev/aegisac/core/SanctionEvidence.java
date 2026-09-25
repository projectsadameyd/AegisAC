package dev.aegisac.core;

import java.util.Deque;

/** A sustained sequence of distinct, already rate-limited alerts. */
final class SanctionEvidence {
    private SanctionEvidence() {}

    static boolean recordAndReady(Deque<Long> hits, long now, long windowMillis, int required, long minimumSpanMillis) {
        while (!hits.isEmpty() && now - hits.peekFirst() > windowMillis) hits.removeFirst();
        // Clock changes or duplicate timestamps should not count as independent evidence.
        if (!hits.isEmpty() && now <= hits.peekLast()) return false;
        hits.addLast(now);
        return hits.size() >= required && now - hits.peekFirst() >= minimumSpanMillis;
    }
}
