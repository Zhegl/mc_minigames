# Potato Minigames

Server fork of [Petr1Furious/potato-minigames](https://github.com/Petr1Furious/potato-minigames),
preserving the upstream Git history and the server's downstream patches.
The original Apache-2.0 license and attribution are retained in `LICENSE` and `NOTICE`.

One Paper plugin with five game categories on a shared framework, built for a server
anybody can join and play at any time: players queue for a game, wait in a lobby,
and the game starts by itself. Different games run side by side.
Targets Paper/AdvancedSlimePaper **26.3** (26.2 still builds, see Building), Java 25.

## Games

| Game | Command | Modes | What it is |
|---|---|---|---|
| **Pillars** | `/pillars` | `original`, `chaos`, `item-shuffle`, `lucky`, `tactical`, `big_house`, `sniper_duel`, `party`, `rave`, `fusion` | Everyone on a bedrock pillar in the sky, a random item every few seconds, last one standing wins; `lucky` builds the pillars from [New Amazing Lucky Blocks](https://github.com/I2000C/NewAmazingLuckyBlocks) lucky blocks. `tactical` hands everyone the same random items each round (`sameItems`, also available in other modes). `big_house` puts the pillars 2.5x further apart (`distanceMultiplier`) with chaos-speed items. Ordinary handed-out items that can be enchanted have a one-in-three enchantment chance (`enchantChance`). Ported from [PillarPeril](https://github.com/MarcPG1905/PillarPeril). |
| **Bingo** | `/bingo` | `default`, `butfast`, `collectall`, `lockout` | Teams race to collect items on a generated board. Cage drop into a random arena; deaths are short spectator punishments. In `lockout` each item goes to the team that collects it first: a line wins, else the most items when time runs out. |
| **Warfare** | `/war` | `classic`, `team_beacon` | Formerly hide-and-seek. `classic`: invisible free-for-all with a bucketed-distance sidebar and gadgets; `team_beacon`: defend your wool beacon, respawn at it until it's destroyed. |
| **Chase** | `/chase` | — | Formerly ultimate tag. A fixed number of rounds; hunters track runners by compass. |
| **Party / Вечеринка** | `/party` | `pvp`, `sumo`, `arms_race` | Random-class PvP and sumo use round-robin BO3 tournaments. Arms Race is a simultaneous free-for-all with weapon progression and respawns. |

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

| Class | Equipment and tradeoff | Active ability (right-click slot 8) |
|---|---|---|
| Страж / Guardian | Stone sword, shield, iron armour with netherite boots and Projectile Protection I chestplate; 2 Healing I splashes, 1 Slowness I splash | **Bastion:** Resistance II + self Slowness I for 4 s; 22 s cooldown |
| Стрелок / Ranger | Wooden sword, Power I bow, unlimited regular arrows and 6 spectral arrows; chain chestplate, remaining iron armour; 1 healing and 1 slowness splash | **Retreat:** backward dash and Speed I for 4 s; 16 s cooldown |
| Берсерк / Berserker | Iron axe, backup stone sword, chain chest/leggings and iron helmet/boots; 2 speed and 1 healing splash. Melee hits heal half a heart at most once per 3 s | **Blood Fury:** costs one heart; Strength I + Speed I for 4 s; 22 s cooldown. Unavailable at one heart or less |
| Дуэлянт / Duelist | Sharpness I iron sword, 2 pearls, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash. Sword hits from behind add 2 base damage at most once per 5 s | **Lunge:** forward dash and Speed I for 3 s; 14 s cooldown |
| Маг / Mage (formerly Alchemist) | Knockback I wooden ritual blade, gold helmet, purple leather robe with Protection II, chain leggings, iron boots; 12 splashes, listed below | **Magic Stock:** restores one harming, poison and slowness potion, capped at two of each; 24 s cooldown. Healing is never restored |
| Инженер / Engineer | Stone sword, Quick Charge I + Piercing I crossbow, unlimited arrows, 64 wool; iron armour with chain leggings; 2 slowness and 1 healing splash | **Fortify:** Absorption II (4 extra hearts) for 6 s and up to 16 wool, capped at 64 carried wool; 24 s cooldown |
| Воздушный рыцарь / Aerial Knight | Density I mace, backup stone sword, 8 wind charges; chain chest/leggings, iron helmet, leather boots with Feather Falling III; 1 speed and 1 healing splash | **Wind Leap:** upward launch and up to 4 wind charges, capped at 8 carried charges; 20 s cooldown |
| Паладин / Paladin | Iron sword and shield, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash | **Purify:** heals 1.5 hearts, removes poison/weakness/slowness and extinguishes fire, then Resistance I for 2 s; 24 s cooldown |
| Криомант / Cryomancer | Sharpness I stone sword, 16 snowballs; cyan leather robe with Protection II, remaining iron armour; 1 slowness and 2 healing splashes. Snowball hits apply Slowness I for 2 s, at most once per 2 s | **Frost:** up to 8 snowballs (cap 16), Speed I for 3 s, and Slowness I for 3 s on a visible opponent within 6 blocks; 20 s cooldown |
| Пиромант / Pyromancer | Fire Aspect I wooden sword, Flame I bow, unlimited arrows; chain chest/leggings and iron helmet/boots; 1 weakness and 1 healing splash. Fire Resistance lasts the round | **Flare:** ignites a visible opponent within 8 blocks for 5 s and grants 2 absorption hearts for 4 s; 20 s cooldown. No target means no cooldown spent |
| Всадник / Lancer | Iron spear, backup stone sword, leather chestplate, chain helmet/leggings, iron boots; 1 speed and 1 healing splash; vulnerable horse, once per round | **Spur:** faster horse for 3 s, or Speed I for 3 s on foot; 20 s cooldown |

The lancer receives a saddled horse **only when the countdown ends**. It has
8 hearts, no armour, fixed movement speed 0.19 (0.23 during Spur), and jump strength
0.4. Killing it leaves the rider on foot until the next round; there is no resummon
or healing the horse with class potions/food. Only its owner can mount it, and its
inventory is locked. Enemy arrows, melee, harmful potions and environmental damage
can hurt it; frost snowballs and Slowness can counter mounted movement. Iron spear
hits, including native speed-scaled charges, are capped at **8 damage before armour**
(4 hearts); this also applies on foot and against horses. A backup stone sword,
light armour, one speed splash and one healing splash complete the kit. Spur gives
the rider Speed I for 3 s when used on foot, with the same 20 s cooldown. Mounts
are removed on elimination, leaving, round reset and session shutdown.

The aerial knight uses native mace smash attacks and native wind-charge boosts.
Its leap preserves falling attacks: no Slow Falling effect is applied. Charges are
limited per stock and replenished by the ability. The full-height world border
aligns with the 49×49 playing area so jumping above the low barrier wall cannot
escape the arena. Neither frost effects nor fire abilities edit terrain.

The mage starts with **12 potions in six types**: 2 Harming I, 2 Poison I (5 s),
2 Slowness I (4 s), 2 Weakness I (6 s), 3 Healing I (two hearts each), and 1 Speed I
(6 s). Weakness and speed start in inventory slots 11 and 12; combat spells and
healing are on the hotbar, with slot 9 reserved for food. Class potions stack up to
eight and have distinct colours and labels.
Helpful splashes affect only their caster, harmful splashes only the active opponent;
spectators are excluded. Other classes use the same potion targeting rules.

Mage refills cannot exceed the individual stock caps or generate healing. Activating
Magic Stock while all three stocks are full does not consume its cooldown. Blood
Fury cannot kill its caster. Backstab and lifesteal cooldowns reset every round,
and class perks do not leave permanent player attributes behind. The mage retains
the original alchemist enum entry. There are now eleven equally likely classes: each
has a 1/11 (about 9.09%) marginal chance per player; the two opening classes are always distinct.

Rangers, engineers and pyromancers start with 64 arrows; their arrow stack is replenished to 64
every second during combat, including overtime. Moving the stack to another slot
does not prevent replenishment. Other classes do not receive arrows.

The arena is a symmetric **49×49 courtyard** with short cover and clear spawn
lanes. A one-block rim and eight barrier blocks above it enclose the arena; the roof
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
and no building. The stick applies stronger controlled knockback; sprinting adds
a small boost. Empty-hand punches remain weaker. A low upward impulse and a
horizontal speed cap prevent runaway launches from repeated hits. Falling below
the platform loses the round. Each player's hit immunity lasts ten server ticks;
it resets before every round. Both falling in the same server tick replays the round.

The arena is a round, **23-block-wide platform** above the void, without walls or
hidden floors. The extra space balances the stronger stick. Players start eight blocks apart and swap sides for round two.
After 45 seconds the outer ring disappears; another ring goes every two or three
seconds. The larger platform still disappears by 28 seconds of overtime, leaving
time for ring-outs before the 30-second overtime limit. Every round restores the full platform. A five-second
countdown pins participants in place and blocks jumping, flight and early hits.
Waiting players spectate the duel after the countdown.

Admin commands: `/party start sumo @a`, `/party test sumo` (solo platform training),
`/party stop`. Settings in `games/party.yml`: `sumo.roundSeconds`,
`sumo.countdownSeconds`, `sumo.intermissionSeconds`; changes apply next session.

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

The arena is a protected 49×49 courtyard with cover; building, breaking, crafting,
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
`checkPartyRuntime` checks fixed countdown positions, cancelled jumps, blocked
spectator flight, exact movement-speed restoration, and targeted projectile cleanup
and lifetime bounds, mount ownership and cleanup, the spear damage cap, and death-pause timing (including early manual respawn,
duplicate callbacks and per-player/round cleanup).
`checkPartyPvp` also enforces the lightweight map geometry.
`checkPvpClasses` verifies kit slot safety, the mage's twelve-potion loadout, stock
caps and potion targets, all 110 equally weighted opening matchups, backstab,
lifesteal and frost cooldowns, safe dash/leap vectors, the native mace/wind-charge
loadout, unlimited ammunition for every bow class and the Blood Fury health cost.

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
