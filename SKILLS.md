# SKILLS.md — Domain Knowledge for DoubleDoors

This file packages domain-specific knowledge that AI agents and contributors need to work safely on this plugin.

---

## Skill: Door block-data model

**What you need to know before touching door logic:**

- A `Door` block occupies **two** block positions vertically (`BOTTOM` + `TOP` halves, indicated by `Bisected.Half`).
- Always normalise to the `BOTTOM` half before comparing facing/hinge — use `DoorUtil.toLowerDoorBlock()`.
- A classic "double door" has two adjacent `Door` blocks with **identical facing** and **opposite hinges** (`LEFT`/`RIGHT`).  
- After changing the partner's open state, also update the `TOP` half; `setBlockData(data, applyPhysics=false)` does **not** propagate vertically.

---

## Skill: Fence gate & trapdoor linking

**How the BFS approach works:**

- `DoorUtil.findConnectedDoors(origin, maxDistance)` does a breadth-first search from `origin` and returns all reachable blocks that share the **exact same material**.
- Used for fence gates and trapdoors (single-tall, no hinge concept).
- `maxDistance` is capped at 32 and comes from `recursiveOpeningMaxBlocksDistance` in `config.yml`.
- Doors bypass BFS entirely — they use `findMirroredDoubleDoorPartner` for the exact mirror match.

---

## Skill: Event timing

**Why you must never read block data inline at `MONITOR` priority:**

Paper 1.21 settles vanilla block-data updates *after* all event listeners have completed.
Reading `block.getBlockData()` inside the event handler returns the **pre-click / pre-power** state.

| Source | Required delay |
|---|---|
| Player right-click | 1 tick (`runTask`) |
| Redstone change | 1 tick (`runTaskLater(…, 1L)`) |
| Villager AI (open / close) | 2 ticks (`runTaskLater(…, 2L)`) |

---

## Skill: Per-player preferences

**Architecture of `PlayerPreferences`:**

- In-memory cache: `Map<UUID, PlayerPref>` — fast read on every interact event.
- Backed by `plugins/DoubleDoors/players.yml` (YAML).
- `toggleAll / toggleDoors / toggleFenceGates / toggleTrapdoors` mutate the cache, then call `saveAsync()`.
- `saveAsync()` delegates to `runTaskAsynchronously` so disk I/O never blocks the main thread.
- `save()` is also called synchronously in `onDisable` to flush unflushed changes before the process exits.
- On `/doubledoors reload`, `PlayerPreferences.load()` re-reads the YAML and rebuilds the cache.

**Precedence:** server-side config (`PluginConfig`) takes priority.  
If `enableDoors = false` in `config.yml`, door linking is off for *everyone*, regardless of individual player preferences.

---

## Skill: Protection integration (GriefPrevention)

**What `ProtectionCompat` does:**

- Resolves GriefPrevention's `dataStore` field via reflection.
- Finds the claim at the linked-block location.
- Calls `allowBuild` or `checkPermission` on the claim to decide whether the player may open the partner block.
- **Fail open** policy: any `ReflectiveOperationException` or unexpected `null` → returns `true` (allow).
- Only called for player interactions, not for redstone or villager events.

---

## Skill: Adding a new linkable block type

Step-by-step guide for adding a new block category (e.g. `_SHUTTER`):

1. Add the material-name suffix check to `DoorInteractListener.isEnabledType`.
2. Add a `boolean enableShutters` field to `PluginConfig`, load it from config, expose a getter.
3. Add a `boolean enableShutters` field to `PlayerPreferences.PlayerPref`, wire in `toggle`/getter methods.
4. Add `enableShutters: true` to `src/main/resources/config.yml` with a descriptive comment.
5. If the new type is two-tall (like `Door`), add its own `findMirroredPartner` helper in `DoorUtil`.
6. In `RedstoneListener.applyConnectedState`, check `originData instanceof <NewBlockDataType>` before falling through to BFS.
7. Bump the version and write a release note.

**Translation note:** When you add a new message key (e.g., `cmd.toggle.shutters.enabled`), update **only** `defaults.json` and `en_US.json`. Do not add it to other regional locales unless the user explicitly asks. Other locales will fall back to the English US text.
