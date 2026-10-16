# R17 NativeGuard Preview 1 validation

Validation date: 15 October 2026. This is a reference preview for Paper 1.21.11, not a production compatibility certification.

## Artifact and baseline

- JAR: AegisAC-R17-NativeGuard-preview1-1.21.11.jar
- SHA-256: `3718c95ce820e076a1cefe3d159576efe7ad97182041a98dbfe3b8422276f139`
- R16 baseline SHA-256: `d9eb4b0ab5589e4ab6b51db1f5a34a67df603f015041bbbb36c5fb6d8b1cab1f`
- All **5,931 inherited engine class files** matched the published R16 baseline byte for byte. Only the Aegis integration/guard overlay and product resources are replaced or added.
- Runtime tested: Windows 11, Amazon Corretto Java 25.0.4.1, Paper 1.21.11 build 132 (`c5eb079`). Paper JAR SHA-256: `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.
- Java 21 is the required minimum for Paper 1.21.11. This validation run used Java 25; Java 21 gameplay was not independently rerun here.

## Automated regressions

| Suite | Assertions | Result |
| --- | ---: | --- |
| Retained native actions, permissions, persistence and migration | 1,045 | PASS |
| Bounded queues, sessions, history and output | 24 | PASS |
| Existing TimerLimit profile migration and routing | 46 | PASS |
| VPN screening, provider parsing, caching and HTTP limits | 137 | PASS |
| New remote interaction rules, grace, reach, bursts and concurrency | 280 | PASS |
| Anti-Xray profile, backups, idempotency and invalid YAML | 24 | PASS |
| Actual R17 timing sanction routing and persistence | 16 | PASS |
| **Total** | **1,572** | **PASS** |

R17 intentionally changes timing-only auto-ban behaviour. Two expectations in the generated retained-action suite are updated from R16's Timer-ban expectation to the new no-auto-ban contract. The original R16 source and test files remain unchanged. The additional timing suite exercises the real command-event and direct action-routing paths for Timer, TimerA, TimerLimit, TickTimer, NegativeTimer and VehicleTimer; it verifies that no sanction or confirmed-cheating history is fabricated.

## Live server and protocol-client checks

The isolated server bound only `127.0.0.1:25581`, with a fresh test world and local test identities. No production server was changed.

- Server enabled the R17 plugin and guard, accepted admin commands, shut down cleanly and restarted with the generated anti-Xray profile. The subsequent installation result was `worlds-configured-on-disk=2 changed=0 errors=0`.
- A Mineflayer 4.39.0 client using protocol 1.21.11 completed the smoke script for idle joining, movement control inputs, jumps, sneak/strafe, hotbar selection, camera rotations and a normal block break without an R17 guard cancellation or kick. Guard status after that script: `blocked=0 kicks=0`; the test UUID's sanction stage remained zero.
- An artificial stone enclosure contained one real diamond ore block. The client sampled 125 interior locations; 124 locations known to be stone were advertised as decoys. Console checks separately confirmed the real stone and diamond blocks. This verifies that chunk obfuscation was active in the tested world; it does not prove all X-ray strategies are prevented.
- A second client sent three block-start requests approximately 500 ms apart to a block about 20 blocks from its body. The guard reported distance 19.57, allowed envelope 5.75, confirmations 3; it cancelled the requests and produced the branded `Remote Interaction Blocked` disconnect with reference `R17-MV9O2TYD`.
- Guard status after that test: `blocked=3 kicks=1 pending=0 overload=0 decodeUncertainty=0`. The kicked UUID remained at `stage=0 permanent=false remaining=none`. One `REMOTE_INTERACTION_KICK` record was retained.

The initial protocol-client attempt triggered the inherited TickTimer ban path. Mineflayer does not emit the modern end-of-client-tick packet by default; the QA harness supplies it after its movement writes. That initial attempt is retained as evidence and is not proof of a human vanilla-client false positive. Independently, R17's safer sanction policy now prevents timing-only checks from causing automatic bans, as verified by the new routing suite.

These are protocol-client smoke tests, not a certification of complete vanilla-client movement physics, all freecam implementations, client rendering behaviour or real-world false-positive rates. Full source, client results and selected server evidence are retained in `r17/results/`.

## Limits and unvalidated areas

- Passive camera-only freecam, arbitrary client files, Mods folders, installed cheat capabilities and reliable unused-client identification are not server-observable capabilities. No fake scan or fabricated client identification is implemented.
- The disconnect is the normal AegisAC-branded Minecraft kick screen. The plugin does not control a client overlay or observe when a local toggle is turned off.
- Obfuscation does not guarantee protection against world-seed inference, previously learned terrain or every exposed block. The End's configuration is preserved unchanged. Cave-air decoys may increase client rendering cost.
- No human Meteor/Fabric-client test, full production plugin combination, Geyser gameplay matrix, large-player benchmark or validation of every wall-interaction edge case was completed.
- No external live VPN-provider reputation request was required for the local loopback tests. The VPN regression suite covers its policy, failure, timeout and bounded-resource behaviour with controlled responses.
- No other Minecraft version is advertised as tested for this R17 overlay.
- Required dependency notices and source references remain included. Product branding does not establish exclusive ownership of inherited dependency code.

Use the preview on a test copy of the server first. Keep existing configuration backups and use `action: CANCEL` if staff want interaction cancellation while gathering additional evidence before enabling guard kicks.
