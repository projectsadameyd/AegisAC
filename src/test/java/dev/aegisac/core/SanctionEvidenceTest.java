package dev.aegisac.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** Run with java -ea; tests the sanction threshold independently of Paper. */
public final class SanctionEvidenceTest {
    public static void main(String[] args) {
        Deque<Long> alerts = new ArrayDeque<>();
        for (int i = 0; i < 7; i++) {
            assert !SanctionEvidence.recordAndReady(alerts, 100_000L + i * 1250L, 90_000L, 8, 5000L);
        }
        assert SanctionEvidence.recordAndReady(alerts, 108_750L, 90_000L, 8, 5000L)
                : "eight sustained Speed alerts must trigger the first sanction";

        alerts.clear();
        for (int i = 0; i < 8; i++) {
            assert !SanctionEvidence.recordAndReady(alerts, 200_000L + i * 500L, 90_000L, 8, 5000L)
                    : "a short burst must not trigger a sanction";
        }
        assert !SanctionEvidence.recordAndReady(alerts, 203_500L, 90_000L, 8, 5000L)
                : "duplicate timestamps must not count twice";
        assert SanctionEvidence.recordAndReady(alerts, 205_000L, 90_000L, 8, 5000L);

        alerts.clear();
        for (int i = 0; i < 7; i++) {
            assert !SanctionEvidence.recordAndReady(alerts, 300_000L + i * 1250L, 90_000L, 8, 5000L);
        }
        assert !SanctionEvidence.recordAndReady(alerts, 400_001L, 90_000L, 8, 5000L)
                : "expired alerts must not count";
        assert alerts.size() == 1;
        System.out.println("SanctionEvidenceTest passed");
    }
}
