# R17 Companion Preview 2 validation

Server JAR SHA-256: `654d18386ad94c1f91ed7c7bbb0dcf39c95057868a9d545ca83ee18aa7d3122e`.

Companion JAR SHA-256: `cf78185128f1e73a53569168183ded2ffcc01d9f4f642f025cb7370c11c9d4a7`.

Both JARs built successfully for 1.21.11. The server build passed 1,778 assertions across eight suites; the actual production Minecraft/Fabric payload codecs passed seven additional checks. All 5,931 inherited engine class files remain byte-for-byte identical to R16. The native-action suite retains the two documented R17 timing-sanction expectation changes.

The scanner detected official Meteor 0.5.9 and 1.21.11-86 JARs, including renamed copies. Official Sodium mc1.21.11-0.8.15-beta.1-fabric and the Paper API produced no known match. Wurst and LiquidBounce rules have compiled synthetic fixtures; their actual client JARs were not tested here. Several compiled class identities within a common code layout are required. These are curated signatures, not universal mod-capability analysis.

An isolated Paper 1.21.11 build 132 server confirmed pre-world missing-companion and Meteor block dialogs and download actions without adding bans. These protocol tests use the same Java scanner through a loopback-only harness, not a human Fabric client or visual Minecraft rendering test. They preceded the final event-priority adjustment. The final production companion codecs preserve the exact server wire bytes and reject oversized payloads.

AuthMe 6.0.1 was successfully enabled. The initial harness ignored AuthMe's own pre-join registration dialog and consequently timed out; this was a test-harness failure, not a demonstrated human login failure. The final server code orders Aegis admission at LOWEST event priority, and the final harness can answer AuthMe's dialog. An execution-environment reset interrupted the final combined run, so successful end-to-end AuthMe authentication with the final build remains unverified. AuthMe settings, passwords and authentication checks are not bypassed or changed.

QA_REPORT.md records Preview 1's remote-interaction and anti-Xray tests. Those earlier results are not a fresh live result for this final build with AuthMe.

This is a test preview. The player controls their device and can modify the companion or forge a passing report. A per-connection nonce prevents blind replay; it does not attest that honest code ran. Unknown clients, injected code, altered code layouts and some cheat mods can evade screening. There is no every-client, unbypassable or zero-false-block guarantee. Forge, NeoForge, Bedrock and other Minecraft versions are unsupported by this Fabric companion build.
