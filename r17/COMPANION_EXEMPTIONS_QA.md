# R17 Preview 2 server Hotfix 3: companion username exemptions

Target: Paper 1.21.11, Java 21+. The existing Fabric 1.21.11 companion Hotfix 2 remains compatible and unchanged.

## Behavior

- `/aegis client exempt add <username>` saves an exemption immediately; `/aegis client exempt remove <username>` removes it; `/aegis client exempt list` displays the active list.
- These commands require `aegisac.admin` and also work from the server console. Suggestions include `client exempt` and `add`, `remove`, `list`.
- Names match exactly, ignoring case. Only standard Minecraft usernames of 3-16 letters, digits and underscores are accepted. Wildcards are rejected. The default list is empty.
- Settings persist in `plugins/AegisAC/client-guard.yml` under `exempt-usernames`. An existing configuration without that key has no exemptions. Manual edits take effect with `/aegis client reload`.
- An exempt connection returns from companion admission before acquiring a verification slot, opening a dialog, sending a challenge or scanning code. Changes apply on the next connection, including after a server restart.
- AuthMe authentication, VPN screening, native anticheat checks, remote interaction protection and sanction history keep their existing behavior. This exemption grants no anticheat permission or authentication privilege.
- On offline-mode servers the name is supplied before AuthMe authentication. AuthMe must still verify ownership of that account. Do not treat this as authentication.
- Configuration writes replace the YAML through a temporary file, preserving unrelated settings. Failed validation does not save an invalid exemption. Failed startup configuration and server shutdown cannot be bypassed by the list.

## Validation on 2026-10-16

`java r17/tools/BuildR17.java` passed all nine regression suites: 1,812 assertions in total. The build compares all 5,931 retained engine classes with the pinned R16 artifact byte for byte and reports no changes.

The new 34-assertion suite exercises the actual admission handler with Paper connection interfaces. It checks exact/case-insensitive matching, missing names, an empty list, startup failure, shutdown, no companion/dialog/state access for an exempt join, and non-admin command rejection. It also checks real YAML save/reload, case-insensitive removal, duplicate adds/removes, invalid usernames, cleanup of temporary files and preservation of a custom download URL and existing timeouts.

The other suites retain movement/action, bounded-state, timer safety, VPN, remote interaction, anti-Xray, timing-sanction and companion protocol/scanner coverage. Official Meteor and Sodium artifacts remain in the scanner regression.

The production companion JAR was not rebuilt or modified. Its previous payload/lifecycle validation remains documented in CLIENT_LIFECYCLE_HOTFIX_QA.md.

A new full Paper/AuthMe join test was attempted but not completed: the existing isolated local server held its world/log files, preventing the new controlled instance from starting. The new loopback harness is included as `r17/tests/companion-exemption-join-qa.cjs`; its presence is not a claim that those end-to-end cases passed. Production deployment has not been observed.

## Installation

Stop Paper, replace the previous AegisAC server JAR with `AegisAC-R17-Companion-preview2-hotfix3-1.21.11.jar`, and start Paper. Keep only one AegisAC server JAR. Keep the existing companion Hotfix 2 for players who are not exempt. Add usernames with the commands above, then have those players reconnect.
