# Potato Minigames

One Paper plugin unifying three minigames (formerly the separate `bingo-plugin`,
`hide-and-seek`, and `ultimate-tag-plugin` repos) on a shared framework.
Targets Paper/AdvancedSlimePaper **26.2**, Java 25.

## Games

| Game | Command | What it is |
|---|---|---|
| **Bingo** | `/bingo` | Teams race to collect items on a generated 5×5 board (modes: `default` line bingo, `butfast` first-come scoring, `collectall` 3×3). Cage drop into a random arena; deaths are short spectator punishments. |
| **Warfare** | `/war` | Formerly hide-and-seek. `classic`: invisible free-for-all with a bucketed-distance sidebar and gadgets; `team_beacon`: defend your wool beacon, respawn at it until it's destroyed. |
| **Chase** | `/chase` | Formerly ultimate tag. Fixed number of rounds; a fair rotation picks hunters who track runners by compass (plus optional gadgets); survivors and catchers score. |

## Shared commands

There is no lobby. A game starts the moment you run `start`, with the players you
name or everyone online; `join` means "join the game already in progress".
Anything you want arranged beforehand — teams, settings — is set while idle.

| command | what it does |
|---|---|
| `start [players]` | start now with the selector, or everyone online |
| `stop` | stop the running game |
| `restart [players]` | end the current game and immediately start another with the same players (or a new selector). Works as a plain start when nothing is running, so it's a "go again" button. |
| `join [players]` | late-join a game in progress |
| `leave [players]` | leave the game |
| `list` | who's playing; with no game running, the teams arranged for next time |
| `team join <color> [players]` / `team leave` / `team cycle` / `team swap` / `team list` | **works with no game running** — colours are per game, saved to disk |
| `points [reset]`, `give <gadget>`, `config <section> <get\|set>`, `reload` | as before |

Colours are remembered per game, so you can be red in bingo and blue in warfare.

`/pm` is the cross-game admin root: `status`, `games`, `stop`, `reload`,
`config <section> ...` (all sections incl. `core` and `arena.asp`),
`inventory <save|load|copy>` (named kit helpers), `give <gadget>`.

## Gadgets

The former hide-and-seek weapons, now game-agnostic: `infinite-crossbow`,
`oreshnik`, `himars`, `locator`, `fpv-drone`, `radar`, `grapple-bow`.
Each has an `allowOutsideGame` config (default true, except oreshnik) — with no
game running the plugin is otherwise fully inert.

## Config layout (plugin data folder)

```
config.yml                    core: end-of-game grace seconds, asp world-reset settings
games/bingo.yml               mode, difficulty (+ difficulty-levels table), timings
games/bingo-board-entries.yml the crafting-cost node graph behind board generation
games/warfare.yml             mode, arena/border, distances, lifts, teamBeacon, beacons, kit
games/chase.yml               rounds, hunters, timings, build limits, hunterGadgets
gadgets.yml                   one section per gadget
data/                         points.yml, teams.yml (standing colours per game), inventories.yml
```

All flat values are editable at runtime via the generic `config set` commands
and are written back to the files.

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
