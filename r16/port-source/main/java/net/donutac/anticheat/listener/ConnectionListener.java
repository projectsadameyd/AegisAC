package net.donutac.anticheat.listener;

import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.ban.BanEntry;
import net.donutac.anticheat.util.ChatUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public final class ConnectionListener implements Listener {
   private final DonutAntiCheat plugin;

   public ConnectionListener(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   @EventHandler
   public void onLogin(PlayerLoginEvent var1) {
      BanEntry var2 = this.plugin.banManager().activeBan(var1.getPlayer().getUniqueId());
      if (var2 != null) {
         var1.disallow(Result.KICK_BANNED, this.plugin.banManager().buildBanScreen(var2));
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onChat(AsyncPlayerChatEvent var1) {
      if (this.plugin.punishmentManager().isMuted(var1.getPlayer().getUniqueId())) {
         var1.setCancelled(true);
         var1.getPlayer().sendMessage(ChatUtil.component("&c&lMUTED &7> &fYou cannot speak in chat right now."));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent var1) {
      this.plugin.playerDataManager().remove(var1.getPlayer().getUniqueId());
   }
}
