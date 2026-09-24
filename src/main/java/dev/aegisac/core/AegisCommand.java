package dev.aegisac.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AegisCommand implements CommandExecutor {
    private final AegisAC plugin;

    public AegisCommand(AegisAC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aegis.admin")) {
            sender.sendMessage("§cYou do not have permission to use AegisAC commands.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§bAegisAC §7v" + plugin.getDescription().getVersion() + " §8- §falert-first Paper 1.21.11 anti-cheat");
            sender.sendMessage("§7Made by §f@_adam814§7. This build never automatically bans or kicks.");
            return true;
        }
        if (args[0].equalsIgnoreCase("alerts")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can toggle in-game alerts.");
                return true;
            }
            boolean enabled = plugin.violations().toggleAlerts(player);
            sender.sendMessage(enabled ? "§aAegis alerts enabled." : "§cAegis alerts disabled.");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aAegisAC configuration reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§bAegisAC §7tracking §f" + plugin.data().size() + " §7player record(s). TPS: §f"
                    + String.format(java.util.Locale.ROOT, "%.2f", plugin.currentTps()));
            return true;
        }
        sender.sendMessage("§7Usage: §f/aegis <alerts|status|reload|info>");
        return true;
    }
}
