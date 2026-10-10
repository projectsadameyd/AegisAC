package net.donutac.anticheat.ban;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.ConfigManager;
import net.donutac.anticheat.util.ChatUtil;
import net.donutac.anticheat.util.TimeUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class BanManager {
   private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
   private final SecureRandom random = new SecureRandom();
   private final DonutAntiCheat plugin;
   private final File file;
   private final Map<UUID, List<BanEntry>> history = new ConcurrentHashMap<>();

   public BanManager(DonutAntiCheat var1) {
      this.plugin = var1;
      this.file = new File(var1.getDataFolder(), "bans.yml");
      this.load();
   }

   public BanEntry ban(OfflinePlayer var1, String var2, String var3, String var4, String var5) {
      long var6 = TimeUtil.parseDurationMillis(var4);
      long var8 = var6 < 0L ? -1L : System.currentTimeMillis() + var6;
      String var10 = this.generateBanId();
      BanEntry var11 = new BanEntry(var10, var1.getUniqueId(), this.safeName(var1), var2, var3, var5, System.currentTimeMillis(), var8);
      this.history.computeIfAbsent(var1.getUniqueId(), var0 -> new ArrayList<>()).add(var11);
      this.save();
      if (var1.isOnline() && var1 instanceof Player var12) {
         var12.kickPlayer(this.buildBanScreen(var11));
      }

      String var14 = this.plugin.luckPermsHook().primaryGroup(var1);
      String var13 = "**Player:** "
         + this.safeName(var1)
         + (var14 != null ? " (" + var14 + ")" : "")
         + "\n**Check:** "
         + var2
         + "\n**Reason:** "
         + var3
         + "\n**Duration:** "
         + (var11.isPermanent() ? "permanent" : var4)
         + "\n**Banned by:** "
         + var5
         + "\n**Ban ID:** #"
         + var10;
      this.plugin.discordWebhook().send("bans", "Player Banned", var13, 12597547);
      return var11;
   }

   public boolean isBanned(UUID var1) {
      return this.activeBan(var1) != null;
   }

   public BanEntry activeBan(UUID var1) {
      List<BanEntry> var2 = this.history.get(var1);
      return var2 == null ? null : var2.stream().filter(BanEntry::isActive).max(Comparator.comparingLong(BanEntry::timestamp)).orElse(null);
   }

   public List<BanEntry> historyOf(UUID var1) {
      return this.history.getOrDefault(var1, List.of()).stream().sorted(Comparator.comparingLong(BanEntry::timestamp).reversed()).toList();
   }

   public boolean pardon(UUID var1) {
      BanEntry var2 = this.activeBan(var1);
      if (var2 == null) {
         return false;
      }

      var2.setPardoned(true);
      this.save();
      return true;
   }

   public List<UUID> bannedPlayers() {
      return this.history.keySet().stream().filter(this::isBanned).toList();
   }

   public String buildBanScreen(BanEntry var1) {
      ConfigManager var2 = this.plugin.configManager();
      String var3 = var2.serverName();
      String var4 = var2.message("ban-screen.title", "&6&l%server%").replace("%server%", var3);
      String var5 = (var1.isPermanent()
            ? var2.message("ban-screen.permanent-line1", "&7You have been &c&lpermanently banned&7 from &6%server%&7.")
            : var2.message("ban-screen.line1", "&7You have been &c&lbanned&7 from &6%server%&7."))
         .replace("%server%", var3);
      StringBuilder var6 = new StringBuilder();
      var6.append(var4).append("\n\n").append(var5);
      if (!var1.isPermanent()) {
         String var7 = var2.message("ban-screen.time-left", "&7Time Remaining: &e%time%").replace("%time%", TimeUtil.formatRemaining(var1.remainingMillis()));
         var6.append("\n\n").append(var7);
      }

      String var9 = var2.message("ban-screen.ban-id", "&7Ban ID: &f#%banid%").replace("%banid%", var1.banId());
      var6.append("\n").append(var9);
      String var8 = var2.message("ban-screen.appeal", "&7Appeal at: &b%appeal%").replace("%appeal%", var2.appealLink());
      var6.append("\n\n").append(var8);
      return ChatUtil.multiline(var6.toString());
   }

   private String generateBanId() {
      String var1 = this.plugin.configManager().banIdPrefix();
      StringBuilder var2 = new StringBuilder(var1);

      for (int var3 = 0; var3 < 6; var3++) {
         var2.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(this.random.nextInt("abcdefghijklmnopqrstuvwxyz0123456789".length())));
      }

      return var2.toString();
   }

   private String safeName(OfflinePlayer var1) {
      String var2 = var1.getName();
      return var2 != null ? var2 : var1.getUniqueId().toString();
   }

   public void save() {
      YamlConfiguration var1 = new YamlConfiguration();
      int var2 = 0;

      for (List<BanEntry> var4 : this.history.values()) {
         for (BanEntry var6 : var4) {
            String var7 = "bans." + var2++;
            var1.set(var7 + ".banId", var6.banId());
            var1.set(var7 + ".uuid", var6.playerUuid().toString());
            var1.set(var7 + ".name", var6.playerName());
            var1.set(var7 + ".check", var6.checkName());
            var1.set(var7 + ".reason", var6.reason());
            var1.set(var7 + ".bannedBy", var6.bannedBy());
            var1.set(var7 + ".timestamp", var6.timestamp());
            var1.set(var7 + ".expiresAt", var6.expiresAt());
            var1.set(var7 + ".pardoned", var6.isPardoned());
         }
      }

      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         var1.save(this.file);
      } catch (IOException var8) {
         this.plugin.getLogger().severe("Failed to save bans.yml: " + var8.getMessage());
      }
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection var2 = var1.getConfigurationSection("bans");
         if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
               ConfigurationSection var5 = var2.getConfigurationSection(var4);
               if (var5 != null) {
                  UUID var6 = UUID.fromString(var5.getString("uuid"));
                  BanEntry var7 = new BanEntry(
                     var5.getString("banId"),
                     var6,
                     var5.getString("name"),
                     var5.getString("check"),
                     var5.getString("reason"),
                     var5.getString("bannedBy"),
                     var5.getLong("timestamp"),
                     var5.getLong("expiresAt")
                  );
                  var7.setPardoned(var5.getBoolean("pardoned", false));
                  this.history.computeIfAbsent(var6, var0 -> new ArrayList<>()).add(var7);
               }
            }
         }
      }
   }
}
