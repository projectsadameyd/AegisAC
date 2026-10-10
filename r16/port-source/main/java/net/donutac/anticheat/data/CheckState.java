package net.donutac.anticheat.data;

public final class CheckState {
   private int violationLevel = 0;
   private long lastViolationMillis = 0L;
   private int highestPunishedThreshold = 0;

   public int violationLevel() {
      return this.violationLevel;
   }

   public void increment(int var1) {
      this.violationLevel += var1;
      this.lastViolationMillis = System.currentTimeMillis();
   }

   public void decay(int var1) {
      this.violationLevel = Math.max(0, this.violationLevel - var1);
   }

   public long lastViolationMillis() {
      return this.lastViolationMillis;
   }

   public int highestPunishedThreshold() {
      return this.highestPunishedThreshold;
   }

   public void markPunished(int var1) {
      this.highestPunishedThreshold = Math.max(this.highestPunishedThreshold, var1);
   }
}
