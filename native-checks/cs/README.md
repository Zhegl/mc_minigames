# Counter-Strike native regression check

`CsGrenadeSmoke` invokes the production flash detonation against Paper's actual
particle factory. The previous untyped `FLASH` call fails here because that
particle requires `Color` on both supported server versions. Bukkit sounds also
require native registries, so this fixture deliberately runs outside the
server-free `CsCheck`.

Use a fresh disposable Paper/AdvancedSlimePaper server directory and Java 25; never run
this fixture on a shared game server. Bind to `127.0.0.1` and an unused port,
disable RCON/query/status/management, use offline mode with an enforced whitelist,
set `pause-when-empty-seconds=-1`, and disable the plugin's lobby and ASP map
storage in that disposable configuration. Compile this source against the
server's Paper API and dependency JARs, including `CsGrenadeSmoke.java`,
`CsScopeSmoke.java`, `CsVisualSmoke.java` and `CsSpawnSmoke.java`; package all classes with `plugin.yml`, and
put it beside the built PotatoMinigames JAR in `plugins/`.

Start the server normally. The fixture writes `plugins/CsGrenadeSmoke/result.txt`
and logs `CS_GRENADE_SMOKE_PASS` after a successful detonation, or logs
`CS_GRENADE_SMOKE_FAIL` with the exception. It shuts down the disposable server
automatically. `CsScopeSmoke` additionally checks the production scope helper
against real native movement attributes: FOV ratios, compensated movement,
non-stacking modifiers and stop/clear restoration. It uses simulated player
transport and cannot replace an actual client visual test. Success also logs
`CS_SCOPE_SMOKE_PASS`. To check the shared native metadata adapter too, compile
`../finals/FinalsTeammateGlowSmoke.java` into the same fixture JAR and launch with
`-Dcs.smoke.includeFinalsGlow=true`; success logs `CS_SHARED_GLOW_SMOKE_PASS`.
`CsVisualSmoke` checks private scoreboard name visibility for both teams and
spectators, real cosmetic armor components and
captures fire block packets to verify real terrain stays unchanged, shared fire
cells survive overlap, late spectators see fire, and cleanup restores the current
background. Success also logs `CS_VISUAL_SMOKE_PASS`. Repeat on 26.2 and 26.3 when preparing the PR; keep console logs and
the tested plugin JAR's SHA-256 outside Git.

`CsVisualSmoke` additionally constructs the real client-only tracer spawn,
metadata and removal packets using unspawned native BlockDisplays. It checks
fractional coordinates at long-ray midpoints, zero extra entity rotation, native
metadata, no world entities, three-tick expiry, the 128-streak cap and idempotent
cleanup. Transport is captured, so this does not replace a connected-client
visual test. These tracer checks passed on both supported Paper versions during
2026-11-14 PR validation. The fixture also verifies every weapon item: only
semi-automatic pistols have a silent long-use component with no animation,
consume particles, vibration or movement penalty. Server-free `CsCheck` covers the display
transform's endpoints and thickness for forward/backward, diagonal and vertical
shots at short and full range.

`checkCounterStrike` additionally covers 150-block rifle/sniper hits, cover and
miss trace endpoints, the CS2 purchase prices, armor refill, the loss-bonus ladder,
the halftime price swap, roster-scaled payouts for all 1–5 player team sizes,
category coverage and price ordering, snowball-like grenade air motion and
localized roster/chat text. The existing native
Finals teammate-glow fixture exercises the shared private metadata adapter.

Client playtest before merging:

- Fire at nearby cover, players and distant points across Dust II with rifles,
  pistols and snipers. A thin yellow solid streak must end at the actual hit or
  continue through penetrated cover, remain visible without particle settings,
  and disappear quickly. Check upward/downward shots, zoom, the midpoint outside
  the camera frustum, shooter/opponent/spectator views, and distances over 32 and
  100 blocks. USP-S/M4A1-S stay without streaks; shotguns draw only their first
  pellet. Verify rapid automatic fire, the last kill of a round, halftime,
  disconnect/world changes and match cleanup. No server world entities may be
  added and rays must not glow through walls.
- Open CS in the lobby and select Wingman (`/play cs wingman`). Try 2v2 and
  3v3/5v5: roster limits, MR12 rules, money and equipment must match Classic.
  Walk T→Long→A, T→upper Mid→Short→A and CT→A. The B exit next to T spawn,
  tunnels, Mid doors and CT's lower Mid/B exit must be blocked; upper Short above
  the CT gate must stay open. Try firing and throwing grenades at gates, then
  advance rounds and swap sides. No start may appear outside play or in a gate.
  Classic and Deathmatch must still use the unrestricted map.
- Throw Molotovs onto floors, stairs and near walls. Animated ground fire must
  match supported surfaces in the existing damage zone, without spreading or
  changing the real map. Test overlapping fire, smoke extinguishing one/both
  zones, expiry, spectator arrival, chunk reload, leaving and round/end cleanup.
- Check red/blue full leather uniforms, purchased iron chestplates/helmets,
  depleted vests and halftime. Inspect native attributes: none of the visible
  armor may add protection, toughness or knockback resistance. Existing logical
  damage and armor consumption must stay identical.

- With AWP, SSG 08, AUG and SG 553, left-click in air, at blocks and at entities.
  One click must enable zoom, a later click must disable it. Hold right-click to
  fire while scoped; no native spyglass overlay/use must start. Check movement,
  hit tagging, reload, switching/dropping, death, menus, disconnect, halftime and
  match-end cleanup. Enable client FOV Effects; zero strength intentionally
  disables vanilla dynamic FOV. Sniper FOV is stronger than AUG/SG zoom.

- Check the one-screen buy menu on both sides: all legal weapons visible at once,
  distinct colored category columns/pairs, ascending prices and bottom-row gear.
  Weapon/gear clicks must buy only once; category headers must not debit money.
- Throw each grenade level, upward and downward, from standing/moving/jumping
  positions and next to thin cover. Compare the arc with a vanilla snowball;
  utility must still bounce, detonate once and clean up at round end.
  Check both left-click long throws and right-click short, splash-potion-like
  throws; one click must consume exactly one grenade.
- Check 2v2 (full payouts per person), 2v3 (150% vs 100%, with $1,200 vs $800 pistol
  cash), and 5v5 (baseline). Deaths or disconnects during a round must not boost
  that round's payouts; the following round must update the roster snapshot.

- With two allies and an enemy, check that only allies have outlines, including
  through walls, and that smoke/flash suppress them. Check death, reconnect and
  round/end cleanup; other modes must retain their original glow behavior.
- Only allied overhead names should be visible, including after halftime and
  reconnect. Enemy names remain hidden; external spectators receive no allied
  names. Smoke/flash entity hiding must also hide the corresponding nameplate.
- Walk into/out of smoke and through overlapping smoke and flashes. The cloud
  should obscure the environment; bullets must still cross it, and visual
  intensity should be checked on an actual client.
- Open `/cs score` alive and dead. Check status changes, K/D/A, MVP and own-team
  cash only. The sidebar must show correct T/CT alive counts after halftime.
- Send `!private`, `!<red>literal</red>`, an empty `!`, and ordinary global chat.
  Test with opponents, a spectator, another active game, a disconnected/rejoined
  teammate and after halftime. Private messages must reach only current allies;
  ordinary messages keep the server's existing chat behavior.

- Check the continuous ammo/reload action bar, including its changing tenths of
  a second and the final completion time for multi-shell shotgun reloads.
  Verify weapon/headshot kill messages against the lethal hit, including a
  nonlethal headshot followed by a body-shot kill.
- Open CS from the game menu and select both Classic and Deathmatch. In
  Deathmatch, check the five-minute timer, three-second death screen/respawn,
  spawn protection ending on attack, all-player damage, individual results and
  free weapon selection. A combat loadout change must apply on the next respawn;
  it must not refill the current magazine. Check reconnect and match cleanup.

## Validation evidence

The native runs and hashes below identify the CS-only artifact at commit
`7e2e601`. The subsequent Miners finale-zone fix is covered by full builds on
both APIs, including `checkPartyMiners` and i18n validation. It does not change
CS code; its translucent zone walls still require an actual client playtest.
The Parkour Sprint expansion is also covered by both full builds, including
`checkParkourRace`; its native movement tasks still need a client playtest.

On 2026-11-12, full Gradle builds passed on both 26.2 and 26.3; the final CS
suite passed **2,003 checks** on each API, and both i18n gates passed. Native
Paper 26.2 #135 and 26.3 #249 runs passed flash detonation, scope attributes and
restoration, cosmetic armor, private ally/enemy/spectator nametag settings,
fire overlays/overlap/cleanup, plus **43 shared
teammate-glow packet assertions** per version. Both isolated servers shut down
cleanly. Player transport is simulated; an actual client visual playtest remains
outstanding, especially FOV Effects, smoke density and floor-fire presentation.

Production JAR SHA-256 (identical class/resource payload for both classifiers):

```text
605c3848558b4bb65067463b418000bec90d0a73445ae05c179a8510373fb81d
```

For the plain Paper runs only, the cached ASP API 4.2.0-SNAPSHOT classes were
added to disposable test copies because the plugin already requires those
classes at load time even with ASP storage disabled. No ASP classes are bundled
in the production artifacts or source change. On AdvancedSlimePaper, use the
unmodified production JAR. Tested copy SHA-256 values:

```text
26.2 fb5c614f23ddb24ed1a4914d9cc381687c9d58c8091d85e8e50f4915b579f6ad
26.3 fb5c614f23ddb24ed1a4914d9cc381687c9d58c8091d85e8e50f4915b579f6ad
```

Build logs, native console logs, runtime worlds and generated binaries are kept
outside Git in the task's `work/` directories.

### Deathmatch follow-up after merging upstream

On 2026-11-13, the combined tree at `132f1b7` (feature commit `e47b8da` plus
upstream `8b64d1c`) passed full Gradle builds for 26.2 and 26.3. Each build passed
**5,872 CS assertions**, both localization catalogs and all upstream game checks.
The updated native fixture also passed on Paper 26.2 #135 and 26.3 #249: flash
detonation, stronger scope FOV ratios and movement restoration, cosmetic armor,
private name visibility, fire overlays/cleanup and **43 shared glow packet
assertions** per version. Both disposable servers shut down cleanly.

Production JAR SHA-256 (both classifiers):

```text
90a258d3e0560b4447c6eea0db61e59ebbaeee151060d2d2bbe30b32de257bbf
```

Disposable native-copy SHA-256 (both versions, with only the same ASP API
class-loading workaround described above):

```text
6edb698b513b668b8a2a7db83b03ca4369a1bce65519370b9eabdc265aa8b55a
```

Deathmatch rules, loadouts, ranking, timer, protections, menu/queue wiring and
imported-map spawn geometry are covered by server-free checks. Native fixtures
still use simulated player transport; they do not exercise a complete connected
client match. The client playtest checklist above remains outstanding.

## Imported-map spawn regression

`CsSpawnSmoke` builds the full imported Dust II in the disposable world and
checks every classic pool slot across shuffled rounds, every distributed
Deathmatch candidate, maximum-distance selection across twenty respawns, and
fallback selection after obstructing a candidate in each mode. It uses Paper's
native block passability and floor support, including slabs and stairs. Success
logs `CS_SPAWN_SMOKE_PASS` with the assertion count. Use a fresh disposable world
for this run so terrain left by another fixture cannot overlap the map. It
validates spawn selection and geometry; no connected-client match is simulated.

On 2026-11-14, Paper 26.2 #135 and 26.3 #249 each passed **2,838 native spawn
assertions**, followed by `CS_SCOPE_SMOKE_PASS`, `CS_VISUAL_SMOKE_PASS` and
`CS_GRENADE_SMOKE_PASS`. Both disposable servers shut down cleanly. Full builds
for both classifiers passed all 57 tasks, including i18n and 6,122 CS assertions.
The production JAR SHA-256 was identical for both classifiers:

```text
27587921163fe5c2f68410b9ff6b5fbea0b6d9287dd750a3bc2e46b1302a5100
```

The disposable copies with the ASP API workaround had SHA-256:

```text
a396b79923cf2280c4cfd6d20b3a251d510fe1cd0668075519cc7e797bc19adf
```

Logs and runtime worlds are kept outside Git. Production artifacts contain no
fixture or ASP workaround classes. Connected-client spawn timing still requires
an actual match playtest.

## Current CS update: validation on 2026-11-14

Full Gradle `build` runs passed for both `-Pmc=26.2` and `-Pmc=26.3`: all 57 tasks,
including i18n and **6,669 CS assertions** per classifier. New model cases cover
Wingman menu/queue/team limits and paths, headshot-only kill-feed suffixes,
full per-person equal-team payouts, weapon-specific movement speeds, native-scope
compensation bounds, firing/movement accuracy, pistol trigger release and display
endpoints/thickness for horizontal, diagonal, vertical and reversed shots.

Disposable native Paper **26.2 #135** and **26.3 #249** runs passed:

- `CS_SPAWN_SMOKE_PASS`: **3,029 native assertions** on each version. CT starts
  are in the covered Y=75 underpass; all 15 T/5 CT candidates are valid. Wingman
  gates, upper Short clearance, A-only planting and gate persistence are checked.
- `CS_SCOPE_SMOKE_PASS`: actual movement attributes, weapon-specific compensated
  speeds/FOV, walk-speed bounds, non-stacking modifiers and complete restoration.
  Spawn randomization is disabled for the zombie stand-in: a baby zombie's
  movement bonus must not contaminate this player-attribute fixture.
- `CS_VISUAL_SMOKE_PASS`: armor, private nametags, fire overlays, all gun item
  components and real client-only display packets. Pistol consumable state is
  silent/invisible with speed multiplier 1; automatic guns have no such state.
  Tracers preserve fractional midpoint coordinates, add no world entities, expire
  after three ticks, respect the 128-streak cap and clear idempotently.
- `CS_GRENADE_SMOKE_PASS`: native flash particle construction and clean shutdown.

Production JAR SHA-256 (identical class/resource payload for both classifiers):

```text
71e669ec5f5cc8688c8a0bf755e96b111a2b2121a0487881d07c12fb34b04eaf
```

Production artifacts contain no fixture/check classes or ASP API workaround.
Only disposable test copies include the ASP API class workaround described above.
Logs, compiled fixtures and runtime worlds are outside Git. The runs use captured
or simulated player transport, not a connected Minecraft client.

Before merging, use a real client to verify pistol press/release while looking
into air/at blocks/at players and while holding through reload; check unchanged
CZ75 automatic fire, silent native use and normal movement. Also playtest recoil
recovery, moving/crouched/scoped accuracy, FOV Effects, positional audio, tracer
appearance at range, post-round looting and Wingman's stacked CT/Short routes.
Fall damage must stay disabled in all three CS modes; ordinary weapon/grenade
and void-boundary damage must still work. Client controls, visual/audio feel and
latency-sensitive behavior are not proven by the native fixtures alone.

## Recessed CT spawn follow-up (2026-11-15)

A spawn-only entry point ran the updated `CsSpawnSmoke` against Paper 26.3 #249
and the current local production JAR: `CS_RECESS_SPAWN_PASS: 3437 native assertions`.
Every Classic, Wingman and Deathmatch candidate has map membership, native
floor support and a clear 0.6×1.8 player body (`ServerLevel.noCollision`). The
five recessed CT starts use the original bottom slabs at feet Y=75.5, facing
west, and remain inside Wingman's allowed area. Obstructed-slot fallback and
Wingman seals passed too. `checkCounterStrike` passed 6679 assertions.
Only spawn checks ran on the disposable server; other native suites were not
repeated. Log: `../cs-issue-32-native-26.3/ct-recess-spawn-console.log`.
