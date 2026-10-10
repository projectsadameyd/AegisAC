package net.donutac.anticheat.config;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

public final class CheckConfig {
   private final String id;
   private final String displayName;
   private final boolean enabled;
   private final Map<String, Double> settings;
   private final TreeMap<Integer, PunishmentRule> punishments = new TreeMap<>();

   public CheckConfig(String var1, String var2, boolean var3, Map<String, Double> var4, List<PunishmentRule> var5) {
      this.id = var1;
      this.displayName = var2;
      this.enabled = var3;
      this.settings = var4;

      for (PunishmentRule var7 : var5) {
         this.punishments.put(var7.threshold(), var7);
      }
   }

   public String id() {
      return this.id;
   }

   public String displayName() {
      return this.displayName;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public double setting(String var1, double var2) {
      return this.settings.getOrDefault(var1, var2);
   }

   public PunishmentRule ruleFor(int var1) {
      Entry var2 = this.punishments.floorEntry(var1);
      return var2 == null ? null : (PunishmentRule)var2.getValue();
   }

   public PunishmentRule exactRule(int var1) {
      return this.punishments.get(var1);
   }
}
