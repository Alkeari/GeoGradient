# GeoGradient

**A Minecraft mod that replaces the game's default temperature system with a latitude system driven by your Z coordinate — the further north or south you travel, the colder or hotter the world becomes, producing natural climate bands across an otherwise geographically senseless world.**

Available for **Forge** and **Fabric** — Minecraft 1.20.1.

***

## The Problem

Vanilla Minecraft assigns temperature to each biome as a fixed property of the biome itself. A snowy plains biome is cold, a desert is hot, and the two can sit directly next to each other with no meaningful transition. The world has no concept of geography — there is no north, no south, no equator, and no poles. Climates are scattered at random, and no matter how far you travel in any direction, the pattern never makes logical sense.

***

## What This Mod Does

GeoGradient overrides biome temperature at the selection level, replacing it with a value calculated from the player's **Z coordinate** using a repeating sine wave. Every biome still exists — GeoGradient does not add or remove any biomes — but which ones appear at your location is now driven by where you are in the world, not by an arbitrary preset.

### Latitude Bands

The world is divided into five climate zones that repeat as you travel north or south:

| Zone | Vanilla Tier | Default Width | Character |
| --------------- | ------------ | -------------- | -------------------------------------------- |
| **Polar** | Freezing | ~3,500 blocks | Frozen tundra, icy peaks, and snowy plains |
| **Subarctic** | Cold | ~1,010 blocks | Boreal taiga, windswept hills |
| **Temperate** | Temperate | ~1,120 blocks | Forests, plains, rivers, and beaches |
| **Subtropical** | Warm | ~1,210 blocks | Jungles, swamps, and mushroom fields |
| **Tropical** | Hot | ~3,150 blocks | Deserts, badlands, and savannas |

Each zone boundary corresponds exactly to a vanilla biome tier boundary, so every zone transition is a visible change in terrain and biomes. Widths are approximate — the sine-wave curve is non-linear, so zones near the poles and equators occupy more blocks than central zones.

With the default configuration, **spawn (Z = 0)** is placed in the **Temperate** zone — halfway between the North Pole and the nearest Equator. The Temperate zone spans approximately 1,120 blocks total around spawn.

| Landmark | Block Z | Zone |
| ------------ | ------- | ----------- |
| North Pole | −5,000 | Polar |
| **Spawn** | 0 | Temperate |
| Equator | +5,000 | Tropical |
| South Pole | +15,000 | Polar |
| Next Equator | +25,000 | Tropical |

The full climate cycle repeats every **20,000 blocks**. Increasing `globe_size` in the config stretches the world accordingly.

### Organic Biome Borders

Rather than drawing perfectly straight horizontal biome boundaries, GeoGradient applies a **Simplex noise warp** to the latitude calculation before sampling temperature. This breaks up the transition zones into natural-looking, irregular edges — the kind you would expect to find at the edge of a real tundra or jungle.

### F3 Debug Overlay

GeoGradient appends a line to the F3 debug screen showing your current climate data:

```
GeoGradient: 0.87 [Tropical] | NP 8320N | EQ 1680S | SP 11680S
```

| Field | Meaning |
| ------------- | ------------------------------------------------------- |
| `0.87` | Raw temperature value (−1.0 at poles, +1.0 at equators) |
| `[Tropical]` | Current climate zone |
| `NP 8320N` | Nearest North Pole is 8,320 blocks to the north |
| `EQ 1680S` | Nearest Equator is 1,680 blocks to the south |
| `SP 11680S` | Nearest South Pole is 11,680 blocks to the south |

### `/geogradient` Command

Query climate information for any location in the world.

| Command | Description |
| ------------------------------ | --------------------------------------- |
| `/geogradient info` | Climate data at your current position |
| `/geogradient info <x> <z>` | Climate data at any block coordinate |

***

## Configuration

Configuration is available via the platform's native config system. On **Forge**, edit `config/geogradient-common.toml`. On **Fabric**, edit `config/geogradient.json` (or use a Cloth Config-compatible in-game editor).

| Option | Default | Range | Description |
| ------------------- | ------- | --------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `globe_size` | `10000` | 1,000–1,000,000 | Distance in blocks from any pole to the nearest equator. Spawn sits at the midpoint. The full climate cycle = 2 × this value. |
| `border_amplitude` | `200` | 0–10,000 | Maximum Z-axis warp from the border noise. Higher values produce more jagged biome boundaries. |
| `border_frequency` | `0.002` | 0.0001–0.1 | Spatial frequency of the border noise. Lower = broad, sweeping curves. Higher = tight, frequent ripples. |
| `spawn_latitude` | `0.0` | −1.0–1.0 | Where new players spawn. `0.0` = Temperate (default). `1.0` = North Pole (Polar). `−1.0` = Equator (Tropical). |

> **Note:** `globe_size` is best set before world creation. Changing it on an existing world shifts all climate landmarks. If you update `spawn_latitude` after a world has already loaded, delete `geogradient_spawn_applied.flag` from the world's save folder to re-apply it.

**Examples:**

- `globe_size = 10000` → North Pole at Z −5,000 · Equator at Z +5,000 _(default)_
- `globe_size = 25000` → North Pole at Z −12,500 · Equator at Z +12,500 _(larger world)_
- `spawn_latitude = 1.0` → new players spawn at the North Pole
- `spawn_latitude = -1.0` → new players spawn at the Equator
- `border_amplitude = 0` → perfectly straight horizontal biome boundaries _(no noise warp)_

***

## Requirements

### Forge

| Dependency | Version |
| --------------- | --------------- |
| Minecraft | 1.20.1 |
| Forge | 47.4.0 or later |
| Architectury API | 9.2.14 or later |

### Fabric

| Dependency | Version |
| --------------- | ---------------- |
| Minecraft | 1.20.1 |
| Fabric Loader | 0.14.23 or later |
| Fabric API | 0.90.4 or later |
| Architectury API | 9.2.14 or later |
| Cloth Config | 11.1.136 or later |

> **Note:** NeoForge is not supported for Minecraft 1.20.1 — the NeoForge fork began at 1.20.2.

***

## Installation

1. Install [Architectury API](https://modrinth.com/mod/architectury-api) for your loader. Fabric users also need [Fabric API](https://modrinth.com/mod/fabric-api) and [Cloth Config](https://modrinth.com/mod/cloth-config).
2. Download the correct jar for your loader (`-FORGE.jar` or `-FABRIC.jar`) from the [Releases](https://github.com/Alkeari/GeoGradient/releases) page.
3. Drop it into your `mods/` folder.
4. Launch the game. Adjust `globe_size` and other settings in the config before creating a new world.

***

## Compatibility

- GeoGradient does **not** add any new biomes. It reshuffles which vanilla biomes appear at each location by changing the temperature value the game sees at selection time — all vanilla biomes remain available.
- Compatible with mods that **add new biomes**, provided those biomes declare a standard temperature value. GeoGradient will weight them into the correct latitude band automatically.
- Compatible with **TerraBlender** — when TerraBlender is present, GeoGradient hooks into its regional biome system automatically. No additional configuration is required.
- Does **not** affect humidity, continentalness, erosion, depth, or weirdness — only the temperature axis is modified. All other biome placement logic remains vanilla.
- The noise used for border warping is **seeded from the world seed**, so biome border shapes are fully deterministic and consistent across sessions.

***

## License

See [LICENSE.md](LICENSE.md).

***

## Issues & Feedback

Found a bug or have a suggestion? Open an issue on the [GitHub Issues](https://github.com/Alkeari/GeoGradient/issues) page.
