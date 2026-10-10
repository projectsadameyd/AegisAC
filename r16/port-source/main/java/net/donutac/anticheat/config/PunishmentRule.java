package net.donutac.anticheat.config;

public record PunishmentRule(int threshold, String action, String duration, String reason) {
   public boolean isBan() {
      return "BAN".equalsIgnoreCase(this.action);
   }

   public boolean isKick() {
      return "KICK".equalsIgnoreCase(this.action);
   }

   public boolean isMute() {
      return "MUTE".equalsIgnoreCase(this.action);
   }

   public boolean isWarn() {
      return "WARN".equalsIgnoreCase(this.action);
   }
}
