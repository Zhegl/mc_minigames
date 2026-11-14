# Parkour Sprint native regression check

`ParkourSmoke` builds two actual lanes with the production sliced arena builder.
It checks real chest block entities, glass obstruction rules, isolated one-bucket
inventories and repeated attempt refills. Native inventory events exercise the
open/click permissions for falling owners, rivals, grounded players, stale views,
hotbar transfers, shift-click and clock protection. It also verifies dry tunnel
water rollback and the production rocket's Flight Duration 2 metadata.
The powder-snow bucket regression uses the real `BlockPlaceEvent` path, checking
placement on its own task, rejection on other tasks/lanes and protected terrain,
and restoration of air on reset.

Run only on a disposable Paper server with Java 25. Bind to localhost, disable
RCON/query/status/management, use offline mode with an enforced whitelist, set
`pause-when-empty-seconds=-1`, and disable lobby and ASP map storage in the plugin
configuration. Compile `ParkourSmoke.java` and `PartyPvpSmoke.java` against the supported Paper API and
its dependency JARs; package the resulting classes with this `plugin.yml` and put
the fixture beside the production PotatoMinigames JAR. Avoid other automatic
shutdown fixtures in that runtime. Repeat for 26.2 and 26.3.

The fixture modifies the disposable world's terrain. It logs
`PARKOUR_SMOKE_PASS` and writes `plugins/ParkourSmoke/result.txt` on success, or
logs `PARKOUR_SMOKE_FAIL` with the exception. It shuts down the server after
either result. Inspect the marker; a normal server exit alone does not prove
success. Keep runtime worlds, console logs and JARs outside Git.

Player transport and inventory views are substituted; terrain, native container
inventories, obstruction checks, inventory-event classes and item metadata are
real. This does not test mouse/keyboard timing or a complete connected-client
match. Before merging, play the chest catch, water placement/retrieval in the dry
tunnel, the broad ice bends and the one-rocket flight on an actual client. The
rocket's final difficulty margin and boat handling still require that playtest.

On plain Paper, the plugin currently requires cached ASP API classes at load
time even with ASP storage disabled. They may be added only to a disposable test
copy of the JAR, as described in `../cs/README.md`; never bundle that workaround
into the production artifact.

## Validation evidence

On 2026-11-13, full Gradle builds passed for 26.2 and 26.3 after merging upstream
`697331b` with the Parkour/One Shot feature commit `c84bb2c`. Both builds include
Parkour Sprint, One Shot, i18n and 5,872 CS checks. Native Paper 26.2 #135 and
26.3 #249 each passed **21 assertions**, including actual chest opening under
glass, refills, inventory permissions, water rollback and the rocket metadata.
Both disposable servers shut down cleanly. Connected-client timing, flight
difficulty and boat steering remain unverified by this fixture.

Production JAR SHA-256 (both classifiers):

```text
d5b63d1c31435c7f6a29d5c62c1c7548d5d6d0fa9872c3db8ae64aa1fbe3fc6f
```

Disposable Paper copy SHA-256 (both versions, with the ASP API workaround):

```text
5e715b50881848ef03f756e7c544c4e0246b7756914756d99ad421e793270082
```

## Powder-snow fix validation

On 2026-11-14, full builds passed for 26.2 and 26.3 against upstream `047bd59`,
including i18n, Parkour Sprint and 6,122 CS assertions. Native Paper 26.2 #135
and 26.3 #249 each passed **27 assertions**. The additional cases exercise the
actual powder-snow `BlockPlaceEvent` handler, task/lane/terrain restrictions,
and reset cleanup. Native terrain and events are real; player transport is
substituted as described above.

Production JAR SHA-256 (both classifiers):

```text
27587921163fe5c2f68410b9ff6b5fbea0b6d9287dd750a3bc2e46b1302a5100
```

Disposable Paper copy SHA-256 (both versions, ASP API workaround only):

```text
a396b79923cf2280c4cfd6d20b3a251d510fe1cd0668075519cc7e797bc19adf
```

## Take-off chest validation

On 2026-11-14, full 26.2 and 26.3 builds passed all 57 tasks against upstream
`bccbb7d`, including Parkour geometry/reach regressions and i18n. Native Paper
26.2 #135 and 26.3 #249 each passed **27 assertions** with the chest at take-off
height, including native opening under glass, isolated bucket refills and access
restrictions. Connected-client jump timing was not exercised.

Production JAR SHA-256 (both classifiers):

```text
563a19c2a9362c5e7f380035125ffebd2bb170e6d4bf3828b06c9d3e4173d516
```

Disposable Paper copy SHA-256 (both versions, ASP API workaround only):

```text
ee35b827c274dfac0d97bad6ab631297986a084217b6ef828f083dab993d1568
```

## Honey catch and ice-boat follow-up

The new honey patch is three rows long and six blocks high at the bottom of a
36-block drop, entirely after the departure checkpoint. A full solid cap removes
the safe top landing. Ice boats now use a five-block-wide packed-ice road with
80 blocks of forward travel, broad bends, straight entry/exit and two-block-high
guardrails. Walking remains legal; there is no artificial speed penalty.

Updated server-free cases cover both mirrored honey layouts, previous-task
isolation, capped tops, cap fall distance, reachable catch distance, connected
ice, hull/passenger clearance, gradual centerline changes and guarded edges.
These cases passed with i18n in full builds for both supported APIs during PR
preparation. The native fixture checks the built honey caps and all ice rows in
both lanes. Connected-client playtesting must still include a late
honey-side catch versus falling onto the cap, the preceding water-drop task,
boat steering at speed, mirrored lanes, a narrow ladder-traverse departure,
dismounting at the goal and clock reset. Historical validation above predates
these changes.

## Party kit selection, bomber and Parkour PR validation (2026-11-14)

Both complete Gradle builds (`-Pmc=26.3` and `-Pmc=26.2`) passed all 57 tasks,
including i18n, Party group combat/selection, class balance, swept TNT contact,
lifecycle/interactions, Miners isolation, Parkour geometry and ordered neighbouring
tasks. Class-menu cleanup is restricted to the new selection mode, preserving
the lifecycle behavior of Classic, Group PvP and Sumo.

Native Paper 26.3 #249 and 26.2 #135 each passed **1,065 assertions** and logged
`PARKOUR_SMOKE_PASS` before clean shutdown. In addition to the historical chest,
water and rocket checks, these cover:

- The real Party menu → PvP → Classic/Group/Choose Kits navigation and back button.
- All fifteen native class icons, localization, foreign-player rejection, stale
  click/shift-click cancellation, drag cancellation and the localized reopen star.
- The complete lower honey patch and native solid caps in both built lanes.
- Five native packed-ice columns and full hull/passenger headroom on every road row.
- Native TNT fuse/yield settings, swept collision against an actual living entity,
  placement at the impact point, fuse-zero arming, owner tracking and solid-cover
  rejection. Placed/airstrike charges keep their timed fuses and separate damage
  caps; cleanup removes every test charge.

Production JAR SHA-256 (identical payload for both classifiers):

```text
dda1c376d3ab0e7db1c9663c6e7137ff2fdf0c2383f0e814e7949f4e5caa4421
```

Only disposable test copies include the ASP API workaround. No native fixture or
workaround classes are included in production JARs. Logs, fixture JARs and fresh
runtime worlds are outside Git. Player transport/inventory views are substituted;
this does not establish real-client input timing, a full selection/countdown
match, boat steering feel or late honey-side catches. Those remain a manual
playtest before merging. Native contact tests inspect fuse arming and tracking,
then remove charges before their blast; they do not measure player blast damage.
