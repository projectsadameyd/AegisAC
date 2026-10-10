package net.donutac.anticheat.offense;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.donutac.anticheat.DonutAntiCheat;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class OffenseManager {
   private final DonutAntiCheat plugin;
   private final File file;
   private final Map<UUID, List<OffenseEntry>> offenses = new ConcurrentHashMap<>();

   public OffenseManager(DonutAntiCheat var1) {
      this.plugin = var1;
      this.file = new File(var1.getDataFolder(), "offenses.yml");
      this.load();
   }

   public OffenseEntry record(OfflinePlayer var1, String var2, String var3) {
      String var4 = var1.getName() != null ? var1.getName() : var1.getUniqueId().toString();
      OffenseEntry var5 = new OffenseEntry(var1.getUniqueId(), var4, var2, var3, System.currentTimeMillis());
      this.offenses.computeIfAbsent(var1.getUniqueId(), var0 -> new ArrayList<>()).add(var5);
      this.save();
      return var5;
   }

   public List<OffenseEntry> historyOf(UUID var1) {
      return this.offenses.getOrDefault(var1, List.of()).stream().sorted(Comparator.comparingLong(OffenseEntry::timestamp).reversed()).toList();
   }

   public int countOf(UUID var1) {
      return this.offenses.getOrDefault(var1, List.of()).size();
   }

   private void save() {
      YamlConfiguration var1 = new YamlConfiguration();
      int var2 = 0;

      for (List<OffenseEntry> var4 : this.offenses.values()) {
         for (OffenseEntry var6 : var4) {
            String var7 = "offenses." + var2++;
            var1.set(var7 + ".uuid", var6.playerUuid().toString());
            var1.set(var7 + ".name", var6.playerName());
            var1.set(var7 + ".reason", var6.reason());
            var1.set(var7 + ".issuedBy", var6.issuedBy());
            var1.set(var7 + ".timestamp", var6.timestamp());
         }
      }

      try {
         if (!this.plugin.getDataFolder().exists()) {
            this.plugin.getDataFolder().mkdirs();
         }

         var1.save(this.file);
      } catch (IOException var8) {
         this.plugin.getLogger().severe("Failed to save offenses.yml: " + var8.getMessage());
      }
   }

   private void load() {
      if (this.file.exists()) {
         YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection var2 = var1.getConfigurationSection("offenses");
         if (var2 != null) {
            for (String var4 : var2.getKeys(false)) {
               ConfigurationSection var5 = var2.getConfigurationSection(var4);
               if (var5 != null) {
                  UUID var6 = UUID.fromString(var5.getString("uuid"));
                  OffenseEntry var7 = new OffenseEntry(
                     var6, var5.getString("name"), var5.getString("reason"), var5.getString("issuedBy"), var5.getLong("timestamp")
                  );
                  this.offenses.computeIfAbsent(var6, var0 -> new ArrayList<>()).add(var7);
               }
            }
         }
      }
   }
}
