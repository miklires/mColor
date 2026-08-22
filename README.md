<div align="center">
  <img alt="mColor" width="160" src="https://raw.githubusercontent.com/miklires/mColor/main/docs/assets/mcolor-icon.png">
  <h1>mColor</h1>
  <p>Player name colors, multi-stop gradients, presets, and network synchronization.</p>

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

## Features

- Strict `#RRGGBB` colors, configurable named palettes, rainbow mode, and gradients with up to eight color stops.
- `/color gui` with live name previews, palette entries, gradient presets, rainbow, and reset.
- Direct Adventure display-name and player-list rendering, so PlaceholderAPI is optional.
- H2 storage by default, plus SQLite, MySQL, MariaDB, and PostgreSQL.
- Optimistic asynchronous writes with automatic rollback when persistence fails.
- Optional shared-database polling for server networks.
- English and Russian messages, a Java service API, PlaceholderAPI, bStats, and granular permissions.

## Requirements and installation

mColor requires Java 25 and Paper, Purpur, or Folia 26.2.

1. Put `mColor-1.0.0.jar` in the server's `plugins` directory.
2. Start the server. The default H2 database needs no configuration.
3. Grant palette or feature permissions with your permission plugin.
4. Edit `plugins/mColor/config.yml` to change palettes, presets, storage, or synchronization.

If storage is unavailable, mColor remains active in memory-only mode. Stored colors are loaded again when the connection is available after a restart.

## Commands

| Command | Description |
|---|---|
| `/color gui` | Open the color menu |
| `/color <name>` | Apply a named palette color |
| `/color #RRGGBB` | Apply a hexadecimal color |
| `/color gradient <color1> <color2> [color3...]` | Apply a multi-stop gradient |
| `/color rainbow` | Apply the rainbow preset |
| `/color preview` | Preview the current name style |
| `/color reset` | Restore the normal name |
| `/mcolor reload` | Reload messages, palettes, presets, and safe settings |

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `mcolor.use` | everyone | Use `/color` and the GUI |
| `mcolor.color.<name>` | unset | Use one configured named color |
| `mcolor.color.*` | operators | Use every named color |
| `mcolor.hex` | operators | Use arbitrary hex colors |
| `mcolor.gradient` | operators | Use gradients and presets |
| `mcolor.rainbow` | operators | Use rainbow mode |
| `mcolor.admin` | operators | Reload configuration |

## PlaceholderAPI

- `%mcolor_name%` — legacy-colored player name
- `%mcolor_name_mm%` — MiniMessage-colored player name
- `%mcolor_name_stripped%` — plain player name
- `%mcolor_color%`, `%mcolor_color2%` — first and second colors
- `%mcolor_gradient%` — all render colors separated by commas
- `%mcolor_has_color%` — `yes` or `no`
- `%mcolor_of_PlayerName%` — another online player's colored name

All placeholders read the in-memory cache and never query SQL on the server thread. Other plugins can use `MColorService` through Bukkit's services manager without depending on PlaceholderAPI.

## Networks

Choose MySQL, MariaDB, or PostgreSQL on every backend, point them at the same database, assign a unique `sync.server-id`, and enable `sync.enabled`. Changes are detected by polling and applied to online players. A proxy plugin is not required.

## Telemetry

mColor uses [bStats plugin ID 33355](https://bstats.org/plugin/bukkit/mColor/33355) for anonymous usage statistics. Set `metrics.enabled: false` to opt out.

## Build

```bash
./gradlew clean build
```

The server artifact is `build/libs/mColor-1.0.0.jar`. The project is licensed under the MIT License.
