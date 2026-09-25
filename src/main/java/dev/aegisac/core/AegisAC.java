package dev.aegisac.core;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AegisAC extends JavaPlugin {
    private final PlayerDataManager data = new PlayerDataManager();
    private ViolationEngine violations;
    private Moderation moderation;
    private VpnGate vpnGate;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        moderation = new Moderation(this);
        vpnGate = new VpnGate(this);
        violations = new ViolationEngine(this);
        getServer().getPluginManager().registerEvents(new AegisListener(this), this);

        PluginCommand command = getCommand("aegis");
        if (command != null) command.setExecutor(new AegisCommand(this));
        PluginCommand ipCommand = getCommand("aegis-ip");
        if (ipCommand != null) ipCommand.setExecutor(new AegisCommand(this));

        scheduleCreditBroadcast();
        getLogger().info("AegisAC enabled for Paper 1.21.11. Automatic sanctions: " + getConfig().getBoolean("enforcement.enabled", false));
    }

    @Override
    public void onDisable() {
        getLogger().info("AegisAC disabled.");
    }

    private void scheduleCreditBroadcast() {
        if (!getConfig().getBoolean("broadcast-credit", true)) return;
        long seconds = Math.max(60L, getConfig().getLong("broadcast-credit-seconds", 1800L));
        long ticks = seconds * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.broadcastMessage("Made by @_adam814"), ticks, ticks);
    }

    public PlayerDataManager data() {
        return data;
    }

    public ViolationEngine violations() {
        return violations;
    }

    public Moderation moderation() { return moderation; }

    public VpnGate vpnGate() { return vpnGate; }

    public double currentTps() {
        try {
            double[] values = Bukkit.getServer().getTPS();
            if (values.length > 0) return Math.min(20.0, values[0]);
        } catch (Throwable ignored) {
        }
        return 20.0;
    }
}
