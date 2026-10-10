package dev.aegisac;

import ac.grim.grimac.platform.bukkit.GrimACBukkitLoaderPlugin;
import java.io.File;
import java.io.IOException;
import net.donutac.anticheat.DonutAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.HandlerList;

public final class AegisPlugin extends GrimACBukkitLoaderPlugin {
   private static AegisPlugin instance;
   private DonutAntiCheat donut;
   private VpnGate vpn;

   public static AegisPlugin instance() {
      return instance;
   }

   public DonutAntiCheat donut() {
      return this.donut;
   }

   VpnGate vpn() {
      return this.vpn;
   }

   @Override
   public void onLoad() {
      instance = this;
      NativeConfig.install(this);

      try {
         PresentationConfig.migrate(this.getDataFolder().toPath());
      } catch (IOException var2) {
         throw new IllegalStateException("Cannot migrate AegisAC presentation settings", var2);
      }

      super.onLoad();
   }

   void reloadConnectionGuard() throws IOException {
      if (this.vpn == null) {
         this.vpn = new VpnGate(this);
         Bukkit.getPluginManager().registerEvents(this.vpn, this);
      } else {
         this.vpn.reload();
      }
   }

   @Override
   public void onEnable() {
      super.onEnable();

      try {
         this.reloadConnectionGuard();
      } catch (IOException var2) {
         this.getLogger().warning("VPN/proxy screening unavailable; correct anti-vpn.yml and use /aegis reload.");
      }

      AegisGrimAddon.install(this);
      this.donut = new DonutAntiCheat();
      this.donut.onEnable();
      AegisGrimAddon.connectDonut(this.donut);
      AegisGrimAddon.configureBranding();
      if (!new File(this.getDataFolder(), "aegis-logo.png").exists()) {
         this.saveResource("aegis-logo.png", false);
      }

      this.getLogger().info("R16-VPNGUARD1: AegisAC detection and packet prevention active; engine commands disabled.");
      this.getServer()
         .getScheduler()
         .runTaskTimer(this, () -> this.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&eMade by @_adam814")), 12000L, 12000L);
   }

   @Override
   public void onDisable() {
      if (this.vpn != null) {
         HandlerList.unregisterAll(this.vpn);
         this.vpn.close();
         this.vpn = null;
      }

      if (this.donut != null) {
         HandlerList.unregisterAll(this.donut);
         this.getServer().getScheduler().cancelTasks(this.donut);
         this.donut.onDisable();
      }

      AegisGrimAddon.shutdown();

      try {
         super.onDisable();
      } finally {
         instance = null;
      }
   }
}
