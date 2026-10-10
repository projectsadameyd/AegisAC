package net.donutac.anticheat.offense;

import java.util.UUID;

public final class OffenseEntry {
   private final UUID playerUuid;
   private final String playerName;
   private final String reason;
   private final String issuedBy;
   private final long timestamp;

   public OffenseEntry(UUID var1, String var2, String var3, String var4, long var5) {
      this.playerUuid = var1;
      this.playerName = var2;
      this.reason = var3;
      this.issuedBy = var4;
      this.timestamp = var5;
   }

   public UUID playerUuid() {
      return this.playerUuid;
   }

   public String playerName() {
      return this.playerName;
   }

   public String reason() {
      return this.reason;
   }

   public String issuedBy() {
      return this.issuedBy;
   }

   public long timestamp() {
      return this.timestamp;
   }
}
