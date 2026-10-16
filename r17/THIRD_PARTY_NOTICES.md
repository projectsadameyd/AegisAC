# AegisAC R17 NativeGuard notices

This build retains the R16 detection stack; it does not claim that upstream detection code is independently authored or exclusively owned by AegisAC. Product commands, kick messages and presentation use AegisAC branding. Internal package names and permission nodes are retained for compatibility.

The upstream engine classes are byte-for-byte identical to the published R16 baseline. GrimAC is distributed under GPL-3.0; its licence, corresponding-source references and copyright notices remain included. DonutAntiCheat's existing MIT notices remain included. Packet transport and other bundled libraries retain their respective licences. See the inherited THIRD_PARTY_NOTICES.md, LICENSE-AegisAC-GPL-3.0.txt, LICENSE-DonutAntiCheat-MIT.txt and META-INF notices inside the JAR.

R16 exact build/source inputs:
https://github.com/projectsadameyd/AegisAC/releases/tag/v4.2.0-r16-vpnguard1

R17 additional source and build instructions are in the repository's r17 directory. The build tool verifies every inherited engine class against the pinned R16 baseline. No command registrar or physics/check thresholds are rewritten by this R17 overlay.
