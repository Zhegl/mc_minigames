# Potato Minigames

Server fork of [Petr1Furious/potato-minigames](https://github.com/Petr1Furious/potato-minigames),
preserving the upstream Git history and the server's downstream patches.
The original Apache-2.0 license and attribution are retained in `LICENSE` and `NOTICE`.

One Paper plugin with seven game categories on a shared framework, built for a server
anybody can join and play at any time: players queue for a game, wait in a lobby,
and the game starts by itself. Different games run side by side.
Targets Paper/AdvancedSlimePaper **26.3** (26.2 still builds, see Building), Java 25.

## Games

| Game | Command | Modes | What it is |
|---|---|---|---|
| **Pillars** | `/pillars` | `original`, `chaos`, `item-shuffle`, `lucky`, `tactical`, `big_house`, `sniper_duel`, `party`, `rave`, `adhd`, `perks`, `fusion` | Everyone on a bedrock pillar in the sky, a random item every few seconds, last one standing wins; `lucky` builds the pillars from [New Amazing Lucky Blocks](https://github.com/I2000C/NewAmazingLuckyBlocks) lucky blocks. `tactical` hands everyone the same random items each round (`sameItems`, also available in other modes). `big_house` puts the pillars 2.5x further apart (`distanceMultiplier`) with chaos-speed items. Ordinary handed-out items that can be enchanted have a one-in-three enchantment chance (`enchantChance`). Ported from [PillarPeril](https://github.com/MarcPG1905/PillarPeril). |
| **Bingo** | `/bingo` | `default`, `butfast`, `collectall`, `lockout` | Teams race to collect items on a generated board. Cage drop into a random arena; deaths are short spectator punishments. In `lockout` each item goes to the team that collects it first: a line wins, else the most items when time runs out. |
| **Warfare** | `/war` | `classic`, `team_beacon` | Formerly hide-and-seek. `classic`: invisible free-for-all with a bucketed-distance sidebar and gadgets; `team_beacon`: defend your wool beacon, respawn at it until it's destroyed. |
| **Chase** | `/chase` | — | Formerly ultimate tag. A fixed number of rounds; hunters track runners by compass. |
| **Party / Вечеринка** | `/party` | `pvp`, `sumo`, `pvp_ffa`, `pvp_select`, `sumo_ffa`, `arms_race`, `one_shot`, `tnt_run`, `mob_count`, `rhino`, `miners`, `magic_battle`, `parkour_race` | PvP and sumo have round-robin BO3 tournaments and simultaneous one-life FFA variants. Arms Race is a simultaneous free-for-all with weapon progression and respawns. TNT Run, mob counting and ravager/mine dodging are one-life survival games. Miners combines private ore mining with random equipment upgrades, consumable loot and underground PvP. Magic Battle puts six wands and forty-two spells on the imported Lighthouse sky-island map. |
| **Counter-Strike** | `/cs` | `default`, `deathmatch`, `wingman` | Classic bomb defusal, A-side Wingman or a timed free-for-all with respawns and free weapon selection on Dust II. |
| **The Finals** | `/finals` | — | Two teams open vaults, carry cash boxes and defend or steal cashouts in a disposable arena. The first team to $20,000 wins, or the richer team after ten minutes. |

### The Finals

Join with `/play finals` or use the lobby compass. Two players can play against
each other; the queue supports 2–12 players split between red and blue teams.
This is a Minecraft adaptation using vanilla items, sounds and particles; no
client mod or resource pack is required.

Right-click the emerald vault to open it in five seconds, then touch the cashbox.
The carrier glows and moves slowly; aim and press Q to throw the cashbox forward
with force, including when your selected hotbar slot is empty. The thrown box follows
a gravity-driven arc; death still drops it normally.
Throw the cashbox into either cashout to deposit it automatically for the thrower's
team. Fast throws are checked along their full path, and walls block insertion.
Right-click gold cashout A or B
to deposit it. After 90 seconds the current owner banks $10,000. To steal an
ongoing enemy cashout, **sneak + right-click the station and stay within three
blocks for five seconds**. Damage, death, leaving the area or releasing sneak
interrupts the steal. The action bar shows progress, and the boss bar shows the
current owner and payout time. A two-tone siren sounds at the cashout while a steal
is in progress, stopping when it succeeds or is interrupted. Stealing never resets
that timer; a payout that finishes on the same tick wins over a steal. Cashout stations are simulated entities:
destroying their support makes them fall and settle on terrain. Their labels,
deposit/steal interactions and ongoing payout timers follow their current positions.
Active cashouts alternate between vanilla **Pigstep** and **Lava Chicken** at 30%
volume in the Jukebox/Note Blocks sound category. Nearby players hear the music
within 32 blocks of the station. The background track follows each listener while
they remain in range, with a smoothed overhead sound source to reduce movement jitter.
Leaving the radius makes the same track inaudible; returning continues its current
playback position, with a brief two-tick fade at the boundary. Payout, disconnect, world changes, and
match end stop it. Respawning/reconnecting starts a fresh stream because vanilla
clients cannot seek within a disc. The old saved 40-second default is upgraded once;
other custom timings remain.

First to $20,000 wins; the richer team wins after ten minutes, with equal cash
a draw. Existing six-minute defaults upgrade once; custom match durations remain.
Death normally has a five-second automatic respawn delay. Matches that
start with exactly two teams of two or three use the downed/revival rules below.
When all members of a two- or three-player team are dead together, their respawns
switch to a shared 16-second
team-wipe countdown. They return at the same randomly selected team spawn; a
disconnected teammate keeps that location and timer. Defibrillators cannot bypass
the team-wipe delay. Respawns grant two seconds of spawn protection,
during which combat and objectives are disabled. Teams remain fixed; a disconnected
team has 15 seconds to return before the match ends without points. Connected
winners receive three points; practice, draws and aborted matches award none.

While downed or waiting to respawn, press **Sneak** (Shift by default) to choose a
loadout, or use `/finals loadout`. Changes apply at your next automatic, Space-key
or team-wipe respawn. Manual and defibrillator statue revives keep the kit you had
when eliminated; your new selection stays pending until you respawn. During the
pre-match countdown, loadout changes still apply immediately.

**2v2 and 3v3 revivals:** elimination leaves a revive statue. After eight seconds the
fallen player can press **Space** to respawn; reaching zero does not respawn them
automatically. They remain available for revival until they actually respawn.
Teammates see a downed marker at the statue, can right-click to carry or drop it,
and press **Q** to throw it toward cover. Only teammates can carry the statue.
Sneak + right-click to begin a four-second manual revive, then stay crouched,
nearby and aimed at the statue with clear line of sight. Taking damage, attacking,
opening the loadout menu or breaking the channel cancels it. Manual revives restore
full health at a safe position beside the statue. A full wipe clears statues and
pending revives, then automatically returns the team together after 16 seconds.

In 2v2 and 3v3, Defibrillator activation charges for one second while held and aimed at
the statue, then starts a three-second reconstruction. It restores half health
and blocks gadgets and specializations for five seconds; weapons remain usable.
An accepted activation uses a gadget charge even if its initial charge-up is
interrupted. The reconstruction can finish after the reviver dies, provided the
team has not been wiped and the target has not respawned or disconnected.
The original game's [revive-statue interaction](https://www.thefinals.wiki/wiki/Revive_Statue),
[Defibrillator reconstruction](https://www.reachthefinals.com/patchnotes/200),
[charge time](https://www.reachthefinals.com/patchnotes/760) and
[revive sickness](https://www.reachthefinals.com/patchnotes/500) inform this adaptation;
the eight-second Space unlock and 16-second wipe are this mode's custom timings.

**Five-player matches:** the last player in the lobby's participant/join order
becomes the **Game Master**. The first four form two teams of two, with the same
revival rules. The Game Master flies in spectator mode, belongs to neither team,
earns no match points, and can reconnect without becoming a contestant.

The Game Master opens **`/finals events`** to choose a game-show event after the
loadout countdown. Events last 30 seconds, one at a time. Commands also work
directly from spectator mode:

| Command | Effect |
|---|---|
| `/finals event low_gravity` | Classic Low Gravity: higher jumps and slower falls for contestants. |
| `/finals event meteor_shower` | Marked impacts warn players before meteors damage contestants and destroy cover. |
| `/finals event orbital_lasers` | Standing still attracts a warning beam and a delayed strike; move to evade it. |
| `/finals event stop` | End the current event and clear pending strikes/effects. |
| `/finals event status` | Show the active event and remaining time. |

Only the assigned Game Master can operate these controls. Destruction stays within
the match arena and respects its protected blocks. These are simplified adaptations
of the original game's events: [Orbital Lasers target stationary contestants](https://www.reachthefinals.com/patchnotes/970),
and [12.0 confirms Meteor Showers and removes Low Gravity from the rotation](https://www.reachthefinals.com/patchnotes/12-00).
Low Gravity is included here as a classic event; the 30-second duration and manual
Game Master controls are custom rules for this mode.

**Loadouts:** `/finals loadout` opens the menu. You can also use
`/finals class S|M|L` (or `light|medium|heavy`) and `/finals weapon <id>`.
Choose one specialization and three distinct, class-compatible gadgets in the
menu. Choices are saved per player in `finals/loadouts.yml`; old saved loadouts
receive the class's default equipment. Countdown changes apply
immediately; combat changes apply on the next spawn. Opening the menu does not
protect you from enemy damage. Original health, size and movement settings return when
you leave.

| Class | Health / movement | Weapons | Specializations |
|---|---|---|---|
| Light (S) | 15 HP / 120% | M11 (`m11`), XP-54 (`xp54`), SR-84 (`sr84`), Sword (`sword`), Dagger (`dagger`) | Evasive Dash, Cloaking Device, Grappling Hook |
| Medium (M) | 25 HP / 100% | AKM (`akm`), FCAR (`fcar`), Model 1887 (`model1887`), Riot Shield (`riotshield`), Dual Blades (`dualblades`) | Healing Beam, Guardian Turret, Dematerializer |
| Heavy (L) | 35 HP / 85% | M60 (`m60`), Lewis Gun (`lewisgun`), SA1216 (`sa1216`), Sledgehammer (`sledgehammer`), Spear (`spear`) | Charge 'n' Slam, Mesh Shield, Winch Claw |

Light is 80% of normal player size, Medium is 100%, and Heavy is 125%.
Heavy needs to crouch through standard two-block doorways. Fall damage is disabled.

All classes can choose Frag, Smoke, Gas, Pyro and Goo Grenades or Flashbangs.
Light also has Sonar Grenade, Vanishing Bomb and Breach Charge; Medium has Sonar
Grenade, Jump Pad, Defibrillator, Explosive Mine, Gas Mine and APS Turret; Heavy has RPG-7,
Dome Shield, Barricade, Explosive Mine and Pyro Mine. The menu shows charges,
recharge times and controls for every item.

The primary weapon is in slot 1, three gadgets in slots 2–4, specialization in
slot 5, and loadout menu in slot 9. Right-click to
fire/use a gadget, left-click for melee, and **F to reload**. Guns have magazines,
timed reloads and unlimited reserve ammunition. Gadget charges recharge and reset
on respawn. Weapon values are balanced for Minecraft; team leather armor reduces
the displayed base damage. Gun damage is increased by one third for roughly 25%
shorter time to kill, with the same firing cadence. Gunshots, including reflected
bullets, do not knock players back. The dagger rewards backstabs, the spear has longer
reach, and the hammer's right-click destroys nearby cover. Hold right-click with
the Riot Shield to reduce frontal damage. Hold right-click with Dual Blades to guard:
frontal bullets are reflected back at the shooter, with damage credited to the
defender. Melee attacks and explosions are not reflected.

Passive health regeneration replaces Minecraft's food healing. After the last
damage taken, Light waits 8 seconds, Medium 9, and Heavy 10, then heals 4 Minecraft
health points (2 hearts) per second up to full health. Further damage restarts the
delay; moving and firing do not. Reviving starts a fresh delay, and Healing Beam
healing remains independent. Delays follow the current
[Light patch notes](https://www.reachthefinals.com/patchnotes/12-00),
[Medium patch notes](https://www.reachthefinals.com/patchnotes/300), and
[Heavy build documentation](https://www.thefinals.wiki/wiki/Builds). The fixed rate
uses the [community-reported 40 HP/s](https://www.reddit.com/r/TheFinalsAcademy/comments/1gvc91c/health_regen_stats/)
at this mode's 1:10 health scale.

Sonar reveals hidden enemies. Carrying a cashbox or attacking ends invisibility.
Hold right-click with the Healing Beam to heal allies (or yourself more slowly).
The heat bar appears above the hotbar: eight seconds of healing overheats the
beam, and four seconds without healing fully cools it. Releasing right-click,
switching items, or losing sight of the target stops the beam. In 2v2/3v3, aim the
Defibrillator at a teammate's statue and right-click to charge the revive; outside 2v2/3v3,
Defibrillator instantly revives a nearby teammate at their death position during
the respawn delay. Dome Shield
blocks enemy attacks from outside while enemies can still enter it; cashbox
carriers do not receive dome protection. Explosives can hurt their owner.
Turrets and mines can be destroyed with weapons. APS intercepts enemy grenades
and rockets. Smoke obscures enemies and extinguishes fire; fire clears gas.
Dematerializer holes restore once clear of players. Mesh Shield requires holding
the specialization and blocks attacks from the front. Winch Claw pulls enemies,
cashboxes and cashouts, with terrain stopping their movement.
Glass blocks and panes can be broken with an empty hand. Right-click doors and
trapdoors to open or close them, including iron variants. Fence gates and buttons
support normal right-click interaction, and pressure plates activate when stepped
on. Buttons and plates retain Minecraft's normal redstone behavior. There is no demolition
pickaxe; weapons and explosives still destroy arena cover and temporary barricades,
while objectives, spawn areas and the protected floor remain intact.

Vault and cashout labels show their remaining timers. Distant objective and teammate
markers are drawn closer to the viewer when needed, with the actual distance shown,
so they remain visible beyond entity render distance. Marker refreshes restore
private visibility after chunk tracking is lost and returns. Eliminations appear in
chat, cashout starts show a title, and vaults, cashouts, kills, explosions and
respawns have sound cues.

**Custom maps:** the generated arena is a fallback. You can import a vanilla Java
overworld folder or ZIP, or register an existing persistent world. Put imports in
`plugins/PotatoMinigames/finals/imports/`; use quotes around a filename with spaces.
You can also give a direct public HTTP/HTTPS download URL:
`/finals map import rooftop https://example.com/maps/rooftop.zip`.
The ZIP downloads in the background and imports automatically; wait for the
completion message before using `goto`. Redirects are supported, but login pages,
map listing pages, and private-network URLs are not. Failed downloads leave no map
entry or partial archive. Imports create an editor copy, and matches use a fresh
copy of a saved snapshot.

1. `/finals map import rooftop "Rooftop.zip"`, then `/finals map goto rooftop`.
   Alternatively, stand in an existing world and run `/finals map create rooftop`.
2. Look at opposite corner blocks and run `/finals map pos1 rooftop` and
   `/finals map pos2 rooftop`. Only X/Z are selected; the world's entire build
   height is included automatically, even when both corners are at ground level.
3. Stand at each desired team spawn and run `/finals map spawn rooftop red`
   or `/finals map spawn rooftop blue`. Repeat for multiple spawns per team;
   `/finals map spawnclear rooftop red|blue` clears that team's draft list.
4. Stand at the objective positions and run `/finals map vault rooftop`,
   `/finals map cashout rooftop a` and `/finals map cashout rooftop b`.
   For random vault locations, stand at each extra spot and run
   `/finals map vaultadd rooftop` (up to 16 spots total).
   `vault` replaces the whole list with your position; `vaultclear` clears it.
   Add extra cashout locations with `/finals map cashoutadd rooftop` (up to 16
   total). `cashout rooftop a|b` edits the first two candidate locations and keeps
   the extras; `/finals map cashoutclear rooftop` clears the draft cashout list.
5. `/finals map save rooftop`, wait for the saved message, then `/finals map use rooftop`.
   Use `/finals map info rooftop` or `/finals map list` to inspect setup.
   Select `generated` to return to the fallback arena.

To make several playable areas from one city, import the city once, then create
additional maps in its editor world:

```text
/finals map create city_north city
/finals map create city_south city
/finals map goto city_north
```

Here `city` is the existing imported map name. Each new map starts with empty
setup: select its own `pos1`/`pos2`, add its own spawns, vaults and cashouts, then
save it. `/finals map create <name>` while standing in the city also reuses that
world. Both forms retain the imported-world information across server restarts.
Terrain edits are shared in the editor; each area's saved snapshot and playable
bounds stay independent until that map is saved again. No second world import or
city copy is needed. `/finals map use city_north` selects the saved northern area.

Remove one draft point by its preview number with `/finals map vaultremove rooftop 2`,
`/finals map cashoutremove rooftop 3`, or `/finals map spawnremove rooftop red 1`.
Numbers start at 1; remaining points are renumbered after removal. Save again to apply
the edits to future matches. Invalid selection errors list every affected point's
type, number and coordinates.

The vault chooses a random configured location at match start and after each
completed cashout. With two or more locations it never chooses the previous spot
again immediately. The current vault stays in place throughout its cashout cycle.
Existing maps with one vault keep using that position. Exactly two cashouts are
active during a match. After a cashout pays out, only that station moves to a
random unused location; the other station stays where it is. Configure at least
three cashout locations to enable movement. Existing maps with just two keep
both in place. Saving requires every cashout candidate to be at least eight
blocks from every other cashout and vault candidate, and every objective to be
at least three blocks from team spawns. `/finals map info rooftop` lists all
configured locations.

Editor previews turn on when you use `create` or `goto`. Green markers show
vault candidates, gold markers show cashout candidates, and red/blue markers
show team spawns. Labels identify each numbered location; spawn arrows show the
saved facing direction. Previews follow draft edits without changing blocks or
saved snapshots, and are visible only to the editor who enabled them. Use
`/finals map preview rooftop off` to hide them or
`/finals map preview rooftop on` to show them again while in the editor world.

Before the cashbox opens, the vault has a green outline. Both cashout stations have
gold outlines while the cashbox is available or carried; after depositing it, only
the occupied cashout is highlighted. Labels remain visible through walls and follow
falling stations. Teammates have private outlines and name markers visible only to
their allies, including a marker at their death location while they respawn. Normal
overhead player names are hidden during Finals. Visibility is still subject to the
server's entity tracking and the client's rendering distance.

The Tab player list shows each contestant's equipped class and match kill count.
Only teammates (including yourself) see current/max HP or a respawning indicator.
Rows update during countdown and play, use each viewer's language, and return to
their normal names when the match ends. Kills persist through respawns and reconnects.

Every match starts with an 18-second loadout selection window. The loadout menu
opens automatically, movement and combat remain locked, and changes apply immediately.
You can close it and reopen it with the slot 9 item. The menu closes when play begins.
`games/finals.yml` can extend `countdownSeconds` up to 60; values below 18 use 18 seconds.

Setup requires admin permission. Saving validates bounds, safe spawn support,
headroom and objective spacing. Native snapshots retain block data, chest
contents and sign text, but exclude entities. Draft edits do not change the
last saved snapshot until another successful save. Match destruction never edits
the imported source or saved template. There are no plugin-imposed map dimensions,
volume, import size or file-count caps. ZIP64 imports are supported. Saves capture
native chunk data into a single `.slime` world template and report progress while
preparing it. Matches use the same disposable AdvancedSlimePaper world clones as
War: template reading and decompression happen off-thread, then the ready world is
loaded on the server thread. Empty air does not become thousands of structure files.
Full-height selections, exact horizontal boundaries, and configured objectives/spawns
are preserved. Large maps still require sufficient memory and disk space.
Existing `.nbt` and `.tiles` snapshots are automatically converted once during their
first load, which can take longer; subsequent matches use the prepared template.
Alternatively, `/finals map save <name>` captures the editor's current contents directly
in the new format. Failed or cancelled saves leave the previous saved map usable.
Only the overworld is imported. Command blocks and portal blocks are rejected in
playable snapshots.

Admins can use `/finals test` for solo practice, `/finals start @a` to launch a
match, or `/finals start @a rooftop` to force a saved map. `/finals stop` ends it.
Timing and score settings are in `games/finals.yml`, editable with
`/finals config`; changes apply next match. All game text uses the shared
English/Russian i18n catalogs, selected by each player's client language.

### Party Games and new party games

Join **Вечеринка → Party Games**, or run `/play party party_games`.
The default match picks six distinct games at random from the fifteen-game pool, with
2–12 players. PvP and Sumo become simultaneous, one-life free-for-alls inside
this match; their standalone modes retain the existing round-robin BO3 rules.
Eliminated players spectate until the next game. Each completed game awards
**3 / 2 / 1 points** to first / second / third place, with no extra kill points.

Equal results share a place and skip occupied positions: `1, 2, 2, 4` awards
`3, 2, 2, 0`. Simultaneous eliminations use server ticks; a failed color or
building check is one shared elimination group. The final ranking compares
points, then first places, then second places; exact ties share the title.
Round results are followed by a four-second intermission. Every game then has
its own four-second countdown after its arena is ready and players have spawned.
The current game owns the
sidebar during play; total points appear between games. Arms Race uses six weapon
stages only inside Party Games (standalone keeps twelve), with a three-minute limit;
Miners uses 15 seconds preparation, 90 seconds combat and a 30-second finale.

These games are also independently available:

| Mode | Command | Rules |
| --- | --- | --- |
| Цветной пол | `/play party color_floor` | Each stage puts a concrete block of the target color in your hand instead of a large center-screen color title. Reach that color before the other tiles disappear; the cue block cannot be placed or dropped. Starts with six seconds, reaching two seconds by stage six; twelve stages. Wrong tiles disappear and players are eliminated only after falling below the platform, so jumping to safety remains possible. The palette grows from 6 colors to 8 at stage 3, 10 at stage 6, and 12 at stage 9. Colors form patches of different sizes with rare islands. A target remains within eight blocks of every floor cell. |
| Наковальни | `/play party anvils` | Dodge staggered rain on a 42×42 platform or shelter under slabs. Waves last six seconds. Rain-free relocation starts at ten seconds and shortens by one second per wave to a six-second minimum: old anvils disappear and shelters move randomly. Start with four shelters; remove one every two waves, minimum one. Roofs shrink from 2×2 to 1×1 from wave six. From wave three, punches push without health damage. Anvil contact eliminates. Ten waves. Rain keeps full density within a horizontal radius of eight blocks around living players and falls four times less densely elsewhere; overlapping zones do not stack. Distant cells rotate evenly over four sweeps instead of guaranteeing every cell is hit each wave. At most 2560 airborne anvils, with rate-limited impact sounds; slabs intercept anvils above shelters. |
| Повтори постройку | `/play party build_memory` | Private platforms form a circle around an elder guardian jury. The first two stages pick distinct small models of 6–8 blocks; later stages draw from fourteen larger recognizable models without repeats. Material palettes, rotations and small changes to existing structural pieces vary the builds; random filler is one third of the former budget. Memorization drops from 12 to 5 seconds; construction starts at 30 seconds and adds five seconds per stage, up to 65. After the deadline, the guardian beams at each platform for three seconds. Red outlines mark missing, wrong and extra cells; chat reports errors and similarity (correct expected blocks / total expected blocks). One player with the most errors is eliminated; ties eliminate the later finisher, measured by the last change to the final build. If everyone is perfect, nobody leaves in stages 1–5; from stage 6 the slowest builder leaves. Edits in the same server tick use roster order as a final tie-break. Facing and upper/lower placement matter; sign text and automatic connections do not. Screens prevent copying during construction. The invisible beam target is parked above the central jury between reviews, outside every build footprint. |
| Гонка инструментов | `/play party tool_race` | Mine down exactly **50 blocks** in your own 1×1 shaft. Everyone gets the same shuffled wood/dirt/stone sequence and unbreakable iron axe, shovel and pickaxe in slots 1–3. Incorrect tools are slower, not an instant loss. Unbreakable glass walls reveal other racers. The personal scoreboard shows current place, blocks mined and blocks remaining. Finishers fall onto a shared landing and remain in survival mode; the last finisher gets three seconds to land. No fall damage or drops. Finish time determines places; after three minutes, unfinished players rank by progress. |
| Паркур-спидран | `/play party parkour_race` | Seven or eight random vanilla tasks, sampled without repeats from a twenty-four-task pool, on identical parallel lanes with invisible barrier walls. Every checkpoint replaces the entire old inventory with a focused task kit; earlier supplies and construction cannot carry forward. Slot 9 returns to the last checkpoint and restores that section’s kit while rolling back its blocks, water and pending projectiles; the race timer keeps running. |
| Дырка в стене | `/play party hole_in_the_wall` | Each round randomly selects Plus, Snake or Donut. Plus has four permanent 2×2 corner islands; its arms narrow from five to three blocks at the first shrink. Snake is a connected S-shaped track; Donut is a rounded ring with a permanent central pit. All layouts shrink by removing floor at one third and two thirds, with three-second red warnings; paths never teleport or grow. Plus uses twenty silhouettes, Snake and Donut twelve each, with map-specific side-lane routes and localized introductions. Every wall offers walk/jump/crouch alternatives; precision routes appear only after ten arrivals. First five walls teach walking and jumping. Types shuffle without repeats within their map catalog. Symmetric outlines and grounded frames replace scattered floating blocks; the hurdle uses a full-block sill instead of an automatically climbed slab. Concurrent perpendicular walls require compatible passages above the final floor, never just above the center pit. Real walls step one block at a time, can be ridden, and strongly push on side contact. All four directions stay in rotation. PvP is off; falling eliminates. Configure `holeInTheWall.durationSeconds` (45–300, default 120). Survival awards 3/2/1. |

Tool Race, Anvils, Color Floor and Build Memory use fixed daytime and clear weather.

The new standalone modes award the same podium points to `/party points`.
**Parkour Sprint** also appears in the mixed Party Games pool. Solo practice is
`/party test parkour_race`. `parkourRace.durationSeconds` in `games/party.yml`
sets the time limit (60–600 s, default 240). Existing customized `partyGames.modes`
lists need `parkour_race` added to include it in mixed matches.

Each lane is twenty-seven blocks wide and has the same eight or nine checkpoint islands.
A shared seven- or eight-task order is sampled without replacement from twenty-four obstacle
types once per race, with either length equally likely; resets do not reroll it. At least four movement families appear in
each random course, and at most two tasks share one family. Ordinary islands are seven blocks wide;
the ladder traverse uses two narrow three-block-wide ledges separated by a
twenty-block gap straight ahead. Every task continues in the same forward direction.
Absolute heights are translated together so any
sampled sequence of climbs and drops fits safely inside the arena.
Barriers enclose its sides, ends, floor and ceiling, so neighbouring racers remain
visible but cannot interact. Construction is sliced at 4096 blocks per tick; the
full countdown starts after the course is ready. The HUD names the active task,
and the player receives a localized hint on entering each section.

Checkpoint islands use white quartz with a gold stripe (emerald at the finish).
Intermediate walls, tunnel shells and support bases use dark deepslate tiles;
intermediate pads and obstacle markers use light-blue concrete. Functional honey and ice retain their native materials.

Swimming, Riptide launch and water-boat starts extend their island
with a twelve-block dry run-up before the original water entry. The incoming
checkpoint remains on the front seven-block pad; future water is outside the
previous attempt, so it cannot replace a fall clutch or checkpoint landing.
The water obstacles keep their original gap lengths and height differences.
The swim tunnel starts entirely dry, including its entrance. Racers receive one
water bucket and place/retrieve the source themselves to swim through its low
passage and alternating barriers. Player water can flow through the empty channel;
the shell and barriers remain protected. Clock return or the next checkpoint
removes every placed source and flow, restoring the dry passage and the next kit.

Parkour Sprint highlights only the current runner's next checkpoint with a private,
non-colliding green outline and a numbered label visible through walls. A view-relative
arrow, distance and height difference stay on the action bar; the clock's left-click
repeats the task hint, and right-click still restores the current attempt. Spawn and
clock return face the next island. Ladder clutch supports
are only three blocks high at the bottom of the fall. Placement on these tasks is
limited to those bottom three rows, preventing gradual descent via the invisible lane
shell. Supports stop before the destination island and stay on the outer side of the
lane, leaving the next departure open; blue stripes mark their ends. Course generation validates checkpoint
headroom and forward passages before construction, retries blocked compositions and
has a bounded safe fallback with the same chosen count of seven or eight distinct tasks. Regression checks
cover all ordered neighbouring task pairs in both mirrored layouts.

The honey catch starts entirely beyond its departure checkpoint and exists only
at the bottom of the fall. Its solid cap prevents landing on honey's soft upper
surface; the 36-block drop onto the cap fails the attempt unless the player first
catches the side. Six lower honey rows leave room for the player's full hitbox
to clear the cap and make that catch. The ice-boat road uses five clear columns,
long sweeping bends, straight entry/exit and two-block-high guardrails rather
than repeated right-angle corners. Packed ice gives vanilla boats a speed
advantage with more manageable steering; walking remains legal and takes the
same guarded route. Both layouts preserve checkpoint headroom and lane bounds.

All kits use vanilla Minecraft items and enchantments with native physics/cooldowns.
A checkpoint removes the old inventory, cursor, armour and offhand before granting
only the next task's kit. Elytra are equipped automatically on the flight task.
Potion effects and freezing are cleared on reset/checkpoint changes. Boats belong to their lane,
reject foreign passengers and cannot activate a checkpoint while being ridden.
Dismount onto the island; clock return removes the vehicle and restores the kit.
The clock remains in slot 9; the other items can be rearranged.

| Task | Gap between islands | Height change | Exclusive kit |
| --- | ---: | ---: | --- |
| Bridge | 18 | 0 | 22 wool, shears |
| Wind ascent | 2 | +28 | 36 wool, 8 wind charges; build up or boost and place against the wall |
| Water drop | 4 | -44 | One water bucket |
| Midair chest clutch | 4 | -56 | No starting supplies; jump out to a side chest at take-off height, take its water, then clutch at the bottom |
| Scaffolding tower | 2 | +24 | 30 scaffolding |
| Ladder wall | 1 | +20 | 24 ladders |
| Slime rebound | 15 | -20 | 4 slime blocks; place slime on a bare landing pad 36 blocks below the starting island |
| Pearl throw | 32 | 0 | 3 ender pearls |
| Swim tunnel | 20 | 0 | One water bucket; initially dry entrance and one-block-high passage with four blue barriers and alternating left/right openings; place and move the water source yourself |
| Ladder clutch | 3 | -36 | 8 ladders; a three-block-high bottom support for a last-moment catch |
| Cobweb clutch | 3 | -28 | 3 cobwebs and shears |
| Elytra flight | 50 | +64 | Elytra and one Flight Duration 2 rocket; gain altitude on the boost, then glide to the goal |
| Ladder traverse | 20 forward | 0 | 12 wool and 12 ladders; alternate blocks and side-mounted ladders |
| Block staircase | 14 | +14 | 32 wool and shears; build forward and up |
| Flower-pot staircase | 14 | +14 | 16 wool and 18 small flower pots; use each pot to place the next wool block diagonally forward and up |
| Low-ceiling hops | 20 | 0 | No items; short holes beneath a two-block-high passage |
| Riptide launch | 14 | 0 | Riptide III trident and a starting pool |
| Water ascent | 2 | +20 | Water bucket and a climbing wall |
| Powder-snow clutch | 3 | -40 | Powder snow bucket |
| Wind-charge clutch | 3 | -44 | 3 wind charges; burst one into the floor just before landing, then land back on the checkpoint |
| Honey-wall slide | 4 | -36 | No items; catch the side of a three-row, six-block-high honey patch at the bottom; a solid cap prevents soft landings on top |
| Boat slalom | 32 | 0 | Oak boat, water and three offset gates |
| Ice-boat chicane | 80 | 0 | Oak boat and a five-block-wide packed-ice road with broad bends, straight entry/exit and two-block-high guardrails |
| Wind-charge hops | 14 | +6 | 4 wind charges and a raised middle ledge |

The table lists the full pool; only seven or eight tasks appear in a given race.

The midair chest clutch uses a native chest in each lane at starting feet height,
six blocks beyond the take-off edge. It is outside grounded reach but accessible
after jumping forward. A glass column covers its lid above the starting jump height:
it permits opening the chest but prevents landing on its lid or roof. Only the
falling owner on that task may open/take items; hover the bucket and press the
first hotbar key to transfer it without a cursor detour. Each attempt refills one
water bucket. Water placement is limited to the bottom three rows, preventing a
midway water save against the chest column. The clock clears the attempt and
restores the chest, and other racers cannot access its contents.

The flower-pot staircase uses ordinary small flower pots as placement anchors:
put a pot on the current wool block, then place the next wool block against the
pot's forward face, one block higher and one block farther forward. Its 16 wool
blocks are insufficient for an ordinary supported staircase across the rising
gap; the pot route needs 14 wool and 14 pots, leaving two wool and four pots spare.
Construction on this task is limited to the central three columns marked by blue
deck tiles, keeping barrier walls and neighbouring obstacle walls out of reach
as placement supports. Broken supports on this task yield no items, so the
limited wool cannot be recycled to bypass the pot mechanic. The clock restores
the full kit and clears the attempt if placement goes wrong.

Course walls and islands are protected; racers may break and reclaim their own
attempt's construction on the other tasks. Land on the next island to lock in progress. Both a
checkpoint and a clock reset remove the attempt's building, fluids, loose items
and pending pearls, wind charges and fireworks. Thus old supplies and water cannot
bypass the next task. Neither action resets the overall race timer. A checkpoint also stops residual
velocity and gliding, so flight cannot carry over into the next kit.

Fall, drowning and flight-collision damage use native rules. Lethal damage is
cancelled and queues an attempt reset before checkpoint detection; a failed
water/slime/ladder/web/powder-snow/wind-charge clutch cannot claim the destination checkpoint.
Nonlethal damage remains meaningful within the attempt; a fresh kit restores
health and air. Falling out of the section also returns to the checkpoint.
Every movement packet is checked against the current task bounds. Flying past
an unlanded checkpoint is rejected before the player enters the next section and
queues a reset. Checkpoints require a grounded, non-gliding landing on the deck;
gliding may start only on the selected elytra task. PvP, item dropping and crafting are off. Water, projectiles and building stay
within the active lane and section, with at most 4096 journalled blocks per racer.
The clock is the only game utility; there are no custom movement powers or perks.

Finishers spectate; once everyone finishes, results follow after three seconds.
Finish time ranks first, then unfinished racers by checkpoint and current-section
distance. A reset clears only the distance after the current checkpoint. Equal
results share places and podium points (3/2/1). Leaving withdraws the racer;
reconnecting only spectates the current race.

Build Memory also supplies an unbreakable iron pickaxe, iron axe and shears in
hotbar slots 7–9 during construction. Breaking a placed block returns that block
directly to the inventory; the foundation remains protected.
Completed mixed matches have a separate leaderboard:
`/party points party_games`. Administrators can reset it using
`/party points party_games reset`. Test games and interrupted mixed matches
save no points. Disconnecting disqualifies a player from the current game's
awards, even if they reconnect before it ends; they can play the next game.
Explicitly leaving is permanent for that match. New arrivals can spectate.
Between rounds, fewer than two connected participants triggers a 15-second
reconnect grace period before an unscored early finish.

Configure the game pool and number of rounds using `partyGames.modes` (a
comma-separated string of unique mode IDs) and `partyGames.rounds` in
`games/party.yml`; the round count is capped by the selected pool size.
The same file contains wave, stage and match timing settings. Changes apply
to new games. Solo practice: `/party test <mode>`; admin launch:
`/party start <mode> @a`; stop: `/party stop`.

Automated checks cover standings, generators, arena geometry and event
routing. **The new games still need live multiplayer balance testing**,
especially anvil dodging/relocation and the later memory-building deadlines.

### Party PvP

Open the compass → **Вечеринка** → **pvp**, or run `/play party pvp`.
The default queue accepts 2–12 players. Each pair plays once, in rotating tours
(an odd roster has byes). Duels run sequentially on one arena; waiting players
spectate. Each match is **first to two round wins**, ending 2:0 or 2:1, and awards
**3 tournament points**. Drawn rounds are replayed without awarding a win.
Equal tournament points are broken by wins in direct meetings among tied players.
If first place is still tied, the contenders play extra BO3 matches in a randomly
seeded knockout; the winner takes the title. Playoffs do not add league points.
The final league points are saved under `/party points`; test games award nothing.

Classes are random but mirrored to reduce matchup luck: round one draws two
distinct classes, round two swaps those classes and spawn sides, and round three
gives both players the same random class. Equipment, health, abilities and the
arena reset before every round. All players have 20 health, unbreakable gear,
32 wool (64 for the engineer), shears for breaking player-placed wool, and 16 steaks
in hotbar slot 9. Normal Minecraft health regeneration and hunger are enabled during
PvP combat: eat to keep hunger high and regenerate. Each class also has a limited
supply of healing potions. Abilities and passives are described in the starting chat
messages and the ability item's tooltip. Sumo still has no hunger or health damage.

| Class | Equipment and tradeoff | Class ability (slot 8) |
|---|---|---|
| Страж / Guardian | Stone sword, shield, iron armour with netherite boots and Projectile Protection I chestplate; 2 Healing I splashes, 1 Slowness I splash | **Bastion:** Resistance II + self Slowness I for 4 s; 22 s cooldown |
| Стрелок / Ranger | Wooden sword, Power I bow, unlimited regular arrows, 10 Instant Damage I arrows, 10 Slowness I arrows (4 s), 10 Poison I arrows (4 s), 10 Weakness I arrows (5 s), 10 Nausea arrows (8 s) and 10 spectral arrows; chain chestplate, remaining iron armour; 1 healing and 1 slowness splash | **Retreat:** backward dash and Speed I for 4 s; 16 s cooldown |
| Берсерк / Berserker | Iron axe, backup stone sword, chain chest/leggings and iron helmet/boots; 2 speed and 1 healing splash. Melee hits heal half a heart at most once per 3 s | **Blood Fury:** costs one heart; Strength I + Speed I for 4 s; 22 s cooldown. Unavailable at one heart or less |
| Дуэлянт / Duelist | Sharpness I iron sword, 2 pearls, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash. Sword hits from behind add 2 base damage at most once per 5 s | **Lunge:** forward dash and Speed I for 3 s; 14 s cooldown |
| Маг / Mage (formerly Alchemist) | Knockback I wooden ritual blade, gold helmet, purple leather robe with Protection II, chain leggings, iron boots; 14 splashes, listed below | **Magic Stock:** restores one harming, poison, slowness and nausea potion, capped at two of each; 24 s cooldown. Healing is never restored |
| Инженер / Engineer | Stone sword, Quick Charge III + Piercing I crossbow, unlimited arrows, 64 wool; iron armour with chain leggings; 2 slowness and 1 healing splash | **Fortify:** Absorption II (4 extra hearts) for 6 s and up to 16 wool, capped at 64 carried wool; 24 s cooldown |
| Воздушный рыцарь / Aerial Knight | Density I mace, backup stone sword, 8 wind charges; chain chest/leggings, iron helmet, leather boots with Feather Falling III; 1 speed and 1 healing splash | **Wind Leap:** upward launch and up to 4 wind charges, capped at 8 carried charges; 20 s cooldown |
| Паладин / Paladin | Iron sword and shield, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash | **Purify:** heals 1.5 hearts, removes poison/weakness/slowness/blindness/nausea and extinguishes fire, then Resistance I for 2 s; 24 s cooldown |
| Криомант / Cryomancer | Sharpness I stone sword, 16 snowballs; cyan leather robe with Protection II, remaining iron armour; 1 slowness and 2 healing splashes. Snowball hits apply Slowness IV for 4 s and Weakness I for 3 s, at most once per 0.5 s | **Frost:** up to 8 snowballs (cap 16), Speed I for 3 s, and Slowness I for 3 s on a visible opponent within 6 blocks; 20 s cooldown |
| Пиромант / Pyromancer | Fire Aspect I wooden sword, Flame I bow, unlimited arrows; chain chest/leggings and iron helmet/boots; 1 weakness and 1 healing splash. Fire Resistance lasts the round | **Flare:** ignites a visible opponent within 8 blocks for 5 s and grants 2 absorption hearts for 4 s; 20 s cooldown. No target means no cooldown spent |
| Всадник / Lancer | Netherite spear, backup stone sword, leather chestplate, chain helmet/leggings, iron boots; 1 speed and 1 healing splash; vulnerable horse, once per round | **Spur:** faster horse for 3 s, or Speed I for 3 s on foot; 20 s cooldown |
| Гарпунщик / Harpooner | Loyalty III trident with +2 main-hand melee damage, backup stone sword; iron helmet/boots and chain chest/leggings; 1 speed and 1 healing splash | **Dry Riptide:** 0.6 s forward spin with native collision damage, capped at 6 before armour (3 hearts), and moderate knockback; 20 s cooldown. One hit per target; shields block it |
| Громовержец / Thunderer | Iron thunder axe, backup stone sword; iron helmet/boots and chain chest/leggings; 1 speed and 1 healing splash. Unblocked axe hits apply Glowing for 3 s, at most once per 8 s | **Thunderstrike:** hold the storm wand to preview the aimed block within 48 blocks, then right-click. After 1 s, a fixed 3-block-radius strike deals 8 damage in its central core, falling to 3 at the edge before armour; 3 s cooldown. Move out or use cover |
| Подрывник / Demolitionist | Stone sword; iron helmet/boots, chain leggings and orange leather chestplate with Blast Protection II; 12 tagged TNT, 1 speed and 1 healing splash | **Carpet Bombing:** aim at a 7×7 square within 48 blocks; a half-second warning precedes nine weaker TNT in three rows, plus up to 4 carried TNT (cap 12). 18 s cooldown. Manual throws every 1 s; placement every 0.3 s |
| Тень / Shadow | Unenchanted diamond sword, full dark leather armour (hidden to others during invisibility), 2 splash Invisibility potions (up to 20 s each), 1 speed and 1 healing splash. Hits do not remove invisibility; its stock cannot be replenished | **Dark Wand:** hit an opponent with the tagged stick in slot 8 to apply Blindness I for 2 s and Slowness II for 3 s, once per 20 s. No health damage; shields block the proc; right-click does not activate it |

Demolitionist uses the tagged TNT stack in hotbar slot 4. Right-click an arena
surface to spawn an already primed charge in the adjacent empty buildable cell;
no TNT block or separate ignition tool is needed. Left-click air, a block or an
entity to throw one along the look direction, including when the target's damage
invulnerability would prevent a normal punch. Both modes consume one charge only
after a successful spawn. Placement/hand interval is 6 ticks (0.3 s); throwing has
an additional 20-tick (one-second) cooldown. Alternating modes does not bypass the throw cooldown,
and placing remains possible while a throw is on cooldown. Invalid placement,
full live-bomb slots or blocked cooldowns spend no ammo. The loadout starts with
12 charges. Carpet Bombing uses the tagged gunpowder in slot 8: while held, the
caster sees the aimed block in gold when ready or red during cooldown, with a
7×7 square marking the drop zone. Aim reaches 48 blocks and stops at solid cover.
The entire square must fit inside the arena. Right-click locks the target and
warns everyone for half a second, then drops three rows of three TNT, four ticks
between rows, from six blocks above that surface. These charges have a 0.9-second
fuse, power 1.5 and a damage cap of 9 (4.5 hearts before armour) each. They can
also hurt their owner. The nine airstrike charges have a separate allowance from
the four manual charges. The ability also adds up to four carried TNT, capped at
12, on an 18-second cooldown; it still calls the strike when stock is full. An
invalid target consumes neither cooldown nor supplies. Death, round transition
and stop cancel pending rows, remove live charges and restore preview blocks.
The Miners mode keeps its separate two-second throw cooldown, 50-tick fuse,
10-damage cap and ordinary contact behavior.

Thrown Demolitionist TNT also detonates on direct contact with an active opponent
or their horse. A swept check along the TNT's actual movement accounts for its
collision box, fast crossings and solid cover. The owner, their own mount and
spectators are excluded. Contact stops the same tracked TNT at the hit and sets
its native fuse to zero, producing one ordinary owned explosion on the next
entity tick; there is no second direct-damage hit. Placed and airstrike charges
keep their timers. Added timing, damage, contact/cover/miss and Miners-isolation
regressions passed in both supported full builds during PR preparation.

At most four manually thrown/placed TNT can be live per bomber. Each manual charge has a 30-tick (1.5-second) fuse,
explosion power 2 (roughly 4-block reach, with native distance/cover attenuation),
and no incendiary effect. Native raw blast damage is multiplied by 1.4 and capped at 16 (8 hearts before armour)
for enemies, horses and the bomber; armour, Blast Protection and native damage
invulnerability still apply. The orange Blast Protection II leather chestplate
softens self damage while leaving the class vulnerable in melee. Blasts never
break arena blocks or player wool. Owner tracking credits TNT kills and allows a
single blast to trade kills in the same server tick: vanilla discards TNT before
applying damage, so its owner reference lasts through that blast. Other live
charges are removed on death/withdrawal, and all charges/cooldowns are cleared at
round reset, result and session stop. Cleanup scans only the bounded tracked charges.

Thunderer's storm wand (a tagged lightning rod in slot 8) is a reusable ranged
attack with a 3-second cooldown. While held in the main hand, a private preview
colours the first solid block under the crosshair: gold when ready, red while on
cooldown. The centre spark and 3-block ring show the exact impact and splash area.
Aiming reaches 48 blocks and stops at cover; any face selects that block's top
centre. Invalid/outside-arena targets cost nothing. Only the caster sees this
preview, which updates every four ticks while holding the wand. Colouring uses
client-only packets and never changes server blocks. Moving aim or putting the
wand away restores live block data, including wool changed while aiming; cleanup
also runs on death, leaving, round result/reset and stop, without leaking old-world
block packets into a new world.

Right-click fixes the target and warns everyone with an electric ring for 20 ticks
(1 second); looking elsewhere or swapping items does not move or cancel that cast.
The strike hits only active enemies and their horses within 3 horizontal blocks,
from 0.5 below to 2.5 above the mark. Its central 1-block core deals 8 damage before
armour (4 hearts); damage then falls linearly to 3 (1.5 hearts) at the outer edge.
Solid cover between the mark and the target blocks damage. Visual-only lightning
cannot ignite/break blocks, change weather, hurt the caster or spectators, or add
native lightning damage on top. Normal armour, shields and invulnerability frames
apply; kills credit the caster. Axe Static keeps its independent 8-second cooldown,
requires unblocked damage, and cannot trigger from Thunderstrike after switching
to the axe during its warning. Pending strikes are cancelled on caster elimination,
leaving, round result/reset and stop; preview watching remains active after a shot.

Harpooner's shell in slot 8 starts a dry-land Riptide charge without a Riptide
enchantment or rain. Its native spin stops on collision; horizontal launch speed
is fixed at 1.45 with a small lift and bounded vertical aim. It grants no immunity,
extra healing, Strength, armour penetration or fall protection. Each target can
be hit only once per 12-tick charge, including overlapping manual attacks. A
successful unblocked hit gives moderate knockback (0.65 horizontal / 0.25 vertical),
reduced by knockback resistance. Failed activations while the trident is thrown
spend no cooldown: wait for Loyalty III to return it, or use the backup sword.
Harpooner's trident gains +2 attack damage (one heart before armour on a normal
fully charged hit) through a main-hand item attribute. Native attack speed and
all other default modifiers are retained; attack cooldown and critical hits remain
native. Thrown-trident damage and the Dry Riptide damage cap are unchanged.

Tracked tridents are exempt from ordinary arrow expiry so Loyalty can finish;
round reset, leaving and shutdown still remove them. Native spin state and pending
knockback are cleaned up on elimination, round reset, leaving and shutdown.

The lancer receives a saddled horse **only when the countdown ends**. It has
8 hearts, no armour, fixed movement speed 0.25 (0.30 during Spur), and jump strength
0.4. Killing it leaves the rider on foot until the next round; there is no resummon
or healing the horse with class potions/food. Only its owner can mount it, and its
inventory is locked. Enemy arrows, melee, harmful potions and environmental damage
can hurt it; frost snowballs and Slowness can counter mounted movement. Netherite spear
hits, including native speed-scaled charges, deal their normal Minecraft damage
without a class-specific cap, both on foot and against horses. A backup stone sword,
light armour, one speed splash and one healing splash complete the kit. Spur gives
the rider Speed I for 3 s when used on foot, with the same 20 s cooldown. Mounts
are removed on elimination, leaving, round reset and session shutdown.

Shadow's invisibility is a finite consumable, not a cooldown ability. Each of its
two splashes grants up to 20 seconds, depending on splash intensity; refreshing the
effect does not add unused duration. The potions only affect their caster, and no
ability restores them. Attacking and taking damage do not remove invisibility.
Items held in either hand remain visible, as with native Minecraft invisibility;
fire and Glowing also give away the player. The full leather set keeps its normal
protection during invisibility, while opponents and spectators see empty armour
slots. The owner still sees their real equipment and armour bar. When invisibility
expires or is removed, the current armour becomes visible again. Equipment changes
and newly tracking viewers are masked at the end of the server tick after native
equipment broadcasts, without resending unchanged packets every tick. Cloaks are
cleared on elimination, leaving, round reset and shutdown. Light armour, limited
potions and a 20-second wand cooldown offset the diamond sword and concealment.
The wand perk requires a successful unblocked melee hit on a player; it deals no
health damage. Shields do not spend its cooldown, hit spam cannot refresh its
control, and the HUD shows its remaining cooldown. Paladin Purify removes Blindness.

Cryomancer snowballs now apply Slowness IV for four seconds and Weakness I for
three seconds, at most once every ten server ticks (0.5 s) per caster. The hit
cooldown is independent for each cryomancer and resets on leaving/round reset.
A hit on a mounted player also applies the effects to their horse; a direct horse
hit affects the horse. The snowballs remain control projectiles without direct
health damage. Paladin Purify removes these debuffs and the new nausea effect.

The aerial knight uses native mace smash attacks and native wind-charge boosts.
Its leap preserves falling attacks: no Slow Falling effect is applied. Charges are
limited per stock and replenished by the ability. The full-height world border
aligns with the 49×49 playing area so jumping above the low barrier wall cannot
escape the arena. Neither frost effects nor fire abilities edit terrain.

The mage starts with **14 potions in seven types**: 2 Harming I, 2 Poison I (5 s),
2 Slowness I (4 s), 2 Nausea (8 s), 2 Weakness I (6 s), 3 Healing I (two hearts each),
and 1 Speed I (6 s). Nausea, weakness and speed start in inventory slots 10–12; combat spells and
healing are on the hotbar, with slot 9 reserved for food. Class potions stack up to
eight and have distinct colours and labels.
Helpful splashes affect only their caster, harmful splashes only the active opponent;
spectators are excluded. Other classes use the same potion targeting rules.

Mage refills cannot exceed the individual stock caps or generate healing. Activating
Magic Stock while all four refillable stocks are full does not consume its cooldown. Blood
Fury cannot kill its caster. Backstab and lifesteal cooldowns reset every round,
and class perks do not leave permanent player attributes behind. The mage retains
the original alchemist enum entry. There are now fifteen equally likely classes: each
has a 1/15 (about 6.67%) marginal chance per player; the two opening classes are always distinct.

Ranger ammunition occupies the top inventory row, left to right by general utility:
**instant damage → slowness → poison → weakness → nausea → spectral → ordinary** (slots 10–16, counting
from 1). The offhand starts empty so native bow selection follows that order;
move any chosen stack into the offhand to prioritise it manually. Special arrows
start with ten of each type (60 special arrows total), are finite, cannot be picked
back up, and only reset between rounds. Regular-arrow
refills never take an earlier slot ahead of the special stacks. Custom tipped-arrow
durations are explicitly scaled to 1 so the short effects last their stated 4/4/5/8 s.

Rangers, engineers and pyromancers start with 64 ordinary arrows; their arrow stack is replenished to 64
every second during combat, including overtime. Moving the stack to another slot
does not prevent replenishment. Other classes do not receive arrows.

PvP, group PvP and Arms Race share two symmetric **49×49** battle maps.
Each session independently picks **Stone Courtyard** or **Desert Ruins** with equal
50% probability and announces the selected name in the viewer's language. The
choice stays fixed across tournament pairings, BO3 rounds, overtime and respawns;
each new session or corresponding round in Party Games draws again.
Stone Courtyard retains its low stone cover and cross-shaped lanes. Desert Ruins
has a sandstone/red-sandstone floor, broken inner walls with five-block-wide
entrances, four L-shaped outer ruins and small stepping stones near the centre.
Cover stays at most two blocks high, and clear outer lanes serve both circular
FFA spawns and all sixteen Arms Race respawn points. A one-block rim and eight barrier blocks above it enclose the arena; the roof
is open. The original floor, cover and boundary cannot be broken or exploded.
Players can place wool up to five blocks above the floor and break either player's
placed blocks. Those blocks and stray projectiles are removed between rounds.
Crafting and item dropping are disabled. Pearls stay inside the arena. A five-second
countdown pins every participant, including waiting spectators, to their spawn:
walking, jumping and spectator flight are disabled; looking around is allowed.
Combat restores the original movement speeds. Attacks, building and abilities
remain disabled during the countdown.
Lethal PvP hits now produce the normal Minecraft death screen with the killer's
name, plus a session-only kill message and a title for the killer. The round pauses
for three seconds before resolving BO3 and starting intermission; same-tick mutual
kills remain a draw. Clicking Respawn early puts the loser in spectator mode and
never re-enters the fight. Automatic respawn also works if no button is clicked.
Sumo ring-outs show who pushed whom and use the same three-second result pause,
without introducing health damage or a native death in sumo.
After 90 seconds the border shrinks over 30 seconds; if both players survive,
remaining health plus absorption decides the round (an exact tie is replayed).

Disconnecting or leaving withdraws a player for the rest of that tournament:
unfinished matches are technical losses, with no points when both players withdraw.
Rejoining allows spectating and does not restore a kit or reroll a class.

Admin commands: `/party start pvp @a`, `/party test pvp` (solo arena training),
`/party stop`. Timing settings live in `games/party.yml`: `pvp.roundSeconds`,
`pvp.countdownSeconds`, `pvp.intermissionSeconds`. Changes apply next session.
The class loadouts are initial balance values; live duels are needed for tuning.

### Party Sumo

Open the compass → **Вечеринка** → **sumo**, or run `/play party sumo`.
Sumo uses the same 2–12 player round-robin tournament, BO3, three points per
match, title tie-breaks, spectator handling and withdrawal rules as PvP.
The difference is the fight: a **Knockback I stick**, no armour, no health damage
and no building. The stick applies controlled horizontal knockback of 0.9, or
1.1 when sprinting (about 30% stronger than the previous 0.7/0.85 impulses).
Empty-hand punches remain weaker. A low upward impulse and a
horizontal speed cap prevent runaway launches from repeated hits. Falling below
the platform loses the round. Each player's hit immunity lasts ten server ticks;
it resets before every round. Both falling in the same server tick replays the round.

The arena is a round, **19-block-wide platform** above the void, without walls or
hidden floors. Players start eight blocks apart and swap sides for round two.
After 45 seconds the outer ring disappears; another ring goes every three
seconds. The platform disappears by 27 seconds of overtime, leaving
time for ring-outs before the 30-second overtime limit. Every round restores the full platform. A five-second
countdown pins participants in place and blocks jumping, flight and early hits.
Waiting players spectate the duel after the countdown.

Admin commands: `/party start sumo @a`, `/party test sumo` (solo platform training),
`/party stop`. Settings in `games/party.yml`: `sumo.roundSeconds`,
`sumo.countdownSeconds`, `sumo.intermissionSeconds`; changes apply next session.

### PvP BO3 with chosen starting classes

Admins can run `/party duel <class_A> <class_B>` to start an ordinary two-player
PvP BO3. Class numbers are 1–15, as listed by `/party classes`; `-1` independently
draws any random class for that player. Zero and out-of-range numbers are rejected.
The same class may be assigned to both players.

Player A is the command author, player B is the other free online player. Exactly
two free players are required; players in another game cannot be taken into the
duel. From the console, A/B follow alphabetical player-name order. The command
announces both assignments and tab completion shows class names for each number.

For example, `/party duel 11 14` starts Lancer against Thunderer, and
`/party duel -1 12` gives A a random class and B Shadow. Round one uses those
starting kits, round two swaps them, and a deciding third round gives both players
one identical random class. A draw replays the same round; two wins end the BO3.
Assignments are bound to player UUIDs and survive tournament shuffling.

### Party group PvP and sumo

Open the compass → **Вечеринка** → **PvP** → **Групповое**, or choose **Массовое сумо**,
or run `/play party pvp_ffa` / `/play party sumo_ffa`.
Both variants support **2–16 players** fighting at once with one life each.
The last survivor wins three Party points. If everyone dies together, the battle
ends in a draw. Eliminations never restart the battle or reshuffle surviving kits.
Leaving or reconnecting cannot grant a second life.

In group PvP, each player receives an independent uniformly random class from the
full pool of fifteen; duplicate classes are allowed. All current class balance
changes and abilities apply. The arena stays protected, while players can place
and break their own wool. Targeted fire/frost abilities select the nearest visible
opponent within their normal range, without requiring the caster to aim at them. Lightning can hit multiple enemies in its area.
After death, the native death screen remains for three seconds, then the player
becomes a spectator while everyone else keeps fighting. Respawning early also
leads to spectator mode.

Group sumo uses the original 19-block platform and the stronger Knockback I stick.
All players start around a ring, face the centre and remain frozen during the
countdown. Hits cause no health damage. Falling eliminates the player and moves
them to spectator mode immediately.

Group PvP overtime starts after 180 seconds: the border shrinks to the centre
over 30 seconds, then combat continues until the last survivor; health is never
used to pick a timed winner. Group sumo starts shrinking after 45 seconds.
Configure these separately through `pvpFfa.overtimeSeconds` and
`sumoFfa.overtimeSeconds` in `games/party.yml`. The respective existing PvP/sumo
countdown settings also apply to group battles.

Admin commands: `/party start pvp_ffa @a`, `/party start sumo_ffa @a`,
`/party test pvp_ffa`, `/party test sumo_ffa`, `/party stop`.
Solo testing ends on elimination, without a new round.

Group PvP spawns lie on a 20-block-radius ring; sumo uses a six-block-radius ring.
FFA results wait two ticks after the most recent elimination to account for
simultaneous deaths. Eliminated players' projectiles are cleaned up after that
window, while surviving players' projectiles remain. In sumo, eliminated players
move to the spectator viewpoint immediately; PvP keeps its three-second death
screen. Eliminated spectators can teleport to follow survivors. During combat,
the boss bar shows the fraction of players still alive, and overtime has its own
label instead of a timer stuck at zero.

### PvP class selection

The Party lobby now groups PvP behind one entry: **Классик / Classic** (`pvp`),
**Групповое / Group** (`pvp_ffa`) and **Выбор китов / Choose Kits** (`pvp_select`).
Queue and command IDs remain unchanged for the existing variants. Sumo and all
other Party entries retain their original menus. Direct entry to the new queue
is `/play party pvp_select`; `/party start pvp_select @a` and
`/party test pvp_select` use the same factory.

Kit selection uses exactly the group PvP arena, limits (2–16), one-life combat,
class abilities, overtime, spectator behavior and awards. Players first spawn
on the arena, frozen and protected, with a private menu of all fifteen classes.
Weapon/role icons, descriptions, passives and abilities explain each class.
Duplicate choices are allowed. Click a class to choose it; right-click the star
in the hotbar to reopen the menu and change it while others are deciding.

Once everyone has chosen, the selected classes are locked and equipped, all
selection windows close and the ordinary fight countdown starts. Selection lasts
at most 30 seconds; at the deadline only missing choices become random classes.
The boss bar shows the number ready and seconds remaining. Withdrawn players do
not hold the phase open; reconnects still spectate. Menu clicks/drags cannot take
items, foreign/stale menus cannot select a class, and menu callbacks stop after
withdrawal, countdown or game end. Menu and item text use the recipient's locale.

Model regression cases cover grouping/queue wiring, all class choices, duplicates,
changes before locking, withdrawals, spectator rejection and timeout assignment.
Full builds passed for both supported APIs during PR preparation. The native
fixture additionally checks the real Party/PvP menu navigation, all class icons,
foreign/stale clicks and drag protection. A connected-client selection/countdown
playthrough remains necessary before merging.

### Party Arms Race

Open the compass → **Вечеринка** → **arms_race**, or run `/play party arms_race`.
This mode is a **2–16 player free-for-all**, with everyone fighting at once.
There are no duel pairings, BO3s or tournament rounds. One kill with the current
weapon advances one step; the first player to get a kill with the final weapon wins.
The shared chain is:

1. Diamond sword
2. Iron sword
3. Diamond axe
4. Stone sword
5. Iron axe
6. Bow
7. Crossbow
8. Stone axe
9. Wooden sword
10. Wooden axe
11. Golden sword
12. Golden shovel (a kill wins the game)

All players wear identical unbreakable chainmail armour. Arrows are replenished
automatically. Every successful upgrade heals two hearts, up to full health;
passive regeneration is disabled. Bare-hand hits, bow melee attacks and projectiles
from an earlier weapon step cannot deal damage or advance progression. Old arrows
are removed on upgrade, death or disconnect, and one sword sweep cannot skip steps.
Environmental deaths can credit the last valid hit within eight seconds, only if
the attacker's current weapon step still matches that hit. Suicides give no progress.

Players see the native death screen with their killer's name and respawn after
three seconds, **keeping their weapon step**. The configured respawn delay has a
three-second minimum. Clicking Respawn early keeps the player waiting as a spectator
until their timer expires. A kill message names killer and victim; the killer also
sees a confirmation title. Final victory waits three seconds before session cleanup,
so the final victim's death screen is visible too. Respawning
selects one of sixteen clear spawn points farthest from the nearest living enemy.
The two-second spawn shield prevents damage and ends when the player attacks or
shoots. Temporarily dead players wait without a spectator follow camera. Reconnecting
preserves progress and uses the respawn delay; late joiners can only spectate.

Arms Race uses the same randomly selected protected 49×49 map as PvP (Stone
Courtyard or Desert Ruins); building, breaking, crafting,
item drops and pickups are disabled. Participants are held at their spawn during
the five-second countdown, with walking, jumping and flight blocked until combat.
The game lasts
up to ten minutes. On timeout, most kills wins, then fewer deaths, then the earliest
arrival at the current step; no kills means a draw. Disconnected players cannot win
on timeout. If only one player remains, there is a 15-second reconnect grace period
before they win (disabled in solo tests). If everyone leaves, the game aborts.
Party standings award one point per qualifying kill, plus three for the winner;
aborted and test games do not save points.

Admin commands: `/party start arms_race @a`, `/party test arms_race`, `/party stop`.
Timing settings in `games/party.yml`: `armsRace.durationSeconds`,
`armsRace.countdownSeconds`, `armsRace.respawnSeconds`,
`armsRace.spawnProtectionSeconds`. Changes apply next session.

### Party One Shot

Join through the Party menu or `/play party one_shot`; admin practice is
`/party test one_shot`. Also included in the default mixed Party Games pool.
Existing saved `partyGames.modes` lists need `one_shot` appended to include it.

**2–16 players** fight on **Desert Ruins**, fixed for this mode. Everyone has an
unbreakable bow, a wooden sword, one ordinary arrow and no armour. Any bow-arrow
hit is lethal, including a lightly charged shot; swords deal native melee damage.
A bonus shield absorbs one damaging attack from any direction, whether a sword
hit or an arrow, and then breaks. Shields do not stack and do not occupy an armour
or offhand slot.

Each credited kill adds one arrow, including sword kills. Missed arrows cannot be
picked up. When the last arrow is fired, a ten-second refill timer starts; if
still empty at its deadline, exactly one arrow returns. Getting an arrow from a
kill or pickup cancels the pending refill. Extra arrows stack to 64; a full stack
leaves arrow bonuses available for other players. Taking a new shield while one
is already active likewise leaves it on the field.

Every six seconds, an arrow or shield pickup is selected with equal probability
and placed at a clear designated spot. At most three pickups exist at once;
unclaimed pickups expire after thirty seconds. Only living participants can
collect them, and ordinary dropped/embedded arrows are never pickups.

Deaths keep the native death screen for three seconds, then respawn at one of
sixteen clear points chosen farthest from living opponents. Each life starts
with one arrow, full health and no bonus shield. Two seconds of spawn protection
end as soon as the player attacks or shoots. Old-life projectiles are removed on
death/disconnect; reconnects preserve kills/deaths and use the respawn delay.
The countdown freezes movement. Building, map damage, hunger, item drops,
inventory transfers and crafting are disabled.

A match lasts **two minutes** by default. Places compare **kills only**;
equal kill counts share places, regardless of deaths. Winners receive the normal
Party podium points (3/2/1); mixed matches use the shared Party Games standings.
If only one participant remains, the match ends after a fifteen-second reconnect
grace period; solo practice runs to the time limit. Settings in `games/party.yml`:
`oneShot.durationSeconds` (60–1800, default 180) and `oneShot.bonusSeconds`
(2–30, default 6). `checkOneShot` covers ammunition timing, extra arrows, shield
charges, respawn-life isolation, death deduplication, kill-only ties and registration.
An optional [MockBukkit component harness](mock-checks/one-shot/README.md) also
checks real inventory APIs, shield feedback, pickups and the per-tick respawn/refill controller.

### Party TNT Run

Join with `/play party tnt_run`, or train with `/party test tnt_run`.
Up to sixteen players run across three circular TNT floors, six blocks apart.
Tiles under a player's footprint turn red and disappear after half a second;
standing still never refreshes their timer. Jumping and falling to a lower floor
are part of the game. Every 30 seconds the collapse delay shortens, down to three
ticks. Platforms never shrink: only tiles stepped on by players disappear.
There is no hidden support floor. Falling below the third layer eliminates you.
The default time limit is three minutes.

Settings: `tntRun.durationSeconds`, `tntRun.collapseTicks` in `games/party.yml`.

### Party Пересчёт / Mob Count

Join with `/play party mob_count`, or train with `/party test mob_count`.
The board has **36 platforms**, each **2×2 blocks**, with **two-block gaps**.
Each platform has a mob head and a label. During counting, glass fills the gaps
and harmless, stationary mobs appear across the board. Count each species:
initially four species and ten mobs; later a fifth species joins and the total
grows to at most sixty. Counting time starts at ten seconds and drops to six.

The mobs and glass then disappear and a number appears. Each player starts the
jump phase on their nearest safe platform and has **ten seconds** to land on any
platform showing the species that appeared that many times. Counts are distinct,
so exactly one species is correct. Standing on its head also counts; being in the
air or above a gap at the deadline does not. Incorrect platforms disappear and
the result settles for three seconds. Jumping to safety after the deadline cannot
undo a wrong choice. Surviving platforms and glass return for the next round.
After twelve rounds, remaining players share victory.

Settings: `mobCount.countSeconds`, `mobCount.answerSeconds`, `mobCount.rounds`.

### Party Носорожий переполох / Rhino

Join with `/play party rhino`, or train with `/party test rhino`.
The circular arena starts with holes and two ravagers acting as rhinos. They
choose among the least-targeted living players, glow before charging, then dash in that fixed
direction without drawing a route on the floor. A dash knocks each player back
at most once. The first rhino starts its windup after one second, with the others
staggered one second apart. Initial dash speed is 0.65 blocks per tick, with a
6.5-second recovery between charges. While resting, crowded rhinos reposition onto
free solid floor; charging rhinos keep their announced direction. Targets stay
distinct when enough players are alive, and are shared evenly otherwise.
Their speed and frequency rise every 45 seconds,
and additional ravagers join, up to six. The floor starts shrinking after 45
seconds, losing a ring every 15 seconds down to a five-block radius. Every 45
seconds more holes open in the remaining floor, including after shrinking stops;
existing holes stay open and the central refuge remains solid.

Glowing orange mine bottles spawn on the floor. Walk over one to pick it up;
right-click to throw it like a splash potion. Carry at most one. Each mine
explodes **eight seconds after its original spawn** by default, including while held:
pickup, landing, rethrowing and dropping it on elimination never restart the fuse.
A landed mine can be picked up again. Its name/action bar shows remaining time.
Explosions push nearby players without health damage or terrain destruction.
More mines appear as the game progresses; the live count is capped at thirty.
The default time limit is four minutes.

Settings: `rhino.durationSeconds`, `rhino.ravagers`, `rhino.mineFuseSeconds`,
`rhino.mineSpawnSeconds`.
On an existing server with the old saved fuse, run `/party config rhino.mineFuseSeconds 8`
to use the new timing from the next session.

All three survival games accept 2–16 players, have a five-second countdown and
use protected throwaway worlds. Health damage, PvP, building, crafting and item
storage are disabled. Falling eliminates a player for the rest of the match;
leaving/disconnecting also withdraws them, and reconnecting only spectates.
The last survivor wins; simultaneous final falls draw. At a time/round limit,
remaining players share victory. Each winner gets three Party points; aborted
and solo test matches award nothing. Solo tests continue until falling or the
limit, so one player can train every mechanic. Admin commands also support
`/party start <mode> @a` and `/party stop`. Countdown is configured with
`survival.countdownSeconds`; these initial balance values apply next session
and are intended for live playtesting.

### Party Magic Battle / Магическая битва

Join **Вечеринка → Магическая битва**, or use `/play party magic_battle`.
This is a one-life, spell-first free-for-all for **2–12 players**, also included
in the default mixed Party Games pool. Each mage starts with six reusable wands,
64 wool and a spellbook; native melee and vanilla health regeneration are disabled.
The last survivor wins; standings award the usual 3/2/1 Party points.

The arena is the ready-made **Lighthouse** map by **JangoMango / NetherGames**,
released under **CC BY 4.0**, rather than generated islands. The bundled palette
contains all 40,571 source terrain blocks and twelve original island spawn columns.
Players stand on the existing island ground below the old cage markers. The map
is copied into an isolated disposable world in bounded slices; no WorldEdit,
resource pack, runtime download or external map setup is required. Source world,
license, pinned revision and conversion tool are in
`assets/maps/nethergames-lighthouse/` and `tools/import_magic_lighthouse.py`.

**Controls:** hotbar slots 1–6 select a school. **Right-click** casts the selected
spell; **left-click** cycles that wand's seven spells. Sneak + left-click cycles
backward. Slot 7 contains building wool; right-click the spellbook in slot 8 to
open a six-row menu with all **42 spells**. Click a spell to select it and equip
its wand; selection never spends mana or resets cooldowns.
The action bar shows mana, selected spell and cooldown. Aimed area spells show
private target rings; a cast locks the position and gives everyone a warning.

Mana starts at **100**, regenerates **7/s**, and gains another **5/s** near the
central lighthouse. Source chests are shared mana caches: right-click one while
holding wool or with an empty hand for **35 mana** and up to **16 wool**; each cache
recharges after **45 s**. Full mana does not waste the cache. Switching wands
never resets cooldowns. A shared four-tick cast interval prevents same-tick burst
exploits, and every successful spell has its own mana cost and cooldown.

Damage values below are health points (two points equal one heart):

| School | Spell | Mana / cooldown | Effect |
| --- | --- | --- | --- |
| Fire | Firebolt | 16 / 1.8 s | Swept projectile, 6 damage and 3 s burning |
| Fire | Fireball | 32 / 5 s | Impact explosion, 10→4 damage in radius 3, 4 s burning; destroys terrain and buildings |
| Fire | Meteor | 50 / 12 s | Fixed target after 1.2 s warning, 14→4 damage in radius 4.5 |
| Fire | Flame Cone | 26 / 4 s | Scorches a visible cone up to 8 blocks away: 7 damage and burning for 4 s. |
| Fire | Cinder Rain | 38 / 9 s | After a 1 s warning, creates a radius-4 zone for 5 s up to 48 blocks away: 3 damage/s and burning. Cover blocks it. |
| Fire | Phoenix Step | 24 / 6 s | Dashes forward, leaving a 3 s flame trail that deals 3 damage/s and ignites enemies. No fall immunity. |
| Fire | Combustion | 30 / 7 s | Consumes burning on visible enemies within 10 blocks to deal 10 instant damage. Needs a burning target. |
| Ice | Ice Lance | 14 / 2 s | 5 damage, Slowness II for 3 s |
| Ice | Frost Nova | 30 / 6 s | Visible enemies within 6 blocks take 6 damage and Slowness III for 2 s |
| Ice | Ice Wall | 25 / 7 s | Temporary 5×3 wall within 12 blocks, lasts 8 s; cannot suffocate players |
| Ice | Blizzard | 36 / 9 s | After a 0.7 s warning, creates a radius-4 blizzard for 5 s up to 48 blocks away: 2 damage/s and Slowness II. |
| Ice | Frozen Orb | 28 / 5 s | Explodes on impact: 6→3 damage within 3.5 blocks and Slowness II for 4 s. Can be reflected. |
| Ice | Ice Armor | 26 / 10 s | Grants 6 absorption health for 5 s. Unspent temporary hearts disappear when it expires. |
| Ice | Shatter | 28 / 6 s | Hits a visible cone up to 9 blocks away: 11 damage against slowed enemies, otherwise 4. Consumes Slowness on hit. |
| Storm | Gale | 18 / 3.5 s | 4 damage and strong knockback |
| Storm | Wind Step | 20 / 5 s | Forward dash and lift, usable in midair |
| Storm | Thunderstrike | 38 / 6 s | 0.8 s warning, 10→4 damage in radius 3, 4 s Glowing |
| Storm | Chain Lightning | 36 / 8 s | Aim at an enemy within 36 blocks: lightning jumps to up to two more enemies within 8 blocks per jump, dealing 8/6/4 damage. Stops at cover. |
| Storm | Updraft | 28 / 9 s | After a 0.7 s warning, creates a radius-3 updraft for 4 s within 48 blocks. Lifts every mage inside; enemies take 1 damage/s. |
| Storm | Dispel | 30 / 9 s | Removes visible enemy projectiles, marked strikes and magic zones within 7 blocks; nearby enemies take 2 damage and are pushed away. Leaves buildings intact. |
| Storm | Skyhook | 22 / 6 s | Aim at a block within 32 blocks to launch yourself towards it. Respects physical walls; does not teleport or prevent fall damage. |
| Earth | Stone Shot | 14 / 2 s | Accurate projectile dealing 7 damage |
| Earth | Earth Bridge | 25 / 6 s | Up to 12 planks underfoot ahead, lasting 20 s |
| Earth | Earthquake | 40 / 8 s | 0.7 s warning, 8 damage in radius 5, launches targets |
| Earth | Stone Dome | 34 / 12 s | Builds a small 5×5 shelter with a roof around you for 5 s. Skips terrain and bodies; opponents can break its blocks. |
| Earth | Fault Line | 30 / 7 s | A narrow piercing ground-level wave travels up to 20 blocks, dealing 8 damage and launching enemies. Solid cover stops it. |
| Earth | Erosion | 24 / 7 s | After a 0.7 s warning, removes terrain and buildings in radius 4 within 48 blocks. Deals no damage. |
| Earth | Stone Pillar | 22 / 6 s | Raises you 3 blocks on a temporary pillar lasting 8 s. Needs clear space above; cannot push through a ceiling or another player. |
| Life | Mend | 24 / 7 s | Heal 6 health; full health spends nothing |
| Life | Aegis | 28 / 8 s | For 3 s reflect projectiles and halve other damage; each projectile reflects at most once |
| Life | Sanctuary | 40 / 18 s | Fixed radius-4 zone for 6 s; caster gains 1 health and 8 mana/s inside |
| Life | Purify | 18 / 6 s | Removes harmful potion effects, burning and freezing, then heals 2 health. Needs a harmful effect to cleanse. |
| Life | Second Wind | 55 / 30 s | For 8 s, the first lethal hit instead leaves you alive with 6 health and extinguishes you. One trigger; cannot save you from the void. |
| Life | Life Tether | 24 / 6 s | A reflectable projectile deals 5 damage and heals you for 2 health only on a damaging hit. |
| Life | Mana Channel | 8 / 18 s | At 65 mana or less, spend 8 mana and stand still without casting for 2 s to regain 48 mana. Movement or damage interrupts; spent mana is not refunded. |
| Void | Rift Bolt | 20 / 3 s | 7 damage, drains up to 8 enemy mana and restores half to caster |
| Void | Blink | 26 / 6 s | Up to ten blocks along gaze, including air; respects walls, bodies and border |
| Void | Gravity Well | 45 / 12 s | 0.7 s warning; pulls visible enemies in radius 5 for 3 s, 2 damage/s |
| Void | Silence | 28 / 9 s | A reflectable projectile deals 3 damage and prevents the enemy from casting for 2 s. They can still move and build. |
| Void | Exchange | 42 / 14 s | Aim at an enemy within 24 blocks to swap positions. Both must stand on solid ground with clear bodies, inside the border. |
| Void | Rewind | 30 / 12 s | Returns you to your position 3 s ago without changing health, mana or cooldowns. Fails if that position is occupied, blocked or outside the border. |
| Void | Dark Veil | 32 / 10 s | Grants invisibility for 5 s and leaves a radius-5 veil for 5 s that briefly blinds visible enemies inside. Held items remain visible. |

Fire and ice have setup/finisher combinations: ignite a target before Combustion,
or slow it before Shatter. Dispel removes visible enemy projectiles, pending area
strikes and zones near the caster, while Erosion counters construction. Exchange
requires two grounded endpoints; Rewind restores only position, respects occupied
space and the moving border, and requires three seconds of recorded history.
Mana Channel takes two stationary seconds and blocks casting; movement or damage
interrupts it without refund. Second Wind lasts eight seconds and saves one lethal
hit with six health, excluding the void. Temporary absorption capacity, active
channels, personal history and other spell state are cleaned up at round end.

Cover blocks offensive areas and projectile travel. During combat, the original
map and player/spell construction can be mined with a non-wand item or destroyed
by Fireball, Meteor, Earthquake and Erosion. Native explosions also affect terrain
inside the arena. White wool uses normal right-click placement in either hand.
Sneak to place wool against a mana chest instead of using it. The caster hears a short hit confirmation
when a spell reduces an opponent's health or absorption. Temporary construction
restores live state on expiry/end;
spell projectiles, delayed casts and zones have bounded lifetimes and stop when
their caster leaves or is eliminated. Countdown locks movement and spell use.
Deaths retain the native death screen for three seconds, then spectate; early
respawns and reconnects do not grant another life. After 180 s (120 s inside a
mixed match) the border shrinks toward the lighthouse over 30 s; increasing damage
then prevents infinite camping. Test solo with `/party test magic_battle`.

Configure `magicBattle.roundSeconds`, `magicBattle.countdownSeconds` and
`magicBattle.chestRechargeSeconds` in `games/party.yml`. On existing servers, add
`magic_battle` to a customized `partyGames.modes` list to include it in mixed matches.
All built-in text is available in English and Russian in `i18n/locales/*/party/magic.yml`.
Compilation and automated model checks do not replace live multiplayer balance
and visual testing, especially knockback, spell cover and mana economy.

### Party Битва шахтёров / Miners

Open the compass → **Вечеринка** → **Битва шахтёров**, or run `/play party miners`.
The mode accepts **2–12 players**; `/party test miners` starts a solo training game.
Each participant starts inside a separate **16×16×16** stone box with the same
ore counts, shuffled independently. The starter kit is an unbreakable Efficiency III
iron pickaxe, stone sword, leather armour and 64 cobblestone; no starting food. Night Vision
and Haste I last through the match. Construction is spread across ticks, then a
five-second countdown freezes players before mining begins.

Players have **15 seconds** to mine privately. Ores roll **random equipment upgrades
and consumables**. A stronger pickaxe, weapon or armor piece automatically replaces
the matching equipment; weaker or equal finds are discarded. The comparison includes
Efficiency, Sharpness, Protection, Power and Quick Charge. Swords, axes, bows,
crossbows and maces upgrade independently, so a ranged weapon never replaces the melee weapon.
Moved gear is found throughout the inventory. New weapon types prefer hotbar slots
2 (sword), 4 (axe), 5 (bow) and 6 (crossbow) and 7 (mace); the pickaxe starts in slot 1.
Consumables and ammunition still drop on the ground for pickup. Identical
consumables have the same metadata across rarity rolls and stack up to their normal
limits (class splash potions: eight). Food is found in coal ore, rather than supplied
in the starter kit.
There is no fixed progression or guaranteed improvement from each ore. Existing
ordinary/good/rare rolls keep their **70%/25%/5%** odds; combat enchantments reach
level II and mining efficiency reaches V.

| Ore | Blocks per private box | Rewards |
| --- | ---: | --- |
| Coal | 80 | Food or automatic faster iron/diamond pickaxe upgrades |
| Iron | 64 | Random automatic armor upgrades; rare diamond pieces; a 6% overall chance of an unbreakable shield |
| Diamond | 24 | Automatic weapon upgrades, including Fire Aspect I sword variants. A 1/60 overall chance of a Density I–II mace with 4 wind charges. Bows/crossbows include 32–64 regular and 4–8 spectral arrows |
| Lapis | 24 | Combat splashes, plus Regeneration I (8 s), Fire Resistance (10 s), rare Strength I (5 s) or Absorption I (8 s). Detection Potion remains a 2.5% roll |
| Redstone | 32 | TNT, pearls, wind charges, 1–3 cobwebs, 1–2 Gust Feathers or Leap Charms |
| Emerald | 8 | A separate 10% roll for one Totem of Undying; otherwise good/rare loot from the other pools or a golden apple |
| Gold | 16 | Each ore immediately grants +1 maximum heart (80%) or +2 (20%); capped at fourteen total hearts |
| Lapis block | 8 | One uniformly random enchanted miners perk from the seven-item pool below |

Each private mine has eight perk blocks, separate from ordinary lapis ore.
Perks drop as finite enchanted items and can be traded or saved for later. Single-use
perks stack to 16; drills keep individual three-use counters. Except
for the drill, use them with right-click in the main hand. Invalid targets or
attempts to reactivate an active perk do not spend an item.
Mine normally with the drill using left-click; a charge is spent only when the
target block finishes breaking, not when mining starts or is interrupted.

| Perk | Item | Effect |
| --- | --- | --- |
| Drill | Enchanted diamond pickaxe | Three mining operations, each breaking a 3×3 plane perpendicular to the look direction |
| Tunnel Borer | Enchanted end rod | One right-click mines ten cells deep from the aimed block, along the look direction |
| Ore Vision | Enchanted quartz | Private glowing ore outlines within eight blocks, refreshed as you move, for three seconds |
| Lucky Vein | Enchanted emerald | Next three ordinary reward ores roll 50/40/10 quality instead of 70/25/5; gold and perk blocks do not consume the buff |
| Sifter | Enchanted flint | Next twenty natural stone blocks each have a 15% chance of a small consumable reward instead of cobblestone |
| Mining Bomb | Enchanted respawn anchor | Right-click placement immediately mines a three-block-radius sphere; during combat damages players, including the owner, within four blocks, up to 10 raw damage at the centre with distance falloff |
| Geological Audit | Enchanted book | Twenty seconds of ore counts within five blocks, updated once per second as you move; includes neighbouring mines |

Perk mining shares the normal ore provenance and automatic equipment upgrades;
no ore pays twice. Bursts can also find perk blocks. Before the partitions open,
mining remains confined to the owner's cell. The shell and closed partitions are
never mined, and tunnels stop at protected walls. Perk bombs do not ignite anything
or use native respawn-anchor block destruction. Combat blast damage keeps native
armor and shields; preparation still prevents health damage. All timed effects,
private outline entities and counters are cleared on elimination, leaving and end.

At the end of preparation shared bedrock partitions become **mineable diorite**
and **PvP begins**. The solid dividers keep mine boundaries visible until players
dig through them. Mining them gives the usual capped cobblestone supply, without
ore loot. Continue mining, dig tunnels, build with cobblestone and use the tracking
compass to locate
the nearest surviving opponent. PvP has a **three-minute** phase followed by a
**one-minute** shrinking zone with glowing players, stopping at **4×4**. The light-blue
translucent walls mark the safe area without collision or mining restrictions;
blocks on both sides remain mineable within the arena. Zone damage applies only
when the player's position is outside these walls, starting at 2 damage per second
and increasing after the shrinking minute. Players inside the final area never
take zone damage. Nametags are hidden before the finale; opponents remain visible when encountered
in tunnels. Spectral arrows keep their native temporary glowing effect. Detection
Potions can be drunk after the partitions open to make all other surviving players
glow for five seconds, visible to everyone. They are finite loot (one bottle),
and repeated uses refresh rather than stack the duration. In the finale, persistent
entity outlines reveal survivors even while sneaking or after drinking milk. The
outline is released on elimination, leaving and round cleanup. One life: the last survivor
wins three Party points; simultaneous final deaths draw. Disconnecting withdraws
a player and reconnecting only spectates. Solo training and aborted matches award
no points. Deaths show the killer and a three-second death-screen pause.

Gold fills only the newly added hearts; excess gold at the cap provides no healing.
The bonus is a temporary max-health modifier, removed on leaving, spectating and
match end. A two-heart roll near the cap is clipped to the remaining bonus.
Totems use native Minecraft resurrection and must be held in either hand; they
are not automatically activated from storage. Cobweb loot can be placed during
combat and broken inside the editable arena; breaking it never produces stone rewards.

Gust Feathers give one forward dash; Leap Charms launch upward, including in midair.
Use them with right-click in the main hand after mines open. Each use consumes one
item; both share a one-second cooldown. They grant no invulnerability or fall immunity.
They stack by type, independently of rarity. Shield finds are physical loot: hold
one in either hand to block. Equipment upgrades and consumables remain random;
new potions do not change the starting kits of PvP classes.

Preparation blocks damage, pearls and wind charges. The bedrock shell is always
protected. During combat, tracked TNT destroys mineable blocks in the native blast
area, including the opened diorite partitions. Mining uses the normal ore provenance,
loot and automatic upgrade rules. A blast that kills its owner still breaks terrain,
but does not grant equipment or healing after death. Foreign explosions remain blocked.
TNT uses right-click placement or left-click throwing, with a 2.5-second fuse,
0.3-second placement / two-second throw cooldowns and at most four active charges
per player. Placed cobblestone cannot generate ore loot; carried cobblestone from
mining is capped at 128. Crafting is disabled and abandoned ground stacks are
capped at 256. Original inventories, effects and health are restored on exit.

Admin commands: `/party start miners @a`, `/party test miners`, `/party stop`.
Settings in `games/party.yml`: `miners.preparationSeconds`, `miners.combatSeconds`,
`miners.finaleSeconds` and the shared `survival.countdownSeconds`. Values apply
next session; balance still needs live multiplayer playtesting.

Party void worlds use view distance 4 and simulation distance 3; spawn chunks are
not kept loaded, and spectators cannot generate new chunks. The shared PvP/Arms
Race courtyard has 4,461 blocks instead of 9,663: the redundant bottom layer and
full-map barrier roof were removed, and the stone wall is now a low rim with a
barrier boundary. During combat, physical collisions enforce the perimeter without
per-movement teleport corrections. Arrows stuck in blocks are cleared once per
second; all tracked arrows expire after ten seconds. Arms Race removes a shooter's
arrows directly on upgrade/death, without scanning all world entities per kill.

## Playing: queues and the lobby

Every game and mode has a queue. Players join one with `/play <game> [mode]`, by
clicking a game in the lobby menu (the compass), or by clicking `[Join]` in chat. A
player is in one queue at a time.

| command | what it does |
|---|---|
| `/play` | list the queues; click one to join |
| `/play <game> [mode]` | join that queue (or spectate, if that game is being played) |
| `/play vote` | vote to start without waiting |
| `/play team <colour>` | pick a team for the queued game (team games only) |
| `/play difficulty <level\|any>`, `/play map <map\|any>` | vote on what the queued game plays: bingo's difficulty, warfare's map |
| `/play leave` | leave the queue, or stop spectating or playing |
| `/lobby` | go to the lobby from anywhere |

Once a queue has its minimum (`queues.<game>.<mode>.minPlayers`) it counts down from
60 s. The countdown only ever gets shorter: 15 s once every free player online is
queued, 5 s once the queue votes (all but one of three or more, both of two, the only
one of one). It is cancelled if the queue drops below its minimum. Leaving the server
or being AFK for 5 minutes leaves the queue.

A game type plays one session at a time, so joining the queue of a running game
spectates it, and when a game starts, everyone left in its queues (other modes,
players past its maximum) becomes a spectator. Spectators see the game's sidebar,
boss bars and chat, cannot teleport out of it, and leave with `/play leave`.

Votes (difficulty, map) are shown in the menu after joining, one after another, before the
team picker. The most-voted choice wins; ties go to the default difficulty, or a random map.

With `spectate.followPlayers` (default on), spectators and players knocked out of a game
watch the remaining players through their eyes; sneaking moves to the next one. A player
only briefly out — waiting to respawn in team beacon, a bingo death punishment — is not
moved, so they cannot scout.

A game's chat (deaths, rounds, results) goes only to its players and spectators;
the rest of the server just sees "Pillars started with 4 players" and the result.

**The lobby** is the `lobby` world. Entering it — on a first login, by queueing, with
`/lobby`, or any other way — saves the player's state and puts them in adventure
mode with an empty inventory and the menu compass. The lobby takes no damage, no
hunger and no item drops, and falling out of it lands you back at spawn. Leaving it
on your own gives everything back where you arrive. Players who queued from outside
the lobby are restored there after their game; players who queued in the lobby come
back to it. Admins are never put into the lobby state, so they can build there.

Saved states (`data/snapshots/<uuid>.yml`) survive restarts. If a game ends while a
player is offline, they get their things back when they log in.

## Admin commands

Each game's root (`/pillars`, `/bingo`, `/war`, `/chase`, `/party`) has the shared subtree.
Starting, stopping and joining games, changing teams, setting `config` values and
`reload` need `potatominigames.admin`; the rest is open to everyone.

| command | what it does |
|---|---|
| `start [mode] [players] [arena\|difficulty]` | start now, with the selector or everyone online who is not playing; see below |
| `test [mode] [arena\|difficulty\|map]` | start a test game with just yourself; see below |
| `stop` | stop this game |
| `restart [mode] [players] [arena\|difficulty]` | end the current game and start another; by default with the same players, mode and arena/difficulty |
| `join [players]` | put players into the running game, or a knocked-out player back in |
| `leave [players]` | take players out of the game |
| `list`, `points [reset]` | who's playing; standings |
| `team join <color> [players]` / `leave` / `cycle` / `swap` / `list` | saved colours, used only by games an admin starts (queued games use `/play team`) |
| `config [path] [value]`, `reload` | settings in `games/<game>.yml`; see [Config commands](#config-commands) |

`start` takes its arguments in order: the mode (games with several modes; without one,
the configured `mode`), a player selector, then a game-specific value: `/pillars start
chaos @a lodestone` forces an imported arena, `/bingo start butfast @a hard` a
difficulty (otherwise the configured one), `/war start classic @a mibc` a map.
`/chase start [players]` has neither. The
defaults are set with `config mode <value>` (and `/bingo config difficulty <value>`).

A test game — from `test`, or any game started with a single player, queued or not — awards
no points and does not end for the last one standing: warfare runs until you die or `stop`,
team beacon allows one team, and alone in chase you hunt every round until the clock runs
out. It is marked "(test)" in chat and the summary. To let anyone queue alone, set
`/pm config queues.<game>.<mode>.minPlayers 1`.

Pillars adds `respawn [player]`, `time add|remove|set <time>`, `info`, `arenas` and
`arena import <arena|all> [world] [radius]` (see below). Warfare adds `map add|remove|list`,
`beacon set|clear|info`, `kit save|load` and `setcenter` (see [Warfare maps](#warfare-maps)).

`/pm` is the cross-game root: `status` (games and queues), `games`, `stop [game]`,
`forcestart <game> [mode]` (start a queue's game now with whoever is in it, ignoring
its minimum and timers; without a mode, the fullest queue), `reload`,
`config [path] [value]` (`config.yml`, plus `gadgets.yml` under `gadgets.`),
`inventory <save|load|copy>`, `give <gadget>`.

## Pillars Sniper Duel

Join with `/play pillars sniper_duel`, or use `/pillars test sniper_duel` as an admin.
Pillars are 1.5 times further apart. The default item pace and time limit match
original: one drop every 5 seconds, a 5-minute game.

Each player's first five drops are random vanilla `*_log` items (including stripped
logs); the sixth is a fresh crossbow with no enchantments. Every later drop chooses
one of three categories with equal probability:

- an arrow: ordinary, spectral, or tipped with a random potion effect;
- an explosive firework rocket with a random shape, colour, trail/flicker and flight duration;
- a normal drop from the configured weighted Pillars pool, with its usual group boosts
  and enchantment chance. This category can also contain ammunition.

Forced logs, the crossbow and ammunition bypass the general item blacklist.
Drop progress is per player, survives reconnects, and resets with each new game.
This is a standalone mode, not another random Fusion modifier.

## Pillars Item Shuffle

In `item-shuffle` and every Fusion/preset with `shuffle`, each new set expires the
previous items everywhere in the arena: inventories (including armour, offhand,
cursor and crafting grids), dropped items, container contents and equipment/storage
on entities. Boats, minecarts, hanging decorations and armour stands also disappear,
so breaking them cannot recover an old item. Placed blocks stay, but their inventories empty.
Unloaded chunks are cleaned when they load, and reconnecting cannot restore an
earlier set. Ender chests are unavailable during shuffle; their persistent contents
are left intact. SUPERHOT cannot return frozen items from a previous set.

## Pillars fusion

`fusion` rolls its rules every game: chaos (random layout, chaos-speed items), shuffle (at
item-shuffle's own pace outside ADHD bursts), tactical (with shuffle, everyone gets the same
set), big_house and adhd, each with a 50% chance, perks with a 50% chance, and party or rave with 25% each. The values come from
those modes' settings. The roll is announced at the start and shown in the summary.

## Pillars ADHD / СДВГ

`/play pillars adhd` starts ordinary Pillars with intermittent **30-second bursts**.
Burst starts vary between 90 and 150 seconds apart (120 seconds on average), with
the same delay before the first burst. At the start, everyone's items are immediately
replaced with a shuffle set. Sets then change **three times as fast as item-shuffle**:
every 3⅓ seconds at the default 10-second shuffle interval. The cleanup rules above
apply to each set; placed blocks remain, but stored items expire.

During the burst, random events roll **three times as often as configured rave**
(a 10-second mean with bundled defaults, 6⅔ seconds with the server's 20-second rave).
An already faster base event rate stays faster. The remaining burst time appears
in the HUD. At the end, base item drops/shuffle and event frequency resume; events
and pending warnings created by the burst stop. Pre-existing base events continue.
Expired item stashes stay expired even if their chunk loads after the burst.
Ender chests are blocked for the entire ADHD-enabled match to prevent hiding items
between bursts; their persistent contents remain intact.

The `adhd` modifier can be added to fixed presets alongside any other modifier,
and Fusion independently rolls it with a 50% chance. To enable it on an existing
mode, including classic, use `/pillars config modes.original.adhd true` (next game).
Burst timing lives in `games/pillars.yml`: `adhd.meanSeconds` and
`adhd.durationSeconds`; these are read at game start. Item/event multipliers are 3.

```
/pillars preset save distracted adhd
/pillars preset save distracted_duel tactical big_house rave adhd
/pillars test adhd
```

## Player Pillars presets

Every player can save up to **five** named, fixed Fusion mixes. Names are shared
server-wide; anyone can play a preset, but only its owner can replace or delete it.
Ownership and the limit use the player's UUID, including across restarts.

```
/pillars preset save duel tactical big_house rave
/pillars preset list
/play pillars duel
/pillars preset delete duel
```

Choose any of `chaos`, `shuffle`, `tactical`, `big_house`, `party`, `rave`, `adhd`, `perks`.
`party` and `rave` are mutually exclusive. With no modifiers, `save` creates plain
Pillars with Fusion's base settings. Unlike Fusion, the selected modifiers always
apply: there is no random roll. Their settings and precedence are the same as
Fusion, including shuffle's fixed item pace and the live party/rave event intervals.

Use 1–24 letters `a-z`, digits, `_` or `-`, starting with a letter or digit.
Names are lowercased; built-in mode names are reserved. Saving your own existing
name replaces it without taking another slot. A preset cannot be changed or
deleted while anyone is queued for it or it is being played.

Saved presets appear immediately in `/play`, the lobby menu and command suggestions;
menus paginate when necessary. Queue starts and voting work normally. Admins can
also use `/pillars start duel @a [arena]` or `/pillars test duel [arena]` immediately,
without reloading commands. Other games and the built-in Pillars modes are unchanged.

Presets live in `data/pillars-presets.yml` inside the plugin's data folder.
Writes are atomic; a failed write leaves the previous presets intact.


## Pillars perks

Join through the Pillars mode menu or `/play pillars perks`; administrators can use
`/pillars start perks @a` or `/pillars test perks`. The mode uses ordinary Pillars
rules and item timing. The `perks` modifier can also be saved in presets, e.g.
`/pillars preset save tricks chaos perks`, and has a 50% independent chance in Fusion.
Enable it on another built-in mode with `/pillars config modes.<mode>.perks true`.
Sniper Duel keeps its five logs and sixth crossbow; perks can replace later drops.

Each item draw has a **20%** chance to become a special perk instead. Configure
`perks.dropChance` in `games/pillars.yml` for the next session. The twenty-three ordinary
perks have weight 4 each; the two legendary weapons have weight 1 each. Each
legendary therefore has a **0.213%** chance per item draw at the default setting,
four times rarer than each ordinary perk. Tactical presets share perk kinds and
ordinary item rolls, with separate item instances/charges per player.

Every perk has enchantment glint, a Russian name, instructions and remaining charges.
Active items use the main hand; normal items with the same material have no perk.

| Item | Perk and controls |
| --- | --- |
| Feather / Спасатель | Right-click for a 3×3 slime platform just below you, expiring after 10 seconds. One use. |
| Quartz block / Кубик шулера | Right-click to replace the entire latest reward, including its stack or bow/ammo bundle. Three uses. The whole unchanged reward must still be in your inventory; spent, placed, modified, stored or missing parts prevent a reroll. Receiving a new cube retains the preceding reroll target. |
| Fishing rod / Воровской крюк | Hit an opponent with the hook to transfer their complete main-hand stack, preserving metadata and remaining charges. One successful theft; an empty hand or miss costs no charge. |
| Ghast tear / Призрак | Right-click for 2.7 seconds of spectator flight/phasing. Normal play then resumes in a clear position nearby. Following players and leaving the arena bounds are blocked. One use. |
| Iron boots / Якорь | Wear to cancel knockback from three damaging enemy hits, then break. Health damage remains; removing/repairing the boots cannot refill charges. |
| Rabbit foot / Воздушный шаг | Carry in your inventory and press jump again while airborne. Three extra jumps, usable together or on separate falls; landing does not refill them. |
| Clock / Отмотка | Right-click to return to the position from 60 ticks ago. Requires three seconds of recorded history and a clear destination. Health, items and charges are never rolled back. One use. |
| Brick / Карманный домик | Right-click for a wooden house only 3×3 externally: one-player interior, two blocks of headroom, door, windows and torch. Existing blocks are preserved; a blocked footprint refuses the cast. One use. |
| Blue ice / Ледяной луч | Right-click a ray up to 32 blocks. A hit encases the opponent in protected ice for four seconds; they cannot move, attack, use items or take damage. One attempt, including misses. |
| Legendary bow / До свидания | Punch 100, one arrow included. The bow breaks after its single shot, including a miss. |
| Legendary crossbow / TNT-почта | Right-click to throw primed TNT; three shots, two-second fuse. No ordinary ammunition required. |
| Leather boots / Плотник | First equip starts a five-second timer: permanent planks appear underfoot, including over the void. Removing, stealing or re-equipping never restarts the timer. Afterwards the boots retain ordinary leather protection. |
| Blaze rod / Дезинтегратор | Hold right-click and aim continuously at the same block within 12 blocks for three seconds. Release, switching item, looking away or target replacement resets progress. Breaks even bedrock, without block/container drops. One successful use. |
| Sheep egg / Камикадзе | Right-click to summon a sheep carrying primed TNT. It chases the nearest active opponent, can leap gaps, and explodes after five seconds. The caster is never its target, but explosions can still hurt the caster. One use. |
| Ink sac / Дымовая завеса | Right-click for a smoke cloud with a four-block radius lasting five seconds. Enemies inside are blinded; the caster is unaffected. One use. |
| Fire charge / Фаербол | Right-click to throw an explosive fireball along your view. One use. |
| Carved pumpkin / Проклятая тыква | Right-click to throw a pumpkin straight along your view. A direct hit puts a carved pumpkin with Curse of Binding on an active opponent's head. Their previous helmet goes into their inventory, or drops at their feet if full. The projectile disappears on impact without exploding. One throw, including misses. |
| TNT / Атомная бомба | Right-click a block to place already primed TNT in the adjacent free cell. Four-second fuse; native explosion power 16 instead of ordinary TNT's 4, increasing blast range fourfold. It damages players and terrain, including its owner, while respecting protected perk terrain. One use. |
| Stick / Тяжёлый прогноз | Aim at a block up to 192 blocks away and right-click. After one second, nine damaging anvils fall from 24 blocks above the target in shuffled order, three ticks apart: one per cell of a 3×3 square. They land as ordinary anvils without dropping items if placement fails. One use. |
| Amethyst shard / Алхимик | Right-click a block within six blocks. All touching blocks, connected through faces rather than diagonals, turn into one randomly selected material. Containers release their contents; protected ice and liquids are excluded. Includes bedrock. Components larger than 4096 blocks or reaching unloaded chunks are refused before any changes, preserving the charge. One use. |
| Nether star / Благословение | Right-click for 60 seconds of Fire Resistance, Resistance I, Strength II, Regeneration II and Absorption IV. One use. |
| Eye of ender / Перевёртыш | Right-click to reverse only your own gravity for three seconds, then recharge for fifteen seconds. Reusable; copies and item transfers cannot bypass the user's cooldown. Normal gravity is restored on expiry, elimination, disconnect and stop. |
| Oak planks / Блок-обманка | Four right-click placements. Looks like ordinary planks to everyone, but the world remains air and players fall straight through. Build within six blocks, including extending from another fake block's visible face. Left-click a fake to remove it without drops. Real construction replaces the visual; remaining visuals disappear when their builder leaves or the match stops. |
| Sugar / Подозрительные таблетки | Right-click to affect one random active opponent, never yourself. For 60 seconds only that player hears phantom footsteps, landings, attacks, arrows or a TNT fuse from random positions nearby, at four-to-six-second intervals. No damage or actual entities. Reapplication refreshes one schedule instead of stacking; death, disconnect and match end clear it. No eligible opponent preserves the charge. One use. |
| Zombie egg / Двойник | Right-click a nearby block to summon a stationary mannequin with your skin, name, armour and held items. Opponent melee/projectile hits trigger one small explosion: power 1.5, damage capped at two hearts before armour, no fire or terrain damage, owner immune. Owner hits and environmental damage never trigger it. Copied gear cannot be removed and never drops. One egg; at most two active copies per owner, lasting 60 seconds or until owner exit/match end. |

Special effects, entities, history and pending block leases are scoped to the match
and cleaned on stop. Temporary terrain never overwrites existing blocks or deletes
a player's replacements when expiring. Freeze blocks resist breaking, melting,
pistons and explosions. Charge/timer metadata follows dropped or stolen items.
Shuffle replaces perk inventory along with ordinary inventory; already running
timed effects keep their original deadlines. Spectator mode is restored on exit,
disconnect and stop; reconnecting never grants extra charges.

For live testing, admins in a running perks match can use `/pillars perk <type>`:
`save`, `reroll`, `steal`, `ghost`, `anchor`, `jump`, `rewind`, `house`, `freeze`,
`bow`, `crossbow`, `planks`, `break`, `sheep`, `smoke`, `fireball`, `pumpkin`,
`atomic_bomb`, `anvil_rain`, `transmute`, `blessing`, `gravity`, `fake_block`, `pills`, `decoy` (tab completion).
This grants the item to the command sender after the countdown. Multiplayer
balance and client interactions still require live playtesting.

## Pillars events (party, rave)

`party` and `rave` add random events: every second one starts with the same chance, so they come
every `eventMeanSeconds` on average (60 in party, 30 in rave, which also drops items as fast as
chaos). Each is picked evenly among those not already running. Most events are announced 5s ahead.
"Moving" means having moved within the last 10 ticks, so lag does not count as standing still.

| event | lasts | what happens |
|---|---|---|
| Boat Rain | 15s | unbreakable boats fall from the sky; afterwards riders get a slow fall and boats vanish |
| Disco | 30s | standing still hurts (1 heart more each time) and darkens the screen for 3s |
| Block Ban | 7s | a slot-machine subtitle picks a placed block type (the pillars' own, rarely); all of it is removed and it stops dropping |
| Stiff Neck | 20s | the view is locked left-right; up-down still works |
| Swap | — | two or three players trade places immediately, without a warning countdown |
| Low Gravity | 10s | gravity at a quarter |
| Giants & Gnomes | 20s | everyone at double or half size |
| Slippery Floor | 20s | placed blocks turn to packed ice |
| Spotlight | 15s | everyone glows |
| Hangover | 15s | nausea III, slowness II and a flash of wither |
| Wind | 60s | a steady push in one direction, shown as an arrow; sneaking holds on |
| Knockback Party | 20s | a Knockback II stick for everyone, taken back after |
| Item Frenzy | 15s | items three times as often |
| Hot Potato | 20s | a TNT passed on by hitting someone; it explodes on whoever holds it at the end |
| Darkness | 15s | darkness |
| Vanishing Blocks | rest of game | placed blocks disappear after 15s |
| SUPERHOT | 30s | standing still takes your items away until you move |
| Airdrop | — | a chest with a row of random items appears between the players, 3 to 15 blocks above the pillars |
| Contract Ad / Реклама | 5s | a large central title advertising contract service flashes in different colours; other event titles are suppressed while the ad is visible |
| Math / Контрольная | 10s | solve a small addition, subtraction or multiplication problem by typing the integer answer in chat; each living player who has not answered correctly takes 3 hearts of damage at timeout |

Math questions appear in chat and in the action bar with a countdown. Wrong answers
allow retries until the deadline; spectators and eliminated players cannot answer
or receive the penalty. Both events also enter Fusion and player presets whenever
the party or rave modifier is active. Ending the game clears the ad and cancels
the question without dealing damage.

## Pillars arenas

Every pillars game picks a random arena from `games/pillars.yml`, unless `/pillars start`
names one, and plays in a fresh in-memory copy of that arena's slime template
(`slime_worlds/pillars_<arena>.slime`), which is thrown away afterwards. Templates keep the coordinates of the world they were
cut from, so an arena's `x`/`z` stay valid.

To create or rebuild templates, build the arena in a normal world and run
`/pillars arena import <arena|all> [world] [radius]` (default world `pillars`, radius
64): it copies the chunks within the radius of each arena's center into its template.
An arena with nothing in it becomes an empty (void) template. `/pillars arenas` shows
which arenas have a template.

## Warfare maps

Warfare can rotate between maps. Each is a slime template (`slime_worlds/<template>.slime`,
imported with `/swp import`) plus a center, a radius players spawn within, and its own team
beacons; every game plays in a throwaway copy, so damage never persists. Queued players vote
for the map (`/play map`), and `noHunger` (default on) keeps their food full.

```yaml
maps:
  mibc:
    template: mibc
    center: 880.0 4.0 760.0
    radius: 250
    beacons:            # team beacon mode needs two; set with /war beacon set <team>
      red: 900.0 5.0 700.0
```

In game: `/swp goto <template>`, stand at the center, `/war map add <name> [radius]`; then
`/war beacon set <team>` while looking at each beacon spot. Team beacon mode offers only
the maps with two beacons, and only the colours every such map has. `/war map list` shows
them all. Without any map, warfare plays in `gameWorld` around `gameCenter`, as before.

## Gadgets

The former hide-and-seek weapons, now game-agnostic: `infinite-crossbow`,
`oreshnik`, `himars`, `locator`, `fpv-drone`, `radar`, `grapple-bow`.
Each has an `allowOutsideGame` config (default true, except oreshnik); gadgets never
work in the lobby.

## Config layout (plugin data folder)

```
config.yml                    core, lobby, queue timers, per-queue limits, asp world reset
games/pillars.yml             pillars modes, platform, item pool and groups, arenas
games/bingo.yml               mode, difficulty (+ difficulty-levels table), timings
games/bingo-board-entries.yml the crafting-cost node graph behind board generation
games/warfare.yml             mode, arena/border, distances, lifts, noHunger, teamBeacon, maps, beacons, kit
games/chase.yml               rounds, hunters, timings, build limits, hunterGadgets
games/party.yml               PvP/sumo/Arms Race timings, survival countdown, TNT Run, mob counting, ravagers/mines, Miners
games/finals.yml              match, vault, cashout, steal and respawn timings; cashout value and winning score
gadgets.yml                   one section per gadget
data/                         points.yml, teams.yml, inventories.yml, snapshots/, pillars-presets.yml
```

Values are edited at runtime with the `config` commands below and written back to the files.

The running server overrides the bundled party/rave event intervals to 40/20 seconds
on average. To apply the same settings:

```
/pillars config modes.party.eventMeanSeconds 40
/pillars config modes.rave.eventMeanSeconds 20
```

Worlds, player data and separately installed plugins are not included in this repository.

## Config commands

`/<game> config` edits `games/<game>.yml`; `/pm config` edits `config.yml` and, under
`gadgets.`, `gadgets.yml`. A path is dotted, mirroring the YAML, and tab-completes a
segment at a time.

```
/pillars config                              # the whole file; click a value to edit it
/pillars config modes.chaos                  # one section
/pillars config modes.chaos.layout random    # set: prints `path: old → new`
/pillars config modes.chaos.layout default   # reset a declared setting, removing it from the file
/pillars config arenas.lodestone.time night
/pillars config items.blacklist add bedrock  # flat lists: add / remove <entry>
/pm config queues.pillars.chaos.maxPlayers 12
/pm config gadgets.himars.cooldown 5
```

Settings the code declares apply at once; other keys (arenas, item weights, lists) are
re-read by the next game. Structured sections are hidden and edited in the file:
pillars `items.groups` and `items.custom`, bingo `difficulty-levels`, warfare `maps`
(`/war map`), `beacons` (`/war beacon`) and `gameInventory` (`/war kit`). Each game declares its hidden prefixes
with `ConfigRegistry.hide` next to its config registration.

## Counter-Strike — standalone bomb defusal

`cs` is a separate category in the main menu, outside Party Games. Join with
`/play cs`; administrators can start selected players with `/cs start @a`.
`/cs test` starts the framework's practice session (one player is enough, $16,000
per round, no elimination win or saved points). The normal queue admits 2–10
players, balances red/blue teams and supports up to five players on each side.

The initial map is **[LesRaisins | CSGO] Dust II** by xjqsh / xjqshm and
LesRaisinsStudio, imported from the published Java world, rather than generated
by the plugin. Original map: Valve.
[Source and author’s usage terms](https://www.curseforge.com/minecraft/worlds/lesraisins-csgo-dust-ii).
The map has its own **non-commercial, attribution-required terms**, separate from
the code's Apache-2.0 license. Preserve the bundled notices; commercial use needs
the map author's authorization. The pinned archive, checksum and conversion
instructions are in `assets/maps/lesraisins-dust2/`. Only block geometry is loaded;
source entities, player records and executable world data are excluded.

### Rules and economy

- CS2-style MR12: first to 13 rounds, up to 24 rounds; 12:12 is a draw.
  Sides swap after round 12, with an equipment and $800 economy reset.
- Each round has 15 seconds of frozen preparation and 115 seconds of play.
  Buy at your side's spawn during preparation and the first 20 seconds of play.
  Players have 100 logical HP, no regeneration and no respawns until next round.
  Fall damage is disabled in both Classic and Deathmatch.
- T carry one C4 and plant at the imported A/B sites while stationary for 3.2
  seconds. The fuse lasts 40 seconds. CT defuse in 10 seconds, or five with a kit.
  Moving, releasing the button, switching slots or dying interrupts the action.
  Eliminating all T does not end a planted round; CT must still defuse or escape.
  Nearby players hear positional plant/keypad sounds (36-block radius), the start
  and completion of a defuse (66 blocks), the plant-finish cue (136 blocks) and
  ticking (44 blocks). These are maximum ranges with native distance fading,
  calibrated against A--B and A--CT Spawn landmark distances rather than a
  one-to-one unit conversion. Starting and cancelling a defuse produces a fake
  defuse cue; holding use does not replay it. Plant keypad clicks stop on cancel,
  and ticking stops immediately after defuse or round end. See
  [Dust II audio calibration](assets/maps/lesraisins-dust2/AUDIO-Dust2.md).
- Cash starts at $800 and is capped at $16,000. Kill rewards depend on weapon,
  wins pay $3,250 or $3,500 for C4 outcomes, and loss payouts grow from $1,900
  toward $3,400. Winning reduces the loss counter by one. Planting/defusing has
  individual rewards; a planted losing T team gets an extra $600. T who survive
  a timeout receive no loss payout. Team damage is reduced, and teamkills cost
  money and a kill. Assists and round MVPs are recorded.
- Equal-sized teams receive the full 5v5 payout **per person**, including in 1v1,
  2v2, 3v3 and 4v4. Their total team income is naturally lower because fewer
  players receive the reward: a $3,250 round award totals $6,500 for two players
  and $16,250 for five. Only the smaller side gets an additional
  `largerTeam / ownTeam` multiplier per player: in 2v3 the two-person side gets
  150% and the three-person side gets 100% of the same base reward. This applies
  to kills, wins, losses, plant/defuse and the planted-loss bonus. Pistol-round
  cash stays $800 on equal sides and receives the same smaller-team compensation
  ($1,200 vs $800 in 2v3). Shop prices, the $16,000 cap and $300 misconduct
  penalties stay fixed. Counts are snapshotted from online participants at round
  start, never from survivors or mid-round disconnects. Practice retains its
  $16,000 allowance.
- Survivors keep guns, remaining armor, kits and unused grenades; gun ammunition
  restocks at round start. Death drops guns with their actual remaining ammo,
  utility, kit and C4. Rejoining never grants a mid-round respawn. A team absent
  for 60 seconds forfeits. `/cs pause` requests one of four 30-second team pauses.
- Dead players get a short death screen, then spectate teammates only; sneak to
  switch teammates. The result lists K/D/A and MVPs. Winners receive three saved
  points, drawn teams one; practice gives no points.

### Weapons and controls

The arsenal contains **27 guns**, plus the knife:

| Category | Weapons |
| --- | --- |
| Pistols | Glock-18 (T), USP-S (CT), P250, Dual Berettas, Five-SeveN (CT), Tec-9 (T), CZ75-Auto, Desert Eagle |
| SMGs | MAC-10 (T), MP9 (CT), UMP-45, MP7, P90 |
| Assault rifles | Galil AR (T), FAMAS (CT), AK-47 (T), M4A1-S (CT), M4A4 (CT), AUG (CT), SG 553 (T) |
| Sniper rifles | SSG 08, AWP |
| Shotguns | Nova, XM1014, MAG-7 (CT), Sawed-Off (T) |
| Machine gun | Negev |

Faction restrictions apply to purchases, not captured weapons. The buy menu has
54 slots on one screen. Colored columns group pistols, SMGs, rifles, snipers,
shotguns and machine guns under category headers; pistols, rifles and shotguns
use pairs of columns. Weapons are ordered by ascending price within each group,
left to right and then down. The bottom row holds armor and grenades. All legal
weapons stay visible together. Weapon entries show price, ammunition,
category, damage per bullet/pellet, armor penetration and rate of fire. Every gun
has its own price, magazine/reserve, fire/reload timing, mobility and spread.
Purchase prices and the planted-loss award follow the verified CS2 economy updates:
M4A4 $2,900, FAMAS $1,950, MP7 $1,400, CT incendiary $500, and $600 for a
planted losing T team. Armor refill costs $650 when the helmet is already owned;
a helmet-only upgrade with full armor costs $350. Shop quotes and debits use the
same equipment price calculation. Sources: Valve's
[May 2024 update](https://store.steampowered.com/news/posts/?appids=730&enddate=1716500781&feed=steam_community_announcements),
[January 2025 update](https://store.steampowered.com/news/posts/?appids=730&enddate=1738107723&feed=steam_community_announcements), and
[January 2026 update](https://store.steampowered.com/news/posts/?appids=730&enddate=1769472539&feed=steam_community_announcements).
Fire timing, accuracy and movement remain Minecraft adaptations; this is not exact
parity with every current CS2 weapon statistic. The default match format is MR12.

The CS lobby category opens three modes: **Classic** (queue ID `default`, keeping
existing queue configuration and `/play cs` compatible), **Deathmatch**
(`/play cs deathmatch`) and **Wingman** (`/play cs wingman`). Wingman uses exactly
Classic's round format, economy, equipment and team limits (up to five per side,
not restricted to 2v2). Only map access differs: A is the only bombsite; Long and
Short remain open. The B route closes immediately north of T spawn, the lower
CT-to-Mid/B exit closes below Short, and tunnels and Mid doors are sealed.
Visible red glass marks blocked passages, with invisible clipping above it;
gates cannot be destroyed or penetrated by gunfire. All twenty possible starts
are inside the playable area and checked for native collision before play.
See [Wingman map boundaries](assets/maps/lesraisins-dust2/WINGMAN-Dust2.md).
Deathmatch supports
2–16 players on Dust II, with a five-second countdown and a five-minute match.
Every player is an opponent; kills rank first and fewer deaths breaks ties.
Players respawn after three seconds at a walkable point selected away from living
opponents. Each spawn grants three seconds of protection, ending when the player
shoots or swings the knife. Spawn candidates are derived from connected walkable
surfaces in the imported blueprint, seeded from the classic starts and bombsites.

Deathmatch grants an AK-47, USP-S, vest and helmet; all guns from either faction
are free in a single categorized weapon-selection screen. Open it with `/cs buy`
or right-click the radar; `/cs score` shows individual standings. Loadout choices
persist across lives. A choice applies immediately during countdown or spawn
protection; in combat it is saved for the next respawn, preventing free mid-fight
magazine refills. Magazines and reload timing remain normal, with unlimited reserve
ammunition. There is no C4, utility, economy, round elimination, team spotting,
team chat or allied glow in this mode. Rejoining participants respawn with their
existing statistics. `deathmatch.durationSeconds` and `deathmatch.countdownSeconds`
in `games/cs.yml` configure the timer and countdown. Non-test matches award 3/2/1
points by place to players with kills; equal kills/deaths share a place.

Round starts use a CS2-style pool of fifteen T Start positions and five CT Spawn
positions by the passage under A. Each side's pool is shuffled once per round,
so teammates get distinct slots. Positions and buy areas are fitted to the
existing Minecraft map geometry; the original map architecture stays intact.
See [spawn references and adaptation](assets/maps/lesraisins-dust2/SPAWNS-Dust2.md).

The action bar continuously shows the selected gun's magazine/reserve and the F
reload hint. During reloading it shows tenths of a second remaining until the
entire reload completes, including all remaining shotgun shells. Brief notices
share the ammo line; planting/defusing progress takes priority.

Kill messages include the lethal weapon and whether the killing hit was a
headshot. Knife, HE, Molotov/incendiary and C4 deaths use their own source labels;
earlier nonlethal headshots do not mark a later body-shot kill as a headshot.

There are finite magazines/reserves, draw times, held automatic fire,
burst recoil/recovery, movement/jump spread, crouch accuracy, head/body/leg
damage, armor/helmet penetration, hit tagging and aim punch. AWP and SSG 08
have scoped accuracy; SSG is cheaper and faster to move with,
but an armored body hit is survivable. AUG and SG 553 trade mobility for scoped
accuracy at range. Their scope toggles with left-click, while right-click keeps
firing. Zoom uses a transient movement-attribute modifier and compensated walk
speed: approximately 0.555× normal FOV for AWP, 0.6196× for Scout and 0.578× for
AUG/SG. Walking follows each weapon's CS2 scoped speed: AWP 100/200, Scout
230/230, AUG 150/220 and SG 150/210, rather than a universal 45% multiplier. Vanilla client FOV Effects must be
enabled for visible zoom; their strength scales the result. No spyglass use is
forced: a vanilla client releases it as soon as the use button is not held, and
holding a spyglass consumes attack input. Scoped gun icons remain non-usable,
preventing that native interaction. One click's overlapping interact/attack/swing events are
debounced; reload, slot switch, gun drop, death, menus, leaving and round/match
cleanup remove both scope state and the temporary modifier. All guns trace up to 256 blocks, covering Dust II; distance
falloff and spread determine effectiveness instead of a 100-block rifle cutoff.
Damage falloff is continuous and compressed to 64% of the original distance
scale: `rangeFalloff ^ (distance / 10.24)`. A shot at 64 blocks now retains the
same damage as an original shot at 100 blocks. At 100 blocks, ordinary
pistols/rifles retain about 82% damage, M4A1-S 55%, most SMGs 26%, UMP-45 6%,
shotguns 3% and snipers 91%. Point-blank base damage is unaffected.
Visible trails are thin, full-bright yellow `BlockDisplay` streaks covering the
actual hitscan path from half a block ahead of the eye to the hit target, stopping
at unpenetrated cover (maximum 256 blocks). They are 0.04 blocks thick and last
three ticks (about 150 ms at 20 TPS); all parts appear immediately, without
changing hit registration. Only the first pellet of a shotgun blast is drawn.
Displays are created without spawning world entities; bundled spawn/metadata
packets go directly to current match viewers in the same world, bypassing the
old particle delivery radius and server entity tracking range. Client display
view range is 512 blocks and midpoint frustum culling is disabled for long and
vertical streaks. Normal terrain depth occlusion still applies: they have no
glowing outline through walls. Client render settings may still limit visibility.
At most 128 streaks remain active per match; expiry and round/match cleanup send
removal packets. The lethal shot retains its normal lifetime during the round-end
loot period. Packet bindings target the supported Paper 26.2/26.3 runtimes.
USP-S and M4A1-S are quieter and omit visible bullet trails; M4A1-S trades magazine
size and fire rate for accuracy. CZ75-Auto fires automatically, but has only one
reserve magazine, a longer draw time and a $100 kill reward. P90 has 50 rounds
and the normal $300 kill reward; other SMGs pay $600. Shotguns lose damage faster
at distance: Nova, XM1014 and Sawed-Off load individual shells and can interrupt
reload to fire; MAG-7 replaces a whole magazine. XM1014 supports held fire.
Negev is slow and takes a long time to reload, with a 150-round belt; its initially
wide spread stabilizes on the twelfth uninterrupted shot, and resets after an
idle gap. Limited wallbangs lose damage; breakable glass is restored each round.
FAMAS currently uses automatic fire only; Glock burst mode and removable
suppressors are not implemented.

HE, flash, smoke and Molotov/incendiary grenades have two throw modes.
**Right-click** is a short potion-like toss: speed 0.5 blocks/tick, gravity 0.05,
a -20-degree pitch offset and 0.99 air drag. **Left-click** is a long snowball-like throw:
1.5 blocks/tick initial speed, 0.03 gravity and 0.99 drag, with no artificial upward
kick. The throw inherits horizontal motion and airborne vertical motion, starts
0.1 blocks below the eye without skipping cover, and retains collision bounces
and the utility fuse. Both modes share the existing 0.8-second throw cooldown,
so overlapping click/swing events cannot consume a second grenade;
HE respects cover, flash duration depends on distance and facing, smoke hides
players while allowing bullets through, and smoke extinguishes fire. Molotov
zones render animated fire blocks over supported floor cells within the existing
radius and line of sight. These are batched client-only block changes, also shown
to spectators: the server terrain, native ignition and combat calculations are
untouched. Overlapping zones share their visual cells; extinguishing, expiry,
leaving and round/end cleanup restore the current real block data. Active visuals
refresh after chunk loads. Smoke's dense
large-particle cloud also applies Darkness while the player’s eyes are inside.
Leaving clears smoke Darkness without cancelling a longer flash effect. Flash
particles include the color data required by Paper 26.2/26.3. Carry at
most four grenades, with at most two flashes and one of each other kind.

| Input / slot | Action |
| --- | --- |
| Right-click | Fire / short grenade throw / heavy knife attack |
| Left-click | Long grenade throw / quick knife attack / toggle scope on AWP, SSG 08, AUG or SG 553 |
| F / `/cs reload` | Reload |
| Q / `/cs drop` | Drop selected weapon, grenade, kit or C4 |
| Slots 1–2 | Primary and secondary gun |
| Slot 3 | Knife; backstabs deal more damage |
| Slots 4–7 | HE, flash, smoke, fire grenade |
| Slot 8, hold right-click | Plant or defuse; look at C4 within three blocks to defuse |
| Slot 9 | Native map radar; right-click at spawn for buy menu |
| `/cs buy`, `/cs help` | Buy menu / controls |
| `/cs score` / sneak + right-click with radar | Live roster, alive/dead/disconnected status, K/D/A and MVPs |
| `!message` | Private chat to current teammates, including dead teammates |

After a classic round ends, survivors have five seconds to move around and
collect dropped equipment. Q or `/cs drop` frees an occupied gun slot so a
replacement can be picked up. Survivors keep the collected equipment for the
next round; freeze time starts at the next spawn. Halftime still resets equipment.
The round winner also appears for two seconds as a bold centered subtitle:
green for the winning team's viewers, red for opponents, and gold for neutral
spectators. It names the winning T/CT faction and follows the halftime side swap.

Semi-automatic pistols require a separate right-click for each shot. Holding RMB
fires once and stays latched until release, including through a reload. The CZ75
retains automatic held fire. Native use tracks the real release event without a
client mod, hand animation, consumption sounds, particles or movement slowdown.
The normal weapon cadence still limits rapid individual clicks.

Rapid accepted shots also accumulate a separate inaccuracy penalty. The first
settled shot keeps its existing baseline spread; rapid pistol taps and automatic
sprays become less accurate. Penalties decay continuously with time, including
while holstered, so paced shots recover sooner than sustained fire. Switching
weapons does not instantly reset accuracy. Scoping reduces the penalty without
removing it; crouching, movement and airborne modifiers still apply. Each weapon
family has its own accumulation/recovery rates, with slower Deagle recovery and
Negev's existing sustained-fire stabilization retained. One shotgun trigger pull
adds one penalty, regardless of pellet count. New rounds reset inaccuracy.
These rates are tuned for Minecraft rather than exact CS2 numeric conversions.

Held-weapon movement uses the CS2 `m_flMaxSpeed` values relative to knife/C4 at
250: light pistols and MP9/MAC-10 240, Deagle/UMP/P90/Scout 230, M4s/MAG-7 225,
MP7/FAMAS/AUG/Nova 220, AK/Galil/XM1014 215, SG/Sawed-Off 210, AWP 200 and Negev
150. Grenades use 245. Negev slows to half of its held speed while firing.
These ratios retain the arena's existing base movement scale. Slot switches apply
the new speed immediately during play and the post-round loot window, without
unlocking freeze time. See [CS2 movement reference](assets/maps/lesraisins-dust2/MOVEMENT-Dust2.md).

Movement adds a separate speed-dependent spread penalty. Each of the 27 guns has
its own running penalty: MP9/MAC-10 and Tec-9 are more forgiving while moving,
Deagle and rifles need steadier positioning, and AWP is strongly penalized.
Slow movement increases spread less than full-speed movement; scope does not
remove that penalty. Horizontal travel is measured from accepted movement events,
including multiple packets per server tick, rather than only knockback velocity.
Stationary players recover movement accuracy within two ticks; teleporting,
respawning and round reset clear the movement history. Vertical motion retains
its separate airborne penalty. Vanilla sprint remains disabled as before; the
normal full-speed CS movement and slow crouched movement use their actual speeds.

Shots are positional sounds emitted at the shooter's eye/weapon height, with
native stereo direction and distance fading. Maximum audible ranges are 128 blocks
for unsuppressed pistols, 144 for SMGs/shotguns, 176 for rifles/machine guns and
192 for snipers. USP-S and M4A1-S use a short subdued click within 36 blocks.
Only match viewers in the same world receive the cue. A shotgun emits one shot
sound per trigger pull, rather than one per pellet; dry fire emits no shot sound.
These are Minecraft gameplay ranges, not claims of exact CS2 gunshot curves.

Drop your occupied gun slot before walking over a replacement. The radar uses the
actual imported map: teammates are always shown, enemies only when a living
teammate sees them, with a brief stationary last-seen marker. Smoke and flashes
prevent spotting; CT learn the bomb location after seeing it. Ordinary Minecraft
nameplates are visible only for teammates, including after halftime; enemy names
remain hidden. Native player outlines are sent only to allies, refreshed
every 0.5 seconds and suppressed across smoke or while flashed; enemies and
outsiders never receive the override. Outlines are cleared on leaving, round reset
and match end. The sidebar shows T/CT alive counts; `/cs score` lists each
player's status, with cash visible only to their own team. Team chat uses the stable
team assignment across halftime, accepts literal text and leaves ordinary global
chat unchanged. Players wear a full red or blue leather uniform matching their
stable team, including after halftime. Purchased kevlar shows an iron chestplate;
a purchased helmet shows an iron helmet. Leggings and boots retain the team
color. A depleted vest returns to the leather appearance; the owned helmet stays
visible until equipment resets. All uniform pieces have explicit empty native
attribute modifiers and are unbreakable: they add no native armor protection.
Protection, penetration, durability and fire damage remain exclusively in the
existing CS rule model. Controls, gun visuals, movement and flash rendering are
Minecraft adaptations; this does not reproduce the Source client, its netcode,
resource-pack weapon models or every CS game mode/weapon.

Round timings and half length are configurable in `games/cs.yml` and `/cs config`.
On upgrade, the old unversioned `roundsPerHalf: 15` default migrates once to 12;
other customized half lengths remain intact. `formatVersion: 2` records that
migration, so later admin edits are preserved across restarts.
The imported architecture is protected from building/destruction except bullet
breakable glass. `checkCounterStrike` covers the rule model, ammunition, economy,
objective deadlines, rounds, reconnects, smoke geometry and imported spawn/site
metadata; the normal `check` includes it. An actual Paper/client match is still
needed to validate controls, collision, camera and map rendering together.

## Localization and contributor rules

The built-in interface supports **English and Russian** without a resource pack.
Russian client locales use Russian; other client languages and the console use
English. User-authored names, command identifiers and configuration values remain
unchanged.

All localization infrastructure lives in **`i18n/`**: Java code in `i18n/java`,
checks in `i18n/checks`, and UTF-8 catalogs in `i18n/locales/en` and
`i18n/locales/ru`. `i18n/locales/catalogs.txt` declares the domains; for example,
`party.rounds` maps to `en/party/rounds.yml` and `ru/party/rounds.yml`.

**Every contribution that adds or changes built-in player text must update both
language catalogs in the same change.** This includes command errors/help, chat,
titles, action bars, boss bars, sidebars, menus, item names/lore and summaries.

Use stable semantic keys and full sentences with MiniMessage component arguments:

```java
I18n.tr("common.summary.winner", player.displayName())
        .color(NamedTextColor.GREEN);
```

```yaml
# i18n/locales/en/common.yml
summary.winner: "Winner: <arg:0>"
# i18n/locales/ru/common.yml
summary.winner: "Победитель: <arg:0>"
```

- Use flat YAML keys and quote text values. Keep the same keys and argument indices
  in both languages; indices start at zero without gaps. Translations may reorder
  arguments. Close MiniMessage formatting tags explicitly.
- Pass numbers, user text and rich Components as arguments. Do not concatenate or
  stringify translated Components, parse user input as MiniMessage, or add new
  built-in prose with `Component.text("...")`.
- Keep command/config/PDC IDs and player/preset/map names literal. For vanilla
  item or entity labels, use native Minecraft translation keys. Server-free model
  initializers must not call registry-dependent `Material.translationKey()`.
- Default item-component definitions can use
  `{translate:"potatominigames.<domain>.<key>"}` for names; Java calls to `I18n.tr`
  omit the internal `potatominigames.` prefix.
- Paper renders translated chat, titles, action bars and boss bars per recipient.
  Render inventory titles with `I18n.render(title, viewer)` and fresh item metadata
  with `I18n.item(item, viewer)`. Never render a shared template in one player's
  language. Rebuild item views from canonical templates on recipient handoffs.
- Pass untranslated Components into `SidebarService`: it localizes each player
  and spectator independently. Shared logical boards must still have per-viewer
  inventory presentations.
- For localized validation errors use `I18n.exception(key, args)` and preserve the
  Component with `I18n.message(error)` when reporting it.

Run `sh ./gradlew checkI18n` and the affected gameplay checks before submitting.
The normal `build` includes the i18n gate: catalog parity, placeholder integrity,
locale fallback, safe rich arguments and malformed-catalog rejection.

## Building

```
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon'            # 26.3
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon -Pmc=26.2'  # 26.2
```

The jar name says which API it was built against (`potato-minigames-<version>-mc26.3.jar`).
The 26.2 build exists for the bot-tested dev server; the servers run 26.3.

`build` runs self-checks without adding a test framework: preset ownership, quota,
reload and failed-write preservation, plus Sniper Duel's opening sequence and equal
later drop categories. Run them alone with `sh ./gradlew checkPillarsPresets checkSniperDuel`.
`checkPartyPvp` also checks round-robin schedules for 2–12 players, BO3, forfeits,
personal-meeting ties, title contenders, random class draws, arena symmetry and
player-block reset without a running server.
`checkPartySumo` checks platform geometry, safe spawns, ring-outs, overtime shrink
and complete restoration, protected terrain, knockback symmetry and hit cooldowns.
`checkPartyArmsRace` checks FFA weapon progression and final victory, duplicate
death and stale-shot handling, respawn/reconnect progress, timeout standings and
all sixteen spawn points, including the farthest-from-enemies selection.
`checkPillarsMath` checks generated arithmetic, independent player answers, retries,
elimination and one-shot timeout penalties.
`checkPillarsShuffle` checks dropped items and container stashes, cursor/crafting
cleanup, entity equipment/storage, unloaded chunks, reconnects and ender-chest isolation.
`checkPillarsPerks` checks all twenty-five weighted outcomes and rarity, atomic TNT power/fuse/owner,
decoy enemy-only one-shot triggers, damage cap and owner/terrain immunity,
shuffled 3×3 anvil plans, face-connected and atomic transmutation, gravity duration/cooldown,
fake-block placement/chaining/removal without terrain writes, opponent-only phantom selection and sound timing/expiry, exact rewind/channel timing,
whole-reward counts, real temporary block writes/restoration and cage leases, tiny house
geometry and queue/preset integration.
`checkPillarsAdhd` checks jittered start intervals, exact burst duration and triple
shuffle speed, configured rave frequency, and cancellation of burst-only event tasks.
`checkPartySurvival` checks TNT geometry and collapse deadlines, exact 2×2 platforms
and two-block gaps, unique mob-count answers, landing deadlines, ravager swept
collision/difficulty, fixed mine fuses through pickup/throw/drop, and elimination ties.
`checkPartyRuntime` checks countdown locking, respawn/result pauses and projectile
cleanup. `checkPvpClasses` checks class equipment, finite stock and combat abilities.
`checkPartyGroupCombat` checks group menu/queue integration, actual clear circular
spawns for 1–16 players, all random classes, one-life outcomes, simultaneous deaths
and withdrawal/reconnect rules.
`checkPartyMiners` checks Party menu/queue integration, equal ore quotas for 1–12
players, sliced construction/opening, protected shells and unused cells, single-use
ore rewards, random loot grades and ammunition, phase timing, capped extra hearts,
TNT spawn clearance and bounded ground-item cleanup. It also checks perk mining
geometry, seven eligible perks, the 50/40/10 lucky distribution, finite personal
perk budgets, the 10% totem roll, 80/20 gold hearts and equipment replacement plans
that preserve a drill even when the inventory is full.

`checkParkourRace` checks seven or eight distinct tasks sampled from the full twenty-four-task
pool, identical sealed lanes, build budgets, sequential grounded checkpoints,
elytra bypass rejection, finite task kits, both orientations of the flower-pot
staircase, lethal-clutch resets, construction rollback and Party Games/queue
registration. It runs as part of `build`.

`checkPvpClasses` additionally verifies Shadow's finite invisibility stock, leather
loadout, client-only armour hiding/restoration, equipment changes and viewer tracking,
independent cloaks, on-hit wand cooldown and blocked-hit rejection, alongside all
210 distinct opening class matchups.

`checkFinals` checks cash-box ownership, deposits, theft, payouts and match results.
`checkFinalsSession` exercises combat attribution, protection, interrupted steals,
respawn/reconnect handling, game-master isolation, team wipes, frozen movement and TNT routing
through event handlers. `checkFinalsEquipment` and `checkFinalsLoadouts` cover class
restrictions, localized equipment and saved choices. `checkFinalsTeamWipes` covers
shared deadlines and keeps duo wipes limited to 2v2; `checkFinalsRevives` covers persistent downed statues, manual
channels and charged Defibrillator reconstruction. `checkFinalsCashboxFlight`
checks swept throw-to-deposit collisions.
`checkFinalsArena` checks the generated arena's protected objectives, safe spawns,
traversable layout and destructible cover. All run as part of `build`.
The live Paper fixtures in `native-checks/finals` additionally cover actual 2v2/3v3
respawns, five-player spectator event controls, private Tab packets, and
`FinalsGameEventsSmoke` checks gravity restoration, telegraphed impacts and protected
map boundaries against native attributes, blocks and ray tracing.

## License

Apache-2.0 (see `LICENSE`). Pillars is derived from PillarPeril by MarcPG, also
Apache-2.0; see `NOTICE`.

## Throwaway worlds (Bingo and Chase)

Bingo and Chase never touch a persistent map. Each game generates a brand new
world with a random seed, plays in it, and deletes it afterwards:

- Worlds are named `potato_tmp_bingo` / `potato_tmp_chase` (plus `_nether` and
  `_the_end`). Nothing outside that prefix is ever unloaded or deleted.
- Bingo starts at the origin, so strongholds — and therefore the End — sit where
  vanilla would put them. Its nether is created up front; the End only when
  someone first walks into an end portal.
- **Saving is left on**, so the world behaves exactly like a normal one while the
  game runs: chunks that unload are written and come back with the players'
  changes intact when someone returns. Deleting the folder at teardown is what
  keeps the server clean — a game costs a few hundred MB of disk that is then
  reclaimed. (Disabling saving would either regenerate roamed-away areas or pin
  every visited chunk in memory for the whole game.)
- Leftovers from a crash are swept on plugin enable. The sweep searches by folder
  name rather than a fixed path, because the storage layout is version-dependent:
  Paper 1.21.9+ nests non-primary worlds at
  `world/dimensions/<namespace>/<name>` instead of the world container.

## Slime-world arenas (ASP)

Warfare can play in a throwaway copy of a template world, so terrain damage never
persists — the game world is re-cloned from the template at every start.

Server-side setup (once), matching how production is configured:

1. Put `asp-plugin-<version>.jar` in `plugins/` (the ASP server jar alone is not enough —
   the plugin is what loads worlds at startup and provides `/swp`).
2. Create the template and give it terrain:
   ```
   /swp create <template> file
   /execute in minecraft:<template> run forceload add -48 -48 48 48
   /execute in minecraft:<template> run fill -48 64 -48 48 64 48 minecraft:stone
   /swp save <template>
   ```
   (or `/swp import <path-to-world-folder> file <template>` to use an existing map)
3. Add it to `plugins/SlimeWorldManager/worlds.yml` with `loadOnStartup: true` and
   `readOnly: true` — the plugin requires the template to be loaded when a game starts.

Plugin-side:

```
/pm config asp.enable true
/pm config asp.templateWorld <template>
/pm config asp.gameWorld <arena>      # cloned world; created automatically
/pm config asp.resetOnStart true
```

While enabled, `asp.gameWorld` is the single source of truth — it overrides
warfare's `gameWorld`, so the world being played and the world being reset cannot drift apart.
Beacon coordinates for team-beacon mode should be set inside the arena world
(`/war beacon set <team>` while standing in it).

Bingo and Chase do not use ASP — they use throwaway worlds instead (see above),
since they need freshly generated terrain rather than a fixed arena map.
