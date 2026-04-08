# DoubleDoors v1.3.2 Release Notes

Release date: 2026-04-08

## Highlights

- Added FastStats metrics integration on both Bukkit and Velocity components.
- Added Folia support for improved performance and compatibility on modern Minecraft server versions.

## Added

- Added FastStats
- Added `enableAnonymousTracking` to Bukkit `config.yml` and proxy `proxy-config.properties`.
- Added `enableExtendedAnonymousTracking`, `trackingCountries`, and `trackingServerLocation` config keys.
- Added region-aware scheduling and shared-state access for Folia support.

## Changed

- Updated plugin metadata to reflect new version and features.
- Updated documentation to include FastStats integration and configuration instructions.

## Fixed

- Corrected internal proxy plugin annotation metadata that previously reported an outdated version.

## Upgrade Guide

1. Back up your server and proxy plugin directories.
2. Replace existing DoubleDoors jars with the `1.3.2` artifacts.
3. Start server/proxy once and confirm plugins load successfully.
4. Verify metrics events appear in your FastStats project dashboard.

## Artifacts

- Bukkit/Spigot/Paper/Purpur: `doubledoors-bukkit-1.3.2.jar`
- Velocity proxy companion: `doubledoors-proxy-1.3.2.jar`

## Notes

- FastStats is initialized at startup and shut down on plugin disable/shutdown.
- Set `enableAnonymousTracking: false` (Bukkit) and `enableAnonymousTracking=false` (Velocity) to disable telemetry.
- Set `enableExtendedAnonymousTracking: true` to send the additional location and system metrics.
- If FastStats cannot initialize, DoubleDoors continues operating without telemetry.
