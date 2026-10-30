# Potato Minigames

Server fork of [Petr1Furious/potato-minigames](https://github.com/Petr1Furious/potato-minigames),
preserving the upstream Git history and the server's downstream patches.
The original Apache-2.0 license and attribution are retained in `LICENSE` and `NOTICE`.

One Paper plugin with four minigames on a shared framework, built for a server
anybody can join and play at any time: players queue for a game, wait in a lobby,
and the game starts by itself. Different games run side by side.
Targets Paper/AdvancedSlimePaper **26.3** (26.2 still builds, see Building), Java 25.

## Games

| Game | Command | Modes | What it is |
|---|---|---|---|
| **Pillars** | `/pillars` | `original`, `chaos`, `item-shuffle`, `lucky`, `tactical`, `big_house`, `party`, `rave`, `fusion` | Everyone on a bedrock pillar in the sky, a random item every few seconds, last one standing wins; `lucky` builds the pillars from [New Amazing Lucky Blocks](https://github.com/I2000C/NewAmazingLuckyBlocks) lucky blocks. `tactical` hands everyone the same random items each round (`sameItems`, which any mode can enable). `big_house` puts the pillars 2.5x further apart (`distanceMultiplier`) with chaos-speed items. One handed-out item in three that can be enchanted comes enchanted (`enchantChance`). Ported from [PillarPeril](https://github.com/MarcPG1905/PillarPeril). |
| **Bingo** | `/bingo` | `default`, `butfast`, `collectall`, `lockout` | Teams race to collect items on a generated board. Cage drop into a random arena; deaths are short spectator punishments. In `lockout` each item goes to the team that collects it first: a line wins, else the most items when time runs out. |
| **Warfare** | `/war` | `classic`, `team_beacon` | Formerly hide-and-seek. `classic`: invisible free-for-all with a bucketed-distance sidebar and gadgets; `team_beacon`: defend your wool beacon, respawn at it until it's destroyed. |
| **Chase** | `/chase` | — | Formerly ultimate tag. A fixed number of rounds; hunters track runners by compass. |

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

Each game's root (`/pillars`, `/bingo`, `/war`, `/chase`) has the shared subtree.
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

## Pillars fusion

`fusion` rolls its rules every game: chaos (random layout, chaos-speed items), shuffle (at
item-shuffle's own pace, which nothing speeds up), tactical (with shuffle, everyone gets the same
set), big_house, each with a 50% chance, and party or rave with 25% each. The values come from
those modes' settings. The roll is announced at the start and shown in the summary.

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

Choose any of `chaos`, `shuffle`, `tactical`, `big_house`, `party`, `rave`.
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

## Building

```
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon'            # 26.3
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon -Pmc=26.2'  # 26.2
```

The jar name says which API it was built against (`potato-minigames-<version>-mc26.3.jar`).
The 26.2 build exists for the bot-tested dev server; the servers run 26.3.

`build` also runs the preset self-check without adding a test framework (quota, ownership,
validation, reload and failed-write preservation). Run it alone with `sh ./gradlew checkPillarsPresets`.

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
