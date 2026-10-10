package net.donutac.anticheat.integration;

import net.donutac.anticheat.DonutAntiCheat;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.OfflinePlayer;

public final class LuckPermsHook {
   private final DonutAntiCheat plugin;
   private boolean available;
   private LuckPerms api;

   public LuckPermsHook(DonutAntiCheat var1) {
      this.plugin = var1;
      this.hook();
   }

   private void hook() {
      try {
         if (this.plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            this.available = false;
            return;
         }

         this.api = LuckPermsProvider.get();
         this.available = true;
         this.plugin.getLogger().info("LuckPerms detected - rank display enabled in /sus, /donutac stats and Discord alerts.");
      } catch (Throwable var2) {
         this.available = false;
         this.api = null;
      }
   }

   public boolean isAvailable() {
      return this.available;
   }

   public String primaryGroup(OfflinePlayer var1) {
      if (this.available && this.api != null) {
         try {
            User var2 = this.api.getUserManager().getUser(var1.getUniqueId());
            if (var2 == null) {
               var2 = (User)this.api.getUserManager().loadUser(var1.getUniqueId()).get();
            }

            return var2 != null ? var2.getPrimaryGroup() : null;
         } catch (Exception var3) {
            return null;
         }
      } else {
         return null;
      }
   }
}
