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

- [x] Repository is `carmelosantana/minecraft-box` with an SSH `origin` and `main` branch. Created public `2026-08-17` (`https://github.com/carmelosantana/minecraft-box`, visibility `PUBLIC` — the updater fetches release assets unauthenticated). Origin is `git@github.com:carmelosantana/minecraft-box.git`; `main` pushed and tracking.
- [x] Existing user-owned worktree changes were identified and preserved. None existed — the local repository was initialised by this pipeline at gate 1 and contained only the design spec and this checklist.
- [x] No `herobrinesystems` references remain in source, metadata, workflows, remotes, or documentation. `rg -n 'herobrinesystems' . --hidden -g '!target/**' -g '!.git/**'` returns exactly one hit: line 174 of this checklist, which is this check's own text, byte-identical to line 26 of the pristine `templates/NEW_PLUGIN_CHECKLIST.md`. No reference exists in source, metadata, workflows, or the remote.

## 3. Metadata

- [x] AGPL-3.0-or-later `LICENSE` and Maven license metadata are present and consistent. Full 661-line AGPL-3.0 text; `pom.xml` declares "GNU Affero General Public License v3.0 or later" pointing at `https://www.gnu.org/licenses/agpl-3.0.html`. Source files carry the matching AGPL header.
- [x] `https://xpfarm.org` metadata and Carmelo Santana author metadata are present. `pom.xml` `<url>` and `<developers>`; `plugin.yml` `website:` and `author:`; repository homepage set to `https://xpfarm.org`.
- [x] `play.xpfarm.org` is recorded as the public Minecraft server hostname where server identity is documented. `README.md` "Playing" section, noting the one hostname serves both Java and Bedrock clients.
- [x] New work uses the `org.xpfarm` Maven group, or an existing-coordinate compatibility decision is documented. `org.xpfarm:box:0.1.0`. No carve-out applied — this is new work.
- [x] Repository slug, artifact, releasable JAR, updater destination, and `plugin.yml` names are consistent. slug `box` → repo `carmelosantana/minecraft-box` → `<artifactId>box</artifactId>` → built `target/box-0.1.0.jar` → updater destination `box.jar` → `plugin.yml` `name: TheBox`. Verified against the chain gate 1 recorded; nothing was renamed to fit.
- [x] No secrets committed in source, defaults, tests, logs, history, or documentation. No credentials, tokens, endpoints, or production configuration exist in the tree — the plugin has no external integrations at all.

## 4. Compatibility

- [x] Java 25/Paper 26.1.2 build 74 compile succeeds and `plugin.yml` uses `api-version: '26.1'`, matching the API compiled against. `mvn clean verify` compiles clean on Temurin 25 against `paper-api 26.1.2.build.74-stable`; the shaded JAR's embedded `plugin.yml` shows `api-version: '26.1'`. Confirmed live at gate 7a: Paper `26.1.2` (protocol 775) loaded and enabled the plugin.
- [x] Hard dependencies, soft dependencies, optional APIs, and load ordering were reviewed and declared. Hard deps: none. Soft-depend: `TheCurse` (declared `softdepend: [TheCurse]`). No `depend`/`loadbefore`/`loadafter` needed. Verified live: the plugin enabled cleanly with `TheCurse` **absent** — `The Box enabled; restored 0 creatures from loaded chunks` (acceptance check 18 enable clause).
- [x] Geyser/Floodgate/ViaVersion review covers Bedrock-safe input, UI, inventory, identity, and protocol behavior. Every mechanic uses only server-side vanilla primitives (a black `Shulker`, potion effects, titles, `playSound`, `spawnParticle`, PDC, `teleport`) — no NMS, no client packets, no display entities, no blocks-on-heads. The Geyser research (§1 Known limitations) drove this. `box.exempt` gates binding, not freezing. Bedrock rendering/audio/parity is a **gate-12 client obligation** (no headless client here). Live: `floodgate`, `Geyser-Spigot`, `ViaVersion`, and `TheBox` all started green together on the Legendary stack.

## 5. External services

- [x] External integrations are disabled by default or require explicit configuration and have bounded timeouts. **No external services** — `External services: none` in the header, no outbound calls anywhere in the code. Gate 5's contract is satisfied vacuously.
- [x] Ollama/Umami-style external endpoints are optional and failure-tolerant when applicable. Not applicable — no Ollama, Umami, or any other outside-service integration.
- [x] Endpoint failure cannot fail server/plugin startup, and diagnostics redact secrets. Not applicable — no endpoints, no secrets. Nothing the plugin does can hang on or leak an external call.

## 6. Tests and build

> Gate 6 now satisfied by the full implementation (16 SDD tasks, each implemented and Opus-reviewed,
> plus a whole-branch review). The scaffold note below is superseded.

- [x] Unit tests cover separable logic, configuration, serialization, permissions, and failure paths where applicable. **170 tests**, all green. Pure-logic cores are exhaustively unit-tested — config validation + per-key fall-back, stage table, starvation curve, gaze cone/distance math, step planner (climb/ceiling/seal), XP point math, PDC codec round-trip, registry, feeding qualify/drain accumulator, movement pacing monotonicity, lock-on timing, artifact redemption gate. Adapter decision-predicates are extracted and tested; live Bukkit paths are recorded as gate-7a/gate-12 obligations.
- [x] `PluginDescriptorTest` parses `plugin.yml` and `config.yml` with SnakeYAML and asserts `name`, `main`, a `String`-typed `api-version`, a fully-substituted `version`, every command the code looks up, every permission the code checks, and the declared soft dependencies. Present and asserts `TheBox` / `org.xpfarm.box.BoxPlugin` / `api-version '26.1'` / substituted version / command `box` / permissions `box.admin` + `box.exempt` / softdepend `TheCurse`.
- [x] `mvn --batch-mode --no-transfer-progress clean verify` succeeds. `BUILD SUCCESS`, `Tests run: 170, Failures: 0, Errors: 0`, on Java 25 / `paper-api 26.1.2.build.74-stable` (verified on `main` @ `e56191e`).
- [x] The shaded releasable JAR and embedded `plugin.yml` were inspected; `original-*` JARs are excluded. `target/box-0.1.0.jar` embeds `plugin.yml` with `name: TheBox`, `main: org.xpfarm.box.BoxPlugin`, `api-version: '26.1'`, `softdepend: [TheCurse]`. The `original-box-0.1.0.jar` pre-shade intermediate is excluded from release assets by the workflow's `!target/original-*.jar` filter.

## 7. Matrix

### 7a — single-plugin runtime verification (this skill)

- [x] Paper, Geyser, Floodgate, and ViaVersion start successfully together. Booted a fresh disposable Legendary stack (`scripts/test-stack.sh up`, `2026-08-17`). The rig confirmed Paper `Done (15.241s)`, the Java port answered a real Minecraft handshake (Paper 26.1.2, protocol 775), and RCON `plugins` listed `floodgate`, `Geyser-Spigot`, `TheBox`, and `ViaVersion` all **green** together.
- [x] Affected commands, permissions, persistence, and configuration reload were exercised over RCON with no server-wide hot reload. `TheBox` loaded and enabled cleanly (`The Box enabled; restored 0 creatures from loaded chunks`, no exceptions). Over RCON: `/box` → usage; `/box list` → "No Box creatures are tracked."; `/box reload` → "The Box configuration reloaded." (plugin-scoped reload, re-wired services, no server hot reload); `/box purge all` → "Purged 0 creatures."; `/box summon` from console → graceful "Console must name a target" (summon is player-targeted — see limitations). **Config fall-back verified live** (check 19): injecting `spawn.chance: 2.0` and reloading logged `[TheBox] Invalid config value for 'spawn.chance': 2.0 (must be 0.0 - 1.0); falling back to default 0.08` and the plugin stayed green — a bad value never disables it. **Unparseable-YAML reload** rejected cleanly ("keeping the previous configuration") and the plugin survived on its last-good config. **Enable-without-`TheCurse`** confirmed (check 18 enable clause): `TheCurse` was absent from the stack and `TheBox` enabled clean.
- [x] Ollama and Umami unavailable-endpoint tests keep the server and plugins available when applicable. **Not applicable** — no external services.

### 7b — full-roster matrix (out-of-band, NOT required for release)

- [ ] Fresh-volume Legendary stack test covers every updater-managed plugin. **Withheld — out-of-band.** Gate 7b belongs to `minecraft-plugin-matrix` and is triggered by an updater manifest change or a stack version bump, not by this `dev` run. Enrolling The Box (gate 10) will itself be a 7b trigger (roster 19 → 20); it does not block this release.
- [ ] Each updater-managed plugin's manifest `enabled` value, default state, and expected fresh-volume behavior are recorded separately. Withheld with 7b above.

### Gate 7a could not reach — carry to gate 12 as the play-test obligation

The Box's entire creature loop is player-driven, and no client attaches to the gate-7a stack. `/box summon` requires an online player target, so **no creature could be spawned headlessly** — every live behavior below is unverified at runtime and must be play-tested on `play.xpfarm.org` by a named owner at gate 12:

- **Spawn/appearance:** a summoned creature is a black shulker with AI off, silent, no bullets, no self-teleport (check 1).
- **Gaze/freeze:** crosshair holds it still within tracking range; a wall occludes; a 200-block player does not freeze it (checks 2, 4, 5).
- **Lock-on:** continuous gaze binds after `lock-on-ticks` with a one-time Nausea+Darkness sting and title; no re-fire; `box.exempt` never bound; bind-time per-player cap (checks 6, 20).
- **Feeding/kill:** opens only for a watcher with XP; drained == banked; closed damage denied, open damage applies; growth stages change stats; contact robs below Gorged and kills at Gorged (checks 7, 8, 9, 10, 11).
- **Movement:** steps toward victim unobserved; climbs a wall; traverses a ceiling; a sealed volume yields WAITING; starvation shortens the interval (checks 3, 14, 15, 16, 17).
- **Persistence:** stage/victim/XP survive chunk unload and restart on a live world (check 13 — codec round-trip is unit-proven, but the live entity-PDC path is not).
- **Death/dimension:** victim death → DORMANT/lockable; no cross-dimension pursuit (check 21).
- **Artifact redemption:** right-clicking the dropped artifact returns XP and (with `TheCurse` present) starts a curse — **including the flagged `RIGHT_CLICK_AIR` vs `RIGHT_CLICK_BLOCK` behavior** (the interact handler uses `ignoreCancelled = true`; verify air-clicks are not suppressed) (checks 12, 18 return clause, 22).
- **Bedrock parity + feel (check 23):** shulker appearance, all configured vanilla sounds (note: `haunting`/Disc-11 is configured but not yet scheduled — a documented deferral), particle density, and disorientation intensity, all identical for a Floodgate Bedrock client.

Everything the gate *could* reach — enable, command surface, config reload + fall-back, unparseable-config safety, enable-without-`TheCurse` — passed.

## 8. CI/CD

- [x] Identical standard plugin Actions workflow is installed with the required triggers, Temurin 25 build, artifact, checksum, and release behavior. `.github/workflows/build.yml` matches `GITHUB_ACTIONS.md` exactly — triggers on push to `main`, `v*` tags, PRs targeting `main`, and `workflow_dispatch`; `actions/checkout@v7`; `actions/setup-java@v5` with Temurin 25 and the Maven cache; `mvn --batch-mode --no-transfer-progress clean verify`; checksums generated from inside `target/` so `SHA256SUMS.txt` records bare filenames rather than `target/`-prefixed paths; `actions/upload-artifact@v7` excluding `original-*`; tag runs use `gh release view` / `gh release create` then upload with `--clobber`.
- [ ] Successful main Actions run is recorded before tagging. **Not this skill's to tick** — `minecraft-plugin-release` owns gate 8b, and it must verify the run for the commit actually being tagged, which does not exist yet. Recorded as scaffold evidence only: the first two `main` pushes both concluded `success` — run `32073852618` on `0e7b423` and run `32073960252` on `9995c20`. Later pushes are not covered by this note.
- [x] Workflow permissions contain no broader access than the documented contract. `permissions: contents: write` and nothing else.

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
