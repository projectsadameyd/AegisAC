# AegisAC 0.2.1-alpha

Paper 1.21.11 / Java 21 anti-cheat prototype. Put the built JAR in `plugins/` and restart Paper. Test on a private server before deploying to players.

## Scope and limitations

No server plugin can guarantee detection of every cheat, instant detection, zero false positives, or superior performance to GrimAC without controlled benchmarks. Freecam that only moves the client's camera does not send a distinctive movement signal to the server. AegisAC cancels **remote block break, placement, and interaction** when the player's actual server-side eye position is more than 8.5 blocks from the block center. This may be caused by unusual server plugins, so automatic sanctions are disabled by default. Paper's built-in Anti-Xray is preferable for ore exposure mitigation.

The existing Speed, Fly, Timer, Reach, AutoClicker, FastPlace, and Velocity checks still generate staff alerts. They do not automatically escalate sanctions by default. The new remote-interaction check adds its own alerts, distinct from claims that a Freecam client was identified.

## Sanctions and appeals

Set `enforcement.enabled: true` only after reviewing alerts and testing compatibility. For each eligible check, four high-confidence alerts within 90 seconds count as one sanction event, with a 10-minute cooldown:

| Event | Action |
| --- | --- |
| 1–3 | Kick |
| 4 | Temporary one-hour login block |
| 5 | Temporary one-day login block |
| 6 | Permanent account and IP login block **only if** `permanent-ip-ban-enabled: true`; otherwise staff review |

The ladder survives restarts in `plugins/AegisAC/sanctions.yml`. Keep backups and handle appeals; a shared household, VPN exit, or proxy address can belong to multiple players. Disabled enforcement does not erase existing bans. `/aegis reset <online-player>` clears a player's sanction stage. `/aegis pardon <uuid>` clears an offline account ban; `/aegis unban-ip <address>` removes an IP ban. Set permissions carefully.

## VPN gate

Optional, requires a [proxycheck.io API key](https://proxycheck.io/api/) in environment variable `AEGIS_PROXYCHECK_KEY` and `vpn.enabled: true`. Lookup uses HTTPS during asynchronous pre-login, with a 1.5-second timeout and a short-lived cache. A positive VPN result rejects that join with a retry message; it is **not a ban** and does not increment sanctions. API failure or unknown result allows the join. This is probabilistic classification, may miss VPNs or reject a normal player, and sends the connecting IP to the provider. If behind Velocity, configure [modern forwarding and proxy security](https://docs.papermc.io/velocity/player-information-forwarding/) before relying on IP controls.

## Commands

| Command | Purpose |
| --- | --- |
| `/aegis info` | Version and credits |
| `/aegis status` | TPS, tracked players, enabled controls |
| `/aegis alerts` | Toggle personal alerts |
| `/aegis inspect <online-player>` | Show alert buffers and sanction stage |
| `/aegis reset <online-player>` | Reset that player's evidence and stage |
| `/aegis pardon <uuid>` | Clear offline player's ban and stage |
| `/aegis unban-ip <address>` | Remove an IP ban |
| `/aegis reload` | Reload configuration and VPN state |
| `/aegis-ip <online-player>` | Show the IP address seen by Paper, privately to a permitted admin |

Permissions: `aegis.admin`, `aegis.ip`, `aegis.alerts` (default op), `aegis.bypass` (default false). The server-seen IP is not necessarily a player's home IP, especially when behind a proxy. Do not share IPs publicly.

Every 30 minutes the plugin broadcasts exactly `Made by @_adam814` by default.

## Console audit

AegisAC logs `[AUDIT]` entries for joins, quits, check alerts, blocked remote interactions, sanctions, VPN login decisions, login denials, and admin changes. Join entries show the address Paper sees beside the player name, for example: `[AUDIT] JOIN Steve [IP: 203.0.113.42] uuid=...`. IP addresses appear only in the server console and server logs; the normal public join message stays as configured by Paper. If you use a proxy, configure secure IP forwarding or the address may be the proxy's. Restrict access to server logs because they contain player IPs. Remote-interaction cancellation messages are limited to one per player every five seconds; normal movement events are not logged.

## Build

With Java 21 and Maven: `mvn package`. Or use Gradle with Java 21: `gradle build`. Paper dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

This code does not implement packet-level physics prediction or benchmark faster than GrimAC. See [Grim's source](https://github.com/GrimAnticheat/Grim) for its different scope. Check Paper's [project setup](https://docs.papermc.io/paper/dev/project-setup/) for current API guidance.
