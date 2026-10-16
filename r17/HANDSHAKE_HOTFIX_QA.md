# R17 Preview 2 - Handshake Hotfix 1

Built and tested on 15 October 2026 for Paper 1.21.11 and Fabric 1.21.11.

## Defect and change

Preview 2 sent its challenge once without first checking the receiver registration. Paper's configuration connection silently skips outgoing plugin messages for channels the client has not advertised. Fabric ordinarily sends its initial configuration receiver list after receiving a server registration packet. A companion could therefore be installed but never receive a challenge.

The hotfix announces the companion receiver during Fabric configuration INIT, using Fabric's existing registration payload codec. The server waits within its original bounded deadline for that receiver before sending one challenge. The admission wire protocol and signature policy remain compatible with Preview 2. Timeout logging records whether a challenge was sent. The stock Preview 2 download URL migrates to the hotfix; custom URLs are preserved.

## Validation

- Original Preview 2, receiver registration delayed 1,000 ms: no challenge, companion-required block, no world admission.
- Hotfix, same 1,000 ms delay: challenge delivered, valid report accepted, AuthMe login dialog completed, world admission.
- Clean-client protocol run: successful AuthMe 6.0.1 login and normal world admission.
- Official Meteor 1.21.11-86 code scanned by the real shared scanner: named Meteor block dialog before world admission; no ban added.
- Missing companion, incomplete scan, wrong nonce, malformed report and unsupported build: no world admission.
- Disconnect button: connection closed without world admission.
- Clean admission followed by repeated excessive remote interactions: successful AuthMe login, then the expected branded remote-interaction kick.
- Eight server regression suites passed: 1,778 assertions. All 5,931 inherited R16 engine class files remain byte-for-byte unchanged.
- Eight actual Minecraft/Fabric payload-codec assertions passed, including raw registration bytes compatible with Paper.

Tests used an isolated loopback Paper 1.21.11 build 132 server with unmodified AuthMe 6.0.1. The protocol harness runs the real scanner and exchanges actual packets. Its dialog-action encoder corrects a length-prefix mismatch in the installed minecraft-data schema, locally within the test harness. No AuthMe authentication bypass was added.

## Limits

These results do not constitute a rendered human Fabric/BlueClient session test, an exhaustive benign-mod test or an unforgeable client attestation. Client reports can be forged. Unknown cheat layouts can escape the curated signatures. Wurst and LiquidBounce coverage remains based on synthetic fixtures; actual Meteor and Sodium artifacts were tested. This remains a beta for testing before production use. Only Paper/Fabric 1.21.11 is supported by this build.

## Artifacts

Server: AegisAC-R17-Companion-preview2-hotfix1-1.21.11.jar

SHA-256: 5f5e3221f55afa7808dc7590126f315a857ee220a89eaadcf40d7e1cbb49d64a

Companion: AegisAC-Companion-R17-preview2-hotfix1-1.21.11.jar

SHA-256: d21f8f8f2304c2df16a783d845ee34770d604228b3e8b08ebf16045c1b5a4862
