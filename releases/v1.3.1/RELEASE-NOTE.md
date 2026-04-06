# DoubleDoors v1.3.1 Release Notes

Release date: 2026-04-06

## Highlights

- Fixed Crowdin locale alias handling for Latin American Spanish (`es_419`).
- Synchronized project and plugin metadata versions to `1.3.1`.

## Added

- Added `es-419` and `es_419` language aliases in translation defaults, mapped to the existing `es_409` bundle.

## Changed

- Updated Maven parent and module versions to `1.3.1`.
- Updated Bukkit plugin descriptor version to `1.3.1`.
- Updated proxy plugin descriptor version to `1.3.1`.

## Fixed

- Crowdin locale compatibility issue where `es_419` could bypass alias resolution and fail to load the intended Spanish LATAM translation fallback.

## Breaking Changes

- None.

## Upgrade Guide

1. Back up your server and proxy plugin directories.
2. Replace existing DoubleDoors jars with the `1.3.1` artifacts.
3. Start server/proxy once and confirm plugins load successfully.
4. If you use Spanish LATAM localization, verify translated messages now resolve as expected.

## Artifacts

- Bukkit/Spigot/Paper/Purpur: `doubledoors-bukkit-1.3.1.jar`
- Velocity proxy companion: `doubledoors-proxy-1.3.1.jar`

## Notes

- This is a patch release focused on translation compatibility and version metadata consistency.
