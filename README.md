# The Box

A rare black block appears above ground at night. If you look at it, it notices you — and from
then on it follows you across the world, forever, but only ever while nobody is watching it.

Staring at it holds it still, which feels safe. It isn't: a held creature opens up and eats your
experience, growing on what it takes. The cursed artifact visible inside its open shell is the
reason you keep looking.

It is only killable in the moment it is feeding on somebody.

## Playing

Join **`play.xpfarm.org`** — the same hostname serves both Java and Bedrock clients.

You do not need to do anything to encounter The Box, and there is no command to find one. That is
the point.

**What you should know**

- It only moves while nobody is looking at it. Any player's gaze holds it still, not just yours.
- Looking at it from close range lets it feed on your experience levels.
- It climbs walls and crosses ceilings. A wall without a roof will not stop it.
- A genuinely sealed room will. No opening, no gap, door shut.
- It cannot follow you into the Nether or the End.
- Dying escapes it. It is still out there.

## Server operators

**Natural spawning ships disabled.** `spawn.enabled` defaults to `false` in `config.yml`, so
installing or updating this plugin never changes an existing server's behavior until you turn it
on deliberately. Use `/box summon` to try it out without enabling natural spawns.

### Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/box summon [player]` | Spawn one creature near a player | `box.admin` |
| `/box purge <player \| all>` | Remove active creatures | `box.admin` |
| `/box list` | List active creatures, stage, victim, location | `box.admin` |
| `/box reload` | Reload configuration | `box.admin` |

### Permissions

| Node | Default | Gates |
| --- | --- | --- |
| `box.admin` | op | All subcommands |
| `box.exempt` | false | Holder can never be bound as a victim. Their gaze still freezes the creature. |

### Compatibility

Built against Paper 26.1.2 build 74 on Java 25, and validated against the Legendary Java Minecraft
Geyser Floodgate stack. Every mechanic uses vanilla entities, effects, and sound events, so Bedrock
players see and hear the same thing Java players do with **no resource pack required**.

Soft-depends on [The Curse](https://github.com/carmelosantana/minecraft-curse). When it is
installed, the cursed artifact also gains curse-starting behavior. When it is absent, the artifact
remains a functional trophy and nothing fails.

## Building

Requires Java 25 and Maven.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

## Documentation

- `docs/PLUGIN_CHECKLIST.md` — lifecycle state and the pipeline's source of truth
- `docs/superpowers/specs/2026-08-17-box-design.md` — the full design and the research behind it

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE).

Copyright (C) 2026 Carmelo Santana · [xpfarm.org](https://xpfarm.org)
