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
| **Pillars** | `/pillars` | `original`, `chaos`, `item-shuffle`, `lucky`, `tactical`, `big_house`, `sniper_duel`, `party`, `rave`, `adhd`, `perks`, `fusion` | Everyone on a bedrock pillar in the sky, a random item every few seconds, last one standing wins; `lucky` builds the pillars from [New Amazing Lucky Blocks](https://github.com/I2000C/NewAmazingLuckyBlocks) lucky blocks. `tactical` hands everyone the same random items each round (`sameItems`, also available in other modes). `big_house` puts the pillars 2.5x further apart (`distanceMultiplier`) with chaos-speed items. Ordinary handed-out items that can be enchanted have a one-in-three enchantment chance (`enchantChance`). Ported from [PillarPeril](https://github.com/MarcPG1905/PillarPeril). |
| **Bingo** | `/bingo` | `default`, `butfast`, `collectall`, `lockout` | Teams race to collect items on a generated board. Cage drop into a random arena; deaths are short spectator punishments. In `lockout` each item goes to the team that collects it first: a line wins, else the most items when time runs out. |
| **Warfare** | `/war` | `classic`, `team_beacon` | Formerly hide-and-seek. `classic`: invisible free-for-all with a bucketed-distance sidebar and gadgets; `team_beacon`: defend your wool beacon, respawn at it until it's destroyed. |
| **Chase** | `/chase` | — | Formerly ultimate tag. A fixed number of rounds; hunters track runners by compass. |
| **Party / Вечеринка** | `/party` | `pvp`, `sumo`, `arms_race`, `tnt_run`, `mob_count`, `rhino`, `miners` | Random-class PvP and sumo use round-robin BO3 tournaments. Arms Race is a simultaneous free-for-all with weapon progression and respawns. TNT Run, mob counting and ravager/mine dodging are one-life survival games. Miners combines private ore mining with random loot and underground PvP. |

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
| Стрелок / Ranger | Wooden sword, Power I bow, unlimited regular arrows, 6 Slowness I arrows (4 s), 4 Poison I arrows (4 s), 4 Weakness I arrows (5 s) and 6 spectral arrows; chain chestplate, remaining iron armour; 1 healing and 1 slowness splash | **Retreat:** backward dash and Speed I for 4 s; 16 s cooldown |
| Берсерк / Berserker | Iron axe, backup stone sword, chain chest/leggings and iron helmet/boots; 2 speed and 1 healing splash. Melee hits heal half a heart at most once per 3 s | **Blood Fury:** costs one heart; Strength I + Speed I for 4 s; 22 s cooldown. Unavailable at one heart or less |
| Дуэлянт / Duelist | Sharpness I iron sword, 2 pearls, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash. Sword hits from behind add 2 base damage at most once per 5 s | **Lunge:** forward dash and Speed I for 3 s; 14 s cooldown |
| Маг / Mage (formerly Alchemist) | Knockback I wooden ritual blade, gold helmet, purple leather robe with Protection II, chain leggings, iron boots; 12 splashes, listed below | **Magic Stock:** restores one harming, poison and slowness potion, capped at two of each; 24 s cooldown. Healing is never restored |
| Инженер / Engineer | Stone sword, Quick Charge III + Piercing I crossbow, unlimited arrows, 64 wool; iron armour with chain leggings; 2 slowness and 1 healing splash | **Fortify:** Absorption II (4 extra hearts) for 6 s and up to 16 wool, capped at 64 carried wool; 24 s cooldown |
| Воздушный рыцарь / Aerial Knight | Density I mace, backup stone sword, 8 wind charges; chain chest/leggings, iron helmet, leather boots with Feather Falling III; 1 speed and 1 healing splash | **Wind Leap:** upward launch and up to 4 wind charges, capped at 8 carried charges; 20 s cooldown |
| Паладин / Paladin | Iron sword and shield, chain chest/leggings and iron helmet/boots; 1 speed and 1 healing splash | **Purify:** heals 1.5 hearts, removes poison/weakness/slowness/blindness and extinguishes fire, then Resistance I for 2 s; 24 s cooldown |
| Криомант / Cryomancer | Sharpness I stone sword, 16 snowballs; cyan leather robe with Protection II, remaining iron armour; 1 slowness and 2 healing splashes. Snowball hits apply Slowness I for 2 s, at most once per 2 s | **Frost:** up to 8 snowballs (cap 16), Speed I for 3 s, and Slowness I for 3 s on a visible opponent within 6 blocks; 20 s cooldown |
| Пиромант / Pyromancer | Fire Aspect I wooden sword, Flame I bow, unlimited arrows; chain chest/leggings and iron helmet/boots; 1 weakness and 1 healing splash. Fire Resistance lasts the round | **Flare:** ignites a visible opponent within 8 blocks for 5 s and grants 2 absorption hearts for 4 s; 20 s cooldown. No target means no cooldown spent |
| Всадник / Lancer | Iron spear, backup stone sword, leather chestplate, chain helmet/leggings, iron boots; 1 speed and 1 healing splash; vulnerable horse, once per round | **Spur:** faster horse for 3 s, or Speed I for 3 s on foot; 20 s cooldown |
| Гарпунщик / Harpooner | Loyalty III trident, backup stone sword; iron helmet/boots and chain chest/leggings; 1 speed and 1 healing splash | **Dry Riptide:** 0.6 s forward spin with native collision damage, capped at 6 before armour (3 hearts), and moderate knockback; 20 s cooldown. One hit per target; shields block it |
| Громовержец / Thunderer | Iron thunder axe, backup stone sword; iron helmet/boots and chain chest/leggings; 1 speed and 1 healing splash. Unblocked axe hits apply Glowing for 3 s, at most once per 8 s | **Thunderstrike:** hold the storm wand to preview the aimed block within 48 blocks, then right-click. After 1 s, a fixed 3-block-radius strike deals 8 damage in its central core, falling to 3 at the edge before armour; 6 s cooldown. Move out or use cover |
| Подрывник / Demolitionist | Stone sword; iron helmet/boots, chain leggings and orange leather chestplate with Blast Protection II; 12 tagged TNT, 1 speed and 1 healing splash | **Ammo Stock:** +4 TNT up to 12 carried; 18 s cooldown. TNT right-click places an instantly primed charge (0.3 s interval); left-click throws one (2 s cooldown) |
| Тень / Shadow | Unenchanted diamond sword, **no armour**, 2 splash Invisibility potions (up to 20 s each), 1 speed and 1 healing splash. Hits do not remove invisibility; its stock cannot be replenished | **Dark Wand:** hit an opponent with the tagged stick in slot 8 to apply Blindness I for 2 s and Slowness II for 3 s, once per 20 s. No health damage; shields block the proc; right-click does not activate it |

Demolitionist uses the tagged TNT stack in hotbar slot 4. Right-click an arena
surface to spawn an already primed charge in the adjacent empty buildable cell;
no TNT block or separate ignition tool is needed. Left-click air, a block or an
entity to throw one along the look direction, including when the target's damage
invulnerability would prevent a normal punch. Both modes consume one charge only
after a successful spawn. Placement/hand interval is 6 ticks (0.3 s); throwing has
an additional 40-tick cooldown. Alternating modes does not bypass the throw cooldown,
and placing remains possible while a throw is on cooldown. Invalid placement,
full live-bomb slots or blocked cooldowns spend no ammo. The loadout starts with
12 charges; Ammo Stock in slot 8 adds up to four, capped at 12 carried charges,
every 18 s. A full stock or full inventory does not spend the refill cooldown.

At most four TNT can be live per bomber. Each native charge has a 50-tick fuse,
explosion power 2 (roughly 4-block reach, with native distance/cover attenuation),
and no incendiary effect. Raw blast damage is capped at 10 (5 hearts before armour)
for enemies, horses and the bomber; armour, Blast Protection and native damage
invulnerability still apply. The orange Blast Protection II leather chestplate
softens self damage while leaving the class vulnerable in melee. Blasts never
break arena blocks or player wool. Owner tracking credits TNT kills and allows a
single blast to trade kills in the same server tick: vanilla discards TNT before
applying damage, so its owner reference lasts through that blast. Other live
charges are removed on death/withdrawal, and all charges/cooldowns are cleared at
round reset, result and session stop. Cleanup scans only the bounded tracked charges.

Thunderer's storm wand (a tagged lightning rod in slot 8) is a reusable ranged
attack with a 6-second cooldown. While held in the main hand, a private preview
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
Tracked tridents are exempt from ordinary arrow expiry so Loyalty can finish;
round reset, leaving and shutdown still remove them. Native spin state and pending
knockback are cleaned up on elimination, round reset, leaving and shutdown.

The lancer receives a saddled horse **only when the countdown ends**. It has
8 hearts, no armour, fixed movement speed 0.25 (0.30 during Spur), and jump strength
0.4. Killing it leaves the rider on foot until the next round; there is no resummon
or healing the horse with class potions/food. Only its owner can mount it, and its
inventory is locked. Enemy arrows, melee, harmful potions and environmental damage
can hurt it; frost snowballs and Slowness can counter mounted movement. Iron spear
hits, including native speed-scaled charges, are capped at **8 damage before armour**
(4 hearts); this also applies on foot and against horses. A backup stone sword,
light armour, one speed splash and one healing splash complete the kit. Spur gives
the rider Speed I for 3 s when used on foot, with the same 20 s cooldown. Mounts
are removed on elimination, leaving, round reset and session shutdown.

Shadow's invisibility is a finite consumable, not a cooldown ability. Each of its
two splashes grants up to 20 seconds, depending on splash intensity; refreshing the
effect does not add unused duration. The potions only affect their caster, and no
ability restores them. Attacking and taking damage do not remove invisibility.
Items held in either hand remain visible, as with native Minecraft invisibility;
fire and Glowing also give away the player. The absence of armour, limited potions
and a 20-second wand cooldown offset the diamond sword and longer concealment.
The wand perk requires a successful unblocked melee hit on a player; it deals no
health damage. Shields do not spend its cooldown, hit spam cannot refresh its
control, and the HUD shows its remaining cooldown. Paladin Purify removes Blindness.

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
the original alchemist enum entry. There are now fifteen equally likely classes: each
has a 1/15 (about 6.67%) marginal chance per player; the two opening classes are always distinct.

Ranger ammunition occupies the top inventory row, left to right by general utility:
**slowness → poison → weakness → spectral → ordinary** (slots 10–14, counting
from 1). The offhand starts empty so native bow selection follows that order;
move any chosen stack into the offhand to prioritise it manually. Special arrows
are finite, cannot be picked back up, and only reset between rounds. Regular-arrow
refills never take an earlier slot ahead of the special stacks. Custom tipped-arrow
durations are explicitly scaled to 1 so the short effects last their stated 4/4/5 s.

Rangers, engineers and pyromancers start with 64 ordinary arrows; their arrow stack is replenished to 64
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

### Party TNT Run

Join with `/play party tnt_run`, or train with `/party test tnt_run`.
Up to sixteen players run across three circular TNT floors, six blocks apart.
Tiles under a player's footprint turn red and disappear after half a second;
standing still never refreshes their timer. Jumping and falling to a lower floor
are part of the game. Every 30 seconds the collapse delay shortens, down to three
ticks. After one minute, an outer ring of every floor disappears every 15 seconds.
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
choose a random living player, glow before charging, then dash in that fixed
direction without drawing a route on the floor. A dash knocks each player back
at most once. Their speed and frequency rise every 45 seconds,
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

### Party Битва шахтёров / Miners

Open the compass → **Вечеринка** → **Битва шахтёров**, or run `/play party miners`.
The mode accepts **2–12 players**; `/party test miners` starts a solo training game.
Each participant starts inside a separate **16×16×16** stone box with the same
ore counts, shuffled independently. The starter kit is an unbreakable Efficiency III
iron pickaxe, stone sword, leather armour, 64 cobblestone and food. Night Vision
and Haste I last through the match. Construction is spread across ticks, then a
five-second countdown freezes players before mining begins.

Players have **two minutes** to mine privately. Ores drop **physical random loot**
onto the ground: pick it up, choose equipment and equip armour yourself. There is
no fixed sequence of upgrades. Ordinary/good/rare rolls have **70%/25%/5%** odds;
combat enchantments reach level II and mining efficiency reaches V.

| Ore | Blocks per private box | Rewards |
| --- | ---: | --- |
| Coal | 80 | Food or faster iron/diamond pickaxes |
| Iron | 64 | Random chainmail/iron armour; rare diamond pieces |
| Diamond | 24 | Swords, axes, bows or crossbows; every ranged drop includes arrows |
| Lapis | 24 | Healing, speed, harming, poison, slowness or weakness splash potions |
| Redstone | 32 | Primed TNT charges, ender pearls or wind charges |
| Emerald | 8 | Good/rare loot from the other pools, or a golden apple |
| Gold | 16 | A personal health bonus instead of an item: every four ores grants two maximum hearts, capped at fourteen hearts |

At the end of preparation all shared partitions open and **PvP begins**. Continue
mining, dig tunnels, build with cobblestone and use the tracking compass to locate
the nearest surviving opponent. PvP has a **three-minute** phase followed by a
**one-minute** shrinking border with glowing players. Increasing damage after
the final minute prevents indefinite camping/healing. One life: the last survivor
wins three Party points; simultaneous final deaths draw. Disconnecting withdraws
a player and reconnecting only spectates. Solo training and aborted matches award
no points. Deaths show the killer and a three-second death-screen pause.

Preparation blocks damage, pearls and wind charges. The bedrock shell is always
protected; TNT damages players without destroying terrain or ore rewards.
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
`perks.dropChance` in `games/pillars.yml` for the next session. The fourteen ordinary
perks have weight 4 each; the two legendary weapons have weight 1 each. Each
legendary therefore has a **0.345%** chance per item draw at the default setting,
four times rarer than each ordinary perk. Tactical presets share perk kinds and
ordinary item rolls, with separate item instances/charges per player.

Every perk has enchantment glint, a Russian name, instructions and remaining charges.
Active items use the main hand; normal items with the same material have no perk.

| Item | Perk and controls |
| --- | --- |
| Feather / Спасатель | Right-click for a 3×3 slime platform just below you, expiring after 10 seconds. One use. |
| Quartz block / Кубик шулера | Right-click to replace the entire latest reward, including its stack or bow/ammo bundle. Three uses. The whole unchanged reward must still be in your inventory; spent, placed, modified, stored or missing parts prevent a reroll. Receiving a new cube retains the preceding reroll target. |
| Fishing rod / Воровской крюк | Hit an opponent with the hook to transfer their complete main-hand stack, preserving metadata and remaining charges. One successful theft; an empty hand or miss costs no charge. |
| Ghast tear / Призрак | Right-click for exactly four seconds of spectator flight/phasing. Normal play then resumes in a clear position nearby. Following players and leaving the arena bounds are blocked. One use. |
| Iron boots / Якорь | Wear to cancel knockback from three damaging enemy hits, then break. Health damage remains; removing/repairing the boots cannot refill charges. |
| Rabbit foot / Воздушный шаг | Carry in your inventory and press jump again while airborne. Three extra jumps, usable together or on separate falls; landing does not refill them. |
| Clock / Отмотка | Right-click to return to the position from 60 ticks ago. Requires three seconds of recorded history and a clear destination. Health, items and charges are never rolled back. One use. |
| Brick / Карманный домик | Right-click for a wooden house only 3×3 externally: one-player interior, two blocks of headroom, door, windows and torch. Existing blocks are preserved; a blocked footprint refuses the cast. One use. |
| Blue ice / Ледяной луч | Right-click a ray up to 32 blocks. A hit encases the opponent in protected ice for four seconds; they cannot move, attack, use items or take damage. One attempt, including misses. |
| Legendary bow / До свидания | Punch 100, one arrow included. The bow breaks after its single shot, including a miss. |
| Legendary crossbow / TNT-почта | Right-click to throw primed TNT; three shots, two-second fuse. No ordinary ammunition required. |
| Leather boots / Плотник | First equip starts a five-second timer: permanent planks appear underfoot, including over the void. Removing, stealing or re-equipping never restarts the timer. Afterwards the boots retain ordinary leather protection. |
| Blaze rod / Дезинтегратор | Hold right-click and aim continuously at the same block within 12 blocks for six seconds. Release, switching item, looking away or target replacement resets progress. Breaks even bedrock, without block/container drops. One successful use. |
| Sheep egg / Камикадзе | Right-click to summon a sheep carrying primed TNT. It chases the nearest active opponent, can leap gaps, and explodes after five seconds. The caster is never its target, but explosions can still hurt the caster. One use. |
| Ink sac / Дымовая завеса | Right-click for a smoke cloud with a four-block radius lasting five seconds. Enemies inside are blinded; the caster is unaffected. One use. |
| Fire charge / Фаербол | Right-click to throw an explosive fireball along your view. One use. |

Special effects, entities, history and pending block leases are scoped to the match
and cleaned on stop. Temporary terrain never overwrites existing blocks or deletes
a player's replacements when expiring. Freeze blocks resist breaking, melting,
pistons and explosions. Charge/timer metadata follows dropped or stolen items.
Shuffle replaces perk inventory along with ordinary inventory; already running
timed effects keep their original deadlines. Spectator mode is restored on exit,
disconnect and stop; reconnecting never grants extra charges.

For live testing, admins in a running perks match can use `/pillars perk <type>`:
`save`, `reroll`, `steal`, `ghost`, `anchor`, `jump`, `rewind`, `house`, `freeze`,
`bow`, `crossbow`, `planks`, `break`, `sheep`, `smoke`, `fireball` (tab completion).
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
`checkPillarsMath` checks generated arithmetic, independent player answers, retries,
elimination and one-shot timeout penalties.
`checkPillarsShuffle` checks dropped items and container stashes, cursor/crafting
cleanup, entity equipment/storage, unloaded chunks, reconnects and ender-chest isolation.
`checkPillarsPerks` checks all sixteen weighted outcomes and rarity, exact rewind/channel timing,
whole-reward counts, real temporary block writes/restoration and cage leases, tiny house
geometry and queue/preset integration.
`checkPillarsAdhd` checks jittered start intervals, exact burst duration and triple
shuffle speed, configured rave frequency, and cancellation of burst-only event tasks.
`checkPartySurvival` checks TNT geometry and collapse deadlines, exact 2×2 platforms
and two-block gaps, unique mob-count answers, landing deadlines, ravager swept
collision/difficulty, fixed mine fuses through pickup/throw/drop, and elimination ties.
`checkPartyRuntime` checks countdown locking, respawn/result pauses and projectile
cleanup. `checkPvpClasses` checks class equipment, finite stock and combat abilities.
`checkPartyMiners` checks Party menu/queue integration, equal ore quotas for 1–12
players, sliced construction/opening, protected shells and unused cells, single-use
ore rewards, random loot grades and ammunition, phase timing, capped extra hearts,
TNT spawn clearance and bounded ground-item cleanup.

`checkPvpClasses` additionally verifies Shadow's finite invisibility stock, unarmoured
loadout, on-hit wand cooldown and blocked-hit rejection, alongside all 132 distinct
opening class matchups.

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
