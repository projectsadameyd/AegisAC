package net.donutac.anticheat.violation;

import dev.aegisac.DonutEvidenceBridge;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import net.donutac.anticheat.config.PunishmentRule;
import net.donutac.anticheat.data.CheckState;
import net.donutac.anticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ViolationManager {
   private final DonutAntiCheat plugin;

   public ViolationManager(DonutAntiCheat var1) {
      this.plugin = var1;
   }

   public int flag(Player var1, String var2, String var3) {
      CheckConfig var4 = this.plugin.configManager().check(var2);
      if (var4 != null && var4.enabled()) {
         PlayerData var5 = this.plugin.playerDataManager().get(var1.getUniqueId());
         CheckState var6 = var5.state(var2);
         var6.increment(1);
         var5.incrementTotalViolations();
         int var7 = var6.violationLevel();
         this.alert(var1, var4, var7, var3);
         PunishmentRule var8 = var4.exactRule(var7);
         if (var8 != null && var6.highestPunishedThreshold() < var8.threshold()) {
            var6.markPunished(var8.threshold());
            this.plugin.punishmentManager().execute(var1, var4, var8, var7);
         }

         int var9 = var7;
         DonutEvidenceBridge.accept(var9, var1, var2, var3, this.plugin);
         return var9;
      } else {
         DonutEvidenceBridge.accept(0, var1, var2, var3, this.plugin);
         return 0;
      }
   }

   private void alert(Player var1, CheckConfig var2, int var3, String var4) {
   }

   public void startDecayTask() {
      long var1 = Math.max(1, this.plugin.configManager().decayIntervalSeconds()) * 20L;
      Bukkit.getScheduler().runTaskTimer(this.plugin, this::decayAll, var1, var1);
   }

   private void decayAll() {
      long var1 = this.plugin.configManager().decayIdleSeconds() * 1000L;
      int var3 = this.plugin.configManager().decayAmount();
      long var4 = System.currentTimeMillis();

      for (PlayerData var7 : this.plugin.playerDataManager().all().values()) {
         for (CheckState var9 : var7.checkStates().values()) {
            if (var9.violationLevel() > 0 && var4 - var9.lastViolationMillis() >= var1) {
               var9.decay(var3);
            }
         }
      }
   }
}
