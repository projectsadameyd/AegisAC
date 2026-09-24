# AegisAC 0.1.0-alpha

A conservative, alert-first anti-cheat prototype for Paper 1.21.11 / Java 21.

## Important accuracy note
No anti-cheat can honestly guarantee 100% detection with zero false positives. AegisAC therefore never bans or kicks in this alpha. Checks use buffers, lag exemptions and conservative thresholds, and only alert staff.

## Included checks
- Speed A: conservative horizontal movement envelope
- Fly A: sustained hover / prolonged upward-motion heuristic
- Timer A: sustained abnormal movement-event rate
- Reach A: server-side hitbox distance, with a 1.21.11 spear exemption
- AutoClicker A: very-high CPS heuristic
- FastPlace A: placement rate / impossible-ish placement distance heuristic
- Velocity A: very low response to meaningful horizontal velocity

## Commands
- `/aegis info`
- `/aegis status`
- `/aegis alerts`
- `/aegis reload`

## Permissions
- `aegis.admin` (default op)
- `aegis.alerts` (default op)
- `aegis.bypass` (default false)

## Credit broadcast
By default, every 30 minutes AegisAC broadcasts exactly:

`Made by @_adam814`

## Build
Requires Java 21.

```bash
./gradlew build
```

Paper dependency: `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`.

## Design research
The design direction was informed by GrimAC's public architecture: shared per-player state, prediction-oriented checks, lag/uncertainty handling, and conservative evidence accumulation. This project contains original code and is not a copy of GrimAC.

For a true packet-prediction anti-cheat comparable to GrimAC, the next major milestone is a packet layer (e.g. PacketEvents), per-player compensated entity/world state, and a deterministic 1.21.11 movement simulator. This alpha intentionally does not pretend those systems already exist.
