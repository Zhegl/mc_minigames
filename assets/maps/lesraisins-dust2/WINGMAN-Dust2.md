# Dust II: Wingman boundaries

Wingman (`wingman`, localized as «Напарники») reuses Classic's model, MR12 match
format, money, weapon pool, team selection and five-player team cap. It is **not
limited to two players per team**. Its only gameplay difference is map access and
the resulting restriction to bombsite A. Classic and Deathmatch keep the full map.

Coordinates below refer to the imported Minecraft blueprint, not Valve units.
X increases right and Z increases down on the repository's top-down map.

| Restriction | Boundary | Routes preserved |
| --- | --- | --- |
| T spawn toward B | Play starts at Z=19; the seal is at Z=18, just north of the closest T start at Z=21.5 | South exit, Long and upper Mid toward Short |
| Dark/lower tunnels | X=52..79, Z<38 is inaccessible; the Mid entrance closes at Z=37 | The T-side Mid street south of the entrance |
| Mid doors | X>=80, Z<47 is inaccessible at every height; seals follow the western and southern faces | Upper Mid can still turn toward Short |
| CT spawn toward CT-Mid/B | The lower region X>=80, Z<58 closes, with the CT-side seal at Z=57 | CT's A exit and Short overhead |
| Upper Short | Within Z=47..57, allow feet Y>=77.5 at X=80..87 and Y>=81.5 farther east | Imported approach at Y=78 and upper deck at Y=82; half-block tolerance accommodates slabs/stairs |

`CsWingman` generates a thin membrane on the forbidden side of the boundaries,
including vertical transitions so players cannot walk around the gates. Native
solid architecture stays in place; air and passable decorations on a boundary
(for example wall signs) become red stained glass through Y=85 and barriers above
that. The blueprint itself is unchanged. Gates are built within the normal
per-tick map construction budget and cached as geometry, never as world blocks.

Glass breaking, grenade glass damage and bullet penetration explicitly exclude
these boundaries. A movement guard prevents active players from crossing the
same layout during live play or the post-round collection period. Existing void
death handling remains in effect. A mode-specific site check rejects B.

All **15 T starts and 5 CT starts** are shared with Classic. T remains on the
street at Y=82; CT starts deeper in its covered recess at Y=75.5 on the original
slab floor, below Short. Before the
first round, every candidate is checked against the mode boundary and native
feet/head/floor collision; shuffling cannot select an out-of-zone fallback.
Spawns are selected by current faction after side swaps, so halftime uses the
same verified pools. No random full-map Deathmatch spawn sampling is used.

## Geometry audit and validation

Static inspection of the current 196,187-cell blueprint found 10,595 boundary
cells, including 8,181 originally empty cells. The conservative walking graph
contains 5,011 reachable positions from either team start: all twenty spawn
candidates, A, Long, the Short approach and upper Short are connected. B, the
tunnels and lower CT-Mid are excluded. Every spawn has original floor support,
two empty body cells and its entire horizontal player footprint inside play.

Added `CsWingmanCheck` to the CS check suite and a Wingman section in native
`CsSpawnSmoke`, covering queue wiring, team limits, paths, all possible spawn
candidates, native gates, upper Short clearance, gate persistence and A-only
planting. Full builds passed for both API classifiers on 2026-11-14, including
the 6,669-case CS suite and i18n validation. Native Paper 26.2 #135 and 26.3 #249
passed 3,029 spawn assertions per version, including all Wingman candidates,
native boundary collision, upper Short clearance, gate persistence and A-only
planting. Static/native geometry does not replace a connected-client walk-through,
particularly at the two stacked CT/Short levels: walk T→Long→A, T→Mid→Short→A
and CT→A before merging.
