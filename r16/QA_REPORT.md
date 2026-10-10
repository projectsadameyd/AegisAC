# Validation

## Preserved runtime

The saved R16 1.21.11 runtime previously passed 1,307 assertions and 170 VPN/HTTP assertions with legacy Gson 2.2.4. An isolated Paper 1.21.11 build 132 server started and stopped cleanly; Aegis status/reload/VPN administration worked and ten old engine command aliases were unavailable.

The new public primary preserves all of that JAR's class bytes. It only updates release metadata and required notices. These historical results are not new live gameplay tests; some original raw logs and newer test sources were lost.

## Restored source

The R16 builder compiles all 21 ports, checks archive integrity and Java class versions, verifies 5,930 unchanged native classes, and runs the 1,115 original action/history/timer assertions plus the VPN recovery suite. Current rerun: 1,252 source assertions (1,115 original plus 137 VPN recovery assertions), with all 137 VPN assertions also passing against the preserved primary. Current machine-readable results are supplied in `validation/`. The VPN recovery suite is run on both recovered-source output and the preserved primary runtime.

## Limits

- Paper 1.21.11 has isolated startup validation. Other variants are compatibility previews.
- No real Minecraft client gameplay was performed in this release workflow.
- No full server-version matrix or every plugin/proxy combination was tested.
- VPN reputation can miss a VPN or classify a legitimate address incorrectly. Allowlisting and the default allow-on-provider-error policy are available.
- Source rebuilds recompile recovered classes; binary identity applies to the public primary, not compiler output.

Use the matching runtime Java version, retain data backups and validate on the actual server before player rollout.
