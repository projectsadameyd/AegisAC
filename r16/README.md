# AegisAC R16 VPNGuard

R16 keeps the native anti-cheat and R15 TimerSafety behavior, adds asynchronous VPN/proxy/Tor connection screening, and exposes only the `/aegis` administration command.

## Install or upgrade

1. Stop the server and back up `plugins/AegisAC/`.
2. Install exactly one JAR for the server version. Remove older AegisAC JARs and separately installed copies of the bundled engines.
3. Keep the existing AegisAC data folder. Start the server and check `/aegis status` and `/aegis vpn status`.
4. Review settings and try the build on a test server before deploying to players.

The primary JAR targets Paper 1.21.11 with Java 21. Other ports are compatibility previews; see [COMPATIBILITY.md](COMPATIBILITY.md) and [QA_REPORT.md](QA_REPORT.md).

## VPN protection

Edit `plugins/AegisAC/anti-vpn.yml`. Screening is enabled by default. Put your proxycheck.io key in `api-key`, keep the three block flags enabled, then run `/aegis reload`. A key is recommended for production quotas.

`/aegis vpn on` enables screening. `/aegis vpn off` disables it. `/aegis vpn status` shows settings and health. Use `/aegis vpn allow <ip>` and `unallow <ip>` for exact-address exceptions; `check <ip>` tests an address and `clearcache` clears cached decisions. Admin permission: `aegisac.admin`.

Only explicit provider VPN/proxy/Tor flags cause a block. A lookup error, timeout, or exhausted quota allows the player by default (`allow-on-lookup-failure: true`). A block is an AegisAC-branded login refusal and never advances cheating sanctions. Public connecting IP addresses are sent to proxycheck.io over HTTPS. Local addresses and allowlisted addresses skip external lookups. Configure secure player IP forwarding when using a Minecraft proxy.

## Native engine and timer safety

Native detection, packet prevention and setbacks remain in place. TimerLimit is excluded from automatic sanctions because latency and packet timing can produce false evidence. Ordinary Timer detection and unrelated checks retain their behavior.

Grim/Donut commands and public command aliases are removed. Legacy permission nodes remain for compatibility with existing server permissions. Required third-party notices remain included. The creator broadcast `Made by @_adam814` remains on its ten-minute schedule.

## Source build

Use JDK 21 or newer and Python 3:

```sh
python3 fetch-build-inputs.py       # Git checkout only; release source ZIP already includes inputs
python3 build.py --jdk /path/to/jdk --output build
```

The source archive contains the pinned baseline JAR, compile dependencies, native corresponding source and patcher. Those inputs are for rebuilding, not separate server installation. Own runtime classes compile with `--release 17`; the builder produces 21 version variants and runs the regression suites.

See [SOURCE_RECOVERY.md](SOURCE_RECOVERY.md) for how modified R16 source was recovered from the validated binary, [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for provenance, and [LICENSE](LICENSE) for the GPL license.
