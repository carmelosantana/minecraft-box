# New or Edited Plugin Checklist

Copy this file for one plugin and replace every `<...>` field. Leave an unchecked box with a short explanation when a gate is not complete; do not silently remove inapplicable checks.

- Plugin name: `The Box`
- Slug: `box`
- Repository: `carmelosantana/minecraft-box`
- Owner: `Carmelo Santana`
- Target version: `0.1.0`
- Paper version: `26.1.2 build 74`
- Java version: `25`
- Updater destination: `box.jar`
- External services: `none`
- Status: `active`
- Autonomy: `autonomous`

Maven `artifactId`: `box`. `plugin.yml` name: `TheBox`. Releasable JAR: `box-<version>.jar`.
Maven group: `org.xpfarm`. Package root: `org.xpfarm.box`. `api-version: '26.1'`.
No prior release — this is a new plugin at gate 1.

The full design lives at `docs/superpowers/specs/2026-08-17-box-design.md`, approved
`2026-08-17`. This checklist is the pipeline's source of truth; the spec is the reasoning behind
it. Where they disagree, the spec is stale and this file wins.

Planned as `Blink` and renamed to `The Box` before any code existed. No `blink` identifier ever
reached a repository, artifact, or manifest.

## 1. Scope

- [x] Status is explicitly recorded as active, experimental, or excluded. `active` — runs every gate, eligible for updater management, no gates withheld.
- [x] Purpose, commands, events, permissions, configuration, persistence, and acceptance checks are defined. See below; captured from the `superpowers:brainstorming` interview on `2026-08-17`.
- [x] Known limitations and any intentionally withheld gates are recorded. See Known limitations; no gates are withheld.

### Player-facing purpose

A rare black block appears above ground at night. If you look at it, it notices you — and from then
on it follows you across the world, forever, but only ever while nobody is watching it. Staring at
it holds it still, which feels safe; it isn't, because a held creature opens up and eats your
experience, growing on what it takes. The cursed artifact visible inside its open shell is the
reason you keep looking. It is only killable in the moment it is feeding on somebody.

### Commands

| Command | Arguments | Who |
| --- | --- | --- |
| `/box summon` | `[player]` | `box.admin` |
| `/box purge` | `[player \| all]` | `box.admin` |
| `/box list` | — | `box.admin` |
| `/box reload` | — | `box.admin` |

No player-facing command locates an active creature; knowing where it is defeats the plugin.
`/box summon` is also the only practical way to exercise the creature at gate 7a, since natural
spawning is rare, night-gated, and ships disabled.

`/box` does not collide with any of the 23 commands registered across the current roster (checked
`2026-08-17`).

### Events

| Event | Why |
| --- | --- |
| `EntityDamageEvent` | Enforce the open-only vulnerability window and immunities |
| `EntityDeathEvent` | Artifact drop, state cleanup, death audio |
| `ProjectileLaunchEvent` | Suppress vanilla shulker bullets |
| `EntityTeleportEvent` | Suppress vanilla shulker self-teleport |
| `EntityTargetLivingEntityEvent` | Suppress vanilla targeting |
| `EntitiesLoadEvent` | Rehydrate creature state from PDC on chunk load |
| `PlayerQuitEvent` / `PlayerJoinEvent` | Dormancy on logout, re-bind within timeout |
| `PlayerDeathEvent` | Release or retain binding per `lifetime.unbind-on-victim-death` |
| `PlayerChangedWorldEvent` | Dormancy on dimension change (it cannot follow through portals) |

Natural spawning deliberately does **not** hook `CreatureSpawnEvent` — placement requires sky-light
access, a distance band, and placement outside the player's view cone, which is a construction
problem rather than a filtering one. A scheduled per-player roll owns it instead.

### Permissions

| Node | Default | Gates |
| --- | --- | --- |
| `box.admin` | op | All four subcommands |
| `box.exempt` | false | Holder can never be bound as a victim. Their gaze still freezes the creature — freezing is physics, binding is targeting. |

### Configuration

Groups: `spawn` (enabled — **defaults to `false`**, chance, check interval, distance band, per-player
and server caps, sky-access requirement, night window, minimum distance from world spawn), `gaze`
(fov-cosine, max-distance, lock-on-ticks, ignore-creative, ignore-spectator), `movement` (per-stage
step interval, climb limits, step up/down), `feeding` (per-stage radius, xp-per-second,
require-xp-to-open), `stages` (health, step interval, feed radius, particle density,
kills-on-contact), `starvation` (thresholds, speed and audio multipliers), `disorientation` (nausea
ticks, darkness ticks, enabled), `contact` (radius, effects, durations), `audio` (every sound key
with volume and pitch), `artifact` (material, name, xp return ratio, curse integration), `lifetime`
(offline dormancy minutes, max lifetime, unbind-on-victim-death).

Validation: every value range-checked on load; invalid values are logged and replaced with defaults.
Configuration never fails server or plugin startup.

`spawn.enabled: false` by default is deliberate. The Box is the first plugin in this roster that acts
on every player unprompted, so installing or updating it — including via an unattended updater
pickup — must be inert until an operator turns it on.

### Persistence

The creature entity's own PDC is the source of truth: id, bound victim UUID, stage, banked XP
(experience **points**, not levels), and last-fed timestamp. This survives chunk unload and server
restart without external storage. A flat-file index in the plugin data folder covers recovery and
creatures sitting in unloaded chunks. The cursed artifact carries its banked XP in its own item PDC.

No database. No external storage.

### Dependencies

Hard dependencies: none. Soft-depend: `TheCurse` — when present, the cursed artifact additionally
gains curse-starting behavior; when absent, it remains a functional trophy that returns XP. No load
order requirement beyond the soft-depend.

### External integrations

`none`.

### Acceptance checks

1. `/box summon` produces exactly one black shulker that fires no bullets and never self-teleports.
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
14. It ascends a vertical wall taller than its step-up limit and crosses to the far side.
15. It traverses a ceiling/overhang and descends on the far side.
16. A player inside a fully sealed volume is never reached; the creature attaches outside and waits.
17. Starvation beyond the configured threshold measurably shortens the step interval.
18. With `TheCurse` absent, the plugin enables cleanly and the artifact still returns XP.
19. Invalid config values log and fall back to defaults without failing startup.
20. A non-victim player standing in the creature's path is never pursued, struck, or killed by it.
21. On victim death with `unbind-on-victim-death: true`, it reverts to dormant and can be locked again.
22. XP transfer is exact in experience points across a full drain-kill-consume cycle, with no rounding loss at level boundaries.
23. A Bedrock player via Floodgate sees the shulker, hears the configured sounds, and receives the effects identically to Java. **Gate 12 client obligation — not verifiable at gate 7a.**

Checks 1–22 are verifiable by gate 6 unit tests and gate 7a RCON runtime verification. Check 23 is
not: no headless client exists on this workstation, so it carries to gate 12 with a named owner and
date.

### Known limitations

Settled by research during the interview rather than left to be discovered at gate 4:

- **Blocks on entity heads are unsupported by Geyser** — "Blocks (excluding jack-o-lantern) on entity heads (E.G. armor stands, players)". An invisible armor stand wearing a black block is invisible to every Bedrock player. This eliminated the most obvious implementation.
- **Display entities are not natively translated by Geyser**; only a third-party extension exists, undocumented as to versions and limitations. This eliminated the second most obvious implementation.
- **Custom player heads** are wearable on Bedrock only via Geyser-side pre-registration plus a generated resource pack, and carry open Geyser bug #5795. Rejected as too fragile for a core visual.
- **Visible size growth is unverified on Bedrock.** `Attribute.SCALE` resizes hitboxes on Java, but no confirmation was found that Geyser translates it for mobs. Growth is carried by stats, reach, particles, and audio; scale is cosmetic-only and must never be load-bearing.
- **It never breaks blocks.** Deliberate grief- and protection-safety choice.
- **A fully sealed player cannot be reached.** Deliberate — it is the plugin's only counterplay.
- **It cannot follow across dimensions.**
- **Bystanders may see it jump position**, since movement is teleport-based. Acceptable: it only moves unobserved.
- **Custom audio would require an externally authored Bedrock pack** in Geyser's packs folder, outside this plugin and outside the updater. Shipped defaults are vanilla sound events only.
- **The shulker silhouette is not a perfect cube.**
- **Gate 7a cannot verify feel.** Audio mix, particle density, and disorientation intensity are unverifiable in automated runtime testing (no headless client on this rig) and carry to gate 12.
- **Deferred, not withheld:** entombment ("it takes you somewhere") is deferred to a future creature; starvation block-breaking was considered and rejected for v1.

**No gates are withheld.** Status is `active` and the plugin runs the full pipeline.

## 2. Repository

- [ ] Repository is `carmelosantana/minecraft-<slug>` with an SSH `origin` and `main` branch.
- [ ] Existing user-owned worktree changes were identified and preserved.
- [ ] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation.

## 3. Metadata

- [ ] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent.
- [ ] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present.
- [ ] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented.
- [ ] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented.
- [ ] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent.
- [ ] No secrets committed in source, defaults, tests, logs, history, or documentation.

## 4. Compatibility

- [ ] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '26.1'`, matching the API compiled against (see `PLUGIN_LIFECYCLE.md` §4 — a lower value opts the JAR into Paper's `Commodore` bytecode rewrites).
- [ ] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared.
- [ ] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior.

## 5. External services

- [ ] External integrations are disabled by default or require explicit configuration and have bounded timeouts.
- [ ] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable.
- [ ] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets.

## 6. Tests and build

- [ ] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable.
- [ ] `PluginDescriptorTest` parses `plugin.yml` and `config.yml` with SnakeYAML and asserts `name`, `main`, a `String`-typed `api-version`, a fully-substituted `version`, every command the code looks up, every permission the code checks, and the declared soft dependencies.
- [ ] `mvn --batch-mode --no-transfer-progress clean verify` succeeds.
- [ ] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded.

## 7. Matrix

- [ ] Fresh-volume [Legendary Java Minecraft Geyser Floodgate stack](https://github.com/TheRemote/Legendary-Java-Minecraft-Geyser-Floodgate) test covers every updater-managed plugin.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately.
- [ ] Paper, Geyser, Floodgate, and ViaVersion start successfully together.
- [ ] Affected commands, permissions, persistence, and configuration reload were exercised over RCON with no server-wide hot reload.
- [ ] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable.

## 8. CI/CD

- [ ] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior.
- [ ] Successful main Actions run is recorded before tagging.
- [ ] Workflow permissions contain no broader access than the documented contract.

## 9. Release

- [ ] Semantic version matches the POM, plugin metadata, and `v<version>` tag.
- [ ] Successful tag Actions run and GitHub release are recorded.
- [ ] Release contains exactly one updater-matching JAR plus `SHA256SUMS.txt` and no `original-*` JAR.
- [ ] Downloaded release assets pass `sha256sum --check SHA256SUMS.txt`.

## 10. Updater

- [ ] Updater manifest/tests cover repository, destination, anchored asset regex, legacy globs, enabled state, and optional pin.
- [ ] Fresh install, upgrade, no-op, legacy archival, endpoint failure, and checksum failure behaviors pass.
- [ ] Updater dry-run uses a disposable directory and never a production plugin directory.
- [ ] Failure retains the installed JAR and default fail-open behavior permits Minecraft startup.

## 11. Deployment

Not a gate. Deployment is updater pickup: a verified release plus a correct manifest entry is all
this lifecycle owes. Leaving this section entirely unticked is the normal resting state and blocks
nothing — not release, not enrolment, not handoff.

- [ ] Enrolment confirmed live and correct: release sound, manifest entry on `origin/main`, gate 10 genuinely completed.
- [ ] Deployment evidence recorded, if and only if an operator relayed some. Otherwise note "enrolled, not known to be deployed" and leave unticked.

## 12. Handoff

- [ ] Current-state documentation refreshed with release, CI, updater, deployment, and local pending state.
- [ ] Known limitations, skipped checks, configuration or migration notes, rollback guidance, and follow-up owner are recorded.
- [ ] Evidence distinguishes source commit, published tag/release, updater state, and deployed state without exposing secrets.
- [ ] Client play-test obligation recorded with a named owner and a target date: `<owner>` / `<date>`.
- [ ] Client play-test outcome recorded once performed, covering Java join, Bedrock join, and any form, inventory, or rendered item behavior this plugin introduces. Leave unchecked with the owner and date above until the team has run it; an unchecked box here does not block a release, but an unrecorded obligation is a gate 12 failure.
- [ ] Public deployment reachability confirmed during that pass: `play.xpfarm.org` reaches the intended Java and Bedrock entry points.
