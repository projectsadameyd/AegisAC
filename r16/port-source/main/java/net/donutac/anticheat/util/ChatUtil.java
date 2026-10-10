package net.donutac.anticheat.util;

import org.bukkit.ChatColor;

public final class ChatUtil {
   private ChatUtil() {
   }

   public static String color(String var0) {
      return var0 == null ? "" : ChatColor.translateAlternateColorCodes('&', var0);
   }

   public static String component(String var0) {
      return color(var0);
   }

   public static String multiline(String var0) {
      return color(var0);
   }
}
