# Dust II spawn metadata

Reference consulted on 2026-11-13:
[setpos.gg's Dust II CS2 spawn coordinates](https://setpos.gg/dust2/spawns).
The reference lists fifteen T positions in three groups and five CT positions
in an arc. The same page links the author's Steam Workshop practice map.

This project imports a Minecraft recreation of the older CS:GO map, not Valve's
CS2 geometry. Source-engine world coordinates cannot be used as Minecraft block
coordinates. The runtime metadata recreates the pool sizes and group layout on
the existing T Start street and covered CT Spawn underpass below Short, with spacing fitted to
the imported walkable surfaces. It is an adaptation, not a centimeter-accurate
conversion of Valve's spawn entities. In particular, the T groups are compressed
to avoid the Minecraft recreation's parked car and masonry.

- T: fifteen positions, feet Y=82, X=-1.5..1.5, Z=21.5..38.5 (shifted three blocks toward +Z on the Minecraft plan).
- CT: five positions, feet Y=75.5, X=102.5..103.5, Z=60.5..68.5, deeper inside the roofed recess east of the through-passage, on its original bottom slabs. Y=82 at these X/Z coordinates is Short, not CT Spawn.
- T faces into the map; CT faces west out of the recess toward the passage.
- Pools are independently shuffled once per round, including after halftime.
  Teams use the first slots without replacement, with at most five players each.
- Buy areas are fixed around these spawn regions, independent of the shuffled
  player order. The former road/mid start locations are outside the new areas.
- No source-world blocks are moved, removed or generated for this change.

The imported-map checks read the bundled blueprint and cover headroom, original
floor support, separation and buy-zone membership for every candidate position.

## Respawn surface correction

The source map contains hollow volumes below some thin street decks. T starts
at Y=82 rather than inside the foundation at Y=78. CT is different: the through-passage has feet Y=75, while the recessed spawn
has feet Y=75.5 on original bottom slabs at Y=75, with its roof at Y=80. Raising
CT to Y=82 incorrectly placed the team on Short. Its five slots and buy zone now
use the recessed lower spawn; the buy zone excludes Short above it.

Deathmatch derives points from reachable surfaces, including slabs and stairs,
but only selects exposed surfaces at Y>=79 (the B platform is Y=79). Lower
foundation floors and cavities below overhead decks are excluded from automatic
sampling. The explicitly verified classic slots, including the roofed CT
underpass, remain available as curated fallback candidates. Descending graph
edges must clear every intermediate body block, so a thin street deck cannot
be skipped during a three-block descent. Both modes recheck live body clearance
and floor support and skip obstructed candidates before choosing a spawn.

Regression assertions distinguish the T street from the covered CT underpass,
check the roof and exclude Short from the CT buy zone. Native spawn selection is
exercised by `native-checks/cs/CsSpawnSmoke.java`. The latest recessed CT correction is validated below.

## Recessed CT start adjustment

All five CT starts now sit behind the main through-passage, with their buy zone
restricted to X=100..105, Y=75..79, Z=58..71. Classic and Wingman share the new
points; Deathmatch also uses them as its curated lower fallback candidates.
Half-slab starts check the upper feet space, full headroom and native support
instead of rejecting the slab as a full block. No map blocks are changed.
Validated on 2026-11-15: `checkCounterStrike` passed 6,679 assertions, including
connected routes from every spawn in Wingman. A spawn-only fixture on Paper
26.3 #249 passed 3,437 native assertions: all Classic, Wingman and Deathmatch
starts are inside the map, supported by native floors and clear for a full
0.6×1.8 player collision box. The fixture also checks obstructed-slot fallback
and physical Wingman seals. No connected-client session was used.
