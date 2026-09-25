package dev.aegisac.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A bounded login-time lookup. Errors and unknown results allow the player through. */
public final class VpnGate {
    private record Entry(boolean vpn, long expiresAt) {}
    private final AegisAC plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(900)).build();
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private volatile boolean enabled;
    private volatile String apiKey;

    VpnGate(AegisAC plugin) { this.plugin = plugin; reload(); }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("vpn.enabled", false);
        String envName = plugin.getConfig().getString("vpn.api-key-env", "AEGIS_PROXYCHECK_KEY");
        apiKey = envName == null ? null : System.getenv(envName);
        cache.clear();
        if (enabled && (apiKey == null || apiKey.isBlank()))
            plugin.getLogger().warning("VPN gate enabled without an API key; fail-open mode is active.");
    }

    public boolean enabled() { return enabled && apiKey != null && !apiKey.isBlank(); }

    public void checkLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled() || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        InetAddress address = event.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()) return;
        String ip = address.getHostAddress();
        Entry entry = cache.get(ip);
        boolean cacheHit = entry != null && entry.expiresAt() >= System.currentTimeMillis();
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            Boolean result = query(ip);
            if (result == null) {
                AegisAudit.warning(plugin, "VPN_UNKNOWN", event.getName() + " ip=" + ip + " action=allow");
                return;
            }
            entry = new Entry(result, System.currentTimeMillis() + (result ? 30_000L : 300_000L));
            cache.put(ip, entry);
            if (cache.size() > 4096) cache.entrySet().removeIf(e -> e.getValue().expiresAt() < System.currentTimeMillis());
        }
        if (entry.vpn()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "This server restricts VPN connections. Turn off your VPN and try again, or contact staff if this is incorrect.");
            AegisAudit.warning(plugin, "VPN_DENIED", event.getName() + " ip=" + ip
                    + " source=" + (cacheHit ? "cache" : "lookup"));
        } else {
            AegisAudit.info(plugin, "VPN_ALLOWED", event.getName() + " ip=" + ip
                    + " source=" + (cacheHit ? "cache" : "lookup"));
        }
    }

    private Boolean query(String ip) {
        try {
            String url = "https://proxycheck.io/v2/" + URLEncoder.encode(ip, StandardCharsets.UTF_8)
                    + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8) + "&vpn=2";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(1500))
                    .header("User-Agent", "AegisAC/0.3.4").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 16_384) return null;
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("status") || !"ok".equalsIgnoreCase(root.get("status").getAsString())) return null;
            JsonObject result = root.getAsJsonObject(ip);
            if (result == null || !result.has("proxy")) return null;
            return "yes".equalsIgnoreCase(result.get("proxy").getAsString())
                    && result.has("type") && "VPN".equalsIgnoreCase(result.get("type").getAsString());
        } catch (Exception ex) {
            return null;
        }
    }
}
