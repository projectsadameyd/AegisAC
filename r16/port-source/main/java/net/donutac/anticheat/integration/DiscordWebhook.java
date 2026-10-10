package net.donutac.anticheat.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public final class DiscordWebhook {
   private final DonutAntiCheat plugin;
   private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();

   public DiscordWebhook(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   public void send(String var1, String var2, String var3, int var4) {
      ConfigManager var5 = this.plugin.configManager();
      if (var5.discordEnabled() && var5.discordEventEnabled(var1)) {
         String var6 = var5.discordWebhookUrl();
         if (var6 != null && !var6.isBlank() && var6.startsWith("http")) {
            String var7 = this.buildPayload(var5.discordUsername(), var5.discordAvatarUrl(), var2, var3, var4);
            Bukkit.getScheduler()
               .runTaskAsynchronously(
                  this.plugin,
                  () -> {
                     try {
                        HttpRequest var3x = HttpRequest.newBuilder()
                           .uri(URI.create(var6))
                           .timeout(Duration.ofSeconds(10L))
                           .header("Content-Type", "application/json")
                           .POST(BodyPublishers.ofString(var7))
                           .build();
                        this.client.send(var3x, BodyHandlers.discarding());
                     } catch (Exception var4x) {
                        this.plugin.getLogger().warning("Discord webhook send failed: " + var4x.getMessage());
                     }
                  }
               );
         }
      }
   }

   private String buildPayload(String var1, String var2, String var3, String var4, int var5) {
      StringBuilder var6 = new StringBuilder("{");
      if (var1 != null && !var1.isBlank()) {
         var6.append("\"username\":\"").append(this.escape(var1)).append("\",");
      }

      if (var2 != null && !var2.isBlank()) {
         var6.append("\"avatar_url\":\"").append(this.escape(var2)).append("\",");
      }

      var6.append("\"embeds\":[{")
         .append("\"title\":\"")
         .append(this.escape(this.plain(var3)))
         .append("\",")
         .append("\"description\":\"")
         .append(this.escape(this.plain(var4)))
         .append("\",")
         .append("\"color\":")
         .append(var5)
         .append("}]}");
      return var6.toString();
   }

   private String plain(String var1) {
      return var1 == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', var1));
   }

   private String escape(String var1) {
      return var1.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
   }
}
