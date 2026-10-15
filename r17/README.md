# AegisAC R17 NativeGuard Preview 1

R17 keeps the published R16 detection stack and adds server-side protection against remote interactions, an AegisAC kick screen, and a strict anti-Xray configuration. This is the Paper 1.21.11 reference build. It is a preview for testing before production deployment.

## Included

- The existing R16 movement, combat, packet prevention, VPN/proxy/Tor screening, staff commands, Discord integration, history and sanction storage.
- An additional remote-action guard. Block interaction packets outside every retained, eligible server reach envelope are cancelled. Repeated large violations can disconnect the player with an AegisAC screen and a support reference.
- Server reach attributes, recent eye positions, teleports, world changes, ping, server stalls, knockback, explicit exemptions and queued-action rechecks are accounted for.
- Packet bursts and duplicate main/off-hand actions cannot supply several kick confirmations at once. Evidence expires and state is bounded. The guard never adds a ban stage.
- Solid-wall interaction checks use several visibility rays and recent positions. Occlusion alone does not cause a kick. World reads remain on the server thread; unloaded chunks are not loaded by this guard. Work is capped and uncertainty defers enforcement.
- Timing-only checks, including TickTimer, retain their native checks/prevention but cannot feed AegisAC's automatic ban ladder. Existing stored bans are not deleted or reset by upgrading.
- Per-world anti-Xray mode 3, full world-height coverage, cave-air decoys, lava obscuring and no permission-based obfuscation bypass. The generated profile protects the overworld and Nether. The End is left unchanged.

## Actual limits

A standard server plugin cannot enumerate another computer's Mods folder, inspect arbitrary mod scripts/options, or prove that a client has an unused cheat capability. R17 performs no client filesystem scan and does not reject Fabric or invent a detected client name.

Camera-only freecam can leave the player's network behaviour identical to normal play. R17 does not claim to detect that toggle. It acts on observable invalid interactions; the kick screen appears after a server disconnect, rather than as a client overlay that waits for a locally enabled feature to be switched off. A player can reconnect; continued invalid interactions can trigger protection again.

Anti-Xray reduces what terrain information reaches a client. It is not an impossible-to-bypass detector. Exposed blocks, already known terrain and seed-based inference remain limitations. No universal zero-false-block guarantee is made. Review the validation scope in QA_REPORT.md.

## Install

1. Stop the server and back up its existing AegisAC data, plugin JAR and world configuration files.
2. Keep one AegisAC JAR in `plugins/`, replacing the R16 JAR with `AegisAC-R17-NativeGuard-preview1-1.21.11.jar`.
3. Start Paper 1.21.11 with Java 21 or newer. The existing AegisAC data folder, credentials, bans and permission nodes are retained. The new settings are in `plugins/AegisAC/r17-guard.yml`.
4. Run `/aegis guard status` and `/aegis xray status`.
5. When R17 reports that world configurations changed, fully stop and start the server again. Anti-Xray changes require that restart; `/reload` does not apply them. Each original world configuration is backed up to `paper-world.before-aegis-r17.yml` before its first modification.
6. Test normal play and the server's other plugins before production rollout. Returning to R16 uses the backed-up JAR and configuration files.

The anti-Xray profile changes only the anti-Xray settings in each `paper-world.yml`; it does not regenerate terrain or alter existing world seeds. Full-height protection and cave-air decoys can add client rendering cost. Set `anti-xray.hide-cave-air: false` if necessary, then apply the profile and restart. Existing custom block-list additions are retained where applicable.

## Commands and permissions

Existing `/aegis` commands remain available. Added commands:

| Command | Purpose |
| --- | --- |
| `/aegis guard status` | Guard counters and capability summary |
| `/aegis guard reload` | Reload `r17-guard.yml`; clear pending guard evidence |
| `/aegis xray status` | Configuration results; indicates on-disk state |
| `/aegis xray install` | Apply the current anti-Xray profile to loaded overworld/Nether worlds |

Administration uses `aegisac.admin`. `aegisac.guard.bypass` is an explicit guard exemption and defaults to false, including for operators. Existing global exemptions and temporary Aegis exemptions are respected. Existing per-check permissions remain unchanged. `/aegis pause` pauses guard kicks as well as the existing automatic sanction review; packet protection can continue.

The guard defaults to `action: KICK`. Change it to `CANCEL` to retain interaction cancellation while disabling guard kicks. `enabled: false` disables only the new guard; the R16 stack continues. The default confirmation window requires at least three widely separated large violations. Settings validate bounded ranges rather than allowing instant single-packet kicks.

## Build and source

From the repository root, with a full JDK 21 or newer:

```text
java r17/tools/BuildR17.java
```

The build downloads hash-pinned R16 release inputs if absent, compiles only the R17 overlay and its integration classes, verifies every inherited engine class byte for byte, runs the R16 and R17 regression suites, and writes the JAR and SHA256SUMS.txt under `r17/build/`.

The source is maintained on the repository's `development/r17-native-guard` branch. R16's exact baseline inputs and corresponding source remain available at:

https://github.com/projectsadameyd/AegisAC/releases/tag/v4.2.0-r16-vpnguard1

Product branding is AegisAC. Required third-party licences, copyright notices, internal package names and compatibility permissions remain preserved; rebranding does not transfer ownership of dependency code. See THIRD_PARTY_NOTICES.md.

## Configuration references

- https://docs.papermc.io/paper/anti-xray/
- https://docs.papermc.io/paper/reference/world-configuration/
- https://docs.papermc.io/paper/dev/plugin-messaging/

Made by @_adam814.
