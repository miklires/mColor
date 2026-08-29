<div align="center">
  <img alt="mColor" width="160" src="https://raw.githubusercontent.com/miklires/mColor/main/docs/assets/mcolor-icon.png">
  <h1>mColor</h1>
  <p>Secure player-name colors, multi-stop gradients, presets, history, and network synchronization.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mBadges/main/docs/assets/folia-available.png"></a>
  </p>

  <p>
    <a href="https://github.com/miklires/mColor"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/project/mcolor"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
  </p>

  <p>
    <a href="https://bstats.org/plugin/bukkit/mColor/33355"><img alt="bStats" src="https://img.shields.io/badge/bStats-33355-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mColor/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mColor?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

mColor applies colors directly through Adventure, so PlaceholderAPI is optional. H2 and safe defaults work immediately, while shared SQL storage, presets, temporary event colors and public copying can be enabled deliberately.

## What it does

- Strict `#RRGGBB` input, configurable named colors, rainbow mode and gradients with up to sixteen stops.
- Localized inventory pages for basic colors, adjacent gradients and server-defined presets.
- Direct display-name and player-list rendering on Paper, Purpur and Folia.
- Permanent or temporary colors with automatic expiry for online and offline players.
- Personal history with an atomic `/color undo` operation, including restoration to no color.
- Privacy-first `/color copy`: copying is private by default and must be enabled by the target player.
- Administrative set, temporary, reset and inspection commands for online players.
- Optimistic in-memory updates with serialized SQL writes and revision-aware rollback.
- H2 by default, plus SQLite, MySQL, MariaDB and PostgreSQL with automatic schema upgrades.
- Optional shared-database polling for server networks.
- English and Russian messages, Java Services API, PlaceholderAPI, bStats and granular permissions.

## Quick start

1. Put `mColor-1.1.0.jar` in the server's `plugins` directory and restart.
2. Grant named colors or special effects through your permissions plugin.
3. Run `/color gui` or `/color red`.
4. Adjust palettes, presets and limits in `plugins/mColor/config.yml` when needed.

The default H2 configuration requires no credentials or external services. If explicitly configured external storage is unavailable, mColor starts in memory-only mode and logs the cause.

## Player commands

| Command | Description |
|---|---|
| `/color gui` | Open the localized color menu |
| `/color <name>` or `/color #RRGGBB` | Apply a solid color |
| `/color gradient <color1> <color2> [color3...]` | Apply a multi-stop gradient |
| `/color preset <name>` | Apply a configured gradient preset |
| `/color rainbow` | Apply the rainbow profile |
| `/color temporary <duration> <profile...>` | Apply a temporary color; durations use `m`, `h`, `d`, or `w` |
| `/color preview [profile...]` | Preview without changing the saved profile |
| `/color history` | Show recent settings and expiry information |
| `/color undo` | Atomically restore the previous setting |
| `/color privacy <public\|private>` | Allow or deny other players copying the active profile |
| `/color copy <online-player>` | Copy a public active profile and its remaining expiry |
| `/color reset` | Restore the normal name |

Administrators can use `/mcolor set <player> <profile...>`, `/mcolor temporary <player> <duration> <profile...>`, `/mcolor reset <player>`, `/mcolor info <player>`, and `/mcolor reload`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `mcolor.use` | everyone | Use `/color` and the GUI |
| `mcolor.color.<name>` | unset | Use one configured named color |
| `mcolor.color.*` | operators | Use every named color |
| `mcolor.hex` | operators | Use arbitrary strict hex colors |
| `mcolor.gradient` | operators | Build custom gradients |
| `mcolor.preset.<name>` | unset | Use one configured preset |
| `mcolor.preset.*` | operators | Use every preset |
| `mcolor.rainbow` | operators | Use rainbow mode |
| `mcolor.temporary` | operators | Apply temporary profiles |
| `mcolor.copy` | everyone | Copy public profiles |
| `mcolor.copy.bypass` | operators | Ignore target copy privacy |
| `mcolor.history` | everyone | View and restore personal history |
| `mcolor.admin` | operators | Manage players and reload configuration |

The GUI enforces the same named-color, preset, gradient and rainbow permissions as commands.

## Language and configuration

English is used by default. Set `language.default: ru_RU` and run `/mcolor reload` to switch to Russian. Set `language.per-player: true` only when the interface should follow each player's Minecraft locale.

`limits.maximum-temporary-days` bounds temporary profiles. History display size, retention and expiry checks are documented under `history`. Copying is private by default through `privacy.copy-public-by-default: false`.

## PlaceholderAPI and Java API

- `%mcolor_name%` — legacy-colored player name
- `%mcolor_name_mm%` — escaped MiniMessage rendering
- `%mcolor_name_stripped%` — plain player name
- `%mcolor_color%`, `%mcolor_color2%` — first and second colors
- `%mcolor_gradient%` — all render colors separated by commas
- `%mcolor_has_color%` — `yes` or `no`
- `%mcolor_of_PlayerName%` — another online player's colored name

All placeholders use the in-memory cache and never query SQL on the server thread. Other plugins can obtain `MColorService` through Bukkit's services manager.

## Storage and networks

The schema records active profiles, expiry, privacy, bounded history and cross-server invalidations. Changes are transactional. SQL tasks are serialized to preserve command order, while revision checks prevent an older failed write from rolling back a newer selection.

For a network, choose MySQL, MariaDB or PostgreSQL on every backend, use the same database, assign a unique `sync.server-id`, and enable `sync.enabled`. Old invalidation rows are cleaned automatically.

## Telemetry

mColor uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mColor/33355) when `metrics.enabled` is `true`. No UUIDs, names, colors, history, privacy settings or database credentials are collected. Disable metrics in the plugin configuration or the global bStats configuration.

## Build

```bash
./gradlew clean build
```

Artifacts are written to `build/libs/mColor-1.1.0.jar` and `api/build/libs/mColor-API-1.1.0.jar`.

Licensed under the MIT License.
