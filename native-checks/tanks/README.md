# Tank native presentation regression fixture

This source-only fixture runs production tank classes on a real disposable
AdvancedSlimePaper server. It checks native world blocks, display transformations,
interpolation metadata, a real passenger attachment, barrel origins, wrecks and
entity removal. A narrow simulated player transport drives the production cannon
method, exercising typed `FLASH`/`DUST` particles, native sounds, shell creation and
muzzle-adjacent obstruction. It does not connect a Minecraft client or verify
appearance, camera comfort, audio mixing, latency or multiplayer input delivery.

From the repository root, with the main plugin JAR already built:

```text
python native-checks/tanks/run_native.py --prepare-only
python native-checks/tanks/run_native.py
```

An alternate main artifact can be passed with `--jar <path>`. The runner checks
that the artifact contains the tank implementation before starting the server.
It compiles the fixture with the cached Java 25 compiler and runtime libraries;
it does not run Gradle, download dependencies, or alter the main JAR.

The runner creates `.tools/tanks-native/mc26.3`, copying only the cached bootstrap
JAR and its `libraries`, `cache` and `versions`. It generates fresh configuration
and worlds, binds to an available `127.0.0.1` port, uses an empty enforced whitelist,
and disables RCON, query, status and management. ASP map storage and the plugin
lobby are disabled. It never copies the existing server's worlds, plugins or
configuration. Run on the task's disposable runtime only.

Success writes `plugins/TanksNativeSmoke/result.txt`, logs
`TANKS_NATIVE_SMOKE_PASS`, and stops the disposable server. Failure records the
assertion count and exception and also stops the server. Preserve `console.log`,
`tested-main-sha256.txt` and `result.txt` beneath the disposable runtime when
reporting evidence. Server logs, worlds, caches, classes and JARs stay outside Git.

The cached native runtime currently available to this runner is ASP/Paper 26.3.
Native 26.2 execution requires a separately cached 26.2 bootstrap, dependency and
version set; a successful compile against its API alone is not a native run.

For an actual client test, prepare a separate server without the smoke plugin:

```text
python native-checks/tanks/prepare_playtest.py
python native-checks/tanks/prepare_playtest.py --name ExactClientName --jar <ready-plugin.jar> --run
```

The first command only prepares configuration. The explicit `--run` command starts
the selected artifact, prints its loopback address, and keeps its console attached;
type `stop` into that console to shut it down. Only explicitly supplied client names
receive throwaway offline UUID whitelist entries and operator access. Join the
printed address and use `/tanks test` for solo practice. This runtime has separate
worlds, plugin data and logs under `.tools/tanks-playtest`.

The optional development helper is built with
`python native-checks/tanks/compile_client_qa.py` and installed separately as
`TanksClientQa.jar` only in the private test server. Operator `range` accepts
`12`, `40`, `70` or `86`: it uses native footprint/corridor/ray checks and reports
the exact hull-center distance used by production spotting, without substituting
the passenger camera distance. `drive <red|blue> <direction> <1..100>` supports
`forward`, `reverse`, `left`, `right`, `forward-left` and `forward-right`; it feeds
the production controller's input handler and releases to neutral after the
bounded run. This is a server-input fixture, not physical keyboard automation.
`probe <1..100>` emits one lifecycle-checked delayed `state` snapshot. State/probe
and range reports go to both the requesting operator and the private server log,
so QA evidence does not depend on finding client chat logs. These commands do not
override HP, class, reload, visibility decisions or capture progress.
`spawn <red|blue>` restores that tank's valid team spawn without healing or
reloading. `view <4..10>` changes only the active disposable match world's view
and send distances for a controlled tracking experiment; it is not a production
configuration command. All helper commands are development-only.

## Recorded native evidence

On 2026-11-17, the immutable Round11 artifact
`.tools/tanks-artifacts/round11-mc26.3.jar`, SHA-256
`87b1d69ad8ce048bbf0f82fa9822029b2efddc2a0bf25f9b39561c34b3fe4ceb`,
passed **3,275 native baseline assertions** and **71 load/lifecycle checks**.
The added seat regression deletes each chassis' anchor three times, mounts an
actual native ArmorStand on every recovered Interaction, and retains the
passenger through motion. The replacement keeps its hidden, invulnerable,
nonliving configuration and swaps one owned entity without rebuilding the hull.
Actual networked Player logout/rejoin and camera appearance remain client checks.

The load fixture checked all **1,776 native foliage blocks** for persistence,
then retained exact entity counts under normal random ticks. The 12-model replay
used 20 warmup and 200 measured ticks, 24 shots (peak 12 flights), 2,400 aim
queries, 480 predictions and 2,880 spotting queries.

| Round11 local measurement, ms | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: |
| Synchronous 12-model replay CPU | 3.359 | 9.384 | 59.111 |
| Scheduled tick interval | 49.927 | 51.485 | 71.407 |

Entities stayed exactly **746** during replay and returned to the **two-label
arena baseline** after cleanup. Production plugin chunk tickets returned from
72 to zero. All 12 scope attributes returned to their measured pre-scope value
of 0.12, the scope's own modifier was absent, and the unrelated fixture modifier
remained. Combat, friendly interception, hidden-contact prediction/radar policy,
tracks/capture reset and duplicate-wreck attribution passed. Both private servers
self-stopped with exit status zero; their ports 56866 and 52963 had no listeners
afterward. Timings include a shared-host outlier and exclude real viewer tracking,
packet serialization and client rendering; they do not establish 12-client FPS.

The preceding `4fdfa4df…026fd` and `ab5ce1fd…bcec` load attempts stopped before
measurement because strict entity counts detected unexpected item drops near
arena trees. Their failure evidence remains archived under
`.tools/tanks-load/mc26.3/evidence/20261117-184253` and `20261117-190008`.
This led to the production persistent-leaf fix; counts were not relaxed and
random ticks were not disabled. Use immutable artifact paths for paired runs:
the mutable release path changed between the earlier baseline and load attempts.

On 2026-11-17, the Round7 balance artifact with SHA-256
`ab60faecb1dfaa72f2b724960317906297a22085df5904250cb5c4b7768dde0a`
passed **1,561 native baseline assertions** and **70 dedicated load/lifecycle
checks**. The baseline reads actual native Avatar attachment/dimension fields:
vehicle attachment Y is 0.6, standing eye height is 1.62, and the corrected seat
places the calculated standing eye 0.28 blocks above each turret roof. Native
Interaction seat Y values were 66.0376, 66.3056 and 66.5 for light/medium/heavy.
This verifies native attachment math and test-rider transport; actual player
camera appearance remains a separate client check.

| Round7 local measurement, ms | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: |
| Synchronous 12-model replay CPU | 2.743 | 7.352 | 87.845 |
| Scheduled tick interval | 49.976 | 51.813 | 182.029 |

The replay kept 749 entities stable and returned exactly to its measured baseline
of five arena/ambient entities. All 72 production tickets were released. It
executed 24 real shots (peak 12 flights), 2,400 aim queries, 480 predictions and
2,880 spotting queries. Adversarial combat/policy checks and all 12 native scope
restorations passed, including own-key removal and unrelated-key preservation.
The coordinator's two-client playtest shared the host during this measurement;
the maximum outliers above are retained and this is not a general TPS/FPS claim.
Both isolated test servers self-stopped with exit status zero. Current logs,
results and installed-artifact hashes are in `.tools/tanks-native/mc26.3` and
`.tools/tanks-load/mc26.3`; the client playtest server was not accessed.

The earlier C41 camera artifact has SHA-256
`c41f48c7f520ecfbd2faa6bf00d77d6175d407d4eee059fe2a1de952082517e8`.
It corrects the native Player vehicle attachment subtraction of 0.6 blocks:
standing Player eye height above a zero-height Interaction seat is 1.02 blocks.
The native geometry assertion now uses the corrected seat offset
`turretPivotY + 0.68 * scale - 0.74`. C41 was not rerun on a native server or
retested in a client; the successful runtime results below identify BF9 instead.
`TanksClientQa` is a development-only manual helper. Its existing range-placement
positions were not fully verified for unobstructed sightlines and must not be
treated as completed range or aiming evidence.

On 2026-11-16, the Interaction/audio artifact with SHA-256
`bf9a85842b75cc3cba2da7e8b61fe467bd96471574df3722529e555ba20cf657`
passed **1,556 native assertions**. It includes all 15 native audio cue registry
calls and actual Interaction passenger attachment through translation/steering.
Observed seat and marker-ArmorStand passenger Y values were 65.533 for light,
65.598 for medium and 65.65 for heavy, with zero attachment offset. These are
recorded native test-rider positions, not a claim about a Minecraft player's eye
height or camera comfort.

The final 2026-11-16 run passed **1,502 native assertions** against unchanged main
JAR SHA-256
`1007779799fdfda44843cff68aac49fed5dd4da419414cd14593ec1908fa2098`.
It additionally invoked the production `retainArenaChunks` method through real
session services, verified all 72 arena chunks and repeat-call idempotence, and
released its tickets to zero. Native narrow-lane and ceiling fixtures confirmed
that the smaller light class fits where the heavy class collides. The server
exited cleanly with status zero. Current result, console and tested hash are under
`.tools/tanks-native/mc26.3`; previous evidence is archived in its `evidence` folder.

## Dedicated load and adversarial lifecycle fixture

```text
python native-checks/tanks/run_native.py --load --jar <ready-plugin.jar>
```

This separate fixture uses `.tools/tanks-load/mc26.3`, never the client playtest
runtime. It builds the actual arena, creates 12 native tank renderers and 12 native
movement attributes, warms up 20 scheduled ticks, and measures 200 ticks of moving
hulls, turret/elevation changes, health-label changes, production cannon volleys
and swept shell flight. It calls production crosshair aim every tank/tick and
prediction plus cross-team spotting at the session's five-tick cadence. Two
12-tank upward volleys bound the active projectile set while avoiding friendly
spawn-row damage during timing.

After timing, deterministic production combat scenarios verify friendly shell
interception, enemy penetration, track/capture reset, wreck attribution and
repeated-wreck hits. The production prediction and radar methods are checked
before/after a controlled spotting update. These establish server-side contact
policy, not actual client packet visibility. Scope cleanup must preserve unrelated
native modifiers, vehicle/scope-body entities must return to the measured arena
baseline, and production plugin chunk tickets must return to zero.

Results include synchronous replay CPU p50/p95/max, scheduled tick-interval
percentiles, exact entity counts and bounded query/projectile counts. The CPU
measurement includes fixture calls and native operations, but excludes real
viewer tracking, packet serialization, client rendering, HUD transport and network
latency. A playerless local run cannot establish multiplayer client FPS or a
general production TPS guarantee. The fixture shuts its isolated server down
after either pass or failure, preserving `result.txt`, `console.log` and the tested
main JAR hash.

### Load evidence

The same `bf9a8584…cf657` artifact passed **70 behavioral load/lifecycle checks**
on 2026-11-16. The measured replay used 12 actual tank renderers, 12 actual native
movement-attribute bodies, 20 warmup ticks and 200 measured ticks. It created 24
production cannon shots in two volleys, with at most 12 simultaneous flights,
and executed 2,400 production aim queries, 480 predictions and 2,880 cross-team
spotting checks.

| Local measurement, ms | p50 | p95 | Maximum |
| --- | ---: | ---: | ---: |
| Synchronous replay CPU | 2.408 | 5.926 | 33.062 |
| Scheduled tick interval | 46.942 | 62.428 | 64.302 |

Native entity count remained exactly 746 throughout the replay and returned to
the pre-spawn baseline of two arena labels after renderer/attribute-body removal.
Production plugin chunk tickets returned from 72 to zero. All 12 scope owners
restored their actual pre-scope native calculations; both 0.12 and 0.18 baselines
were observed because native zombie spawn modifiers vary. The scope's own key
was absent after cleanup and the unrelated fixture modifier remained present.

Production friendly interception, enemy penetration, track/capture reset,
destruction/duplicate-wreck attribution and before/after-spotting prediction/radar
policy all passed. The isolated server exited with status zero. Evidence is under
`.tools/tanks-load/mc26.3`; failed setup runs are retained in its `evidence` folder.
The earlier exact-entity-count and fixed-attribute-value assumptions were fixture
errors corrected without a production code change. This is renderer/combat
resource lifecycle evidence; it does not execute a full networked
`SessionServices.onStart/onEnd` flow or establish 12-client performance.

On 2026-11-16, AdvancedSlimePaper 26.3 (`2044679`, API build235) passed
**1,422 native assertions** against unchanged main JAR SHA-256
`a31e2ec3578675f93f6acdcba81680a4dbddbb338972743db9c4403c5549b830`.
The fixture created its own playerless void world, held tested chunks with plugin
tickets, and waited one server tick after final arena entity creation before
querying labels. The earlier immediate label-query failure was a fixture setup
issue; no production change was made to resolve it. This pass includes all three
chassis, actual native passenger retention/ejection and production cannon methods.
The disposable native server shut down after the pass. Native 26.2 execution was
not performed because its server runtime was not cached locally.
