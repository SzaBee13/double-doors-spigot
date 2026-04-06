# DoubleDoors v1.3.0 Release Notes

Release date: 2026-04-05 (yyyy-mm-dd)

## Highlights

- Introduced a multi-module build structure with separate Bukkit and proxy artifacts.
- Added optional shared SQL storage for player preferences and claim settings.
- Added a new Velocity proxy companion plugin that writes heartbeat presence to shared SQL.
- Added one-time YAML to SQL migration support when SQL mode is enabled.

## Added

- New module: `bukkit` (server plugin artifact).
- New module: `proxy` (Velocity companion artifact).
- New Bukkit storage layer for shared SQL:
  - Player preferences table (`dd_player_preferences`)
  - Claim settings table (`dd_claim_settings`)
  - Proxy presence table (`dd_proxy_presence`)
  - Metadata table (`dd_meta`)
- New migration utility that can import existing `players.yml` and `claims.yml` into SQL.
- New Bukkit config keys under `sql`:
  - `enabled`
  - `jdbcUrl`
  - `username`
  - `password`
  - `migrateFromYaml`
  - `proxyHeartbeatMaxAgeSeconds`
- New proxy config keys:
  - `sql.enabled`
  - `sql.jdbcUrl`
  - `sql.username`
  - `sql.password`
  - `sql.proxyId`
  - `sql.heartbeatSeconds`

## Changed

- Repository/build layout moved from single-module to Maven parent with module children.
- Build output now produces distinct jars for Bukkit and proxy components.
- Bukkit can now detect recent proxy presence through SQL heartbeat and apply Geyser/Floodgate-related behavior consistently across network setups.

## Fixed

- No specific standalone bug-fix commits were tagged for this release; the release mainly focuses on architecture and storage enhancements.

## Breaking Changes

- Build and packaging changed from a single artifact to module-specific artifacts.
- Existing deployment automation that expected the old artifact path/name must be updated.
- SQL mode introduces new required configuration correctness for shared database usage.

## Upgrade Guide

1. Back up your current plugin jars and the `plugins/DoubleDoors` directory.
2. Replace the old Bukkit jar with `doubledoors-bukkit-1.3.0.jar`.
3. If you run Velocity and want proxy heartbeat detection, also deploy `doubledoors-proxy-1.3.0.jar`.
4. Configure SQL in Bukkit `config.yml` (`sql.enabled=true` and matching JDBC settings).
5. Configure proxy `config.properties` with the same SQL database if proxy heartbeat is desired.
6. Keep `sql.migrateFromYaml=true` for first startup to import legacy YAML data.
7. Start server/proxy and verify startup logs for SQL schema initialization and migration completion.
8. After successful migration, keep backups of old YAML files until you confirm data is intact.

## Artifacts

- Bukkit/Spigot/Paper/Purpur: `doubledoors-bukkit-1.3.0.jar`
- Velocity proxy companion: `doubledoors-proxy-1.3.0.jar`

## Notes

- For shared proxy/server SQL setups, use an absolute SQLite path or MySQL to ensure both processes use the same database.
- If SQL is disabled, legacy YAML persistence remains active.
