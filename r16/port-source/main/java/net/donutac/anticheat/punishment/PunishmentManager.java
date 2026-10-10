package net.donutac.anticheat.punishment;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.config.PunishmentRule;
import net.donutac.anticheat.util.ChatUtil;
import net.donutac.anticheat.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PunishmentManager {
   private final DonutAntiCheat plugin;
   private final Set<UUID> muted = new HashSet<>();

   public PunishmentManager(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   public void execute(Player var1, CheckConfig var2, PunishmentRule var3, int var4) {
   }

   private void warn(Player var1, CheckConfig var2) {
      String var3 = this.plugin.configManager().message("warn", "%prefix%&6&lWARNING &7» &fSuspicious activity detected (%check%) on %server%.");
      var1.sendMessage(
         ChatUtil.component(
            var3.replace("%prefix%", this.plugin.configManager().prefix())
               .replace("%server%", this.plugin.configManager().serverName())
               .replace("%check%", var2.displayName())
         )
      );
      this.plugin.discordWebhook().send("mutes-warns", "Player Warned", "**Player:** " + var1.getName() + "\n**Check:** " + var2.displayName(), 15965202);
   }

   private void mute(Player var1, PunishmentRule var2) {
      this.muted.add(var1.getUniqueId());
      String var3 = var2.duration();
      long var4 = TimeUtil.parseDurationMillis(var3);
      String var6 = this.plugin.configManager().message("mute", "%prefix%&c&lMUTED &7» &fYou have been muted for &e%duration%&7. Reason: &f%reason%");
      var1.sendMessage(
         ChatUtil.component(
            var6.replace("%prefix%", this.plugin.configManager().prefix())
               .replace("%duration%", "permanent".equalsIgnoreCase(var3) ? "permanent" : var3)
               .replace("%reason%", var2.reason())
         )
      );
      if (var4 > 0L) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.muted.remove(var1.getUniqueId()), var4 / 50L);
      }

      this.plugin
         .discordWebhook()
         .send("mutes-warns", "Player Muted", "**Player:** " + var1.getName() + "\n**Duration:** " + var3 + "\n**Reason:** " + var2.reason(), 15105570);
   }

   public boolean isMuted(UUID var1) {
      return this.muted.contains(var1);
   }

   private void kick(Player var1, CheckConfig var2, int var3) {
      String var4 = this.plugin.configManager().message("kick", "&6&l%server%\n\n&7You were kicked for &e%check%&7.\n&7Violation Level: &6%vl%");
      String var5 = var4.replace("%server%", this.plugin.configManager().serverName())
         .replace("%check%", var2.displayName())
         .replace("%vl%", String.valueOf(var3));
      Bukkit.getScheduler().runTask(this.plugin, () -> var1.kickPlayer(ChatUtil.multiline(var5)));
      this.plugin
         .discordWebhook()
         .send("kicks", "Player Kicked", "**Player:** " + var1.getName() + "\n**Check:** " + var2.displayName() + "\n**Violation Level:** " + var3, 13849600);
   }
}
