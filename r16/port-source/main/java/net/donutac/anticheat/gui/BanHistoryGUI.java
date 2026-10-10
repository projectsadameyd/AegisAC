package net.donutac.anticheat.gui;

import dev.aegisac.BukkitCompatibility;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.donutac.anticheat.ban.BanEntry;
import net.donutac.anticheat.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class BanHistoryGUI {
   public static final String TITLE_PREFIX = "Ban History: ";
   private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

   private BanHistoryGUI() {
   }

   public static Inventory build(OfflinePlayer var0, List<BanEntry> var1) {
      int var2 = Math.max(3, Math.min(6, var1.size() / 9 + 2));
      Inventory var3 = Bukkit.createInventory(null, var2 * 9, ChatUtil.component("&8Ban History: &6" + safeName(var0)));
      int var4 = 0;

      for (BanEntry var6 : var1) {
         if (var4 >= var3.getSize()) {
            break;
         }

         var3.setItem(var4++, buildBanItem(var0, var6));
      }

      return var3;
   }

   private static ItemStack buildBanItem(OfflinePlayer var0, BanEntry var1) {
      ItemStack var2 = new ItemStack(BukkitCompatibility.playerHeadMaterial());
      SkullMeta var3 = (SkullMeta)var2.getItemMeta();
      if (var3 != null) {
         BukkitCompatibility.skullOwner(var3, var0);
         var3.setDisplayName(ChatUtil.component((var1.isActive() ? "&6&l" : "&7&m") + safeName(var0)));
         ArrayList var4 = new ArrayList();
         var4.add(ChatUtil.component("&7Banned for: &e" + var1.checkName()));
         var4.add(ChatUtil.component("&7Banned by: &f" + var1.bannedBy()));
         var4.add(ChatUtil.component("&7Reason: &f" + var1.reason()));
         var4.add(ChatUtil.component("&7Date: &f" + DATE_FORMAT.format(new Date(var1.timestamp()))));
         var4.add(ChatUtil.component("&7Ban ID: &f#" + var1.banId()));
         if (var1.isPardoned()) {
            var4.add(ChatUtil.component("&a&lPARDONED"));
         } else if (var1.isPermanent()) {
            var4.add(ChatUtil.component("&4&lPERMANENT"));
         } else if (var1.isActive()) {
            var4.add(ChatUtil.component("&6&lACTIVE"));
         } else {
            var4.add(ChatUtil.component("&7&lEXPIRED"));
         }

         var3.setLore(var4);
         var2.setItemMeta(var3);
      }

      return var2;
   }

   private static String safeName(OfflinePlayer var0) {
      String var1 = var0.getName();
      return var1 != null ? var1 : var0.getUniqueId().toString();
   }

   public static boolean isBanHistoryInventoryTitle(String var0) {
      String var1 = ChatColor.stripColor(var0);
      return var1.startsWith("Ban History: ");
   }
}
