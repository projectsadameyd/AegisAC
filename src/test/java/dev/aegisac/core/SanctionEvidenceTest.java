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

        for (CheckType type : CheckType.values()) {
            EnforcementProfiles.Profile profile = EnforcementProfiles.get(type);
            assert profile != null : "missing profile for " + type;
            assert profile.minimumScore() <= profile.maximumScore() : "unreachable profile for " + type;
            assert profile.requiredAlerts() >= 4 : "single-alert sanctions for " + type;
            assert profile.minimumSpanMillis() <= 90_000L : "evidence window too short for " + type;
        }
        alerts.clear();
        EnforcementProfiles.Profile fly = EnforcementProfiles.get(CheckType.FLY);
        for (int i = 0; i < fly.requiredAlerts() - 1; i++) {
            assert !SanctionEvidence.recordAndReady(alerts, 500_000L + i * 1250L, 90_000L,
                    fly.requiredAlerts(), fly.minimumSpanMillis());
        }
        assert !SanctionEvidence.recordAndReady(alerts, 518_750L, 90_000L,
                fly.requiredAlerts(), fly.minimumSpanMillis()) : "all-check mode requires sustained evidence";
        assert SanctionEvidence.recordAndReady(alerts, 520_000L, 90_000L,
                fly.requiredAlerts(), fly.minimumSpanMillis());
        System.out.println("SanctionEvidenceTest passed");
    }
}
