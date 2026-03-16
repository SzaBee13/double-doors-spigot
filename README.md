# Double Doors

[![GitHub Release](https://img.shields.io/github/v/release/SzaBee13/double-doors-spigot)](https://github.com/SzaBee13/double-doors-spigot/releases)
[![GitHub License](https://img.shields.io/github/license/SzaBee13/double-doors-spigot)](https://github.com/SzaBee13/double-doors-spigot/blob/main/LICENSE)
[![GitHub Issues](https://img.shields.io/github/issues/SzaBee13/double-doors-spigot)](https://github.com/SzaBee13/double-doors-spigot/issues)
[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/double-doors-server)](https://modrinth.com/plugin/double-doors-server)

A Bukkit/Spigot plugin that opens mirrored double doors together, with low-latency syncing and optional compatibility handling for common server stacks.

## Features

- Same-tick partner door sync (no scheduled 1-tick delay)
- Strict mirrored pair matching for doors:
  - same door type
  - same facing direction
  - opposite hinge
  - side-by-side only
- Optional recursive opening support for non-door openables (fence gates/trapdoors)
- Per-player toggle: `/doubledoors toggle`
- LuckPerms-friendly permission nodes
- GriefPrevention compatibility check for linked-door claim access
- Duplicate interaction debounce (helps packet duplication patterns seen with some Bedrock/Geyser flows)

## Compatibility

### Geyser / Floodgate

- Plugin declares soft-depends on `Geyser-Spigot` and `floodgate`.
- A short duplicate-interaction debounce window is used to avoid rapid duplicate toggles on the same block.

### LuckPerms

- Works through standard Bukkit permissions, so LuckPerms applies automatically.
- Use `doubledoors.use` to allow/deny linked opening behavior.

### GriefPrevention

- Plugin declares a soft-depend on `GriefPrevention`.
- When present, linked-door interaction is checked against claim build permission before toggling the partner door.

## Commands

- `/doubledoors reload` - reload config
- `/doubledoors toggle` - toggle behavior for yourself
- `/doubledoors server-toggle` - toggle behavior server-wide

## Permissions

- `doubledoors.use` (default: `true`)
- `doubledoors.toggle` (default: `true`)
- `doubledoors.reload` (default: `op`)
- `doubledoors.server-toggle` (default: `op`)

## Config

`src/main/resources/config.yml`

- `enableRecursiveOpening` (default: `true`)
- `recursiveOpeningMaxBlocksDistance` (default: `10`)
- `enableDoors` (default: `true`)
- `enableFenceGates` (default: `true`)
- `enableTrapdoors` (default: `true`)
- `serverWideEnabled` (default: `true`)

## Build

Requirements:

- Java 21
- Maven

Build command:

```bash
mvn -DskipTests package
```

Output jar is generated under `target/`.

## License

Licensed under the GNU General Public License v3.0.
See `LICENSE`.
