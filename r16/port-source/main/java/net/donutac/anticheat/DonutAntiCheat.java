package net.donutac.anticheat;

import dev.aegisac.SensorPlugin;
import net.donutac.anticheat.ban.BanManager;
import net.donutac.anticheat.config.ConfigManager;
import net.donutac.anticheat.data.PlayerDataManager;
import net.donutac.anticheat.gui.GUIListener;
import net.donutac.anticheat.integration.DiscordWebhook;
import net.donutac.anticheat.integration.LuckPermsHook;
import net.donutac.anticheat.listener.CombatListener;
import net.donutac.anticheat.listener.ConnectionListener;
import net.donutac.anticheat.listener.InventoryListener;
import net.donutac.anticheat.listener.MovementListener;
import net.donutac.anticheat.listener.WorldListener;
import net.donutac.anticheat.offense.OffenseManager;
import net.donutac.anticheat.punishment.PunishmentManager;
import net.donutac.anticheat.violation.ViolationManager;
import org.bukkit.plugin.PluginManager;

public final class DonutAntiCheat extends SensorPlugin {
   private static DonutAntiCheat instance;
   private ConfigManager configManager;
   private PlayerDataManager playerDataManager;
   private ViolationManager violationManager;
   private PunishmentManager punishmentManager;
   private BanManager banManager;
   private OffenseManager offenseManager;
   private DiscordWebhook discordWebhook;
   private LuckPermsHook luckPermsHook;

   @Override
   public void onEnable() {
      instance = this;
      this.saveDefaultConfig();
      this.configManager = new ConfigManager(this);
      this.playerDataManager = new PlayerDataManager();
      this.banManager = new BanManager(this);
      this.offenseManager = new OffenseManager(this);
      this.discordWebhook = new DiscordWebhook(this);
      this.luckPermsHook = new LuckPermsHook(this);
      this.punishmentManager = new PunishmentManager(this);
      this.violationManager = new ViolationManager(this);
      this.registerListeners();
      this.violationManager.startDecayTask();
      this.getLogger().info("AegisAC enabled - keeping your server fair and fun.");
   }

   @Override
   public void onDisable() {
      if (this.banManager != null) {
         this.banManager.save();
      }

      this.getLogger().info("AegisAC disabled.");
   }

   private void registerListeners() {
      PluginManager var1 = this.getServer().getPluginManager();
      var1.registerEvents(new MovementListener(this), this);
      var1.registerEvents(new CombatListener(this), this);
      var1.registerEvents(new InventoryListener(this), this);
      var1.registerEvents(new WorldListener(this), this);
      var1.registerEvents(new ConnectionListener(this), this);
      var1.registerEvents(new GUIListener(this), this);
   }

   public void reload() {
      this.reloadConfig();
      this.configManager.reload();
   }

   public static DonutAntiCheat get() {
      return instance;
   }

   public ConfigManager configManager() {
      return this.configManager;
   }

   public PlayerDataManager playerDataManager() {
      return this.playerDataManager;
   }

   public ViolationManager violationManager() {
      return this.violationManager;
   }

   public PunishmentManager punishmentManager() {
      return this.punishmentManager;
   }

   public BanManager banManager() {
      return this.banManager;
   }

   public OffenseManager offenseManager() {
      return this.offenseManager;
   }

   public DiscordWebhook discordWebhook() {
      return this.discordWebhook;
   }

   public LuckPermsHook luckPermsHook() {
      return this.luckPermsHook;
   }
}
