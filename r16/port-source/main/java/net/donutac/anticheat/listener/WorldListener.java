package net.donutac.anticheat.listener;

import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class WorldListener implements Listener {
   private final DonutAntiCheat plugin;

   public WorldListener(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   @EventHandler(ignoreCancelled = true)
   public void onBreak(BlockBreakEvent var1) {
      Player var2 = var1.getPlayer();
      if (!var2.hasPermission("donutac.bypass")) {
         CheckConfig var3 = this.plugin.configManager().check("world.nuker");
         if (var3 != null && var3.enabled()) {
            PlayerData var4 = this.plugin.playerDataManager().get(var2.getUniqueId());
            long var5 = System.currentTimeMillis();
            long var7 = (long)var3.setting("min-break-delay-ms", 60.0);
            long var9 = var4.lastBlockBreakMillis();
            var4.setLastBlockBreakMillis(var5);
            if (var9 != 0L && var5 - var9 < var7) {
               this.plugin.violationManager().flag(var2, "world.nuker", var5 - var9 + "ms between breaks");
            }

            if (var5 - var4.blocksBrokenWindowStart() > 1000L) {
               var4.setBlocksBrokenWindowStart(var5);
               var4.setBlocksBrokenThisSecond(0);
            }

            var4.setBlocksBrokenThisSecond(var4.blocksBrokenThisSecond() + 1);
            int var11 = (int)var3.setting("max-blocks-per-second", 12.0);
            if (var4.blocksBrokenThisSecond() > var11) {
               this.plugin.violationManager().flag(var2, "world.nuker", var4.blocksBrokenThisSecond() + " blocks/s");
            }
         }
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent var1) {
      Player var2 = var1.getPlayer();
      if (!var2.hasPermission("donutac.bypass")) {
         CheckConfig var3 = this.plugin.configManager().check("world.fast-place");
         if (var3 != null && var3.enabled()) {
            PlayerData var4 = this.plugin.playerDataManager().get(var2.getUniqueId());
            long var5 = System.currentTimeMillis();
            long var7 = (long)var3.setting("min-place-delay-ms", 40.0);
            long var9 = var4.lastBlockPlaceMillis();
            var4.setLastBlockPlaceMillis(var5);
            if (var9 != 0L && var5 - var9 < var7) {
               this.plugin.violationManager().flag(var2, "world.fast-place", var5 - var9 + "ms between placements");
            }
         }
      }
   }
}
