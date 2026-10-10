package net.donutac.anticheat.listener;

import dev.aegisac.BukkitCompatibility;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;

public final class MovementListener implements Listener {
   private final DonutAntiCheat plugin;

   public MovementListener(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   @EventHandler(ignoreCancelled = true)
   public void onMove(PlayerMoveEvent var1) {
      Player var2 = var1.getPlayer();
      if (!var2.hasPermission("donutac.bypass")
         && !var2.isFlying()
         && !BukkitCompatibility.isGliding(var2)
         && !var2.isInsideVehicle()
         && !var2.getAllowFlight()) {
         if (var1.getFrom().getWorld() == var1.getTo().getWorld()) {
            PlayerData var3 = this.plugin.playerDataManager().get(var2.getUniqueId());
            double var4 = var1.getTo().getX() - var1.getFrom().getX();
            double var6 = var1.getTo().getZ() - var1.getFrom().getZ();
            double var8 = Math.sqrt(var4 * var4 + var6 * var6);
            this.checkSpeed(var2, var8);
            this.checkFlight(var2, var3);
         }
      }
   }

   private void checkSpeed(Player var1, double var2) {
      CheckConfig var4 = this.plugin.configManager().check("movement.speed");
      if (var4 != null && var4.enabled()) {
         double var5 = var4.setting("max-horizontal-speed", 0.42);
         double var7 = var5 * (1.0 + this.effectBonus(var1));
         if (var2 > var7 * 1.35) {
            this.plugin.violationManager().flag(var1, "movement.speed", String.format("%.2f blocks/tick", var2));
         }
      }
   }

   private void checkFlight(Player var1, PlayerData var2) {
      CheckConfig var3 = this.plugin.configManager().check("movement.flight");
      if (var3 != null && var3.enabled()) {
         int var4 = (int)var3.setting("max-air-time-ticks", 40.0);
         if (!var1.isOnGround() && !BukkitCompatibility.isInWater(var1) && !BukkitCompatibility.isClimbing(var1)) {
            var2.setAirTicks(var2.airTicks() + 1);
            if (var2.airTicks() > var4) {
               this.plugin.violationManager().flag(var1, "movement.flight", var2.airTicks() + " ticks airborne");
               var2.setAirTicks(0);
            }
         } else {
            var2.setAirTicks(0);
         }
      }
   }

   private double effectBonus(Player var1) {
      PotionEffect var2 = BukkitCompatibility.speedEffect(var1);
      return var2 != null ? (var2.getAmplifier() + 1) * 0.2 : 0.0;
   }
}
