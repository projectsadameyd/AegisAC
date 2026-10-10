package dev.aegisac;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;

final class PresentationConfig {
   static void migrate(Path var0) throws IOException {
      migrateFile(var0.resolve("messages.yml"), var0x -> {
         var0x.set("prefix", "&6&lAegisAC &8»");
         var0x.set("help", List.of("/aegis help - AegisAC administration"));
         if (var0x.contains("spectate-return")) {
            var0x.set("spectate-return", "/aegis help");
         }

         for (String var2 : var0x.getKeys(true)) {
            if (var0x.get(var2) instanceof String var4 && var4.contains("Grim:")) {
               var0x.set(var2, var4.replace("Grim:", "Core:"));
            }
         }
      });
      migrateFile(var0.resolve("donut.yml"), var0x -> {
         for (String var2 : List.of("settings.server-name", "settings.prefix", "discord.username")) {
            String var3 = var0x.getString(var2, "");
            if (var3.contains("Donut") || var3.contains("Grim")) {
               var0x.set(var2, var2.endsWith("prefix") ? "&6&lAegisAC &8» " : "AegisAC");
            }
         }

         for (String var6 : var0x.getKeys(true)) {
            if (var0x.get(var6) instanceof String var4 && var4.contains("discord.gg/donutsmp")) {
               var0x.set(var6, var4.replace("discord.gg/donutsmp", "https://discord.gg/ShtDEU2x3z"));
            }
         }
      });
      migrateFile(var0.resolve("config.yml"), var0x -> var0x.set("check-for-updates", false));
   }

   private static void migrateFile(Path var0, Consumer<YamlConfiguration> var1) throws IOException {
      if (Files.exists(var0)) {
         YamlConfiguration var2 = VpnGate.readStrict(var0.toFile());
         String var3 = var2.saveToString();
         var1.accept(var2);
         if (!var3.equals(var2.saveToString())) {
            String var4 = var0.getFileName().toString();
            Path var5 = var0.resolveSibling(var4.replaceFirst("\\.yml$", "") + ".before-aegis-branding.yml");
            if (!Files.exists(var5)) {
               Files.copy(var0, var5);
            }

            VpnGate.writeAtomic(var2, var0);
         }
      }
   }
}
