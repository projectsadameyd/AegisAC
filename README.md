# AegisAC 0.3.3-alpha

Paper 1.21.11 / Java 21 anti-cheat prototype. Put the built JAR in `plugins/` and restart Paper. Test on a private server before deploying to players.

## Scope and limitations

No server plugin can guarantee detection of every cheat, instant detection, zero false positives, or superior performance to GrimAC without controlled benchmarks. **Passive Freecam is not detectable by this plugin**: a camera moving only on the client sends no distinctive movement to Paper. AegisAC immediately cancels block break, placement, and interaction **beyond the player's server-side block-reach attribute plus a safety margin**. This stops a class of remote actions, not passive viewing. Paper's [built-in Anti-Xray](https://docs.papermc.io/paper/anti-xray/) reduces ore information sent to clients; it does not remove Freecam or hide every visible build.

Speed, Fly, Timer, Reach, AutoClicker, FastPlace, and Velocity checks generate staff alerts. Only sustained, qualifying Speed alerts and remote block interactions are eligible for staged sanctions on new installations. Fly and the other heuristic checks produce staff alerts by default; `/aegis enforcement all on` explicitly opts every check into staged sanctions with a reachable per-check score threshold and sustained evidence. A Speed failed sample or a current buffer above threshold is **not** a sanction event; the debug command shows alert count, heuristic score, and the gate that stopped escalation.

## Sanctions and appeals

New installations have `enforcement.enabled: true` with `FREECAM_INTERACT` and `SPEED` eligible. Speed requires eight qualifying alerts within 90 seconds, spanning at least five seconds, at a heuristic score of at least 92%. The score is a threshold, not a measured probability of cheating. The old eight-consecutive-ground-tick sanction gate rejected real alert sequences and has been removed; legitimate players can still trigger this heuristic. Remote interactions require four qualifying alerts at a 98% heuristic score. There is a 10-minute cooldown after a sanction. **Existing `config.yml` files retain their old eligible-check list**: run `/aegis status`, `/aegis enforcement on` if necessary, then `/aegis enforcement speed on` to add Speed. Test on a private server before enabling sanctions for public players. `/aegis enforcement all on` writes every check to the eligible list and sets distinct score and evidence thresholds; it does not enable permanent IP banning.

| Event | Action |
| --- | --- |
| 1–3 | Kick |
| 4 | Temporary one-hour login block |
| 5 | Temporary one-day login block |
| 6 | Permanent account and IP login block **only for remote-interaction evidence** and only if `permanent-ip-ban-enabled: true`; Other checks require staff review |

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
| `/aegis enforcement <check> <on|off>` | Opt in or out of one check (for example `fly` or `speed`); `on` sets its evidence thresholds |
| `/aegis enforcement all <on|off>` | Opt every check in with distinct thresholds, or disable all automatic sanctions |
| `/aegis ipban <on|off>` | Enable or disable the final IP-ban stage, saved to config |
| `/aegis alerts` | Toggle personal alerts |
| `/aegis inspect <online-player>` | Show alert buffers and sanction stage |
| `/aegis reset <online-player>` | Reset that player's evidence and stage |
| `/aegis pardon <uuid>` | Clear offline player's ban and stage |
| `/aegis unban-ip <address>` | Remove an IP ban |
| `/aegis reload` | Reload configuration and VPN state |
| `/aegis-ip <online-player>` | Show the IP address seen by Paper, privately to a permitted admin |

Permissions: `aegis.admin`, `aegis.ip`, `aegis.alerts` (default op), `aegis.bypass` (default false). The server-seen IP is not necessarily a player's home IP, especially when behind a proxy. Do not share IPs publicly.

If a check appears idle, inspect `/aegis debug <player>` while that player is moving and interacting. It separates total movement events from **evaluated** and **exempt** positional movements. Alert count, last heuristic score, the sanction gate, current stage, and cooldown explain why a failed sample did not cause a kick. After eight qualifying Speed alerts spanning at least five seconds, the first sanction is a kick; stages persist between reconnects. Check `/aegis info` for the loaded plugin version and `/aegis status` for sanction settings. The CI build tests the sustained-alert threshold; it does not replace testing on a running Paper server.

### If nothing happens on your server

1. Remove older AegisAC JARs from `plugins/`, install only `AegisAC-0.3.3-alpha.jar`, restart Paper, and check `/aegis info` or `/aegis status` for `0.3.3-alpha`. A config reload cannot replace loaded Java code.
2. Run `/aegis enforcement all on`, then `/aegis status`. If you want the original narrower setup, use `/aegis enforcement all off`, `/aegis enforcement on`, then `/aegis enforcement speed on` and `/aegis enforcement freecam_interact on`. Check `bypass=false` in `/aegis debug <online-player>`.
3. Trigger activity while checking `/aegis debug <online-player>` and console `[AUDIT] ALERT` or `[AUDIT] SANCTION` entries. The `gate=` field names the last reason a sanction did not advance. `stage=1` means the first kick worked; stages 1–3 are kicks, and stage 4 is the first temporary ban. After each sanction there is a 10-minute cooldown. `/aegis status` warns when a configured minimum score exceeds a check's possible score.
4. VPN blocking is separate from sanction enforcement and requires `vpn.enabled: true` and the `AEGIS_PROXYCHECK_KEY` environment variable. `/aegis status` reports whether the gate is ready. Remote block actions can be canceled; passive Freecam viewing cannot be identified from Paper events alone.

All-check mode allows kicks and temporary bans from less reliable event heuristics. An alert or a displayed score is not proof of cheating. IP bans remain off unless separately enabled and are never issued for Speed or the other heuristic checks. Review audit logs and player appeals.

Every 30 minutes the plugin broadcasts exactly `Made by @_adam814` by default.

## Console audit

AegisAC logs `[AUDIT]` entries for joins, quits, check alerts, blocked remote interactions, sanctions, VPN login decisions, login denials, and admin changes. Join entries show the address Paper sees beside the player name, for example: `[AUDIT] JOIN Steve [IP: 203.0.113.42] uuid=...`. IP addresses appear only in the server console and server logs; the normal public join message stays as configured by Paper. If you use a proxy, configure secure IP forwarding or the address may be the proxy's. Restrict access to server logs because they contain player IPs. Remote-interaction cancellation messages are limited to one per player every five seconds; normal movement events are not logged.

## Build

With Java 21 and Maven: `mvn package`. Or use Gradle with Java 21: `gradle build`. Paper dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

This code does not implement packet-level physics prediction or benchmark faster than GrimAC. See [Grim's source](https://github.com/GrimAnticheat/Grim) for its different scope. Check Paper's [project setup](https://docs.papermc.io/paper/dev/project-setup/) for current API guidance.
