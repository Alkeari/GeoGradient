# GeoGradient: 7-Band Climate & Dual-Axis Override — Design Spec

**Date:** 2026-05-16  
**Author:** Alkeari  
**Status:** Approved — ready for implementation planning

---

## 1. Goals

- Replace the 5-zone sinusoidal temperature model with a 7-band piecewise climate model that matches real-Earth hemisphere surface-area proportions.
- Override **both** temperature and humidity (not just temperature) to produce distinct biome groups within shared vanilla tiers.
- Use a non-monotonic temperature curve (subtropical peak) so that Jungles appear at the equator and Deserts appear in the 15–30° belt — matching real ITCZ/subtropical-high physics.
- Fix the existing performance gap: no LRU cache, volatile config reads in the hot path.
- Keep the TerraBlender bridge, mixin structure, and platform entrypoints completely unchanged.

---

## 2. Climate Band Definitions

Bands are indexed by normalized distance `d` from the nearest equator (0.0) to the nearest pole (1.0). Boundaries are derived from real sphere surface-area fractions (sin θ₂ − sin θ₁).

| Band | d range | Temp target | Humidity target | Vanilla tier |
|---|---|---|---|---|
| Tropical Wet | 0.000 – 0.259 | +0.40 | +0.70 | Warm / Wet → Jungle, Bamboo Jungle, Mangrove Swamp |
| Tropical Dry | 0.259 – 0.500 | +0.80 | −0.60 | Hot / Dry → Desert, Badlands, Eroded Badlands |
| Subtropical | 0.500 – 0.643 | +0.35 | −0.10 | Warm / Slightly dry → Sparse Jungle, Savanna |
| Temperate | 0.643 – 0.866 | 0.00 | +0.15 | Temperate → Forest, Plains, Birch Forest |
| Subarctic | 0.866 – 0.940 | −0.30 | 0.00 | Cold → Taiga, Windswept Hills |
| Tundra | 0.940 – 0.985 | −0.60 | −0.20 | Freezing (soft) → Snowy Plains, Ice Spikes |
| Polar | 0.985 – 1.000 | −0.85 | −0.50 | Freezing (hard) → Frozen Peaks, Frozen Ocean |

Constants live as named arrays in `GeoGradientClimate` for easy tuning:

```java
static final double[] BAND_UPPER    = {0.259, 0.500, 0.643, 0.866, 0.940, 0.985, 1.000};
static final String[] BAND_NAME     = {"Tropical Wet","Tropical Dry","Subtropical","Temperate","Subarctic","Tundra","Polar"};
static final float[]  BAND_TEMP     = {0.40f, 0.80f, 0.35f, 0.00f, -0.30f, -0.60f, -0.85f};
static final float[]  BAND_HUMIDITY = {0.70f, -0.60f, -0.10f, 0.15f, 0.00f, -0.20f, -0.50f};
```

---

## 3. Algorithm

### 3.1 Normalized Distance `d`

Replace `computeRawTemperature(z, globeSize)` (sine wave) with `computeNormalizedDistance(angle)`:

```java
double angle    = (effectiveZ / (globeSize * 2.0)) * 2.0 * Math.PI;
double modAngle = ((angle - Math.PI / 2) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
double distAngle = modAngle <= Math.PI ? modAngle : 2 * Math.PI - modAngle;
double d = distAngle / Math.PI; // 0.0 = equator, 1.0 = pole
```

`effectiveZ` is computed exactly as before (SimplexNoise Z-warp applied first).  
Both hemispheres are automatically symmetric.

### 3.2 Band Lookup with Cosine Blending

```java
for (int i = 0; i < BAND_UPPER.length; i++) {
    if (d <= BAND_UPPER[i]) {
        double lower     = i == 0 ? 0.0 : BAND_UPPER[i - 1];
        double halfBlend = bf * 0.5 * (BAND_UPPER[i] - lower); // bf ∈ [0,0.5] prevents zone overlap

        // Blend with next band
        if (i + 1 < BAND_UPPER.length && d > BAND_UPPER[i] - halfBlend) {
            double t = (d - (BAND_UPPER[i] - halfBlend)) / (halfBlend * 2);
            double w = 0.5 * (1 - Math.cos(Math.PI * t));
            return new float[]{lerp(BAND_TEMP[i], BAND_TEMP[i+1], w),
                               lerp(BAND_HUMIDITY[i], BAND_HUMIDITY[i+1], w)};
        }
        // Blend with previous band
        if (i > 0 && d < lower + halfBlend) {
            double t = (d - lower) / halfBlend;
            double w = 0.5 * (1 - Math.cos(Math.PI * t));
            return new float[]{lerp(BAND_TEMP[i-1], BAND_TEMP[i], w),
                               lerp(BAND_HUMIDITY[i-1], BAND_HUMIDITY[i], w)};
        }
        return new float[]{BAND_TEMP[i], BAND_HUMIDITY[i]};
    }
}
```

`blendFraction` (default 0.15, range [0.0, 0.5]) is applied symmetrically at every boundary. The [0, 0.5] range guarantees the two blend zones within any band never overlap.

### 3.3 LRU Cache

Keyed on packed biome-section coordinates (already divided by 4 at the mixin call site). Stores raw `float[2] {temperature, humidity}` before quantization.

```java
private static final int CACHE_SIZE = 1024;
private static final Map<Long, float[]> CLIMATE_CACHE =
    Collections.synchronizedMap(new LinkedHashMap<>(CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, float[]> e) {
            return size() > CACHE_SIZE;
        }
    });

private static long packKey(int x, int z) {
    return ((long) x) | ((long) z << 32);
}
```

`reset()` calls `CLIMATE_CACHE.clear()`.

### 3.4 Volatile Fix

At the top of `sampleClimate()`, read all config volatiles once into locals:

```java
int    gs   = GeoGradientConfig.globeSize;
int    amp  = GeoGradientConfig.borderAmplitude;
double freq = GeoGradientConfig.borderFrequency;
double bf   = GeoGradientConfig.blendFraction;
```

---

## 4. Changed Files

### 4.1 `GeoGradientClimate.java` (core)

- Remove `computeRawTemperature()`.
- Add `computeNormalizedDistance()`, band constants, LRU cache, `lerp()` helper.
- Replace `sampleTemperature(x, z)` with `sampleClimate(x, z)` → `long[2] {tempQuantized, humidityQuantized}`.
- Extend `ClimateInfo` record: add `float humidity` and use 7-band name for `zone`.
- `reset()`: add `CLIMATE_CACHE.clear()`.

### 4.2 `GeoGradientSampler.java`

- Rename `transformTemperature()` → `transformClimate()`.
- Call `GeoGradientClimate.sampleClimate(x, z)`.
- Override **both** `temperature()` and `humidity()` in the returned `Climate.TargetPoint`; all other fields preserved.

### 4.3 `MultiNoiseBiomeSourceMixin.java`

- One call-site change: `transformTemperature` → `transformClimate`. No structural changes.

### 4.4 `GeoGradientConfig.java`

- Add `public static volatile double blendFraction = 0.15;`.

### 4.5 Forge/Fabric config loaders

- Add `blendFraction` field with range [0.0, 0.5] and default 0.15.

### 4.6 `GeoGradientCommand.java`

- Display humidity and updated 7-band zone name in `/geogradient info` output.

---

## 5. Unchanged Files

- `GeoGradientTerraBlenderBridge.java`
- `GeoGradientForge.java` / `GeoGradientFabric.java`
- `MultiNoiseBiomeSourceAccessor.java`
- `DebugScreenOverlayMixin.java` (benefits from cache automatically)
- All build files, `gradle.properties`, `settings.gradle`
- Mixin JSON registration

---

## 6. Config Summary

| Key | Type | Default | Range | Purpose |
|---|---|---|---|---|
| `globe_size` | int | 10000 | 1000–1000000 | Equator-to-pole block distance |
| `border_amplitude` | int | 200 | 0–10000 | Max Z-axis noise warp for jagged borders |
| `border_frequency` | double | 0.002 | 0.0001–0.1 | Border noise frequency |
| `spawn_latitude` | double | 0.0 | −1.0–1.0 | Spawn point latitude |
| `blend_fraction` | double | 0.15 | 0.0–0.5 | Band transition blend width (fraction of band) |

---

## 7. Non-Goals

- No custom biome registration or parameter-space entries.
- No changes to continentalness, erosion, depth, or weirdness.
- No NeoForge support (1.20.1 only per project constraints).
- No changes to TerraBlender integration pattern.
