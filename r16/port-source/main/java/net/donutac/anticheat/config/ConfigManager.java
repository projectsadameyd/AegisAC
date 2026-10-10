package net.donutac.anticheat.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.donutac.anticheat.DonutAntiCheat;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager {
   private final DonutAntiCheat plugin;
   private String prefix;
   private String serverName;
   private String appealLink;
   private boolean alertsEnabled;
   private int decayIdleSeconds;
   private int decayAmount;
   private int decayIntervalSeconds;
   private String banIdPrefix;
   private boolean discordEnabled;
   private String discordWebhookUrl;
   private String discordUsername;
   private String discordAvatarUrl;
   private final Map<String, Boolean> discordEvents = new LinkedHashMap<>();
   private final List<String> offendReasons = new ArrayList<>();
   private final Map<Integer, ViolationLevelInfo> violationLevels = new LinkedHashMap<>();
   private final Map<String, CheckConfig> checks = new LinkedHashMap<>();

   public ConfigManager(DonutAntiCheat var1) {
      this.plugin = var1;
      this.load();
   }

   public void reload() {
      this.violationLevels.clear();
      this.checks.clear();
      this.discordEvents.clear();
      this.offendReasons.clear();
      this.load();
   }

   private void load() {
      FileConfiguration var3 = this.plugin.getConfig();
      ConfigurationSection var4 = var3.getConfigurationSection("settings");
      this.prefix = var4 != null ? var4.getString("prefix", "&8[AegisAC] &r") : "&8[AegisAC] &r";
      this.serverName = var4 != null ? var4.getString("server-name", "AegisAC") : "AegisAC";
      this.appealLink = var4 != null ? var4.getString("appeal-link", "https://discord.gg/ShtDEU2x3z") : "https://discord.gg/ShtDEU2x3z";
      this.alertsEnabled = var4 != null && var4.getBoolean("alerts-enabled", true);
      this.decayIdleSeconds = var4 != null ? var4.getInt("decay-idle-seconds", 60) : 60;
      this.decayAmount = var4 != null ? var4.getInt("decay-amount", 1) : 1;
      this.decayIntervalSeconds = var4 != null ? var4.getInt("decay-interval-seconds", 10) : 10;
      this.banIdPrefix = var4 != null ? var4.getString("ban-id-prefix", "kdr") : "kdr";
      ConfigurationSection var5 = var3.getConfigurationSection("violation-levels");
      if (var5 != null) {
         for (String var7 : var5.getKeys(false)) {
            ConfigurationSection var8 = var5.getConfigurationSection(var7);
            if (var8 != null) {
               int var9 = Integer.parseInt(var7);
               this.violationLevels.put(var9, new ViolationLevelInfo(var9, var8.getString("label", "Unknown"), var8.getString("color", "&7")));
            }
         }
      }

      ConfigurationSection var2;
      this.discordEnabled = (var2 = var3.getConfigurationSection("discord")) != null && var2.getBoolean("enabled", false);
      this.discordWebhookUrl = var2 != null ? var2.getString("webhook-url", "") : "";
      this.discordUsername = var2 != null ? var2.getString("username", "AegisAC") : "AegisAC";
      this.discordAvatarUrl = var2 != null ? var2.getString("avatar-url", "") : "";
      ConfigurationSection var1 = var2 != null ? var2.getConfigurationSection("events") : null;
      if (var1 != null) {
         for (String var16 : var1.getKeys(false)) {
            this.discordEvents.put(var16.toLowerCase(), var1.getBoolean(var16, true));
         }
      }

      this.offendReasons.addAll(var3.getStringList("offend-reasons"));
      ConfigurationSection var15 = var3.getConfigurationSection("checks");
      if (var15 != null) {
         for (String var18 : var15.getKeys(false)) {
            ConfigurationSection var10 = var15.getConfigurationSection(var18);
            if (var10 != null) {
               for (String var12 : var10.getKeys(false)) {
                  ConfigurationSection var13 = var10.getConfigurationSection(var12);
                  if (var13 != null) {
                     this.checks.put(var18 + "." + var12, this.buildCheckConfig(var18 + "." + var12, var13));
                  }
               }
            }
         }
      }
   }

   private CheckConfig buildCheckConfig(String var1, ConfigurationSection var2) {
      String var3 = var2.getString("display-name", var1);
      boolean var4 = var2.getBoolean("enabled", true);
      LinkedHashMap var5 = new LinkedHashMap();

      for (String var7 : var2.getKeys(false)) {
         Object var8;
         if (!var7.equals("display-name") && !var7.equals("enabled") && !var7.equals("punishments") && (var8 = var2.get(var7)) instanceof Number) {
            Number var9 = (Number)var8;
            var5.put(var7, var9.doubleValue());
         }
      }

      ArrayList var15 = new ArrayList();
      ConfigurationSection var16 = var2.getConfigurationSection("punishments");
      if (var16 != null) {
         for (String var18 : var16.getKeys(false)) {
            ConfigurationSection var10 = var16.getConfigurationSection(var18);
            if (var10 != null) {
               int var11 = Integer.parseInt(var18);
               String var12 = var10.getString("action", "WARN");
               String var13 = var10.getString("duration", "permanent");
               String var14 = var10.getString("reason", "Unfair Advantage");
               var15.add(new PunishmentRule(var11, var12, var13, var14));
            }
         }
      }

      return new CheckConfig(var1, var3, var4, var5, var15);
   }

   public String prefix() {
      return this.prefix;
   }

   public String serverName() {
      return this.serverName;
   }

   public String appealLink() {
      return this.appealLink;
   }

   public boolean alertsEnabled() {
      return this.alertsEnabled;
   }

   public int decayIdleSeconds() {
      return this.decayIdleSeconds;
   }

   public int decayAmount() {
      return this.decayAmount;
   }

   public int decayIntervalSeconds() {
      return this.decayIntervalSeconds;
   }

   public String banIdPrefix() {
      return this.banIdPrefix;
   }

   public boolean discordEnabled() {
      return this.discordEnabled;
   }

   public String discordWebhookUrl() {
      return this.discordWebhookUrl;
   }

   public String discordUsername() {
      return this.discordUsername;
   }

   public String discordAvatarUrl() {
      return this.discordAvatarUrl;
   }

   public boolean discordEventEnabled(String var1) {
      return this.discordEvents.getOrDefault(var1.toLowerCase(), true);
   }

   public List<String> offendReasons() {
      return this.offendReasons;
   }

   public CheckConfig check(String var1) {
      return this.checks.get(var1);
   }

   public Map<String, CheckConfig> checks() {
      return this.checks;
   }

   public ViolationLevelInfo levelInfo(int var1) {
      ViolationLevelInfo var2 = this.violationLevels.get(var1);
      if (var2 != null) {
         return var2;
      }

      ViolationLevelInfo var3 = null;

      for (ViolationLevelInfo var5 : this.violationLevels.values()) {
         if (var5.level() <= var1 && (var3 == null || var5.level() > var3.level())) {
            var3 = var5;
         }
      }

      return var3 != null ? var3 : new ViolationLevelInfo(var1, "Unknown", "&7");
   }

   public String message(String var1, String var2) {
      return this.plugin.getConfig().getString("messages." + var1, var2);
   }
}
