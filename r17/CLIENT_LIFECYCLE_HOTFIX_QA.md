# R17 Preview 2 - Client Lifecycle Hotfix 2

## Observed defect

The user's actual Fabric 1.21.11 log confirmed that Hotfix 1 loaded successfully, but its INIT callback sent a configuration custom payload while Minecraft still used the LOGIN outbound packet codec. The result was `EncoderException: Sending unknown packet 'serverbound/minecraft:custom_payload'`. The earlier protocol harness did not execute the Fabric client's lifecycle callbacks, so it did not catch this defect. Hotfix 1's client artifact is superseded.

## Correction

INIT now advances only the connection epoch. The receiver announcement is sent from ClientConfigurationConnectionEvents.START, when Fabric permits configuration packets. The server's bounded registration wait remains. The server download URL and stock old-URL migrations point to Hotfix 2. Custom URLs are retained. The client metadata version is now valid SemVer, `0.17.0-preview2.hotfix2`; admission wire build and signature policy remain `r17-preview2`, compatible with the Hotfix 1 server.

Official lifecycle contract:
https://maven.fabricmc.net/docs/fabric-api-0.141.1%2B1.21.11/net/fabricmc/fabric/api/client/networking/v1/ClientConfigurationConnectionEvents.html

## Validation

- The delivered companion was loaded against the real production Minecraft and Fabric API classes. Its initializer was executed and the real Fabric INIT event was invoked with no active configuration sender. It advanced the epoch successfully without sending a packet. The same regression rejects the Hotfix 1 artifact.
- Nine production payload/lifecycle assertions passed, including exact admission bytes, defensive copies, size limits, Paper-compatible Fabric registration encoding and the no-send INIT regression.
- All eight server regression suites passed: 1,778 assertions, including official Meteor scanning and Sodium's no-match test. All 5,931 inherited R16 engine classes are unchanged.
- JAR checksums and ZIP contents were verified.
- The previous Hotfix 1 server protocol runs remain historical evidence for report admission, AuthMe authentication and remote-interaction behavior. They are not claimed as a full Fabric client test of this correction.

## Remaining validation

A new BlueClient connection with the Hotfix 2 companion has not yet been observed. The user must replace the old companion, restart Minecraft and reconnect. This correction does not provide universal cheat detection, tamper-proof client attestation or a zero-false-block guarantee. Only Paper/Fabric 1.21.11 is supported.

## Artifacts

Companion: AegisAC-Companion-R17-preview2-hotfix2-1.21.11.jar

SHA-256: feedb0ddd2d88f7d91b88e74970241ecb632de1aad343acc568779ab68baabac

Server: AegisAC-R17-Companion-preview2-hotfix2-1.21.11.jar

SHA-256: fb6f69b36723e7e07c5d2e5b6bd9cd0eac2f31539d7061330b29986c0b33b0bb
