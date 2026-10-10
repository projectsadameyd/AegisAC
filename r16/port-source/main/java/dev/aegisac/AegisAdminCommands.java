package dev.aegisac;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class AegisAdminCommands implements TabCompleter {
   static final List<String> COMMANDS = List.of(
      "help",
      "status",
      "engine",
      "player",
      "baninfo",
      "history",
      "listbans",
      "ban",
      "unban",
      "reset",
      "ipcheck",
      "warn",
      "kick",
      "alerts",
      "exempt",
      "exemptions",
      "pause",
      "resume",
      "health",
      "stats",
      "export",
      "clearhistory",
      "webhook",
      "reload",
      "unbanip",
      "vpn"
   );
   private final AegisGrimAddon owner;

   AegisAdminCommands(AegisGrimAddon var1) {
      this.owner = var1;
   }

   private void say(CommandSender var1, String var2) {
      var1.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&lAegisAC &8» &f" + var2));
   }

   private UUID target(String var1) {
      UUID var2 = this.owner.known(var1);
      if (var2 == null) {
         throw new IllegalArgumentException("Unknown player. Use a known name or UUID.");
      } else {
         return var2;
      }
   }

   private static long seconds(String var0, int var1) {
      try {
         long var2 = Long.parseLong(var0);
         if (var2 >= 1L && var2 <= var1) {
            return var2;
         }
      } catch (NumberFormatException var4) {
      }

      throw new IllegalArgumentException("Seconds must be between 1 and " + var1 + ".");
   }

   private Player online(String var1) {
      Player var2 = Bukkit.getPlayer(this.target(var1));
      if (var2 == null) {
         throw new IllegalArgumentException("That player is offline.");
      } else {
         return var2;
      }
   }

   private static String reason(String[] var0) {
      return var0.length < 3 ? "Staff warning" : String.join(" ", Arrays.copyOfRange(var0, 2, var0.length)).replaceAll("[\\p{Cntrl}§]", "");
   }

   boolean handle(CommandSender var1, String[] var2) {
      String var3 = var2.length == 0 ? "help" : var2[0].toLowerCase(Locale.ROOT);

      try {
         switch (var3) {
            case "vpn":
               this.owner.vpnCommand(var1, var2);
               break;
            case "help":
               this.say(var1, "Commands: " + String.join(", ", COMMANDS));
               this.say(var1, "Player tools: /aegis player|baninfo <player>, history <player> [1..50], listbans [page]");
               this.say(var1, "Staff actions: warn|kick <player> [reason], exempt <player> <seconds|off>, pause [seconds], resume");
               this.say(var1, "Monitoring: alerts <on|off>, health, stats, export [player], clearhistory <player>");
               this.say(var1, "Connections: /aegis vpn <status|on|off|check|allow|unallow|clearcache>");
               break;
            case "player":
            case "baninfo":
               if (var2.length < 2) {
                  throw new IllegalArgumentException("Usage: /aegis " + var3 + " <player|uuid>");
               }

               UUID var22 = this.target(var2[1]);
               this.say(var1, this.owner.profile(var22));
               if (var3.equals("player")) {
                  Player var27 = Bukkit.getPlayer(var22);
                  if (var27 != null) {
                     this.say(
                        var1,
                        "Online=true Ping=" + BukkitCompatibility.ping(var27) + "ms Mode=" + var27.getGameMode() + " Eligible=" + NativeSession.health(var22)
                     );
                     Location var30 = var27.getLocation();
                     if (var30 != null && var30.getWorld() != null) {
                        this.say(
                           var1,
                           "World="
                              + var30.getWorld().getName()
                              + " Position="
                              + String.format(Locale.ROOT, "%.2f, %.2f, %.2f", var30.getX(), var30.getY(), var30.getZ())
                        );
                     }
                  } else {
                     this.say(var1, "Online=false");
                  }
               }
               break;
            case "history":
               if (var2.length < 2) {
                  throw new IllegalArgumentException("Usage: /aegis history <player|uuid> [1..50]");
               }

               List<ConfirmedHistory.Entry> var21 = this.owner.history().latest(this.target(var2[1]), var2.length > 2 ? (int)seconds(var2[2], 50) : 10);
               if (var21.isEmpty()) {
                  this.say(var1, "No confirmed events recorded.");
               }

               for (ConfirmedHistory.Entry var29 : var21) {
                  this.say(var1, Instant.ofEpochMilli(var29.time()) + " " + var29.type() + " " + var29.reason());
               }
               break;
            case "listbans":
               int var20 = var2.length > 1 ? (int)seconds(var2[1], 100000) : 1;
               List<UUID> var25 = this.owner.activeBans();
               int var28 = (var20 - 1) * 10;
               if (var28 >= var25.size()) {
                  this.say(var1, "No active UUID bans on this page.");
               } else {
                  this.say(var1, "Active UUID bans: " + var25.size() + "; page " + var20);

                  for (UUID var32 : var25.subList(var28, Math.min(var28 + 10, var25.size()))) {
                     this.say(var1, this.owner.profile(var32));
                  }
               }
               break;
            case "warn":
            case "kick":
               if (var2.length < 2) {
                  throw new IllegalArgumentException("Usage: /aegis " + var3 + " <online-player> [reason]");
               }

               Player var19 = this.online(var2[1]);
               if (var3.equals("warn")) {
                  this.owner.staffWarn(var19, reason(var2));
               } else {
                  this.owner.staffKick(var19, reason(var2));
               }

               this.say(var1, "Staff " + var3 + " applied to " + var19.getName() + ".");
               break;
            case "alerts":
               if (!(var1 instanceof Player var18)) {
                  throw new IllegalArgumentException("Console already receives confirmed detections.");
               }

               if (var2.length < 2 || !List.of("on", "off").contains(var2[1].toLowerCase(Locale.ROOT))) {
                  throw new IllegalArgumentException("Usage: /aegis alerts <on|off>");
               }

               this.say(var1, "Confirmed alerts " + (this.owner.alerts(var18.getUniqueId(), var2[1].equalsIgnoreCase("on")) ? "on" : "off") + ".");
               break;
            case "exempt":
               if (var2.length < 3) {
                  throw new IllegalArgumentException("Usage: /aegis exempt <player|uuid> <seconds|off>");
               }

               UUID var17 = this.target(var2[1]);
               long var24 = var2[2].equalsIgnoreCase("off") ? 0L : System.currentTimeMillis() + seconds(var2[2], 86400) * 1000L;
               NativeSession.manualExempt(var17, var24);
               this.say(
                  var1,
                  "Manual sanction exemption "
                     + (var24 == 0L ? "removed" : "set until " + Instant.ofEpochMilli(var24))
                     + ". Native detector compensation remains active."
               );
               break;
            case "exemptions":
               Map<UUID, Long> var16 = NativeSession.manualExemptions();
               this.say(var1, "Active manual exemptions: " + var16.size());
               var16.entrySet()
                  .stream()
                  .sorted(Entry.comparingByKey())
                  .limit(20L)
                  .forEach(var2x -> this.say(var1, var2x.getKey() + " until " + Instant.ofEpochMilli((Long)var2x.getValue())));
               break;
            case "pause":
               long var15 = var2.length > 1 ? seconds(var2[1], 3600) : 60L;
               this.owner.pause(System.currentTimeMillis() + var15 * 1000L);
               this.say(var1, "Automatic alerts and sanctions paused for " + var15 + " seconds. Packet guards remain active.");
               break;
            case "resume":
               this.owner.resume();
               this.say(var1, "Automatic sanctions resumed; native punishment counters cleared.");
               break;
            case "health":
               double[] var14 = BukkitCompatibility.tps();
               Runtime var23 = Runtime.getRuntime();
               this.say(
                  var1,
                  "TPS="
                     + (var14 == null ? "unknown" : String.format(Locale.ROOT, "%.2f", var14[0]))
                     + " Online="
                     + Bukkit.getOnlinePlayers().size()
                     + " Whole-server heap="
                     + (var23.totalMemory() - var23.freeMemory()) / 1048576L
                     + " MiB; sanctionsPaused="
                     + this.owner.reviewPaused(System.currentTimeMillis())
               );
               break;
            case "stats":
               this.say(
                  var1,
                  "Retained confirmed events="
                     + this.owner.history().size()
                     + "/512; active UUID bans="
                     + this.owner.activeBans().size()
                     + "; pending actions="
                     + this.owner.pendingReviews()
                     + "; Core tracked="
                     + this.owner.nativeTracked()
               );
               break;
            case "clearhistory":
               if (var2.length < 2) {
                  throw new IllegalArgumentException("Usage: /aegis clearhistory <player|uuid>");
               }

               this.owner.history().clear(this.target(var2[1]));
               this.say(var1, "Confirmed-event history cleared. Ban escalation is unchanged.");
               break;
            case "export":
               UUID var6 = var2.length > 1 ? this.target(var2[1]) : null;
               Path var7 = this.owner.folder().toPath().resolve("exports");
               Files.createDirectories(var7);
               Path var8 = var7.resolve("confirmed-" + (var6 == null ? "recent" : var6) + "-" + System.currentTimeMillis() + ".csv");
               StringBuilder var9 = new StringBuilder("time,uuid,name,event,reason\n");

               for (ConfirmedHistory.Entry var11 : this.owner.history().latest(var6, 50)) {
                  var9.append(cell(Instant.ofEpochMilli(var11.time()).toString()))
                     .append(',')
                     .append(cell(var11.player().toString()))
                     .append(',')
                     .append(cell(var11.name()))
                     .append(',')
                     .append(cell(var11.type()))
                     .append(',')
                     .append(cell(var11.reason()))
                     .append('\n');
               }

               Files.writeString(var8, var9, StandardCharsets.UTF_8);
               this.say(var1, "Exported up to 50 recent confirmed events: exports/" + var8.getFileName());
               break;
            default:
               return false;
         }

         return true;
      } catch (IllegalArgumentException var12) {
         this.say(var1, var12.getMessage());
         return true;
      } catch (IOException var13) {
         this.say(var1, "Operation could not be completed: " + var13.getMessage());
         return true;
      }
   }

   private static String cell(String var0) {
      if (!var0.isEmpty() && "=+-@".indexOf(var0.charAt(0)) >= 0) {
         var0 = "'" + var0;
      }

      return "\"" + var0.replace("\"", "\"\"") + "\"";
   }

   @Override
   public List<String> onTabComplete(CommandSender var1, Command var2, String var3, String[] var4) {
      if (!var1.hasPermission("aegisac.admin") && !var1.hasPermission("aegisgrim.admin")) {
         return List.of();
      }

      ArrayList<String> var5 = new ArrayList<>();
      if (var4.length <= 1) {
         var5.addAll(COMMANDS);
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("vpn")) {
         var5.addAll(List.of("status", "on", "off", "check", "allow", "unallow", "clearcache"));
      } else if (var4.length == 2 && var4[0].equalsIgnoreCase("alerts")) {
         var5.addAll(List.of("on", "off"));
      } else if (var4.length == 2
         && List.of("player", "baninfo", "history", "ban", "unban", "reset", "ipcheck", "warn", "kick", "exempt", "export", "clearhistory")
            .contains(var4[0].toLowerCase(Locale.ROOT))) {
         Collection<? extends Player> var6 = Bukkit.getOnlinePlayers();
         if (var6 != null) {
            for (Player var8 : var6) {
               var5.add(var8.getName());
            }
         }
      }

      String var9 = var4.length == 0 ? "" : var4[var4.length - 1].toLowerCase(Locale.ROOT);
      return var5.stream().filter(var1x -> var1x.toLowerCase(Locale.ROOT).startsWith(var9)).sorted().toList();
   }
}
