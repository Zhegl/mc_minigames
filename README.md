# Potato Minigames

One Paper plugin with four minigames on a shared framework, built for a server
anybody can join and play at any time: players queue for a game, wait in a lobby,
and the game starts by itself. Different games run side by side.
Targets Paper/AdvancedSlimePaper **26.3** (26.2 still builds, see Building), Java 25.

## Games

| Game | Command | Modes | What it is |
|---|---|---|---|
| **Pillars** | `/pillars` | `original`, `chaos`, `item-shuffle` | Everyone on a bedrock pillar in the sky, a random item every few seconds, last one standing wins. Ported from [PillarPeril](https://github.com/MarcPG1905/PillarPeril). |
| **Bingo** | `/bingo` | `default`, `butfast`, `collectall` | Teams race to collect items on a generated board. Cage drop into a random arena; deaths are short spectator punishments. |
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
Starting, stopping and joining games, changing teams, `config set` and `reload` need
`potatominigames.admin`; the rest is open to everyone.

| command | what it does |
|---|---|
| `start [mode] [players] [arena\|difficulty]` | start now, with the selector or everyone online who is not playing; see below |
| `stop` | stop this game |
| `restart [mode] [players] [arena\|difficulty]` | end the current game and start another; by default with the same players, mode and arena/difficulty |
| `join [players]` | put players into the running game, or a knocked-out player back in |
| `leave [players]` | take players out of the game |
| `list`, `points [reset]` | who's playing; standings |
| `team join <color> [players]` / `leave` / `cycle` / `swap` / `list` | saved colours, used only by games an admin starts (queued games use `/play team`) |
| `config <section> <get\|set>`, `reload` | settings |

`start` takes its arguments in order: the mode (games with several modes; without one,
the configured `mode`), a player selector, then a game-specific value: `/pillars start
chaos @a lodestone` forces an imported arena, `/bingo start butfast @a hard` a
difficulty (otherwise the configured one). `/chase start [players]` has neither. The
defaults are set with `config <game> set mode|difficulty <value>`.

Pillars adds `respawn [player]`, `time add|remove|set <time>`, `info`, `arenas` and
`arena import <arena|all> [world] [radius]` (see below).

`/pm` is the cross-game root: `status` (games and queues), `games`, `stop [game]`,
`forcestart <game> [mode]` (start a queue's game now with whoever is in it, ignoring
its minimum and timers; without a mode, the fullest queue), `reload`,
`config <section> ...` (every section, including `core`, `lobby`, `queue`,
`queues.<game>.<mode>` and `arena.asp`), `inventory <save|load|copy>`, `give <gadget>`.

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
games/warfare.yml             mode, arena/border, distances, lifts, teamBeacon, beacons, kit
games/chase.yml               rounds, hunters, timings, build limits, hunterGadgets
gadgets.yml                   one section per gadget
data/                         points.yml, teams.yml, inventories.yml, snapshots/
```

All flat values are editable at runtime via the generic `config set` commands
and are written back to the files.

## Building

```
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon'            # 26.3
nix-shell -p temurin-bin-25 --run 'sh ./gradlew build --no-daemon -Pmc=26.2'  # 26.2
```

The jar name says which API it was built against (`potato-minigames-<version>-mc26.3.jar`).
The 26.2 build exists for the bot-tested dev server; the servers run 26.3.

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
/pm config arena.asp set enable true
/pm config arena.asp set templateWorld <template>
/pm config arena.asp set gameWorld <arena>      # cloned world; created automatically
/pm config arena.asp set resetOnStart true
```

While enabled, `arena.asp.gameWorld` is the single source of truth — it overrides
`war.gameWorld`, so the world being played and the world being reset cannot drift apart.
Beacon coordinates for team-beacon mode should be set inside the arena world
(`/war beacon set <team>` while standing in it).

Bingo and Chase do not use ASP — they use throwaway worlds instead (see above),
since they need freshly generated terrain rather than a fixed arena map.
