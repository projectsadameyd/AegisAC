package net.donutac.anticheat.gui;

import dev.aegisac.BukkitCompatibility;
import net.donutac.anticheat.DonutAntiCheat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

public final class GUIListener implements Listener {
   public GUIListener(DonutAntiCheat var1) {
   }

   @EventHandler
   public void onClick(InventoryClickEvent var1) {
      InventoryView var2 = var1.getView();
      if (BanHistoryGUI.isBanHistoryInventoryTitle(BukkitCompatibility.inventoryTitle(var2))) {
         var1.setCancelled(true);
      }
   }
}
