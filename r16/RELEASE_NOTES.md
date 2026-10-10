# AegisAC R16 VPNGuard

R16 adds AegisAC-branded VPN, proxy and Tor connection blocking while preserving the native anti-cheat, packet prevention, setbacks, and R15 TimerSafety fix.

## Changes

- Asynchronous pre-login screening with bounded workers, shared lookups, a bounded cache, full-response deadlines and response size limits.
- Blocks explicit VPN/proxy/Tor classifications. Hosting or risk scores alone do not block a player.
- Login refusal does not create a cheating ban or advance the sanction ladder.
- `/aegis vpn status|on|off|check|allow|unallow|clearcache`.
- Exposed Grim/Donut commands, aliases and player-facing engine branding removed. Compatibility permission nodes and required license notices retained.
- TimerLimit remains excluded from automatic sanctions; ordinary Timer and unrelated checks retain native behavior.

## Downloads and installation

- `AegisAC-R16-VPNGuard-1.21.11.jar`: primary Paper 1.21.11 / Java 21 plugin.
- `AegisAC-All-Versions-R16-VPNGuard.zip`: all 21 version variants, setup notes and source.
- `AegisAC-R16-VPNGuard-Source.zip`: full build inputs, recovered source and regression suites.
- `SHA256SUMS.txt`: checksums for the downloadable artifacts.

Stop the server, back up `plugins/AegisAC/`, replace the old plugin with exactly one matching JAR, retain the data folder, and restart. Do not install multiple AegisAC variants together.

## Enable VPN screening

Screening defaults to enabled. Add a proxycheck.io key to `plugins/AegisAC/anti-vpn.yml`, keep `block-vpn`, `block-proxy`, and `block-tor` true, then run `/aegis reload`. Check `/aegis vpn status`. API failures and exhausted quota allow joins by default. An allowlist is available for legitimate players.

## Validation and compatibility

The primary runtime previously passed 1,307 regression assertions, 170 VPN assertions with legacy Gson, and an isolated Paper 1.21.11 build 132 startup/admin-command check. The public primary preserves every runtime class from that validated R16 binary; only package metadata and license notices change.

Recovered source is recompiled and checked with the original 1,115 sanction/history/timer regression assertions plus a new VPN recovery suite. Results are included in the bundle. Native check classes are verified byte for byte against the pinned baseline.

The 21 variants are compatibility previews. Real client gameplay and startup on every Minecraft version have not been verified. This release is marked as a prerelease.

## Source and licenses

Current R16 source is under `r16/` in the repository. The root Maven/Gradle project is the historical 0.3.5 prototype. Use the R16 Python builder or the full release source ZIP.

GPL native corresponding source and required notices are supplied. Assistant component licensing was verified against the publisher's MIT-labelled Modrinth release; its full MIT notice is included. Source recovery and remaining validation limits are documented in `SOURCE_RECOVERY.md` and `QA_REPORT.md`.
