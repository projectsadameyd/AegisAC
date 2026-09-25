package dev.aegisac.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Run with java -ea; verifies the severe movement boundary and evidence gate. */
public final class MotionEnvelopeTest {
    public static void main(String[] args) {
        double allowed = MotionEnvelope.predictedAllowance(2.34, 0.0, 3.0);
        assert allowed >= 2.34 && allowed < 2.65;
        assert MotionEnvelope.severe(9.548, allowed, 3.0, 6.0)
                : "the reported 9.548 block displacement should be rejected immediately";
        assert !MotionEnvelope.severe(2.5, allowed, 3.0, 6.0);
        assert !MotionEnvelope.severe(9.548, MotionEnvelope.predictedAllowance(10.0, 9.0, 1.0), 3.0, 6.0)
                : "a legitimate high speed attribute must raise the bound";
        assert MotionEnvelope.predictedAllowance(2.34, 1000.0, 3.0) < 2.65
                : "a prior bad movement must not poison the prediction";

        Deque<Long> hits = new ArrayDeque<>();
        assert !SanctionEvidence.recordAndReady(hits, 100_000L, 5000L, 2, 150L);
        assert !SanctionEvidence.recordAndReady(hits, 100_000L, 5000L, 2, 150L)
                : "duplicate attempts are not independent evidence";
        assert !SanctionEvidence.recordAndReady(hits, 100_100L, 5000L, 2, 150L)
                : "short bursts must not escalate";
        assert SanctionEvidence.recordAndReady(hits, 100_150L, 5000L, 2, 150L)
                : "two spaced attempts should escalate";
        hits.clear();
        assert !SanctionEvidence.recordAndReady(hits, 200_000L, 5000L, 2, 150L);
        assert !SanctionEvidence.recordAndReady(hits, 206_000L, 5000L, 2, 150L)
                : "an expired attempt must not escalate";
        System.out.println("MotionEnvelopeTest passed");
    }
}
