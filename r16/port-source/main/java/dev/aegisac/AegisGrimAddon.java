package dev.aegisac;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import ac.grim.grimac.api.event.events.FlagEvent;
import ac.grim.grimac.checks.Check;
import ac.grim.grimac.player.GrimPlayer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.donutac.anticheat.DonutAntiCheat;
import net.donutac.anticheat.config.CheckConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;

public final class AegisGrimAddon implements Listener, CommandExecutor {
   private static volatile AegisGrimAddon INSTANCE;
   private final AegisPlugin plugin;
   private final Path dataFolder;
   private final File configFile;
   private final File sanctionsFile;
   private final ConfirmedHistory history;
   private final AegisAdminCommands admin;
   private final SanctionDispatcher dispatcher = new SanctionDispatcher();
   private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> lastWarning = new ConcurrentHashMap<>();
   private volatile long pausedUntil;
   private volatile boolean pauseResetPending;
   private final Map<UUID, Integer> tempBanCount = new ConcurrentHashMap<>();
   private final Map<UUID, Long> tempUntil = new ConcurrentHashMap<>();
   private final Map<UUID, String> lastName = new ConcurrentHashMap<>();
   private final Map<UUID, String> knownIps = new ConcurrentHashMap<>();
   private final Set<UUID> permanentUuids = ConcurrentHashMap.newKeySet();
   private final Set<String> permanentIps = ConcurrentHashMap.newKeySet();
   private final Object ioLock = new Object();
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
   private final ScheduledExecutorService async = Executors.newSingleThreadScheduledExecutor(var0 -> {
      Thread var1x = new Thread(var0, "AegisAC-Discord");
      var1x.setDaemon(true);
      return var1x;
   });
   private final BoundedDispatcher webhookDispatcher = new BoundedDispatcher(64);
   private volatile AegisGrimAddon.Settings settings;
   private DonutAntiCheat donut;

   private AegisGrimAddon(AegisPlugin var1) throws IOException {
      this.plugin = var1;
      this.dataFolder = var1.getDataFolder().toPath();
      this.configFile = this.dataFolder.resolve("aegis.yml").toFile();
      this.sanctionsFile = this.dataFolder.resolve("aegis-sanctions.properties").toFile();
      this.loadSettings();
      this.loadSanctions();
      this.history = new ConfirmedHistory(this.dataFolder.resolve("aegis-history.properties"), var1.getLogger());
      this.admin = new AegisAdminCommands(this);
   }

   public static void install(AegisPlugin var0) {
      try {
         AegisGrimAddon var1 = new AegisGrimAddon(var0);
         INSTANCE = var1;
         Bukkit.getPluginManager().registerEvents(var1, var0);
         var0.getCommand("aegis").setExecutor(var1);
         var0.getCommand("aegis").setTabCompleter(var1.admin);
         GrimAPI.INSTANCE.getEventBus().subscribe(GrimAPI.INSTANCE.getGrimPlugin(), CommandExecuteEvent.class, var1::onGrimCommand);
         GrimAPI.INSTANCE.getEventBus().subscribe(GrimAPI.INSTANCE.getGrimPlugin(), FlagEvent.class, var0x -> {
            if (var0x.getUser() != null && NativeSession.exempt(var0x.getUser().getUniqueId())) {
               var0x.setCancelled(true);
            }
         });
         var1.async.scheduleAtFixedRate(() -> {
            if (var1.settings.sendCreditEveryTenMinutes) {
               var1.postWebhook("AegisAC", "Made by @_adam814", 16766720);
            }
         }, 10L, 10L, TimeUnit.MINUTES);
      } catch (IOException var2) {
         throw new IllegalStateException("Native action initialization failed", var2);
      }
   }

   public static void shutdown() {
      AegisGrimAddon var0 = INSTANCE;
      INSTANCE = null;
      NativeSession.clear();
      if (var0 != null) {
         var0.saveSanctions();
         var0.history.close();
         var0.async.shutdownNow();
      }
   }

   public static void connectDonut(DonutAntiCheat var0) {
      if (INSTANCE != null) {
         INSTANCE.donut = var0;
      }
   }

   private void onGrimCommand(CommandExecuteEvent var1) {
      if (var1 != null && !var1.isCancelled()) {
         String var2 = NativeOutputBridge.action(var1);
         if (Set.of("[alert]", "[webhook]", "[proxy]").contains(var2)) {
            var1.setCancelled(true);
         } else if ("[aegis]".equals(var2)) {
            var1.setCancelled(true);
            if (var1.getCheck() instanceof Check var3
               && !"Simulation".equalsIgnoreCase(var3.getCheckName())
               && !"TimerLimit".equalsIgnoreCase(var3.getCheckName())
               && var3.isEnabled()
               && !var3.isExperimental()
               && !var3.isExemptPermission()) {
               UUID var6 = var1.getUser().getUniqueId();
               String var5 = var1.getUser().getName();
               this.nativeAction(var6, var5, "AegisAC", var3.getCheckName(), var1.getVerbose(), var3);
            }
         }
      }
   }

   private void nativeAction(UUID var1, String var2, String var3, String var4, String var5, Check var6) {
      if (!this.reviewPaused(System.currentTimeMillis()) && !NativeSession.exempt(var1) && !NativeSession.resetting(var1)) {
         this.dispatcher
            .submit(
               var1,
               var1x -> Bukkit.getScheduler().runTask(this.plugin, var1x),
               () -> {
                  Player var7 = Bukkit.getPlayer(var1);
                  if (var7 != null
                     && var7.isOnline()
                     && !this.reviewPaused(System.currentTimeMillis())
                     && !NativeSession.exempt(var1)
                     && !NativeSession.resetting(var1)
                     && !var7.hasPermission("grim.exempt")) {
                     if (var6 == null || var6.isEnabled() && !var6.isExemptPermission() && !var6.isExperimental()) {
                        this.remember(var7);
                        long var8 = System.currentTimeMillis();
                        if (var8 - this.lastWarning.getOrDefault(var1, 0L) >= 2000L || this.settings.autoBanAccurateChecks) {
                           this.lastWarning.put(var1, var8);
                           this.history.append(var1, var2, "DETECTION", var3 + ": " + var4);
                           this.plugin.getLogger().warning("Confirmed native detection: " + var2 + " (" + var3 + ": " + var4 + ")");
                           this.notifyStaff(var2, var4);
                           if (this.settings.warningsEnabled) {
                              this.deliverWarning(var7, var4, false);
                           }

                           if (this.settings.sendCheatAlerts) {
                              this.postWebhook(
                                 "Cheat Detection",
                                 "**Player:** " + var2 + "\n**Engine:** " + var3 + "\n**Check:** " + var4 + "\n**Details:** " + trim(var5, 350),
                                 16766720
                              );
                           }

                           if (this.settings.autoBanAccurateChecks) {
                              this.applyBan(var1, var2, var4, "Original native punishment threshold", var7);
                           }
                        }
                     }
                  }
               }
            );
      }
   }

   public static void acceptDonut(Player var0, String var1, int var2, String var3, DonutAntiCheat var4) {
      AegisGrimAddon var5 = INSTANCE;
      if (var5 != null && var1 != null && !var5.reviewPaused(System.currentTimeMillis()) && !NativeSession.exempt(var0.getUniqueId())) {
         if (var1.equals("combat.killaura")) {
            CheckConfig var6 = var4.configManager().check(var1);
            if (var6 != null && var6.exactRule(var2) != null) {
               var5.plugin.getLogger().warning("Assistant staff review warning: " + var0.getName() + " (" + var1 + ", VL=" + var2 + ")");
               var5.postWebhook(
                  "AegisAC Review Warning",
                  "**Player:** " + var0.getName() + "\n**Check:** " + var1 + "\n**VL:** " + var2 + "\n**Details:** " + trim(var3, 350),
                  16766720
               );
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void login(PlayerLoginEvent var1) {
      Player var2 = var1.getPlayer();
      UUID var3 = var2.getUniqueId();
      String var4 = var1.getAddress().getHostAddress();
      if (!this.permanentUuids.contains(var3) && !this.permanentIps.contains(var4)) {
         long var5 = this.tempUntil.getOrDefault(var3, 0L);
         if (var5 > System.currentTimeMillis()) {
            var1.disallow(Result.KICK_BANNED, this.tempBanScreen(this.tempBanCount.getOrDefault(var3, 1), remaining(var5), "Active AegisAC ban"));
         }

         if (var1.getResult() == Result.ALLOWED) {
            this.lastName.put(var3, var2.getName());
            this.knownIps.put(var3, var4);
         }
      } else {
         var1.disallow(Result.KICK_BANNED, this.permanentBanScreen("Repeated cheating"));
      }
   }

   @EventHandler
   public void quit(PlayerQuitEvent var1) {
      UUID var2 = var1.getPlayer().getUniqueId();
      this.dispatcher.forget(var2);
      NativeSession.forget(var2);
      this.lastWarning.remove(var2);
      this.alertsOff.remove(var2);
   }

   private void remember(Player var1) {
      this.lastName.put(var1.getUniqueId(), var1.getName());
      if (var1.getAddress() != null && var1.getAddress().getAddress() != null) {
         this.knownIps.put(var1.getUniqueId(), var1.getAddress().getAddress().getHostAddress());
      }
   }

   private void notifyStaff(String var1, String var2) {
      for (Player var4 : Bukkit.getOnlinePlayers()) {
         if (var4.hasPermission("aegisac.alerts") && !this.alertsOff.contains(var4.getUniqueId())) {
            var4.sendMessage(color("&6&lAegisAC &8» &eConfirmed detection: &f" + var1 + " &7(" + var2 + ")"));
         }
      }
   }

   private void deliverWarning(Player var1, String var2, boolean var3) {
      String var4 = var3 ? "Staff Warning" : "Cheat Detected";
      BukkitCompatibility.sendTitle(var1, color("&6&lAegisAC"), color("&e&l" + var4 + " &8• &f" + trim(var2, 32)), 5, 35, 10);
      var1.sendMessage(
         color(
            "&8&m--------------------------------\n&6&lAegisAC &8» &e"
               + var4
               + "\n&7Reason: &f"
               + trim(var2, 120)
               + "\n&7Appeal: &f"
               + this.settings.appealUrl
               + "\n&8&m--------------------------------"
         )
      );
   }

   void staffWarn(Player var1, String var2) {
      this.deliverWarning(var1, var2, true);
      this.history.append(var1.getUniqueId(), var1.getName(), "STAFF_WARNING", var2);
      this.postWebhook("Staff Warning", var1.getName() + ": " + var2, 16766720);
   }

   void staffKick(Player var1, String var2) {
      this.history.append(var1.getUniqueId(), var1.getName(), "STAFF_KICK", var2);
      var1.kickPlayer(
         color(
            "&6&lAegisAC\n&8&m------------------------------\n&e&lDisconnected by Staff\n\n&7Reason: &f"
               + trim(var2, 120)
               + "\n&7Appeal: &f"
               + this.settings.appealUrl
         )
      );
      this.postWebhook("Staff Kick", var1.getName() + ": " + var2, 16766720);
   }

   private void applyBan(UUID var1, String var2, String var3, String var4, Player var5) {
      this.history.append(var1, var2, "BAN", var3);
      int var6 = this.tempBanCount.getOrDefault(var1, 0);
      if (var6 >= this.settings.permanentIpAfterTempBans) {
         this.permanentUuids.add(var1);
         String var10 = this.knownIps.get(var1);
         if (var10 != null) {
            this.permanentIps.add(var10);
         }

         this.tempUntil.remove(var1);
         this.saveSanctions();
         if (var5 != null) {
            var5.kickPlayer(this.permanentBanScreen(var3));
         }

         this.postWebhook("Sanction Applied", var2 + "\n**Action:** Permanent IP Ban\n**Reason:** " + var3, 15158332);
      } else {
         int var7 = var6 + 1;
         long var8 = this.settings.tempBanMinutes[Math.min(var7 - 1, this.settings.tempBanMinutes.length - 1)];
         this.tempBanCount.put(var1, var7);
         this.tempUntil.put(var1, System.currentTimeMillis() + var8 * 60000L);
         this.saveSanctions();
         if (var5 != null) {
            var5.kickPlayer(this.tempBanScreen(var7, humanDuration(var8), var3));
         }

         this.postWebhook(
            "Sanction Applied",
            var2
               + "\n**Action:** Temporary Ban "
               + var7
               + "/"
               + this.settings.permanentIpAfterTempBans
               + "\n**Duration:** "
               + humanDuration(var8)
               + "\n**Reason:** "
               + var3,
            16766720
         );
      }
   }

   private String tempBanScreen(int var1, String var2, String var3) {
      return color(
         "&6&lAegisAC\n&8&m------------------------------\n&e&lCheat Detected\n\n&7Action: &fTemporary Ban &7("
            + var1
            + "/"
            + this.settings.permanentIpAfterTempBans
            + ")\n&7Duration: &f"
            + var2
            + "\n&7Reason: &f"
            + trim(var3, 50)
            + "\n\n&7Made by: &f@_adam814\n&7Appeal: &f"
            + this.settings.appealUrl
            + "\n&8&m------------------------------"
      );
   }

   private String permanentBanScreen(String var1) {
      return color(
         "&6&lAegisAC\n&8&m------------------------------\n&c&lPermanent IP Ban\n\n&7Reason: &f"
            + trim(var1, 50)
            + "\n&7Repeated cheating after "
            + this.settings.permanentIpAfterTempBans
            + " temporary bans.\n\n&7Made by: &f@_adam814\n&7Appeal: &f"
            + this.settings.appealUrl
            + "\n&8&m------------------------------"
      );
   }

   static void connectionBlocked(String var0, String var1) {
      AegisGrimAddon var2 = INSTANCE;
      if (var2 != null) {
         var2.postWebhook(
            "Connection Blocked",
            "**Player:** " + var0 + "\n**Reason:** " + var1 + "\n**Action:** Login refused. Disable the VPN/proxy and reconnect.",
            15158332
         );
      }
   }

   String vpnStatus() {
      return this.plugin.vpn() == null ? "VPN/proxy screening unavailable" : this.plugin.vpn().status();
   }

   void vpnCommand(CommandSender var1, String[] var2) throws IOException {
      if (this.plugin.vpn() == null) {
         throw new IllegalArgumentException("VPN/proxy screening unavailable. Correct anti-vpn.yml, then /aegis reload.");
      }

      this.plugin.vpn().command(var1, var2);
   }

   @Override
   public boolean onCommand(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var1.hasPermission("aegisac.admin") && !var1.hasPermission("aegisgrim.admin")) {
         var1.sendMessage(color("&cNo permission."));
         return true;
      }

      if (var4.length > 0 && this.admin.handle(var1, var4)) {
         return true;
      }

      String var5 = var4.length == 0 ? "status" : var4[0].toLowerCase(Locale.ROOT);

      try {
         switch (var5) {
            case "status":
            case "engine":
               var1.sendMessage(
                  color(
                     "&6&lAegisAC &8» &fR16-VPNGUARD1\n&7Detection: &fActive\n&7Packet prevention and setbacks: &fActive\n&7Assistant: &f"
                        + (this.donut == null ? "disconnected" : "connected")
                        + "\n&7"
                        + this.vpnStatus()
                        + "\n&7Webhook: &f"
                        + (this.settings.webhookUrl.isBlank() ? "not configured" : "configured")
                  )
               );
               break;
            case "reload":
               this.loadSettings();
               this.plugin.reloadConnectionGuard();
               if (this.donut != null) {
                  this.donut.reload();
               }

               NativeConfig.migrateTimerSafety(this.dataFolder);
               PresentationConfig.migrate(this.dataFolder);
               var1.sendMessage("AegisAC settings reloaded; core reload in progress.");
               GrimAPI.INSTANCE
                  .getExternalAPI()
                  .reloadAsync(GrimAPI.INSTANCE.getExternalAPI().getConfigManager())
                  .whenComplete(
                     (var2x, var3x) -> {
                        if (this.plugin.isEnabled()) {
                           Bukkit.getScheduler()
                              .runTask(
                                 this.plugin,
                                 () -> var1.sendMessage(
                                    Boolean.TRUE.equals(var2x) && var3x == null
                                       ? "AegisAC core configuration reloaded."
                                       : "AegisAC core reload failed; check the console."
                                 )
                              );
                        }
                     }
                  );
               break;
            case "webhook":
               if (var4.length < 2) {
                  throw new IllegalArgumentException("Usage: /aegis webhook <URL|off>");
               }

               String var13 = var4[1].equalsIgnoreCase("off") ? "" : var4[1];
               if (!var13.isBlank() && !validDiscordWebhook(var13)) {
                  throw new IllegalArgumentException("Enter a valid Discord HTTPS webhook URL.");
               }

               YamlConfiguration var16 = YamlConfiguration.loadConfiguration(this.configFile);
               var16.set("webhook-url", var13);
               var16.save(this.configFile);
               this.loadSettings();
               configureBranding();
               if (!var13.isBlank()) {
                  this.postWebhook("Webhook Connected", "Native detections and sanctions will be posted here.", 16766720);
               }

               var1.sendMessage("Discord webhook updated.");
               break;
            case "ban":
               UUID var12 = this.target(var4);
               Player var15 = Bukkit.getPlayer(var12);
               if (var15 != null) {
                  this.remember(var15);
               }

               this.applyBan(
                  var12,
                  this.lastName.getOrDefault(var12, var12.toString()),
                  var4.length > 2 ? String.join(" ", Arrays.copyOfRange(var4, 2, var4.length)) : "Staff decision",
                  "Staff decision",
                  var15
               );
               var1.sendMessage("Staff ban applied.");
               break;
            case "unban":
            case "reset":
               UUID var11 = this.known(var4.length > 1 ? var4[1] : "");
               if (var11 == null) {
                  if (var4.length <= 1) {
                     throw new IllegalArgumentException("Supply a player, UUID or IP.");
                  }

                  String var9 = var4[1];
                  this.permanentIps.remove(var9);
                  this.knownIps.forEach((var2x, var3x) -> {
                     if (var3x.equals(var9)) {
                        this.permanentUuids.remove(var2x);
                        this.tempUntil.remove(var2x);
                     }
                  });
               } else {
                  this.permanentUuids.remove(var11);
                  this.tempUntil.remove(var11);
                  String var14 = this.knownIps.get(var11);
                  if (var14 != null) {
                     this.permanentIps.remove(var14);
                  }

                  if (var5.equals("reset")) {
                     this.tempBanCount.remove(var11);
                     this.clearNative(var11);
                  }
               }

               this.saveSanctions();
               var1.sendMessage("Sanction removed.");
               break;
            case "unbanip":
               if (var4.length < 2) {
                  throw new IllegalArgumentException("Supply an IP.");
               }

               this.permanentIps.remove(var4[1]);
               this.saveSanctions();
               var1.sendMessage("Permanent IP entry removed.");
               break;
            case "ipcheck":
               UUID var8 = this.target(var4);
               var1.sendMessage("IP=" + this.knownIps.getOrDefault(var8, "unknown") + " Permanent=" + this.permanentUuids.contains(var8));
               break;
            default:
               var1.sendMessage("Use /aegis help.");
         }
      } catch (IllegalArgumentException | IOException var10) {
         var1.sendMessage(var10.getMessage());
      }

      return true;
   }

   private UUID target(String[] var1) {
      if (var1.length < 2) {
         throw new IllegalArgumentException("Supply a known player or UUID.");
      } else {
         UUID var2 = this.known(var1[1]);
         if (var2 == null) {
            throw new IllegalArgumentException("Unknown player.");
         } else {
            return var2;
         }
      }
   }

   UUID known(String var1) {
      try {
         return UUID.fromString(var1);
      } catch (IllegalArgumentException var5) {
         Player var2 = Bukkit.getPlayerExact(var1);
         if (var2 != null) {
            this.remember(var2);
            return var2.getUniqueId();
         }

         for (Entry var4 : this.lastName.entrySet()) {
            if (((String)var4.getValue()).equalsIgnoreCase(var1)) {
               return (UUID)var4.getKey();
            }
         }

         return null;
      }
   }

   File folder() {
      return this.dataFolder.toFile();
   }

   ConfirmedHistory history() {
      return this.history;
   }

   int nativeTracked() {
      return GrimAPI.INSTANCE.getPlayerDataManager().getEntries().size();
   }

   int pendingReviews() {
      return this.dispatcher.pending();
   }

   String profile(UUID var1) {
      long var2 = this.tempUntil.getOrDefault(var1, 0L);
      return this.lastName.getOrDefault(var1, var1.toString())
         + " stage="
         + this.tempBanCount.getOrDefault(var1, 0)
         + " permanent="
         + this.permanentUuids.contains(var1)
         + " remaining="
         + remaining(var2);
   }

   List<UUID> activeBans() {
      TreeSet var1 = new TreeSet<>(this.permanentUuids);
      this.tempUntil.forEach((var1x, var2) -> {
         if (var2 > System.currentTimeMillis()) {
            var1.add(var1x);
         }
      });
      return new ArrayList<>(var1);
   }

   boolean alerts(UUID var1, boolean var2) {
      if (var2) {
         this.alertsOff.remove(var1);
      } else {
         this.alertsOff.add(var1);
      }

      return var2;
   }

   boolean reviewPaused(long var1) {
      if (this.pausedUntil > 0L && var1 >= this.pausedUntil) {
         synchronized (this) {
            if (this.pausedUntil > 0L && var1 >= this.pausedUntil) {
               this.pauseResetPending = true;
               this.pausedUntil = 0L;
               this.dispatcher.clear();
               Bukkit.getScheduler().runTask(this.plugin, () -> {
                  try {
                     this.clearAllNative();
                  } finally {
                     this.pauseResetPending = false;
                  }
               });
            }
         }
      }

      return this.pausedUntil > var1 || this.pauseResetPending;
   }

   void pause(long var1) {
      this.pausedUntil = var1;
      this.dispatcher.clear();
      this.clearAllNative();
   }

   void resume() {
      this.pauseResetPending = true;
      this.pausedUntil = 0L;
      this.dispatcher.clear();
      this.lastWarning.clear();
      this.clearAllNative();
      this.pauseResetPending = false;
   }

   private void clearAllNative() {
      for (GrimPlayer var2 : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
         this.clearNative(var2.uuid);
      }
   }

   private void clearNative(UUID var1) {
      GrimPlayer var2 = null;

      for (GrimPlayer var4 : GrimAPI.INSTANCE.getPlayerDataManager().getEntries()) {
         if (var4.uuid.equals(var1)) {
            var2 = var4;
            break;
         }
      }

      if (var2 != null) {
         GrimPlayer var7 = var2;
         Object var8 = NativeSession.beginReset(var1);

         try {
            var7.runSafely(() -> {
               try {
                  for (AbstractCheck var4x : var7.checkManager.checks) {
                     ((Check)var4x).violations = 0.0;
                  }

                  var7.punishmentManager.reload(GrimAPI.INSTANCE.getConfigManager().getConfig());
               } finally {
                  NativeSession.finishReset(var1, var8);
               }
            });
         } catch (RuntimeException var6) {
            NativeSession.finishReset(var1, var8);
            this.plugin.getLogger().warning("Native counter reset unavailable: " + var6.getClass().getSimpleName());
         }
      }
   }

   private void loadSettings() throws IOException {
      YamlConfiguration var1 = YamlConfiguration.loadConfiguration(this.configFile);
      boolean var2 = false;
      LinkedHashMap<String, Object> var3 = new LinkedHashMap<>();
      var3.put("webhook-url", "");
      var3.put("send-cheat-alerts", true);
      var3.put("send-credit-every-10-minutes", true);
      var3.put("warnings-enabled", true);
      var3.put("auto-ban-accurate-checks", true);
      var3.put("appeal-url", "https://discord.gg/ShtDEU2x3z");
      var3.put("permanent-ip-after-temp-bans", 3);
      var3.put(
         "temp-ban-minutes",
         List.of(var1.getLong("temp-ban-1-minutes", 60L), var1.getLong("temp-ban-2-minutes", 360L), var1.getLong("temp-ban-3-minutes", 1440L))
      );

      for (Entry<String, Object> var5 : var3.entrySet()) {
         if (!var1.contains((String)var5.getKey())) {
            var1.set((String)var5.getKey(), var5.getValue());
            var2 = true;
         }
      }

      if (var2) {
         var1.save(this.configFile);
      }

      this.settings = new AegisGrimAddon.Settings(var1);
   }

   private void loadSanctions() {
      if (this.sanctionsFile.exists()) {
         Properties var1 = new Properties();

         try (FileInputStream var2 = new FileInputStream(this.sanctionsFile)) {
            var1.load(var2);

            for (String var4 : var1.stringPropertyNames()) {
               String var5 = var1.getProperty(var4);

               try {
                  if (var4.startsWith("count.")) {
                     this.tempBanCount.put(UUID.fromString(var4.substring(6)), Integer.parseInt(var5));
                  } else if (var4.startsWith("until.")) {
                     this.tempUntil.put(UUID.fromString(var4.substring(6)), Long.parseLong(var5));
                  } else if (var4.startsWith("knownip.")) {
                     this.knownIps.put(UUID.fromString(var4.substring(8)), var5);
                  } else if (var4.startsWith("name.")) {
                     this.lastName.put(UUID.fromString(var4.substring(5)), var5);
                  } else if (var4.startsWith("permuuid.")) {
                     this.permanentUuids.add(UUID.fromString(var4.substring(9)));
                  } else if (var4.startsWith("ip.") && Boolean.parseBoolean(var5)) {
                     this.permanentIps.add(var4.substring(3));
                  }
               } catch (RuntimeException var8) {
               }
            }
         } catch (IOException var10) {
            this.plugin.getLogger().warning("Could not load sanctions: " + var10.getMessage());
         }
      }
   }

   private void saveSanctions() {
      synchronized (this.ioLock) {
         Properties var2 = new Properties();
         this.tempBanCount.forEach((var1, var2x) -> var2.setProperty("count." + var1, var2x + ""));
         this.tempUntil.forEach((var1, var2x) -> var2.setProperty("until." + var1, var2x + ""));
         this.lastName.forEach((var1, var2x) -> var2.setProperty("name." + var1, var2x));
         this.knownIps.forEach((var1, var2x) -> var2.setProperty("knownip." + var1, var2x));
         this.permanentUuids.forEach(var1 -> var2.setProperty("permuuid." + var1, "true"));
         this.permanentIps.forEach(var1 -> var2.setProperty("ip." + var1, "true"));

         try {
            Path var3 = this.sanctionsFile.toPath().resolveSibling(this.sanctionsFile.getName() + ".tmp");

            try (OutputStream var4 = Files.newOutputStream(var3)) {
               var2.store(var4, "AegisAC sanctions - Made by @_adam814");
            }

            try {
               Files.move(var3, this.sanctionsFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException var9) {
               Files.move(var3, this.sanctionsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         } catch (IOException var11) {
            this.plugin.getLogger().warning("Could not save sanctions: " + var11.getMessage());
         }
      }
   }

   static boolean validDiscordWebhook(String var0) {
      try {
         URI var1 = URI.create(var0);
         return "https".equals(var1.getScheme())
            && Set.of("discord.com", "discordapp.com").contains(var1.getHost())
            && var1.getUserInfo() == null
            && var1.getPort() == -1
            && var1.getPath().matches("/api(?:/v[0-9]+)?/webhooks/[0-9]+/[A-Za-z0-9_.-]+");
      } catch (RuntimeException var2) {
         return false;
      }
   }

   public static void configureBranding() {
      AegisGrimAddon var0 = INSTANCE;
      if (var0 != null && validDiscordWebhook(var0.settings.webhookUrl)) {
         var0.webhookDispatcher.submit(var0.async, () -> {
            try (InputStream var1 = var0.plugin.getResource("aegis-logo.png")) {
               String var2 = "{\"name\":\"AegisAC\",\"avatar\":\"data:image/png;base64," + Base64.getEncoder().encodeToString(var1.readAllBytes()) + "\"}";
               var0.sendHttp("PATCH", var2);
            } catch (Exception var6) {
               var0.plugin.getLogger().warning("Webhook branding unavailable: " + var6.getClass().getSimpleName());
            }
         });
      }
   }

   private void postWebhook(String var1, String var2, int var3) {
      if (validDiscordWebhook(this.settings.webhookUrl)) {
         String var4 = "{\"username\":\"AegisAC\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{\"title\":\""
            + json(var1)
            + "\",\"description\":\""
            + json(var2)
            + "\",\"color\":"
            + var3
            + ",\"footer\":{\"text\":\"AegisAC • Made by @_adam814\"},\"timestamp\":\""
            + Instant.now()
            + "\"}]}";
         this.webhookDispatcher.submit(this.async, () -> this.sendHttp("POST", var4));
      }
   }

   private void sendHttp(String var1, String var2) {
      String var3 = this.settings.webhookUrl;
      if (validDiscordWebhook(var3)) {
         try {
            HttpResponse var4 = this.http
               .send(
                  HttpRequest.newBuilder(URI.create(var3))
                     .timeout(Duration.ofSeconds(8L))
                     .header("Content-Type", "application/json")
                     .method(var1, BodyPublishers.ofString(var2))
                     .build(),
                  BodyHandlers.discarding()
               );
            if (var4.statusCode() / 100 != 2) {
               this.plugin.getLogger().warning("Discord webhook HTTP " + var4.statusCode());
            }
         } catch (Exception var5) {
            this.plugin.getLogger().warning("Discord webhook failed: " + var5.getClass().getSimpleName());
         }
      }
   }

   private static String json(String var0) {
      StringBuilder var1 = new StringBuilder();

      for (char var5 : String.valueOf(var0).toCharArray()) {
         if (var5 == '"' || var5 == '\\') {
            var1.append('\\').append(var5);
         } else if (var5 == '\n') {
            var1.append("\\n");
         } else if (var5 == '\r') {
            var1.append("\\r");
         } else if (var5 < ' ') {
            var1.append(String.format("\\u%04x", Integer.valueOf(var5)));
         } else {
            var1.append(var5);
         }
      }

      return var1.toString();
   }

   private static String color(String var0) {
      return ChatColor.translateAlternateColorCodes('&', var0);
   }

   private static String trim(String var0, int var1) {
      if (var0 == null) {
         return "";
      } else {
         return var0.length() > var1 ? var0.substring(0, var1) : var0;
      }
   }

   private static String humanDuration(long var0) {
      return var0 % 1440L == 0L ? var0 / 1440L + "d" : (var0 % 60L == 0L ? var0 / 60L + "h" : var0 + "m");
   }

   private static String remaining(long var0) {
      return var0 <= System.currentTimeMillis() ? "none" : humanDuration(Math.max(1L, (var0 - System.currentTimeMillis() + 59999L) / 60000L));
   }

   private static final class Settings {
      final String webhookUrl;
      final String appealUrl;
      final boolean sendCheatAlerts;
      final boolean sendCreditEveryTenMinutes;
      final boolean warningsEnabled;
      final boolean autoBanAccurateChecks;
      final int permanentIpAfterTempBans;
      final long[] tempBanMinutes;

      Settings(YamlConfiguration var1) {
         this.webhookUrl = var1.getString("webhook-url", "");
         this.appealUrl = var1.getString("appeal-url", "https://discord.gg/ShtDEU2x3z");
         this.sendCheatAlerts = var1.getBoolean("send-cheat-alerts", true);
         this.sendCreditEveryTenMinutes = var1.getBoolean("send-credit-every-10-minutes", true);
         this.warningsEnabled = var1.getBoolean("warnings-enabled", true);
         this.autoBanAccurateChecks = var1.getBoolean("auto-ban-accurate-checks", true);
         this.permanentIpAfterTempBans = Math.max(1, Math.min(20, var1.getInt("permanent-ip-after-temp-bans", 3)));
         List<Long> var2 = var1.getLongList("temp-ban-minutes");
         if (var2.isEmpty()) {
            var2 = List.of(60L, 360L, 1440L);
         }

         this.tempBanMinutes = var2.stream().mapToLong(var0 -> Math.max(1L, Math.min(var0, 525600L))).toArray();
      }
   }
}
