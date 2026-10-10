package net.donutac.anticheat.ban;

import java.util.UUID;

public final class BanEntry {
   private final String banId;
   private final UUID playerUuid;
   private final String playerName;
   private final String checkName;
   private final String reason;
   private final String bannedBy;
   private final long timestamp;
   private final long expiresAt;
   private boolean pardoned;

   public BanEntry(String var1, UUID var2, String var3, String var4, String var5, String var6, long var7, long var9) {
      this.banId = var1;
      this.playerUuid = var2;
      this.playerName = var3;
      this.checkName = var4;
      this.reason = var5;
      this.bannedBy = var6;
      this.timestamp = var7;
      this.expiresAt = var9;
   }

   public String banId() {
      return this.banId;
   }

   public UUID playerUuid() {
      return this.playerUuid;
   }

   public String playerName() {
      return this.playerName;
   }

   public String checkName() {
      return this.checkName;
   }

   public String reason() {
      return this.reason;
   }

   public String bannedBy() {
      return this.bannedBy;
   }

   public long timestamp() {
      return this.timestamp;
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   public boolean isPermanent() {
      return this.expiresAt < 0L;
   }

   public boolean isPardoned() {
      return this.pardoned;
   }

   public void setPardoned(boolean var1) {
      this.pardoned = var1;
   }

   public boolean isActive() {
      return this.pardoned ? false : this.isPermanent() || this.expiresAt > System.currentTimeMillis();
   }

   public long remainingMillis() {
      return this.isPermanent() ? -1L : Math.max(0L, this.expiresAt - System.currentTimeMillis());
   }
}
