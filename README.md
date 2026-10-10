<img width="1254" height="1254" alt="AegisAC logo original" src="https://github.com/user-attachments/assets/3ac52706-ffed-48c9-b23f-fabe92974a2d" />

# AegisAC R16 VPNGuard

AegisAC combines native anti-cheat detection, packet prevention and setbacks with server administration and asynchronous VPN/proxy/Tor connection protection.

[Download R16](https://github.com/projectsadameyd/AegisAC/releases/tag/v4.2.0-r16-vpnguard1) · [Installation and VPN setup](r16/README.md) · [Compatibility](r16/COMPATIBILITY.md) · [Validation](r16/QA_REPORT.md)

## Install

Download `AegisAC-R16-VPNGuard-1.21.11.jar` for Paper 1.21.11 / Java 21, or choose the exact server version from the all-versions ZIP. Stop the server, back up `plugins/AegisAC/`, replace the old JAR with exactly one matching variant, keep the existing data folder, and restart.

R16 is a prerelease. The primary runtime has isolated Paper 1.21.11 startup validation; the other ports are compatibility previews. See the included validation notes before deployment.

## VPN protection

Screening is enabled by default. Add your proxycheck.io API key to `plugins/AegisAC/anti-vpn.yml`, keep `block-vpn`, `block-proxy` and `block-tor` enabled, and run `/aegis reload`. Check `/aegis vpn status` or enable it with `/aegis vpn on`.

Explicit provider VPN/proxy/Tor classifications receive an AegisAC-branded login refusal. Lookup failures allow joins by default. VPN screening never advances the cheating sanction ladder. Use `/aegis vpn allow <ip>` for exact-address exceptions.

## What changed

- Native anti-cheat and R15 TimerSafety behavior retained. TimerLimit remains excluded from automatic sanctions; ordinary Timer and unrelated checks keep their behavior.
- Asynchronous connection screening with bounded workers, cache, shared lookups and deadlines.
- Only `/aegis` is exposed; old engine commands and aliases are removed. Required licenses and compatibility permissions remain included.

See [R16 release notes](r16/RELEASE_NOTES.md) for the complete setup and validation details.

## Source

Current R16 source lives in [r16/](r16/). From a Git checkout, use JDK 21 or newer:

```sh
cd r16
python3 fetch-build-inputs.py
python3 build.py --jdk /path/to/jdk --output build
```

The downloadable R16 source ZIP already includes the pinned build inputs. Source recovery and runtime identity are documented in [SOURCE_RECOVERY.md](r16/SOURCE_RECOVERY.md). Native corresponding source and third-party notices are included in the release source asset.

The root Maven/Gradle project and `src/` retain the historical 0.3.5-alpha prototype. Its documentation is archived in [docs/prototype-0.3.5.md](docs/prototype-0.3.5.md); those build commands do not build R16.

## License and provenance

See [GPL license](r16/LICENSE), [third-party notices](r16/THIRD_PARTY_NOTICES.md), and [assistant MIT license](r16/LICENSE-DonutAntiCheat-MIT.txt). Player-facing AegisAC branding does not remove required third-party notices.
