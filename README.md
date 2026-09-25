# AegisAC 0.3.0-alpha

Paper 1.21.11 / Java 21 anti-cheat prototype. Put the built JAR in `plugins/` and restart Paper. Test on a private server before deploying to players.

## Scope and limitations

No server plugin can guarantee detection of every cheat, instant detection, zero false positives, or superior performance to GrimAC without controlled benchmarks. **Passive Freecam is not detectable by this plugin**: a camera moving only on the client sends no distinctive movement to Paper. AegisAC immediately cancels block break, placement, and interaction **beyond the player's server-side block-reach attribute plus a safety margin**. This stops a class of remote actions, not passive viewing. Paper's [built-in Anti-Xray](https://docs.papermc.io/paper/anti-xray/) reduces ore information sent to clients; it does not remove Freecam or hide every visible build.

The existing Speed, Fly, Timer, Reach, AutoClicker, FastPlace, and Velocity checks still generate staff alerts. They do not automatically escalate sanctions by default. The new remote-interaction check adds its own alerts, distinct from claims that a Freecam client was identified.

## Sanctions and appeals

New installations have `enforcement.enabled: true` for the remote-interaction check only. Existing `config.yml` files retain their previous value; run `/aegis status` and, if your server still shows `false`, `/aegis enforcement on`. This does not automatically punish Speed, Fly, Timer, or other heuristic alerts. For each eligible check, four high-confidence alerts within 90 seconds count as one sanction event, with a 10-minute cooldown:

| Event | Action |
| --- | --- |
| 1–3 | Kick |
| 4 | Temporary one-hour login block |
| 5 | Temporary one-day login block |
| 6 | Permanent account and IP login block **only if** `permanent-ip-ban-enabled: true`; otherwise staff review |

The ladder survives restarts in `plugins/AegisAC/sanctions.yml`. It does **not** instantly ban on one detection. Keep backups and handle appeals; a shared household, VPN exit, or proxy address can belong to multiple players. Permanent IP banning is separately disabled by default; `/aegis ipban on` enables it. Disabled enforcement does not erase existing bans. `/aegis reset <online-player>` clears a player's sanction stage. `/aegis pardon <uuid>` clears an offline account ban; `/aegis unban-ip <address>` removes an IP ban.

## VPN gate

Optional, requires a [proxycheck.io API key](https://proxycheck.io/api/) in environment variable `AEGIS_PROXYCHECK_KEY` and `vpn.enabled: true`. Lookup uses HTTPS during asynchronous pre-login, with a 1.5-second timeout and a short-lived cache. A positive VPN result rejects that join with a retry message; it is **not a ban** and does not increment sanctions. API failure or unknown result allows the join. This is probabilistic classification, may miss VPNs or reject a normal player, and sends the connecting IP to the provider. If behind Velocity, configure [modern forwarding and proxy security](https://docs.papermc.io/velocity/player-information-forwarding/) before relying on IP controls.

## Commands

| Command | Purpose |
| --- | --- |
| `/aegis info` | Version and credits |
| `/aegis status` | TPS, tracked players, enabled controls |
| `/aegis debug <online-player>` | Event counts, exemption reason, bypass status, and failed check samples |
| `/aegis enforcement <on|off>` | Enable or disable eligible automatic sanctions, saved to config |
| `/aegis ipban <on|off>` | Enable or disable the final IP-ban stage, saved to config |
| `/aegis alerts` | Toggle personal alerts |
| `/aegis inspect <online-player>` | Show alert buffers and sanction stage |
| `/aegis reset <online-player>` | Reset that player's evidence and stage |
| `/aegis pardon <uuid>` | Clear offline player's ban and stage |
| `/aegis unban-ip <address>` | Remove an IP ban |
| `/aegis reload` | Reload configuration and VPN state |
| `/aegis-ip <online-player>` | Show the IP address seen by Paper, privately to a permitted admin |

Permissions: `aegis.admin`, `aegis.ip`, `aegis.alerts` (default op), `aegis.bypass` (default false). The server-seen IP is not necessarily a player's home IP, especially when behind a proxy. Do not share IPs publicly.

If a check appears idle, inspect `/aegis debug <player>` while that player is moving and interacting. Zero event counts indicate the plugin is not seeing those Paper events; a bypass permission or an exemption reason explains some missing movement alerts. Check `/aegis info` for the loaded plugin version and `/aegis status` for sanction settings. A completed CI build does not replace testing on a running Paper server.

Every 30 minutes the plugin broadcasts exactly `Made by @_adam814` by default.

## Console audit

AegisAC logs `[AUDIT]` entries for joins, quits, check alerts, blocked remote interactions, sanctions, VPN login decisions, login denials, and admin changes. Join entries show the address Paper sees beside the player name, for example: `[AUDIT] JOIN Steve [IP: 203.0.113.42] uuid=...`. IP addresses appear only in the server console and server logs; the normal public join message stays as configured by Paper. If you use a proxy, configure secure IP forwarding or the address may be the proxy's. Restrict access to server logs because they contain player IPs. Remote-interaction cancellation messages are limited to one per player every five seconds; normal movement events are not logged.

## Build

With Java 21 and Maven: `mvn package`. Or use Gradle with Java 21: `gradle build`. Paper dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

This code does not implement packet-level physics prediction or benchmark faster than GrimAC. See [Grim's source](https://github.com/GrimAnticheat/Grim) for its different scope. Check Paper's [project setup](https://docs.papermc.io/paper/dev/project-setup/) for current API guidance.
