package net.donutac.anticheat.listener;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public final class CombatListener implements Listener {
   private final DonutAntiCheat plugin;

   public CombatListener(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   @EventHandler(ignoreCancelled = true)
   public void onAttack(EntityDamageByEntityEvent var1) {
      if (var1.getDamager() instanceof Player var2 && var1.getEntity() instanceof LivingEntity var4) {
         if (!var2.hasPermission("donutac.bypass")) {
            PlayerData var5 = this.plugin.playerDataManager().get(var2.getUniqueId());
            this.checkReach(var2, var4, var5);
            this.checkKillAura(var2, var4, var5);
            var5.setLastAttackMillis(System.currentTimeMillis());
            var5.setLastYawAtAttack(var2.getLocation().getYaw());
         }
      }
   }

   private void checkReach(Player var1, LivingEntity var2, PlayerData var3) {
      CheckConfig var4 = this.plugin.configManager().check("combat.reach");
      if (var4 != null && var4.enabled()) {
         double var5 = var4.setting("max-reach-distance", 3.3);
         double var7 = var1.getEyeLocation().distance(var2.getEyeLocation());
         if (var7 > var5) {
            this.plugin.violationManager().flag(var1, "combat.reach", String.format("%.2f blocks", var7));
         }
      }
   }

   private void checkKillAura(Player var1, LivingEntity var2, PlayerData var3) {
      CheckConfig var5 = this.plugin.configManager().check("combat.killaura");
      if (var5 != null && var5.enabled()) {
         double var6 = var5.setting("max-attack-angle", 70.0);
         double var8 = this.angleBetween(var1, var2);
         boolean var10 = var8 > var6;
         long var11 = System.currentTimeMillis();
         Map var13 = var3.recentTargets();
         var13.put(var2.getUniqueId(), var11);
         Iterator var14 = var13.entrySet().iterator();

         while (var14.hasNext()) {
            if (var11 - (Long)((Entry)var14.next()).getValue() > 1000L) {
               var14.remove();
            }
         }

         int var15 = (int)var5.setting("max-targets-per-second", 4.0);
         boolean var4 = var13.size() > var15;
         if (var10 || var4) {
            String var17 = var10 ? String.format("%.0f° off-target", var8) : var13.size() + " targets/s";
            this.plugin.violationManager().flag(var1, "combat.killaura", var17);
         }
      }
   }

   private double angleBetween(Player var1, LivingEntity var2) {
      Location var3 = var1.getEyeLocation();
      Vector var4 = var3.getDirection().normalize();
      Vector var5 = var2.getEyeLocation().toVector().subtract(var3.toVector()).normalize();
      double var6 = Math.max(-1.0, Math.min(1.0, var4.dot(var5)));
      return Math.toDegrees(Math.acos(var6));
   }
}
