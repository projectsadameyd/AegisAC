package dev.aegisac;

import java.util.Locale;

/** Timing discrepancies are insufficient evidence for the automatic ban ladder. */
final class R17SanctionSafety {
    static boolean timingOnly(String check){return check!=null&&check.toLowerCase(Locale.ROOT).contains("timer");}
}
