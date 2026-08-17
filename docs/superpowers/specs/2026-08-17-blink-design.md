# Blink — Design Spec (v0.1.0)

**Date:** 2026-08-17
**Status:** Approved for planning
**Plugin slug:** `blink`
**Ecosystem:** xpfarm.org Minecraft plugins (Paper 26.1.2 build 74, Java 25)

---

## 1. Concept

A black block sits in the dark. It does not move while you are looking at it.

**Blink** adds a single rare ambient horror creature to the overworld: a black shulker, spawning at
night above ground, that binds itself to one player after eye contact and then follows them across
the world — forever, slowly, and only ever while nobody is watching it.

Its central loop is a trap built out of greed. Looking at it freezes it, which feels like safety.
But a frozen creature within reach **opens**, streams black particles into the watcher, and eats
their XP — and inside the open shell the player can see the **cursed artifact** it carries. The
reason to keep looking is the reason it is killing you.

A sealed shulker has 20 armor points; an open one has 0. It only opens while feeding on a watcher.
Therefore **the only window in which it can be hurt is the window in which it is eating somebody.**
The drain and the kill are one system: the fight costs exactly what the creature is worth.

**Design pillars**

- **Observation is the mechanic.** Movement is gated on being unobserved; feeding is gated on being observed.
- **Safety is the trap.** Freezing it is what exposes you to it.
- **Relentless, not fast.** It is slower than a walking player and never stops, never despawns, and survives your logout.
- **Rare.** One per player at most. Dread does not survive familiarity.
- **Bedrock-identical.** Every mechanic reads the same for Java and Floodgate players. No resource pack required.

---

## 2. Scope

### In scope (v0.1.0 / MVP)

- One creature: a black `Shulker` with vanilla AI fully disabled and all behavior plugin-driven.
- Natural rare night spawning above ground in the overworld, **disabled by default in config**.
- Eye-contact binding, with a one-time disorientation sting (Nausea + Darkness + sound + title).
- Global gaze freezing: **any** player's gaze freezes it; only its bound victim is hunted.
- Unobserved stepping movement, including **climbing walls and traversing ceilings**.
- Feeding: opens on a qualifying watcher, drains XP levels, banks them.
- Three growth stages driven by banked XP; the top stage kills on contact.
- Starvation: the longer it goes unfed, the faster and louder it becomes.
- Vulnerability restricted to the open/feeding window.
- Cursed artifact drop carrying the banked XP; consumable to reclaim XP.
- Soft integration with `TheCurse` when present.
- Admin commands for summon, purge, list, reload.
- Full vanilla, config-driven audio palette.

### Out of scope (v0.1.0)

- Block breaking of any kind.
- Cross-dimension pursuit.
- Entombment / "it takes you somewhere" (deferred; see §10).
- Custom resource-pack audio or models.
- Any external service integration.

---

## 3. The creature

### 3.1 States

| State | Meaning |
|---|---|
| `DORMANT` | Spawned, sealed, unbound. Senses all players; wants nothing. Rare faint clicking. |
| `HUNTING` | Bound to a victim. Moves only while unobserved. |
| `FROZEN` | At least one qualifying player has it in their gaze. Cannot move. |
| `FEEDING` | Frozen, open, draining a qualifying watcher. **Vulnerable.** |
| `WAITING` | Victim offline, in another dimension, or sealed away. Stationary, starving. |

`DORMANT` is not a lesser `HUNTING` — an unbound creature ignores everyone. Sensing is not targeting.

### 3.2 The lock

Binding requires the player to look at it, not merely be near it. All of:

- the creature is within the player's configured FOV cone,
- line of sight is unobstructed,
- it is within `gaze.max-distance` (see §4.2 — this is clamped to entity tracking range),
- held continuously for `gaze.lock-on-ticks`.

On bind, once only: shrieker sting, Nausea + Darkness for the configured duration, a whispered title.
Re-looking at an already-bound creature never re-fires the sting.

### 3.3 Movement

It moves **only** while no qualifying player anywhere has it in their gaze. Movement is one block per
stage-and-starvation-scaled interval, toward the victim, executed as a `teleport()` — honest, because
nothing is ever watching it move.

Traversal rules:

- Step up 1, drop up to 3 on flat ground.
- **Climb**: it may attach to a vertical face and ascend it, then cross the top. `setAttachedFace()`
  is used for the grip, which is native shulker behavior on both editions.
- **Ceilings**: it may traverse an overhang or ceiling face, arriving from above.
- No fall damage (it is a block).
- If blocked head-on, try the two flanking cells; if fully blocked, wait.
- **It never breaks blocks.**

Because it climbs, walls and trenches do not stop it. A walled compound without a roof is not a defense.

### 3.4 Sealed spaces

A player inside a genuinely sealed volume — no opening, no gap, door shut — **cannot be reached.**
The creature attaches to the nearest exterior surface and enters `WAITING`, audible through the wall,
starving and accelerating for the moment the door opens.

This is deliberate: it is the plugin's only real counterplay, and it must be earned rather than
approximated by a lazy wall. Being trapped inside a box with the thing waiting outside is stronger
horror than letting it phase in, and it is what keeps the plugin from being uninstallable.

### 3.5 Feeding

When `FROZEN` with a qualifying watcher inside the stage-scaled feed radius, it opens
(`setPeek(1.0)`), streams particles watcher-ward, drains XP at the configured rate, and banks it.
The cursed artifact is visible inside the open shell — this is the lure.

**It only opens for someone worth eating.** A watcher with no XP does not cause it to open. This
closes an otherwise fight-ending exploit: because the open shell is the *only* vulnerable window, a
zero-XP player could otherwise act as free, cost-free bait while an ally killed it. Requiring
something to drain means the fight is always paid for.

### 3.6 Growth

Three stages, advanced by cumulative banked XP. Each stage raises health, shortens the step
interval, extends feed radius, thickens particles, and intensifies audio. Stage 3 (**Gorged**) kills
on contact.

Visible size scaling via `Attribute.SCALE` is applied as **cosmetic only** and must never be
load-bearing — see §9.

### 3.7 Starvation

Time since last feed drives a speed and audio multiplier. Denying it food is a legitimate strategy
but a decaying one: hide long enough and what is waiting outside is faster than what you hid from.
This exists specifically so that turtling is not free.

### 3.8 Contact and death

- **Contact below Gorged:** drains everything the victim has and applies heavy disorientation. Survivable. It walks away much stronger.
- **Contact at Gorged:** kills.
- **Victim dies by any cause:** it releases them and goes `WAITING` where it stands. Dying is an escape; it is still out there.
- **Creature dies:** drops exactly one cursed artifact carrying its banked XP.

### 3.9 The artifact

An item whose PDC holds the XP the creature consumed. Consuming it returns XP at a configured
ratio — kill it and drink your own levels back.

Soft integration: when `TheCurse` is loaded, the artifact additionally gains curse-starting
behavior. When absent, it remains a functional trophy. Nothing fails without Curse.

### 3.10 Audio

All vanilla sound events, every one a config key:

| Moment | Default |
|---|---|
| Dormant ambience | sculk sensor clicking |
| Lock-on sting | sculk shrieker shriek |
| Proximity pulse | warden heartbeat, quickening as it closes |
| Movement | sculk spread whispers, low volume |
| Feeding | pitched-down experience-orb pickup, played in reverse sense |
| Opening | pitched-down shulker open |
| Death | pitched-down warden death |
| Haunting | rare fragments of Disc 11, to the victim alone |

---

## 4. Architecture

Package root `org.xpfarm.blink`, following Curse's layout: `managers/`, `listeners/`, `commands/`,
`models/`, `utils/`.

### 4.1 Entity configuration

Black `Shulker`, `setAI(false)`, silent, persistent, `setRemoveWhenFarAway(false)`. Three vanilla
behaviors are actively suppressed — bullets, self-teleport, and targeting. Two vanilla behaviors are
kept because they are exactly what the design needs: the 1×1 closed hitbox, and the
20-armor-closed / 0-armor-open asymmetry.

### 4.2 `GazeService`

The correctness-critical component. Three tests, cheapest first:

1. **Dot product** of the normalized eye→creature vector against the player's look direction, versus a configured FOV cosine.
2. **Distance clamp.**
3. **Occlusion**, via `rayTraceBlocks`, evaluated *only* for candidates that passed 1 and 2.

Ordering is deliberate: the raytrace is the expensive call, and most candidates fail the dot product
for free.

**The distance clamp is a correctness constraint, not a tuning knob.** A player 200 blocks away can
hold the creature geometrically inside their view cone while the server has never sent them the
entity — freezing it for a gaze that player could not possibly have. The clamp is therefore the
**entity tracking range** (Paper's monster tracking distance, ~48 by default), not an arbitrary value.

Spectators are excluded; creative players are excluded by config.

### 4.3 `LockOnService`

Deliberately separate from `GazeService`. Freezing asks *"is anyone looking right now"*; locking asks
*"has an unbound creature been held in one player's gaze for `lock-on-ticks`"*. Separating them is
what allows any player to freeze it while only one player owns it.

### 4.4 `MovementService`

Greedy stepping, not pathfinding, per §3.3. Handles ground stepping, wall climbing, ceiling
traversal, flanking on obstruction, and sealed-volume detection.

### 4.5 `FeedingService`

Open/close control, particle streaming, XP transfer, banking, stage-threshold evaluation, and the
"only opens for someone worth eating" rule.

### 4.6 `SpawnService`

A scheduled roll per eligible player rather than a `CreatureSpawnEvent` hook — the placement rules
are too specific to filter after the fact. Requirements: overworld, night (13000–23000), **direct
sky-light access on the spawn block** (this is what "not in caves" means mechanically), a distance
band from the player, placement **outside the player's current view cone** so it never appears in
front of them, per-player cap of 1, a server-wide cap, and a minimum distance from world spawn.

### 4.7 `ArtifactService`

Artifact construction, PDC read/write, XP return, and the `TheCurse` soft-link.

### 4.8 `PersistenceService`

The entity's own PDC is the source of truth — id, victim, stage, banked XP, last-fed timestamp — so
state survives chunk unload and restart without external storage. A flat-file index in the data
folder covers recovery and creatures in unloaded chunks. Rehydration on `EntitiesLoadEvent` and on
enable.

### 4.9 Tick loop

One scheduled task, every 2 ticks, iterating the tracked set only — never a per-player world scan.
Per creature: skip if chunk unloaded → compute gazers → if any, freeze and maybe feed → if none,
close, step, test contact → update audio.

### 4.10 Testability

The risk in this plugin is geometry, and geometry is pure math. `GazeMath` (FOV/dot product),
`StepPlanner` (given a block layout, which cell next, including climbs), `StageTable` (thresholds →
stats), and artifact serialization are plain classes taking vectors and booleans, with thin Bukkit
adapters over them. This keeps the most failure-prone logic unit-testable without a server — which
matters more than usual here, because gate 7a has no headless client (§9).

---

## 5. Naming chain

| Link | Value |
|---|---|
| Slug | `blink` |
| Repository | `carmelosantana/minecraft-blink` |
| Maven `artifactId` | `blink` |
| Maven group | `org.xpfarm` |
| Releasable JAR | `blink-0.1.0.jar` |
| Updater destination | `blink.jar` |
| `plugin.yml` name | `Blink` |

Target version **0.1.0**. Paper **26.1.2 build 74**, Java **25**, `api-version: '26.1'`.
License **AGPL-3.0-or-later**. Owner **Carmelo Santana**. Website **https://xpfarm.org**,
server **play.xpfarm.org**.

---

## 6. Commands and permissions

| Command | Args | Permission |
|---|---|---|
| `/blink summon` | `[player]` | `blink.admin` |
| `/blink purge` | `[player \| all]` | `blink.admin` |
| `/blink list` | — | `blink.admin` |
| `/blink reload` | — | `blink.admin` |

`blink.admin` defaults to op. `blink.exempt` defaults to false — an exempt player can never be
bound, though their gaze still freezes it, because freezing is physics and binding is targeting.

There is deliberately **no player-facing command to locate your stalker.** Knowing where it is
defeats the plugin.

`/blink summon` is not only an event tool: it is the only way to exercise the creature during gate 7a
runtime verification, since natural spawning is rare, night-gated, and disabled by default.

---

## 7. Events, configuration, persistence, dependencies

### 7.1 Events

| Event | Purpose |
|---|---|
| `EntityDamageEvent` | Enforce the open-only vulnerability window and immunities |
| `EntityDeathEvent` | Artifact drop, state cleanup, death audio |
| `ProjectileLaunchEvent` | Suppress shulker bullets |
| `EntityTeleportEvent` | Suppress vanilla shulker self-teleport |
| `EntityTargetLivingEntityEvent` | Suppress vanilla targeting |
| `EntitiesLoadEvent` | Rehydrate from PDC |
| `PlayerQuitEvent` / `PlayerJoinEvent` | Dormancy and re-bind within timeout |
| `PlayerDeathEvent` | Release victim; creature goes `WAITING` in place |
| `PlayerChangedWorldEvent` | Dormancy on dimension change |

### 7.2 Configuration groups

`spawn` (enabled — **default false**, chance, interval, distance band, caps, sky-access requirement,
night window, min distance from world spawn), `gaze` (fov-cosine, max-distance, lock-on-ticks,
ignore-creative, ignore-spectator), `movement` (step interval per stage, climb limits, step
up/down), `feeding` (radius per stage, xp-per-second, require-xp-to-open), `stages` (health, step
interval, feed radius, particle density, kills-on-contact), `starvation` (thresholds, speed and audio
multipliers), `disorientation` (nausea ticks, darkness ticks, enabled), `contact` (effects,
durations), `audio` (every sound key with volume and pitch), `artifact` (material, name, xp return
ratio, curse integration), `lifetime` (offline dormancy minutes, max lifetime, unbind on victim
death).

### 7.3 Persistence

Entity PDC as source of truth; flat-file index in the plugin data folder for recovery and unloaded
chunks.

### 7.4 Dependencies

Hard dependencies: none. Soft-depend: `TheCurse`. **External services: none** — gate 5's contract is
satisfied trivially.

---

## 8. Acceptance checks

Written to pass or fail; gate 6 unit tests and gate 7a runtime verification are built from these.

1. `/blink summon` produces exactly one black shulker that fires no bullets and never self-teleports.
2. Held in a player's crosshair within tracking range with clear line of sight, its position is unchanged over 10s.
3. With nobody looking, position moves monotonically toward its victim at the configured step rate.
4. A wall between player and creature fails the gaze test — it moves while geometrically inside the view cone.
5. A player at 200 blocks with it in view cone does **not** freeze it (tracking-range clamp).
6. Gaze held for `lock-on-ticks` binds it and applies Nausea + Darkness exactly once; re-looking does not re-fire.
7. Closed: damage denied or negligible. Open: damage applies normally.
8. It opens only for a watcher with >0 XP inside feed radius; a 0-XP watcher never opens it.
9. XP drained from the watcher equals XP banked by the creature.
10. Crossing a stage threshold changes health, step interval, and feed radius per the stage table.
11. Contact below Gorged robs and disorients without killing; contact at Gorged kills.
12. Death drops exactly one artifact whose PDC carries the banked XP; consuming it returns XP at the configured ratio.
13. Stage, victim, and banked XP survive both a chunk unload/reload round-trip and a full server restart.
14. **It ascends a vertical wall taller than its step-up limit and crosses to the far side.**
15. **It traverses a ceiling/overhang and descends on the far side.**
16. **A player inside a fully sealed volume is never reached; the creature attaches outside and enters `WAITING`.**
17. **Starvation beyond the configured threshold measurably shortens the step interval.**
18. With `TheCurse` absent, the plugin enables cleanly and the artifact still returns XP.
19. Invalid config values log and fall back to defaults without failing startup.
20. A Bedrock player via Floodgate sees the shulker, hears the configured sounds, and receives the effects identically to Java. *(gate-12 client obligation — §9)*

---

## 9. Known limitations

Recorded honestly at planning time rather than discovered at release.

- **Visible size growth is unverified on Bedrock.** `Attribute.SCALE` resizes hitboxes on Java, but no
  confirmation was found that Geyser translates it for mobs (the one related Geyser issue, #5554,
  concerns the *player's own* first-person camera). Growth is therefore carried by stats, reach,
  particles, and audio; scale is cosmetic-only and must not be load-bearing.
- **It does not break blocks.** A deliberate grief- and protection-safety choice.
- **A fully sealed player cannot be reached.** Deliberate; it is the plugin's counterplay.
- **It cannot follow across dimensions.**
- **Bystanders may observe it jump position**, since movement is teleport-based. Acceptable: it only moves unobserved.
- **Custom audio would require an externally authored Bedrock pack** in Geyser's packs folder, outside
  this plugin and outside the updater. Shipped defaults are vanilla-only.
- **The shulker silhouette is not a perfect cube.**
- **Gate 7a cannot verify feel.** No headless client exists on this workstation, so audio mix, particle
  density, and disorientation intensity are unverifiable in automated runtime testing and carry to
  gate 12 as a client-behavior obligation requiring a named owner and date.
- **Natural spawning ships disabled.** `spawn.enabled` defaults to `false` so that installing or
  updating Blink can never surprise a server, including via an unattended updater pickup. It is
  enabled deliberately in config.

---

## 10. Deferred

- **Entombment.** "It takes you somewhere" — contact blacks you out and you wake sealed underground,
  far from home, with it outside. Explicitly deferred and likely belongs to a **different creature**
  rather than to Blink.
- **Starvation block-breaking.** Considered and rejected for v1: it grieves builds and fights
  protection plugins. If ever added, it ships config-gated and off by default.

---

## 11. Research findings

Established during planning so gate 4 does not rediscover them.

- **Blocks on entity heads are unsupported by Geyser** — "Blocks (excluding jack-o-lantern) on entity
  heads (E.G. armor stands, players)". An invisible armor stand wearing a black block is invisible to
  every Bedrock player. This eliminated the most obvious implementation.
- **Display entities are not natively translated by Geyser.** Only a third-party extension exists,
  undocumented as to versions and limitations. This eliminated the second most obvious implementation.
- **Custom player heads** can be worn on Bedrock only via Geyser-side pre-registration plus a generated
  resource pack, and carry open bug #5795 (plugin-given heads invisible until re-placed). Rejected as
  too fragile for a core visual.
- **Armor stand hitboxes do not include head items** (0.5 × 1.975; 0.5 × 0.9875 small; marker none),
  so a head-mounted block would not match its hitbox for combat.
- **A closed shulker is a true 1×1 full-block hitbox** (1×2 open), native to both editions, available
  in black, with 30 HP, **20 armor points closed and 0 open**. This asymmetry is vanilla and is the
  foundation of the kill mechanic.
- **Geyser does not convert Java resource packs**; Bedrock custom sounds require a separately authored
  pack and have open bugs (#2493). Hence the vanilla-only default palette.

### Sources

- https://geysermc.org/wiki/geyser/current-limitations/
- https://geysermc.org/wiki/geyser/custom-skulls/
- https://github.com/GeyserMC/Geyser/issues/5795
- https://github.com/GeyserMC/Geyser/issues/5554
- https://github.com/GeyserMC/Geyser/issues/2493
- https://github.com/GeyserExtensionists/GeyserDisplayEntity
- https://minecraft.wiki/w/Shulker
- https://minecraft.wiki/w/Armor_Stand
- https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/Shulker.html
- https://docs.papermc.io/paper/dev/display-entities/

### Design lineage

SCP-173 (movement gated on observation), the Weeping Angels and Mario's Boo (trope codifiers),
*It Follows* (slow, unlimited-range, single-target pursuit), and the Minecraft creepypasta "The
Stalker". Prior Minecraft art — From The Fog, Cave Dweller Reimagined, The Man From The Fog — all
succeed through restraint and long build-up rather than jump scares, which informed the "rare and
singular" spawn decision.

---

## 12. Status

**Active.** Runs every lifecycle gate and is eligible for updater management. No gates are withheld.

**Autonomy: `autonomous`.** Recorded at gate 1; this is the GitHub push authorization for the entire
pipeline, granted once in writing. Evidence requirements are unchanged and the pipeline still fails
closed.
