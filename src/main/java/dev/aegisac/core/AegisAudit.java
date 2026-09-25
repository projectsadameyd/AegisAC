package dev.aegisac.core;

import org.bukkit.entity.Player;

import java.net.InetSocketAddress;

/** Console-only audit messages. Never use this for public join/chat text. */
final class AegisAudit {
    private AegisAudit() {}

    static String ip(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null
                ? "unavailable" : address.getAddress().getHostAddress();
    }

    static void info(AegisAC plugin, String action, String detail) {
        plugin.getLogger().info("[AUDIT] " + action + " " + detail);
    }

    static void warning(AegisAC plugin, String action, String detail) {
        plugin.getLogger().warning("[AUDIT] " + action + " " + detail);
    }
}
