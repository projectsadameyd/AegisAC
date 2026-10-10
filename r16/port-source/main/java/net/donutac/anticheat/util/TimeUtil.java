package net.donutac.anticheat.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
   private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhdw])");

   private TimeUtil() {
   }

   public static long parseDurationMillis(String var0) {
      if (var0 != null && !var0.equalsIgnoreCase("permanent") && !var0.equalsIgnoreCase("perm")) {
         Matcher var1 = PATTERN.matcher(var0.trim().toLowerCase());
         long var2 = 0L;
         boolean var4 = false;

         while (var1.find()) {
            var4 = true;
            long var6 = Long.parseLong(var1.group(1));

            var2 += switch (var1.group(2)) {
               case "s" -> var6 * 1000L;
               case "m" -> var6 * 60000L;
               case "h" -> var6 * 3600000L;
               case "d" -> var6 * 86400000L;
               case "w" -> var6 * 604800000L;
               default -> 0L;
            };
         }

         return var4 ? var2 : -1L;
      } else {
         return -1L;
      }
   }

   public static String formatRemaining(long var0) {
      if (var0 <= 0L) {
         return "0s";
      }

      long var2 = var0 / 1000L;
      long var4 = var2 / 604800L;
      long var13;
      long var6 = (var13 = var2 % 604800L) / 86400L;
      long var8 = (var2 = var13 % 86400L) / 3600L;
      long var15;
      long var10 = (var15 = var2 % 3600L) / 60L;
      var2 = var15 % 60L;
      StringBuilder var12 = new StringBuilder();
      if (var4 > 0L) {
         var12.append(var4).append("w ");
      }

      if (var6 > 0L || var4 > 0L) {
         var12.append(var6).append("d ");
      }

      if (var8 > 0L || var6 > 0L || var4 > 0L) {
         var12.append(var8).append("h ");
      }

      if (var10 > 0L || var8 > 0L || var6 > 0L || var4 > 0L) {
         var12.append(var10).append("m ");
      }

      var12.append(var2).append("s");
      return var12.toString();
   }

   public static String formatAgo(long var0) {
      long var4 = System.currentTimeMillis() - var0;
      if (var4 < 0L) {
         var4 = 0L;
      }

      long var2;
      if ((var2 = var4 / 1000L) < 60L) {
         return var2 + "s ago";
      }

      long var6 = var2 / 60L;
      if (var6 < 60L) {
         return var6 + "m ago";
      }

      long var8 = var6 / 60L;
      if (var8 < 24L) {
         return var8 + "h ago";
      }

      long var10 = var8 / 24L;
      return var10 + "d ago";
   }
}
