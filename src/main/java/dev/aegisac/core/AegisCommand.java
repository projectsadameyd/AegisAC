package dev.aegisac.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.net.InetSocketAddress;

public final class AegisCommand implements CommandExecutor {
    private final AegisAC plugin;

    public AegisCommand(AegisAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("aegis-ip")) {
            if (!sender.hasPermission("aegis.ip")) {
                sender.sendMessage("§cMissing aegis.ip permission.");
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage("§7Usage: §f/aegis-ip <online-player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer must be online. IPs are not kept in AegisAC's player history.");
                return true;
            }
            InetSocketAddress address = target.getAddress();
            sender.sendMessage(address == null || address.getAddress() == null
                    ? "§cNo address available." : "§7Server-seen IP for §f" + target.getName() + "§7: §f"
                    + address.getAddress().getHostAddress() + " §8(proxy configuration affects this value)");
            AegisAudit.info(plugin, "IP_LOOKUP", "actor=" + sender.getName() + " target=" + target.getName());
            return true;
        }
        if (!sender.hasPermission("aegis.admin")) {
            sender.sendMessage("§cYou do not have permission to use AegisAC commands.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§bAegisAC §7v" + plugin.getDescription().getVersion() + " §8- §falert-first Paper 1.21.11 anti-cheat");
            sender.sendMessage("§7Made by §f@_adam814§7. Sanctions require opt-in configuration.");
            return true;
        }
        if (args[0].equalsIgnoreCase("alerts")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can toggle in-game alerts.");
                return true;
            }
            boolean enabled = plugin.violations().toggleAlerts(player);
            sender.sendMessage(enabled ? "§aAegis alerts enabled." : "§cAegis alerts disabled.");
            AegisAudit.info(plugin, "ALERTS_TOGGLE", "actor=" + sender.getName() + " enabled=" + enabled);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.vpnGate().reload();
            sender.sendMessage("§aAegisAC configuration reloaded.");
            AegisAudit.info(plugin, "CONFIG_RELOAD", "actor=" + sender.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§bAegisAC §7tracking §f" + plugin.data().size() + " §7player record(s). TPS: §f"
                    + String.format(java.util.Locale.ROOT, "%.2f", plugin.currentTps()));
            sender.sendMessage("§7Sanctions: §f" + plugin.getConfig().getBoolean("enforcement.enabled", false)
                    + "§7; permanent IP bans: §f" + plugin.getConfig().getBoolean("enforcement.permanent-ip-ban-enabled", false)
                    + "§7; VPN gate ready: §f" + plugin.vpnGate().enabled());
            return true;
        }
        if (args[0].equalsIgnoreCase("inspect") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer must be online.");
                return true;
            }
            sender.sendMessage("§b" + target.getName() + "§7 sanctions stage: §f" + plugin.moderation().stage(target.getUniqueId())
                    + "§7, last reason: §f" + plugin.moderation().reason(target.getUniqueId()));
            PlayerData data = plugin.data().get(target.getUniqueId());
            for (CheckType type : CheckType.values()) {
                if (data.buffer(type) > 0) sender.sendMessage("§7" + type.display() + ": §f"
                        + String.format(java.util.Locale.ROOT, "%.2f", data.buffer(type)));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reset") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer must be online; contact staff to review offline bans.");
                return true;
            }
            plugin.moderation().reset(target.getUniqueId());
            plugin.data().remove(target.getUniqueId());
            sender.sendMessage("§aCleared AegisAC evidence and sanction stage for " + target.getName() + ".");
            AegisAudit.warning(plugin, "SANCTION_RESET", "actor=" + sender.getName()
                    + " target=" + target.getName() + " uuid=" + target.getUniqueId());
            return true;
        }
        if (args[0].equalsIgnoreCase("pardon") && args.length == 2) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(args[1]);
                plugin.moderation().reset(uuid);
                plugin.data().remove(uuid);
                sender.sendMessage("§aCleared AegisAC sanctions for UUID " + uuid + ".");
                AegisAudit.warning(plugin, "PARDON", "actor=" + sender.getName() + " uuid=" + uuid);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage("§cProvide the exact account UUID.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("unban-ip") && args.length == 2) {
            boolean removed = plugin.moderation().unbanIp(args[1]);
            sender.sendMessage(removed ? "§aRemoved the IP block."
                    : "§cNo AegisAC IP block found for that address.");
            if (removed) AegisAudit.warning(plugin, "IP_UNBAN", "actor=" + sender.getName() + " ip=" + args[1]);
            return true;
        }
        sender.sendMessage("§7Usage: §f/aegis <alerts|status|inspect|reset|pardon|unban-ip|reload|info>");
        return true;
    }
}
