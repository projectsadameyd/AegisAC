# AegisAC R17 Companion Preview 2

R17 keeps the published R16 detection stack. Preview 2 adds a required Fabric companion and an AegisAC download/block dialog before players enter a world. The server plugin is for Paper 1.21.11; the companion is for Fabric 1.21.11. This remains a preview for testing before production deployment. See COMPANION_QA.md for the current validation scope; QA_REPORT.md records Preview 1's baseline tests.

## Included

- A mandatory companion check during Paper's configuration phase, before world access. There is no OP bypass. AuthMe continues to authenticate afterward, using its own pre-join dialog on versions that support it, or its ordinary in-world login flow.
- Native Minecraft dialogs with a companion download button, Fabric API link, instructions and disconnect button. Download opens the browser; Minecraft cannot install a Java mod from this screen.
- A client-side scan of loaded mod origins for curated compiled-code family signatures. No filename blacklist, generic Fabric rejection, blanket script rejection, unrelated-file scanning, full mod-list upload or machine identifier collection.
- Known signature matches, missing/outdated companions and incomplete checks cannot grant entry. An incomplete check is described as a verification failure, not proof of cheating. No persistent ban or sanction stage is created.
- The existing R16 movement, combat, packet prevention, VPN/proxy/Tor screening, staff commands, Discord integration, history and sanction storage.
- An additional remote-action guard. Block interaction packets outside every retained, eligible server reach envelope are cancelled. Repeated large violations can disconnect the player with an AegisAC screen and a support reference.
- Server reach attributes, recent eye positions, teleports, world changes, ping, server stalls, knockback, explicit exemptions and queued-action rechecks are accounted for.
- Packet bursts and duplicate main/off-hand actions cannot supply several kick confirmations at once. Evidence expires and state is bounded. The guard never adds a ban stage.
- Solid-wall interaction checks use several visibility rays and recent positions. Occlusion alone does not cause a kick. World reads remain on the server thread; unloaded chunks are not loaded by this guard. Work is capped and uncertainty defers enforcement.
- Timing-only checks, including TickTimer, retain their native checks/prevention but cannot feed AegisAC's automatic ban ladder. Existing stored bans are not deleted or reset by upgrading.
- Per-world anti-Xray mode 3, full world-height coverage, cave-air decoys, lava obscuring and no permission-based obfuscation bypass. The generated profile protects the overworld and Nether. The End is left unchanged.

## Actual limits

A standard server plugin cannot enumerate another computer's Mods folder or prove that a client has an unused cheat capability. Preview 2's separately installed companion reads code at the running Fabric loader's mod origins. Its curated signatures currently cover specific Meteor, Wurst and LiquidBounce code layouts. Unknown mods are allowed when the scan completes without a match. This is not universal capability analysis; see COMPANION_QA.md for which actual artifacts were tested.

The player controls their device. A modified companion can forge a passing report, remove the signatures, hide injected code or otherwise defeat this screening. The per-connection random challenge prevents blind report replay; it does not prove that honest companion code ran. Do not advertise this as tamper-proof attestation, every-client detection, or a no-false-block guarantee. Forge/NeoForge clients, other Minecraft versions and Bedrock clients cannot use this Fabric-only companion build.

Camera-only freecam can leave the player's network behaviour identical to normal play. R17 does not claim to detect that toggle. It acts on observable invalid interactions; the kick screen appears after a server disconnect, rather than as a client overlay that waits for a locally enabled feature to be switched off. A player can reconnect; continued invalid interactions can trigger protection again.

Anti-Xray reduces what terrain information reaches a client. It is not an impossible-to-bypass detector. Exposed blocks, already known terrain and seed-based inference remain limitations. No universal zero-false-block guarantee is made. Review the validation scope in QA_REPORT.md.

## Install

1. Stop the server and back up its existing AegisAC data, plugin JAR and world configuration files.
2. Keep one AegisAC server JAR in `plugins/`, replacing the old JAR with `AegisAC-R17-Companion-preview2-1.21.11.jar`. Never put the companion in the server plugins folder.
3. Players install Fabric Loader 0.19.5+, Fabric API 0.141.6+ for 1.21.11 and `AegisAC-Companion-R17-preview2-1.21.11.jar` in their own Minecraft `mods/` folder, then restart Minecraft. Other mods are permitted unless their code matches a prohibited signature.
4. Start Paper 1.21.11 with Java 21 or newer. Existing AegisAC data, AuthMe data, credentials, bans and permission nodes are retained. Settings are in `plugins/AegisAC/client-guard.yml` and `r17-guard.yml`. Companion admission defaults to required; test on a copy first.
5. Run `/aegis client status`. The default download link points to this development branch's companion artifact; operators may host the exact JAR elsewhere and set `download-url` to their HTTPS download URL.
6. Run `/aegis guard status` and `/aegis xray status`.
7. When R17 reports that world configurations changed, fully stop and start the server again. Anti-Xray changes require that restart; `/reload` does not apply them. Each original world configuration is backed up to `paper-world.before-aegis-r17.yml` before its first modification.
8. Test normal play and the server's other plugins before production rollout. Returning to R16 uses the backed-up JAR and configuration files.

The anti-Xray profile changes only the anti-Xray settings in each `paper-world.yml`; it does not regenerate terrain or alter existing world seeds. Full-height protection and cave-air decoys can add client rendering cost. Set `anti-xray.hide-cave-air: false` if necessary, then apply the profile and restart. Existing custom block-list additions are retained where applicable.

## Commands and permissions

Existing `/aegis` commands remain available. Added commands:

| Command | Purpose |
| --- | --- |
| `/aegis client status` | Required-companion status, report/block counters and AuthMe presence |
| `/aegis client reload` | Reload admission settings for new connections |
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

Build the companion with Gradle 9.2.1 / Java 21+: `gradle -p r17/companion build`. On Windows hosts with restricted ZIP filesystem access, run `powershell -File r17/tools/BuildCompanion.ps1`; it downloads checksum-pinned developer inputs and uses directory outputs. Its `-Offline` switch requires all inputs/dependencies to be present. The fallback compiles against Fabric's production intermediary interfaces and runs the actual production payload codecs against Minecraft classes. Neither route bundles Minecraft classes or mapping files in the delivered companion.

Product branding is AegisAC. Required third-party licences, copyright notices, internal package names and compatibility permissions remain preserved; rebranding does not transfer ownership of dependency code. See THIRD_PARTY_NOTICES.md.

## Configuration references

- https://docs.papermc.io/paper/anti-xray/
- https://docs.papermc.io/paper/reference/world-configuration/
- https://docs.papermc.io/paper/dev/plugin-messaging/

Made by @_adam814.
