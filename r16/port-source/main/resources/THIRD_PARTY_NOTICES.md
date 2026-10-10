# Third-party notices

## AegisAC and native anti-cheat

The combined release is distributed under the GNU General Public License v3 supplied in `LICENSE`. The native engine is Grim 2.3.74 at commit `abb95b6`, from the GrimAnticheat project. Its corresponding source archive and the integration RuntimePatcher are included in the full source asset.

The release preserves 5,930 native classes against the pinned R15 baseline, excluding the disabled command registrar. All 176 classes in the native `ac/grim/grimac/checks/` package are preserved. Public Aegis branding does not imply authorship of the bundled third-party engines.

## DonutAntiCheat assistant component

Original plugin author: DonutAC. Publisher: jakeyy44545 on Modrinth.
Publisher project: https://modrinth.com/plugin/donutanticheat
Publisher version: https://modrinth.com/plugin/donutanticheat/version/1.0.0
Project ID: `V1tZp5Vg`; version ID: `oQGpBAPj`.

The publisher metadata identifies the original 1.0.0 artifact as MIT licensed. The published JAR has 71,554 bytes and SHA-512:

`41eb7fef32cde4110112fedef24f122ac18f69784a716b47671eb224a087ade73d2309a047dff28e063686ffbf21e6512c39a328ea941075097e532dae561c3f`.

Its classes and default configuration identify the bundled assistant's provenance. Twenty-six of 29 common original classes match the pinned input byte for byte; the remaining integration classes are adapted. The assistant source in this package is recovered/adapted source, and its original commands are removed. The full MIT permission notice is included in `LICENSE-DonutAntiCheat-MIT.txt`.

This verified publisher license replaces the historical unknown-license/private-server-use statement that was present in the earlier working package. Original credits and legally required notices are retained here.

## Other dependencies

Paper API, API dependencies and linkage dependencies are compile inputs, not separately installed plugins. Their original embedded license notices remain intact. Shaded native dependencies and their notices are retained in the runtime JAR. No third-party endorsement is claimed.

VPN screening uses proxycheck.io over HTTPS. Public connecting IPs are sent to that provider when enabled; no API keys or configured webhook secrets are supplied in release defaults.
