package net.donutac.anticheat.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public final class PlayerData {
   private final UUID uuid;
   private final Map<String, CheckState> checkStates = new HashMap<>();
   private Location lastLocation;
   private long lastGroundedMillis = System.currentTimeMillis();
   private int airTicks = 0;
   private long lastAttackMillis = 0L;
   private float lastYawAtAttack = 0.0F;
   private final Map<UUID, Long> recentTargets = new HashMap<>();
   private long lastInventoryClickMillis = 0L;
   private long lastBlockBreakMillis = 0L;
   private long lastBlockPlaceMillis = 0L;
   private int blocksBrokenThisSecond = 0;
   private long blocksBrokenWindowStart = System.currentTimeMillis();
   private int totalViolations = 0;

   public PlayerData(UUID var1) {
      this.uuid = var1;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public CheckState state(String var1) {
      return this.checkStates.computeIfAbsent(var1, var0 -> new CheckState());
   }

   public Map<String, CheckState> checkStates() {
      return this.checkStates;
   }

   public Location lastLocation() {
      return this.lastLocation;
   }

   public void setLastLocation(Location var1) {
      this.lastLocation = var1;
   }

   public long lastGroundedMillis() {
      return this.lastGroundedMillis;
   }

   public void setLastGroundedMillis(long var1) {
      this.lastGroundedMillis = var1;
   }

   public int airTicks() {
      return this.airTicks;
   }

   public void setAirTicks(int var1) {
      this.airTicks = var1;
   }

   public long lastAttackMillis() {
      return this.lastAttackMillis;
   }

   public void setLastAttackMillis(long var1) {
      this.lastAttackMillis = var1;
   }

   public float lastYawAtAttack() {
      return this.lastYawAtAttack;
   }

   public void setLastYawAtAttack(float var1) {
      this.lastYawAtAttack = var1;
   }

   public Map<UUID, Long> recentTargets() {
      return this.recentTargets;
   }

   public long lastInventoryClickMillis() {
      return this.lastInventoryClickMillis;
   }

   public void setLastInventoryClickMillis(long var1) {
      this.lastInventoryClickMillis = var1;
   }

   public long lastBlockBreakMillis() {
      return this.lastBlockBreakMillis;
   }

   public void setLastBlockBreakMillis(long var1) {
      this.lastBlockBreakMillis = var1;
   }

   public long lastBlockPlaceMillis() {
      return this.lastBlockPlaceMillis;
   }

   public void setLastBlockPlaceMillis(long var1) {
      this.lastBlockPlaceMillis = var1;
   }

   public int blocksBrokenThisSecond() {
      return this.blocksBrokenThisSecond;
   }

   public void setBlocksBrokenThisSecond(int var1) {
      this.blocksBrokenThisSecond = var1;
   }

   public long blocksBrokenWindowStart() {
      return this.blocksBrokenWindowStart;
   }

   public void setBlocksBrokenWindowStart(long var1) {
      this.blocksBrokenWindowStart = var1;
   }

   public void incrementTotalViolations() {
      this.totalViolations++;
   }

   public int totalViolations() {
      return this.totalViolations;
   }
}
