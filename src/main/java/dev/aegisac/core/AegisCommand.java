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
            sender.sendMessage("§7Made by §f@_adam814§7. Use /aegis status and /aegis debug <player> to inspect detection.");
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
            sender.sendMessage("§bAegisAC §f" + plugin.getDescription().getVersion()
                    + " §7tracking §f" + plugin.data().size() + " §7player record(s). TPS: §f"
                    + String.format(java.util.Locale.ROOT, "%.2f", plugin.currentTps()));
            sender.sendMessage("§7Sanctions: §f" + plugin.getConfig().getBoolean("enforcement.enabled", true)
                    + "§7; cooldown: §f" + (plugin.getConfig().getLong("enforcement.cooldown-ms", 600_000L) / 1000) + "s"
                    + "§7; remote-interaction IP bans: §f" + plugin.getConfig().getBoolean("enforcement.permanent-ip-ban-enabled", false)
                    + "§7; VPN gate ready: §f" + plugin.vpnGate().enabled());
            sender.sendMessage("§7Auto-sanction checks: §f" + plugin.getConfig().getStringList("enforcement.eligible-checks")
                    + "§7. Other checks produce staff alerts only.");
            for (CheckType type : CheckType.values()) {
                if (!plugin.getConfig().getStringList("enforcement.eligible-checks").contains(type.name())) continue;
                double minimum = Math.max(plugin.getConfig().getDouble("alerts.minimum-confidence", 0.72),
                        plugin.getConfig().getDouble("enforcement.minimum-confidence-by-check." + type.name(),
                                plugin.getConfig().getDouble("enforcement.minimum-confidence", 0.98)));
                if (minimum > EnforcementProfiles.get(type).maximumScore() + 1.0E-9)
                    sender.sendMessage("§c" + type.display() + " can never sanction: effective required score "
                            + Math.round(minimum * 100) + "% exceeds maximum "
                            + Math.round(EnforcementProfiles.get(type).maximumScore() * 100)
                            + "%. Check alerts.minimum-confidence and run /aegis enforcement "
                            + type.name().toLowerCase(java.util.Locale.ROOT) + " on.");
            }
            if (plugin.getConfig().getBoolean("vpn.enabled", false) && !plugin.vpnGate().enabled())
                sender.sendMessage("§cVPN blocking is configured but unavailable: set the VPN API key environment variable and restart.");
            return true;
        }
        if (args[0].equalsIgnoreCase("enforcement") && args.length == 3
                && args[1].equalsIgnoreCase("cooldown")) {
            int seconds;
            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cUsage: /aegis enforcement cooldown <0-3600 seconds>");
                return true;
            }
            if (seconds < 0 || seconds > 3600) {
                sender.sendMessage("§cCooldown must be between 0 and 3600 seconds.");
                return true;
            }
            plugin.getConfig().set("enforcement.cooldown-ms", seconds * 1000L);
            plugin.saveConfig();
            sender.sendMessage("§aSanction cooldown set to " + seconds + "s, effective immediately, including for existing cooldowns.");
            if (seconds == 0) sender.sendMessage("§eZero cooldown is intended for testing on a private server. Set it back when done.");
            AegisAudit.warning(plugin, "ENFORCEMENT_COOLDOWN", "actor=" + sender.getName() + " seconds=" + seconds);
            return true;
        }
        if (args[0].equalsIgnoreCase("enforcement") && args.length == 3) {
            if (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off")) {
                sender.sendMessage("§7Usage: §f/aegis enforcement <all|check> <on|off>");
                return true;
            }
            boolean enable = args[2].equalsIgnoreCase("on");
            if (args[1].equalsIgnoreCase("all")) {
                if (enable) {
                    java.util.List<String> all = new java.util.ArrayList<>();
                    for (CheckType type : CheckType.values()) {
                        all.add(type.name());
                        setEnforcementProfile(type);
                    }
                    plugin.getConfig().set("enforcement.eligible-checks", all);
                }
                plugin.getConfig().set("enforcement.enabled", enable);
                plugin.saveConfig();
                sender.sendMessage(enable
                        ? "§eAll check sanctions enabled with per-check thresholds. Three kicks precede temporary bans."
                        : "§7All automatic sanctions disabled; eligible checks remain saved for later use.");
                sender.sendMessage("§7IP bans are separate; VPN blocking requires a configured API key. Run /aegis status.");
                AegisAudit.warning(plugin, "ALL_ENFORCEMENT", "actor=" + sender.getName() + " enabled=" + enable);
                return true;
            }
            CheckType selected = null;
            for (CheckType type : CheckType.values()) {
                if (type.name().equalsIgnoreCase(args[1])) selected = type;
            }
            if (selected == null) {
                sender.sendMessage("§cUnknown check. Use /aegis status or /aegis enforcement all on.");
                return true;
            }
            java.util.List<String> checks = new java.util.ArrayList<>(plugin.getConfig().getStringList("enforcement.eligible-checks"));
            CheckType check = selected;
            checks.removeIf(name -> name.equalsIgnoreCase(check.name()));
            if (enable) {
                checks.add(selected.name());
                setEnforcementProfile(selected);
            }
            plugin.getConfig().set("enforcement.eligible-checks", checks);
            plugin.saveConfig();
            sender.sendMessage("§7" + selected.display() + " sanctions " + (enable ? "enabled" : "disabled")
                    + "§7. Global enforcement=" + plugin.getConfig().getBoolean("enforcement.enabled", true));
            AegisAudit.warning(plugin, "CHECK_ENFORCEMENT", "actor=" + sender.getName() + " check=" + selected.name()
                    + " enabled=" + enable);
            return true;
        }
        if (args[0].equalsIgnoreCase("enforcement") && args.length == 2) {
            if (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off")) {
                sender.sendMessage("§7Usage: §f/aegis enforcement <on|off>");
                return true;
            }
            boolean enabled = args[1].equalsIgnoreCase("on");
            plugin.getConfig().set("enforcement.enabled", enabled);
            plugin.saveConfig();
            sender.sendMessage("§aAutomatic sanctions " + (enabled ? "enabled" : "disabled")
                    + ". Eligible checks: " + plugin.getConfig().getStringList("enforcement.eligible-checks"));
            AegisAudit.warning(plugin, "ENFORCEMENT", "actor=" + sender.getName() + " enabled=" + enabled);
            return true;
        }
        if (args[0].equalsIgnoreCase("ipban") && args.length == 2) {
            if (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off")) {
                sender.sendMessage("§7Usage: §f/aegis ipban <on|off>");
                return true;
            }
            boolean enabled = args[1].equalsIgnoreCase("on");
            plugin.getConfig().set("enforcement.permanent-ip-ban-enabled", enabled);
            plugin.saveConfig();
            sender.sendMessage("§7Permanent IP bans " + (enabled ? "enabled" : "disabled")
                    + ". Shared addresses can affect unrelated players.");
            AegisAudit.warning(plugin, "IPBAN_SETTING", "actor=" + sender.getName() + " enabled=" + enabled);
            return true;
        }
        if (args[0].equalsIgnoreCase("debug") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer must be online.");
                return true;
            }
            PlayerData data = plugin.data().get(target.getUniqueId());
            String reason = WorldUtil.movementExemptionReason(target, data,
                    plugin.getConfig().getDouble("minimum-tps", 18.0), plugin.currentTps(),
                    plugin.getConfig().getInt("maximum-ping-ms", 300));
            sender.sendMessage("§bAegis debug §7v" + plugin.getDescription().getVersion() + ": §f" + target.getName() + " §7bypass=§f"
                    + target.hasPermission("aegis.bypass") + "§7, mode=§f" + target.getGameMode()
                    + "§7, ping=§f" + target.getPing());
            sender.sendMessage("§7Movement: §f" + data.moveEvents + " §7events, §f"
                    + data.movementEvaluatedEvents + " §7evaluated, §f"
                    + data.movementExemptEvents + " §7exempt; current reason: §f"
                    + (reason == null ? "none" : reason));
            sender.sendMessage("§7Block interactions: §f" + data.interactionEvents + "§7; canceled as remote: §f"
                    + data.remoteBlocks + "§7; combat events: §f" + data.combatEvents);
            sender.sendMessage("§7Checks with failed samples (not proof of cheating):");
            for (CheckType type : CheckType.values()) {
                long count = data.failedSamples.getOrDefault(type, 0L);
                if (count > 0) sender.sendMessage("§7" + type.display() + " samples=§f" + count
                        + "§7; alerts=§f" + data.alertCounts.getOrDefault(type, 0L)
                        + "§7; last score=§f" + Math.round(data.lastAlertConfidence.getOrDefault(type, 0.0) * 100) + "%"
                        + "§7; gate=§f" + data.sanctionGate.getOrDefault(type, "no alert yet"));
            }
            sender.sendMessage("§7Sanctions=§f" + plugin.getConfig().getBoolean("enforcement.enabled", true)
                    + "§7; eligible=§f" + plugin.getConfig().getStringList("enforcement.eligible-checks")
                    + "§7; stage=§f" + plugin.moderation().stage(target.getUniqueId())
                    + "§7; cooldown=§f" + ((plugin.moderation().cooldownRemaining(target.getUniqueId()) + 999) / 1000) + "s"
                    + "§7; next=§f" + plugin.moderation().nextAction(target.getUniqueId()));
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
        sender.sendMessage("§7Usage: §f/aegis <alerts|status|debug|enforcement [all|check|cooldown] ...|ipban|inspect|reset|pardon|unban-ip|reload|info>");
        return true;
    }

    private void setEnforcementProfile(CheckType type) {
        EnforcementProfiles.Profile profile = EnforcementProfiles.get(type);
        plugin.getConfig().set("enforcement.minimum-confidence-by-check." + type.name(), profile.minimumScore());
        plugin.getConfig().set("enforcement.alerts-required-by-check." + type.name(), profile.requiredAlerts());
        plugin.getConfig().set("enforcement.minimum-evidence-span-ms-by-check." + type.name(), profile.minimumSpanMillis());
    }
}
