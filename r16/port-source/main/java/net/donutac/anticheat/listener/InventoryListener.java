package net.donutac.anticheat.listener;

import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.data.PlayerData;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public final class InventoryListener implements Listener {
   private final DonutAntiCheat plugin;

   public InventoryListener(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   @EventHandler(ignoreCancelled = true)
   public void onClick(InventoryClickEvent var1) {
      HumanEntity var3 = var1.getWhoClicked();
      Player var2;
      if (var3 instanceof Player && !(var2 = (Player)var3).hasPermission("donutac.bypass")) {
         if (var1.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
            CheckConfig var4 = this.plugin.configManager().check("inventory.fast-use");
            if (var4 != null && var4.enabled()) {
               PlayerData var5 = this.plugin.playerDataManager().get(var2.getUniqueId());
               long var6 = System.currentTimeMillis();
               long var8 = (long)var4.setting("min-click-delay-ms", 90.0);
               long var10 = var5.lastInventoryClickMillis();
               var5.setLastInventoryClickMillis(var6);
               if (var10 != 0L && var6 - var10 < var8) {
                  this.plugin.violationManager().flag(var2, "inventory.fast-use", var6 - var10 + "ms between clicks");
               }
            }
         }
      }
   }
}
