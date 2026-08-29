# Changelog

## 1.1.0 - 2026-08-29

### Added

- Temporary colors with bounded durations and automatic expiry.
- Transactional personal history and `/color undo`.
- Privacy-first public/private settings and `/color copy` with expiry preservation.
- Preset commands and administrative set, temporary, reset and info actions.
- Fully localized GUI and fallback defaults for existing language files.

### Changed

- Serialized database writes and added revision-aware optimistic rollback.
- Added schema v2 migrations, history retention and invalidation-log cleanup.
- Enforced the same granular permissions in commands and the GUI.
- Made English the explicit default and updated PostgreSQL to 42.7.12.
- Updated build/release artifacts to 1.1.0 without uploading a new Modrinth icon.

### Fixed

- Removed unsafe online-player lookup from the database callback.
- Prevented oversized profiles loaded from storage.
- Removed the Gradle 10-incompatible execution-time project lookup.

## 1.0.0

### Added

- Named colors, strict hex colors, multi-stop gradients, rainbow mode, previews, and an inventory menu.
- Direct display-name and player-list rendering with optional PlaceholderAPI integration.
- H2, SQLite, MySQL, MariaDB, and PostgreSQL storage with asynchronous optimistic updates.
- Shared-database network synchronization, Java API, English and Russian messages, and bStats.
