package dev.aegisac;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;

final class VpnGate implements Listener, AutoCloseable {
   private final AegisPlugin plugin;
   private final File configFile;
   private volatile VpnGate.Snapshot current;
   private final AtomicLong blocks = new AtomicLong();
   private final AtomicLong unchecked = new AtomicLong();
   private final AtomicLong lastNotice = new AtomicLong(-4611686018427387904L);
   private final AtomicLong lastFailureNotice = new AtomicLong(-4611686018427387904L);

   VpnGate(AegisPlugin var1) throws IOException {
      this.plugin = var1;
      this.configFile = new File(var1.getDataFolder(), "anti-vpn.yml");
      this.reload();
   }

   VpnGate(AegisPlugin var1, File var2, VpnGate.Settings var3, VpnReputation var4) {
      this.plugin = var1;
      this.configFile = var2;
      this.current = new VpnGate.Snapshot(var3, var4);
   }

   synchronized void reload() throws IOException {
      if (!this.configFile.exists()) {
         this.plugin.saveResource("anti-vpn.yml", false);
      }

      VpnGate.Settings var1;
      try {
         var1 = VpnGate.Settings.from(readStrict(this.configFile));
      } catch (IllegalArgumentException var4) {
         throw new IOException(var4.getMessage(), var4);
      }

      VpnGate.Snapshot var2 = new VpnGate.Snapshot(var1, new VpnReputation(var1.policy(), new VpnReputation.HttpLookup()));
      VpnGate.Snapshot var3 = this.current;
      this.current = var2;
      if (var3 != null) {
         var3.service().close();
      }
   }

   static YamlConfiguration readStrict(File var0) throws IOException {
      YamlConfiguration var1 = new YamlConfiguration();

      try {
         var1.load(var0);
         return var1;
      } catch (InvalidConfigurationException var3) {
         throw new IOException("Invalid YAML in " + var0.getName(), var3);
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void preLogin(AsyncPlayerPreLoginEvent var1) {
      if (var1.getLoginResult() == Result.ALLOWED) {
         VpnGate.Snapshot var2 = this.current;
         if (var2 != null && var2.settings().enabled()) {
            VpnReputation.Decision var3;
            try {
               var3 = var2.service().screen(var1.getAddress()).get(var2.settings().policy().timeoutMillis() + 250L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException var5) {
               Thread.currentThread().interrupt();
               var3 = VpnReputation.Decision.unknown("Lookup interrupted");
            } catch (ExecutionException | TimeoutException var6) {
               var3 = VpnReputation.Decision.unknown("Lookup timed out or unavailable");
            }

            if (var3.outcome() == VpnReputation.Outcome.BLOCKED) {
               this.blocks.incrementAndGet();
               var1.disallow(Result.KICK_OTHER, message(var2.settings().blockedMessage().replace("{reason}", var3.reason())));
               if (notice(this.lastNotice)) {
                  String var4 = var1.getName().replaceAll("[^A-Za-z0-9_]", "?");
                  if (var2.settings().logBlocks() && this.plugin != null) {
                     this.plugin.getLogger().info("AegisAC blocked a VPN/proxy connection: " + var4 + " (" + var3.reason() + ")");
                  }

                  if (var2.settings().webhookBlocks()) {
                     AegisGrimAddon.connectionBlocked(var4, var3.reason());
                  }
               }
            } else if (var3.outcome() == VpnReputation.Outcome.UNKNOWN) {
               this.unchecked.incrementAndGet();
               if (!var2.settings().allowFailure()) {
                  var1.disallow(Result.KICK_OTHER, message(var2.settings().unavailableMessage()));
               }

               if (this.plugin != null && notice(this.lastFailureNotice)) {
                  this.plugin
                     .getLogger()
                     .warning(
                        "AegisAC connection verification unavailable; failure policy="
                           + (var2.settings().allowFailure() ? "allow" : "refuse")
                           + ". Check /aegis vpn status and anti-vpn.yml."
                     );
               }
            }
         }
      }
   }

   private static boolean notice(AtomicLong var0) {
      long var1 = System.nanoTime() / 1000000L;
      long var3 = var0.get();
      return var1 - var3 > 1000L && var0.compareAndSet(var3, var1);
   }

   String status() {
      VpnGate.Snapshot var1 = this.current;
      if (var1 == null) {
         return "VPN/proxy screening unavailable";
      }

      VpnReputation.Health var2 = var1.service().health();
      return "VPN/proxy screening="
         + (var1.settings().enabled() ? "enabled" : "disabled")
         + "; blocks="
         + this.blocks.get()
         + "; unchecked joins="
         + this.unchecked.get()
         + "; provider queries="
         + var2.queries()
         + "; cache hits="
         + var2.cacheHits()
         + "; cached IPs="
         + var2.cachedIps()
         + "; lookup failures="
         + var2.failures()
         + "; pending="
         + var2.pendingIps()
         + "; API key="
         + (var1.settings().policy().apiKey().isBlank() ? "not set (100 queries/day)" : "configured")
         + "; on lookup failure="
         + (var1.settings().allowFailure() ? "allow" : "refuse");
   }

   void command(CommandSender var1, String[] var2) throws IOException {
      String var3 = var2.length < 2 ? "status" : var2[1].toLowerCase(Locale.ROOT);
      switch (var3) {
         case "status":
            say(var1, this.status());
            break;
         case "on":
         case "off":
            this.edit(var1x -> var1x.set("enabled", var3.equals("on")));
            say(var1, "VPN/proxy screening " + (var3.equals("on") ? "enabled." : "disabled."));
            break;
         case "allow":
         case "unallow":
            if (var2.length != 3) {
               throw new IllegalArgumentException("Usage: /aegis vpn " + var3 + " <IP>");
            }

            String var10 = VpnReputation.literal(var2[2]).getHostAddress();
            this.edit(var2x -> {
               LinkedHashSet var3x = new LinkedHashSet();

               for (String var5 : var2x.getStringList("allowed-ips")) {
                  var3x.add(VpnReputation.literal(var5).getHostAddress());
               }

               if (var3.equals("allow")) {
                  var3x.add(var10);
               } else {
                  var3x.remove(var10);
               }

               var2x.set("allowed-ips", new ArrayList(var3x));
            });
            say(var1, var3.equals("allow") ? "IP added to the connection allowlist." : "IP removed from the connection allowlist.");
            break;
         case "clearcache":
            this.current.service().clearCache();
            say(var1, "Connection reputation cache cleared.");
            break;
         case "check":
            if (var2.length != 3) {
               throw new IllegalArgumentException("Usage: /aegis vpn check <IP|online-player>");
            }

            InetAddress var6;
            try {
               var6 = VpnReputation.literal(var2[2]);
            } catch (IllegalArgumentException var9) {
               Player var8 = Bukkit.getPlayerExact(var2[2]);
               if (var8 == null || var8.getAddress() == null) {
                  throw new IllegalArgumentException("Supply a literal IP or an online player.");
               }

               var6 = var8.getAddress().getAddress();
            }

            String var7 = var6.getHostAddress();
            say(var1, "Checking connection reputation...");
            this.current
               .service()
               .screen(var6)
               .whenComplete(
                  (var3x, var4) -> {
                     if (this.plugin.isEnabled()) {
                        Bukkit.getScheduler()
                           .runTask(
                              this.plugin, () -> say(var1, var7 + ": " + (var4 == null ? var3x.outcome() + " (" + var3x.reason() + ")" : "lookup unavailable"))
                           );
                     }
                  }
               );
            break;
         default:
            throw new IllegalArgumentException("Usage: /aegis vpn <status|on|off|check|allow|unallow|clearcache>");
      }
   }

   private synchronized void edit(Consumer<YamlConfiguration> var1) throws IOException {
      YamlConfiguration var2 = readStrict(this.configFile);
      var1.accept(var2);

      try {
         VpnGate.Settings.from(var2);
      } catch (IllegalArgumentException var4) {
         throw new IOException(var4.getMessage(), var4);
      }

      writeAtomic(var2, this.configFile.toPath());
      this.reload();
   }

   static void writeAtomic(YamlConfiguration var0, Path var1) throws IOException {
      Path var2 = var1.resolveSibling(var1.getFileName() + ".tmp");
      var0.save(var2.toFile());

      try {
         Files.move(var2, var1, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var4) {
         Files.move(var2, var1, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private static String message(String var0) {
      return ChatColor.translateAlternateColorCodes('&', var0.strip());
   }

   private static void say(CommandSender var0, String var1) {
      var0.sendMessage(message("&6&lAegisAC &8» &f" + var1));
   }

   @Override
   public synchronized void close() {
      VpnGate.Snapshot var1 = this.current;
      this.current = null;
      if (var1 != null) {
         var1.service().close();
      }
   }

   record Settings(
      boolean enabled,
      boolean allowFailure,
      boolean logBlocks,
      boolean webhookBlocks,
      String blockedMessage,
      String unavailableMessage,
      VpnReputation.Policy policy
   ) {
      static VpnGate.Settings from(YamlConfiguration var0) {
         for (String var2 : List.of("enabled", "block-vpn", "block-proxy", "block-tor", "allow-on-lookup-failure", "log-blocks", "webhook-blocks")) {
            if (var0.contains(var2) && !(var0.get(var2) instanceof Boolean)) {
               throw new IllegalArgumentException(var2 + " must be true or false.");
            }
         }

         int var7 = number(var0, "timeout-ms", 2000, 100, 10000);
         int var8 = number(var0, "cache-seconds", 600, 1, 86400);
         int var3 = number(var0, "error-cache-seconds", 10, 1, 300);
         int var4 = number(var0, "max-cache-entries", 2048, 1, 8192);
         if (var0.contains("allowed-ips") && !var0.isList("allowed-ips")) {
            throw new IllegalArgumentException("allowed-ips must be a list.");
         }

         if (var0.contains("api-key") && !(var0.get("api-key") instanceof String)) {
            throw new IllegalArgumentException("api-key must be text.");
         }

         String var5 = text(var0, "messages.blocked", "&6&lAegisAC\n&cVPN / Proxy Connection Blocked\n\n&7Disable your VPN or proxy and reconnect.");
         String var6 = text(var0, "messages.unavailable", "&6&lAegisAC\n&eConnection Verification Unavailable\n\n&7Please try again shortly.");
         return new VpnGate.Settings(
            var0.getBoolean("enabled", true),
            var0.getBoolean("allow-on-lookup-failure", true),
            var0.getBoolean("log-blocks", true),
            var0.getBoolean("webhook-blocks", true),
            var5,
            var6,
            new VpnReputation.Policy(
               var0.getBoolean("block-vpn", true),
               var0.getBoolean("block-proxy", true),
               var0.getBoolean("block-tor", true),
               var7,
               var8 * 1000L,
               var3 * 1000L,
               var4,
               new HashSet<>(var0.getStringList("allowed-ips")),
               var0.getString("api-key", "")
            )
         );
      }

      private static int number(YamlConfiguration var0, String var1, int var2, int var3, int var4) {
         Object var5 = var0.get(var1);
         if (var5 == null) {
            return var2;
         } else if (var5 instanceof Number var6 && var6.doubleValue() == var6.intValue() && var6.intValue() >= var3 && var6.intValue() <= var4) {
            return var6.intValue();
         } else {
            throw new IllegalArgumentException(var1 + " must be an integer from " + var3 + " to " + var4 + ".");
         }
      }

      private static String text(YamlConfiguration var0, String var1, String var2) {
         Object var3 = var0.get(var1);
         if (var3 == null) {
            return var2;
         } else if (var3 instanceof String var4 && !var4.isBlank() && var4.length() <= 2000) {
            return var4;
         } else {
            throw new IllegalArgumentException(var1 + " must be nonempty text.");
         }
      }
   }

   private record Snapshot(VpnGate.Settings settings, VpnReputation service) {
   }
}
