# 7-Band Climate & Dual-Axis Override Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace GeoGradient's 5-zone sinusoidal temperature model with a 7-band piecewise climate system that overrides both temperature and humidity, adds an LRU cache, and fixes volatile config reads in the hot path.

**Architecture:** Band boundaries are cumulative sphere surface-area fractions (matching real-Earth proportions). Temperature peaks in the 15–30° belt (Tropical Dry / Desert) rather than at the equator, creating realistic ITCZ physics. Humidity is added as a second overridden axis to distinguish wet equatorial (Jungles) from dry subtropical (Deserts). A 1024-entry LRU cache eliminates repeated noise+trig per chunk-gen cycle.

**Tech Stack:** Java 21, Minecraft 1.20.1, Architectury 9.2.14, Forge 47.4.x, Fabric 0.14.x, JUnit 5

---

## Task Dependency Graph

```
Task 1 (Config)
     ↓
Task 2 (Climate Core + Climate Tests)
          ↙                    ↘
Task 3 (Sampler + Mixin       Task 4 (Command)
        + Sampler Tests)
          ↘                    ↙
         Task 5 (Build + Deploy)
```

Tasks 3 and 4 can be dispatched in **parallel** after Task 2 completes.

---

## File Structure

| File | Change |
|---|---|
| `common/src/main/java/net/alkeari/geogradient/config/GeoGradientConfig.java` | Add `blendFraction` |
| `forge/src/main/java/net/alkeari/geogradient/forge/config/GeoGradientForgeConfig.java` | Add Forge config entry |
| `forge/src/main/java/net/alkeari/geogradient/forge/GeoGradientForge.java` | Load blendFraction on world load |
| `fabric/src/main/java/net/alkeari/geogradient/fabric/config/GeoGradientFabricConfig.java` | Add fabric config field |
| `fabric/src/main/java/net/alkeari/geogradient/fabric/GeoGradientFabric.java` | Load blendFraction on world load |
| `common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java` | Full rewrite: piecewise bands, LRU cache, sampleClimate() |
| `common/src/main/java/net/alkeari/geogradient/GeoGradientSampler.java` | transformClimate() overrides both temp + humidity |
| `common/src/main/java/net/alkeari/geogradient/mixin/MultiNoiseBiomeSourceMixin.java` | One call-site rename |
| `common/src/main/java/net/alkeari/geogradient/GeoGradientCommand.java` | Show humidity in /geogradient info |
| `common/src/test/java/net/alkeari/geogradient/GeoGradientClimateTest.java` | Replace old sine tests with band tests |
| `common/src/test/java/net/alkeari/geogradient/GeoGradientSamplerTest.java` | humidity is now overridden (not preserved) |

---

## Task 1: Add blendFraction to All Config Files

**Files:**
- Modify: `common/src/main/java/net/alkeari/geogradient/config/GeoGradientConfig.java`
- Modify: `forge/src/main/java/net/alkeari/geogradient/forge/config/GeoGradientForgeConfig.java`
- Modify: `forge/src/main/java/net/alkeari/geogradient/forge/GeoGradientForge.java`
- Modify: `fabric/src/main/java/net/alkeari/geogradient/fabric/config/GeoGradientFabricConfig.java`
- Modify: `fabric/src/main/java/net/alkeari/geogradient/fabric/GeoGradientFabric.java`

- [ ] **Step 1: Write the new GeoGradientConfig.java**

Replace the entire file at `common/src/main/java/net/alkeari/geogradient/config/GeoGradientConfig.java`:

```java
package net.alkeari.geogradient.config;

public class GeoGradientConfig {
    public static volatile int    globeSize       = 10000;
    public static volatile int    borderAmplitude = 200;
    public static volatile double borderFrequency = 0.002;
    public static volatile double spawnLatitude   = 0.0;
    public static volatile double blendFraction   = 0.15;
}
```

- [ ] **Step 2: Add BLEND_FRACTION to GeoGradientForgeConfig.java**

Read the file first. Add the field declaration after the `SPAWN_LATITUDE` field declaration at the top of the class:

```java
public static final ForgeConfigSpec.DoubleValue BLEND_FRACTION;
```

Add the builder entry in the `static` block, after the `SPAWN_LATITUDE` builder call and before `builder.pop()`:

```java
BLEND_FRACTION = builder
        .comment("Width of cosine-blended transition zones at each climate band boundary, as a fraction of the narrower adjacent band. Range [0.0, 0.5].")
        .defineInRange("blend_fraction", 0.15, 0.0, 0.5);
```

- [ ] **Step 3: Load blendFraction in GeoGradientForge.java**

In `onLevelLoad`, add this line immediately after the `GeoGradientConfig.spawnLatitude` assignment:

```java
GeoGradientConfig.blendFraction   = GeoGradientForgeConfig.BLEND_FRACTION.get();
```

- [ ] **Step 4: Add blend_fraction to GeoGradientFabricConfig.java**

Add this field after `spawn_latitude`:

```java
@ConfigEntry.Gui.Tooltip
public double blend_fraction = 0.15;
```

- [ ] **Step 5: Load blendFraction in GeoGradientFabric.java**

In the `ServerWorldEvents.LOAD` lambda, add this line after the `GeoGradientConfig.spawnLatitude` assignment:

```java
GeoGradientConfig.blendFraction   = cfg.blend_fraction;
```

- [ ] **Step 6: Verify compilation**

Run:
```powershell
.\gradlew :common:compileJava :forge:compileJava :fabric:compileJava
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 7: Commit**

```powershell
git add common/src/main/java/net/alkeari/geogradient/config/GeoGradientConfig.java `
        forge/src/main/java/net/alkeari/geogradient/forge/config/GeoGradientForgeConfig.java `
        forge/src/main/java/net/alkeari/geogradient/forge/GeoGradientForge.java `
        fabric/src/main/java/net/alkeari/geogradient/fabric/config/GeoGradientFabricConfig.java `
        fabric/src/main/java/net/alkeari/geogradient/fabric/GeoGradientFabric.java
git -c commit.gpgsign=false commit -m "feat: add blendFraction config to all platforms"
```

---

## Task 2: Rewrite GeoGradientClimate + Update Climate Tests

**Files:**
- Modify: `common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java`
- Modify: `common/src/test/java/net/alkeari/geogradient/GeoGradientClimateTest.java`

**Key concepts before starting:**
- `x` and `z` passed into `computeClimate` are **biome-section coords** (block >> 2). The globeSize is in **block** units. To convert biome-section to block-equivalent, multiply by 4: `effectiveZ * 4.0`.
- The angle formula `(effectiveZ * 4.0 / (gs * 2.0)) * 2π` is equivalent to the old `sin(effectiveZNoise * 4 * π / gs)` — same scale, just written differently.
- `bandLookup(d, bf)` is package-private so tests can call it directly without needing noise to be initialized.
- `sampleTemperature(x, z)` is kept as a backward-compat shim returning `sampleClimate(x, z)[0]` so Genesis and other mods using it via reflection still work.
- The `ClimateInfo` record gains a `humidity` field. Because `ClimateInfo` is only constructed in `sampleAt()` (inside this file), and only read by callers (GeoGradientCommand, DebugScreenOverlayMixin), adding the field does not break existing callers.

- [ ] **Step 1: Write the full new GeoGradientClimate.java**

Replace the entire file at `common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java`:

```java
package net.alkeari.geogradient;

import net.alkeari.geogradient.config.GeoGradientConfig;
import net.alkeari.geogradient.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeoGradientClimate {

    // ── 7-band climate definitions ────────────────────────────────────────────
    // Boundaries are cumulative normalized distances d from the nearest equator
    // (0.0 = equator, 1.0 = pole), derived from real-Earth hemisphere
    // surface-area fractions: sin(θ₂) − sin(θ₁).
    // Temperature is non-monotonic: peaks at Tropical Dry (d ≈ 0.25–0.50),
    // then decreases toward the pole. This places Deserts at 15–30° and
    // Jungles at the equatorial belt, matching real ITCZ/subtropical-high physics.
    static final double[] BAND_UPPER    = {0.259, 0.500, 0.643, 0.866, 0.940, 0.985, 1.000};
    static final String[] BAND_NAME     = {"Tropical Wet", "Tropical Dry", "Subtropical",
                                           "Temperate", "Subarctic", "Tundra", "Polar"};
    static final float[]  BAND_TEMP     = { 0.40f,  0.80f,  0.35f,  0.00f, -0.30f, -0.60f, -0.85f};
    static final float[]  BAND_HUMIDITY = { 0.70f, -0.60f, -0.10f,  0.15f,  0.00f, -0.20f, -0.50f};

    // ── LRU climate cache ─────────────────────────────────────────────────────
    // Key: packed biome-section coords (x, z); value: float[]{temperature, humidity}.
    // Eliminates repeated SimplexNoise + trig calls for the same biome column.
    // reset() clears this so it doesn't straddle world-loads.
    private static final int CACHE_SIZE = 1024;
    private static final Map<Long, float[]> CLIMATE_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<Long, float[]>(CACHE_SIZE + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
                return size() > CACHE_SIZE;
            }
        });

    private static long packKey(int x, int z) {
        return ((long) x) | ((long) z << 32);
    }

    // ── World state ───────────────────────────────────────────────────────────
    private static volatile SimplexNoise noise;
    private static volatile boolean initialized = false;
    private static volatile BiomeSource storedBiomeSource;

    public static synchronized void initialize(long seed) {
        if (initialized) return;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed ^ 0x47656F47L));
        noise = new SimplexNoise(random);
        initialized = true;
    }

    @SuppressWarnings("unused")
    public static synchronized void initialize(long seed, BiomeSource biomeSource) {
        storedBiomeSource = biomeSource;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed ^ 0x47656F47L));
        noise = new SimplexNoise(random);
        initialized = true;
    }

    public static synchronized void reset() {
        initialized = false;
        noise = null;
        storedBiomeSource = null;
        CLIMATE_CACHE.clear();
    }

    @SuppressWarnings("unused")
    public static Holder<Biome> findBiomeForPreview(int x, int y, int z, Climate.Sampler sampler) {
        if (noise == null) return null;
        Climate.TargetPoint original = sampler.sample(x, y, z);
        // NOTE: transformTemperature is updated to transformClimate in Task 3.
        // Keeping the old name here so Task 2 compiles before the Sampler is rewritten.
        Climate.TargetPoint adjusted = GeoGradientSampler.transformTemperature(original, x, z);
        if (storedBiomeSource instanceof MultiNoiseBiomeSourceAccessor acc) {
            return GeoGradientTerraBlenderBridge.findBiome(acc.geogradient$parameters(), adjusted, x, y, z);
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isInitialized() {
        return initialized;
    }

    // ── Core climate computation ──────────────────────────────────────────────

    /**
     * Computes raw (un-quantized) {temperature, humidity} for biome-section coords.
     * x and z are biome-section coordinates (block >> 2). Results are cached at
     * biome-section resolution; call reset() between world loads to invalidate.
     */
    static float[] computeClimate(int x, int z) {
        long key = packKey(x, z);
        float[] cached = CLIMATE_CACHE.get(key);
        if (cached != null) return cached;

        // Read config volatiles once into locals — eliminates memory barriers in the hot path.
        int    gs   = GeoGradientConfig.globeSize;
        int    amp  = GeoGradientConfig.borderAmplitude;
        double freq = GeoGradientConfig.borderFrequency;
        double bf   = GeoGradientConfig.blendFraction;

        // Apply border noise warp (x, z in biome-section units; amp in biome-section units).
        // Multiply by 4.0 to convert biome-section coords to block-equivalent units for
        // the globeSize comparison (globeSize is in blocks).
        double effectiveZ = z + noise.getValue(x * freq, z * freq) * amp;
        double angle      = (effectiveZ * 4.0 / (gs * 2.0)) * 2.0 * Math.PI;

        // Normalized distance d from the nearest equator: 0.0 = equator, 1.0 = pole.
        // Fold the full sine-wave angle into distance from the nearest π/2 peak.
        double modAngle  = ((angle - Math.PI / 2) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
        double distAngle = modAngle <= Math.PI ? modAngle : 2 * Math.PI - modAngle;
        double d         = Math.min(distAngle / Math.PI, 1.0);

        float[] result = bandLookup(d, bf);
        CLIMATE_CACHE.put(key, result);
        return result;
    }

    /**
     * Looks up the {temperature, humidity} pair for normalized equatorial distance d.
     * Applies cosine-interpolated blending in the overlap zone at each band boundary.
     * bf in [0.0, 0.5] — guaranteed not to overlap the two blend zones within one band.
     * Package-private for unit testing.
     */
    static float[] bandLookup(double d, double bf) {
        for (int i = 0; i < BAND_UPPER.length; i++) {
            if (d <= BAND_UPPER[i]) {
                double lower     = i == 0 ? 0.0 : BAND_UPPER[i - 1];
                double halfBlend = bf * 0.5 * (BAND_UPPER[i] - lower);

                // Blend toward next band (poleward boundary)
                if (i + 1 < BAND_UPPER.length && d > BAND_UPPER[i] - halfBlend) {
                    double t = (d - (BAND_UPPER[i] - halfBlend)) / (halfBlend * 2);
                    double w = 0.5 * (1.0 - Math.cos(Math.PI * t));
                    return new float[]{
                        lerp(BAND_TEMP[i],     BAND_TEMP[i + 1],     (float) w),
                        lerp(BAND_HUMIDITY[i], BAND_HUMIDITY[i + 1], (float) w)
                    };
                }
                // Blend toward previous band (equatorial boundary)
                if (i > 0 && d < lower + halfBlend) {
                    double t = (d - lower) / halfBlend;
                    double w = 0.5 * (1.0 - Math.cos(Math.PI * t));
                    return new float[]{
                        lerp(BAND_TEMP[i - 1],     BAND_TEMP[i],     (float) w),
                        lerp(BAND_HUMIDITY[i - 1], BAND_HUMIDITY[i], (float) w)
                    };
                }
                return new float[]{BAND_TEMP[i], BAND_HUMIDITY[i]};
            }
        }
        // d > 1.0: clamp to Polar
        return new float[]{BAND_TEMP[BAND_TEMP.length - 1], BAND_HUMIDITY[BAND_HUMIDITY.length - 1]};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Returns quantized {temperature, humidity} for biome-section coords.
     * Called by {@link GeoGradientSampler#transformClimate} on every biome lookup.
     * Both values are in Minecraft's quantized range [−10000, +10000].
     */
    public static long[] sampleClimate(int x, int z) {
        if (noise == null) {
            throw new IllegalStateException("GeoGradientClimate.initialize() must be called before sampleClimate()");
        }
        float[] raw = computeClimate(x, z);
        return new long[]{
            Climate.quantizeCoord(raw[0]),
            Climate.quantizeCoord(raw[1])
        };
    }

    /**
     * Backward-compatibility shim. External mods (e.g. Genesis) that call
     * sampleTemperature() via reflection continue to work; returns only the
     * temperature component of sampleClimate().
     */
    public static long sampleTemperature(int x, int z) {
        return sampleClimate(x, z)[0];
    }

    // ── Display / debug helpers ───────────────────────────────────────────────

    public record ClimateInfo(float temp, float humidity, String zone,
                              int distNP, int distEQ, int distSP) {}

    public static ClimateInfo sampleAt(int blockX, int blockZ) {
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        float temp = 0f, humidity = 0f;
        String zone = "Unknown";
        if (noise != null) {
            float[] raw = computeClimate(noiseX, noiseZ);
            temp     = raw[0];
            humidity = raw[1];
            // Recompute d for zone name (display-only; noise is cheap after cache warms).
            int    gs   = GeoGradientConfig.globeSize;
            int    amp  = GeoGradientConfig.borderAmplitude;
            double freq = GeoGradientConfig.borderFrequency;
            double effectiveZ = noiseZ + noise.getValue(noiseX * freq, noiseZ * freq) * amp;
            double angle      = (effectiveZ * 4.0 / (gs * 2.0)) * 2.0 * Math.PI;
            double modAngle   = ((angle - Math.PI / 2) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            double distAngle  = modAngle <= Math.PI ? modAngle : 2 * Math.PI - modAngle;
            double d          = Math.min(distAngle / Math.PI, 1.0);
            for (int i = 0; i < BAND_UPPER.length; i++) {
                if (d <= BAND_UPPER[i]) { zone = BAND_NAME[i]; break; }
            }
        }
        int gs         = GeoGradientConfig.globeSize;
        int period     = gs * 2;
        int coldOffset = -(gs / 2);
        int hotOffset  =   gs / 2;
        int npBlock = nearestLandmarkNorth(blockZ, coldOffset, period);
        int eqBlock = nearestLandmark(blockZ, hotOffset, period);
        int spBlock = nearestLandmarkSouth(blockZ, coldOffset, period);
        return new ClimateInfo(temp, humidity, zone,
                               blockZ - npBlock, blockZ - eqBlock, blockZ - spBlock);
    }

    public static float getTemperatureAt(int x, int z) {
        if (noise == null) return 0.0f;
        return computeClimate(x, z)[0];
    }

    public static String formatDist(int diff) {
        if (diff == 0) return "Here";
        return Math.abs(diff) + (diff > 0 ? "N" : "S");
    }

    public static int nearestLandmark(int z, int landmarkOffset, int period) {
        long diff = (long) z - landmarkOffset;
        long k = Math.round((double) diff / period);
        return (int) (landmarkOffset + k * period);
    }

    public static int nearestLandmarkNorth(int z, int offset, int period) {
        long k = Math.floorDiv((long) z - offset, period);
        int atOrNorth = (int) (offset + k * period);
        return (atOrNorth < z) ? atOrNorth : (int) (offset + (k - 1) * period);
    }

    public static int nearestLandmarkSouth(int z, int offset, int period) {
        long k = Math.floorDiv((long) z - offset, period);
        return (int) (offset + (k + 1) * period);
    }
}
```

- [ ] **Step 2: Write the new GeoGradientClimateTest.java**

**What changed:** `computeRawTemperature`, `sampleTemperature`, and `getZoneName` are gone. New test targets are `bandLookup(double d, double bf)` (package-private pure function) and `sampleClimate(int x, int z)` (requires `initialize(seed)`). With `borderAmplitude = 0`, no noise warp is applied, making d values fully predictable.

**Key coordinate facts (with globeSize=10000, amp=0):**
- Equatorial biome-section coord: noiseZ = (blockZ=5000) >> 2 = **1250** → d = 0.0
- North-pole biome-section coord: noiseZ = (blockZ=-5000) >> 2 = **-1250** → d = 1.0
- Spawn biome-section coord: noiseZ = 0 → d = 0.5 (Tropical Dry / Subtropical boundary)

Replace the entire file at `common/src/test/java/net/alkeari/geogradient/GeoGradientClimateTest.java`:

```java
package net.alkeari.geogradient;

import net.alkeari.geogradient.config.GeoGradientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoGradientClimateTest {

    @BeforeEach
    void setup() {
        GeoGradientConfig.globeSize       = 10000;
        GeoGradientConfig.borderAmplitude = 0;   // no warp — makes d values predictable
        GeoGradientConfig.borderFrequency = 0.002;
        GeoGradientConfig.blendFraction   = 0.0; // no blending — tests pure band centers
        GeoGradientClimate.reset();
        GeoGradientClimate.initialize(42L);
    }

    // ── bandLookup — pure function, no noise required ─────────────────────────

    @Test
    void bandLookup_equator_isTropicalWet() {
        float[] result = GeoGradientClimate.bandLookup(0.0, 0.0);
        assertEquals(GeoGradientClimate.BAND_TEMP[0],     result[0], 0.001f, "temp");
        assertEquals(GeoGradientClimate.BAND_HUMIDITY[0], result[1], 0.001f, "humidity");
    }

    @Test
    void bandLookup_pole_isPolar() {
        float[] result = GeoGradientClimate.bandLookup(1.0, 0.0);
        assertEquals(GeoGradientClimate.BAND_TEMP[6],     result[0], 0.001f, "temp");
        assertEquals(GeoGradientClimate.BAND_HUMIDITY[6], result[1], 0.001f, "humidity");
    }

    @Test
    void bandLookup_tropicalDryCenter_returnsHotDry() {
        // d = 0.38 is inside Tropical Dry (0.259–0.500)
        float[] result = GeoGradientClimate.bandLookup(0.38, 0.0);
        assertEquals(GeoGradientClimate.BAND_TEMP[1],     result[0], 0.001f, "temp");
        assertEquals(GeoGradientClimate.BAND_HUMIDITY[1], result[1], 0.001f, "humidity");
    }

    @Test
    void bandLookup_temperateCenter_returnsNeutral() {
        // d = 0.75 is inside Temperate (0.643–0.866)
        float[] result = GeoGradientClimate.bandLookup(0.75, 0.0);
        assertEquals(GeoGradientClimate.BAND_TEMP[3],     result[0], 0.001f, "temp");
        assertEquals(GeoGradientClimate.BAND_HUMIDITY[3], result[1], 0.001f, "humidity");
    }

    @Test
    void bandLookup_atBoundaryWithBlend_isInterpolated() {
        // Exactly at the Tropical Wet / Tropical Dry boundary (d=0.259) with blending on.
        // With bf=0.15, halfBlend = 0.15 * 0.5 * 0.259 ≈ 0.01943.
        // d=0.259 is exactly BAND_UPPER[0], so it's in the "blend toward next band" zone:
        // t = halfBlend / (halfBlend * 2) = 0.5 → w = 0.5 → result = midpoint of both bands.
        float[] result = GeoGradientClimate.bandLookup(0.259, 0.15);
        float expectedTemp = (GeoGradientClimate.BAND_TEMP[0] + GeoGradientClimate.BAND_TEMP[1]) / 2f;
        float expectedHumidity = (GeoGradientClimate.BAND_HUMIDITY[0] + GeoGradientClimate.BAND_HUMIDITY[1]) / 2f;
        assertEquals(expectedTemp,     result[0], 0.01f, "blended temp");
        assertEquals(expectedHumidity, result[1], 0.01f, "blended humidity");
    }

    @Test
    void bandLookup_beyondPole_clampsToLast() {
        float[] result = GeoGradientClimate.bandLookup(1.5, 0.0);
        assertEquals(GeoGradientClimate.BAND_TEMP[6],     result[0], 0.001f, "temp");
        assertEquals(GeoGradientClimate.BAND_HUMIDITY[6], result[1], 0.001f, "humidity");
    }

    @Test
    void bandLookup_sevenBandsDefined() {
        assertEquals(7, GeoGradientClimate.BAND_UPPER.length);
        assertEquals(7, GeoGradientClimate.BAND_NAME.length);
        assertEquals(7, GeoGradientClimate.BAND_TEMP.length);
        assertEquals(7, GeoGradientClimate.BAND_HUMIDITY.length);
    }

    // ── sampleClimate — requires noise init ───────────────────────────────────

    @Test
    void sampleClimate_equator_isInTropicalWetTempRange() {
        // With amp=0, noiseZ=1250 → d=0.0 → Tropical Wet center temp=0.40
        long[] result = GeoGradientClimate.sampleClimate(0, 1250);
        long expectedTemp = net.minecraft.world.level.biome.Climate.quantizeCoord(0.40f);
        assertEquals(expectedTemp, result[0], "temperature at equator");
    }

    @Test
    void sampleClimate_northPole_isInPolarTempRange() {
        // With amp=0, noiseZ=-1250 → d=1.0 → Polar center temp=-0.85
        long[] result = GeoGradientClimate.sampleClimate(0, -1250);
        long expectedTemp = net.minecraft.world.level.biome.Climate.quantizeCoord(-0.85f);
        assertEquals(expectedTemp, result[0], "temperature at north pole");
    }

    @Test
    void sampleClimate_returnsQuantizedValuesInRange() {
        long[] result = GeoGradientClimate.sampleClimate(0, 0);
        assertTrue(result[0] >= -10000L && result[0] <= 10000L,
            "temperature must be in [-10000, 10000], got: " + result[0]);
        assertTrue(result[1] >= -10000L && result[1] <= 10000L,
            "humidity must be in [-10000, 10000], got: " + result[1]);
    }

    @Test
    void sampleClimate_throwsIfNotInitialized() {
        GeoGradientClimate.reset();
        assertThrows(IllegalStateException.class,
            () -> GeoGradientClimate.sampleClimate(0, 0));
    }

    @Test
    void sampleClimate_cachedResultMatchesRecomputed() {
        long[] first  = GeoGradientClimate.sampleClimate(100, 200);
        long[] second = GeoGradientClimate.sampleClimate(100, 200);
        assertArrayEquals(first, second, "cache must return identical values");
    }

    @Test
    void sampleTemperature_backwardCompatShim_returnsTemperatureOnly() {
        long climate = GeoGradientClimate.sampleClimate(0, 1250)[0];
        long legacy  = GeoGradientClimate.sampleTemperature(0, 1250);
        assertEquals(climate, legacy, "sampleTemperature shim must match sampleClimate()[0]");
    }

    // ── Band name checks via sampleAt ─────────────────────────────────────────

    @Test
    void sampleAt_equator_isTropicalWet() {
        // blockX=0, blockZ=5000 → noiseZ=1250 → d=0.0
        GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(0, 5000);
        assertEquals("Tropical Wet", info.zone());
    }

    @Test
    void sampleAt_northPole_isPolar() {
        // blockZ=-5000 → noiseZ=-1250 → d=1.0
        GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(0, -5000);
        assertEquals("Polar", info.zone());
    }

    @Test
    void sampleAt_climateInfo_hasHumidityField() {
        GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(0, 5000);
        // Tropical Wet humidity target is 0.70; with amp=0 this should be exact.
        assertEquals(0.70f, info.humidity(), 0.001f);
    }
}
```

- [ ] **Step 3: Run the new tests**

```powershell
.\gradlew :common:test
```

Expected: All tests pass (both new ClimateTests and the still-unmodified SamplerTest). If `sampleClimate_equator_isInTropicalWetTempRange` fails, double-check that `amp=0` in `@BeforeEach` means the `* 4.0` scale converts noiseZ=1250 to effectiveZ_blocks=5000=globeSize/2 correctly (d should be 0.0).

- [ ] **Step 4: Commit**

```powershell
git add common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java `
        common/src/test/java/net/alkeari/geogradient/GeoGradientClimateTest.java
git -c commit.gpgsign=false commit -m "feat: rewrite GeoGradientClimate with 7-band piecewise model and LRU cache"
```

---

## Task 3: Rewrite GeoGradientSampler + Update Mixin + Update Sampler Tests

**Run in parallel with Task 4 after Task 2 is complete.**

**Files:**
- Modify: `common/src/main/java/net/alkeari/geogradient/GeoGradientSampler.java`
- Modify: `common/src/main/java/net/alkeari/geogradient/mixin/MultiNoiseBiomeSourceMixin.java`
- Modify: `common/src/test/java/net/alkeari/geogradient/GeoGradientSamplerTest.java`

**Key concept:** `transformClimate` now overrides **both** temperature and humidity. The two quantized values come from `GeoGradientClimate.sampleClimate(blockX, blockZ)` as `long[]{temp, humidity}`. The other four fields (continentalness, erosion, depth, weirdness) are preserved exactly as before.

- [ ] **Step 1: Write the new GeoGradientSamplerTest.java first (TDD)**

`humidity` is no longer preserved — it is overridden. The sentinel value `99999L` (used in tests to detect "unchanged") must now detect changes for humidity too. Note: the old test `preservesHumidity` becomes `replacesHumidity`.

Replace the entire file at `common/src/test/java/net/alkeari/geogradient/GeoGradientSamplerTest.java`:

```java
package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GeoGradientSampler}.
 *
 * GeoGradientSampler operates directly on {@link Climate.TargetPoint} records,
 * keeping it free of the MC bootstrap requirement so tests run without a
 * running Minecraft instance.
 */
class GeoGradientSamplerTest {

    // 99999L is outside GeoGradient's output range of [-10000, 10000], so it
    // can never be produced by sampleClimate — assertNotEquals on it is reliable.
    private static final long TEMPERATURE     = 99999L;
    private static final long HUMIDITY        = 99998L; // also outside [-10000,10000]
    private static final long CONTINENTALNESS = 2222L;
    private static final long EROSION         = 3333L;
    private static final long DEPTH           = 4444L;
    private static final long WEIRDNESS       = 5555L;

    @BeforeEach
    void initClimate() {
        GeoGradientClimate.reset();
        GeoGradientClimate.initialize(42L);
    }

    private static Climate.TargetPoint fixedPoint() {
        return new Climate.TargetPoint(
                TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, DEPTH, WEIRDNESS
        );
    }

    @Test
    void replacesTemperature() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertNotEquals(TEMPERATURE, result.temperature(),
            "temperature must be replaced by GeoGradient's climate model");
    }

    @Test
    void replacesHumidity() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertNotEquals(HUMIDITY, result.humidity(),
            "humidity must be overridden by GeoGradient's climate model");
    }

    @Test
    void replacedTemperatureIsInRange() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertTrue(result.temperature() >= -10000L && result.temperature() <= 10000L,
            "replaced temperature must be a valid quantized value");
    }

    @Test
    void replacedHumidityIsInRange() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertTrue(result.humidity() >= -10000L && result.humidity() <= 10000L,
            "replaced humidity must be a valid quantized value");
    }

    @Test
    void preservesContinentalness() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertEquals(CONTINENTALNESS, result.continentalness());
    }

    @Test
    void preservesErosion() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertEquals(EROSION, result.erosion());
    }

    @Test
    void preservesDepth() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertEquals(DEPTH, result.depth());
    }

    @Test
    void preservesWeirdness() {
        Climate.TargetPoint result = GeoGradientSampler.transformClimate(fixedPoint(), 0, 0);
        assertEquals(WEIRDNESS, result.weirdness());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

```powershell
.\gradlew :common:test --tests "net.alkeari.geogradient.GeoGradientSamplerTest"
```

Expected: Compilation error or test failure because `transformClimate` does not exist yet.

- [ ] **Step 3: Write the new GeoGradientSampler.java**

Replace the entire file at `common/src/main/java/net/alkeari/geogradient/GeoGradientSampler.java`:

```java
package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;

/**
 * Transforms a vanilla {@link Climate.TargetPoint}, replacing its temperature
 * AND humidity with GeoGradient's latitude-based climate model.
 *
 * <p>Usage in a mixin:
 * <pre>{@code
 *   Climate.TargetPoint original = ...;
 *   Climate.TargetPoint adjusted = GeoGradientSampler.transformClimate(original, blockX, blockZ);
 * }</pre>
 *
 * <p>This class operates on already-sampled {@link Climate.TargetPoint} records
 * rather than on {@link net.minecraft.world.level.levelgen.DensityFunction} objects,
 * keeping it free of the MC bootstrap requirement for unit testing.
 */
public final class GeoGradientSampler {

    private GeoGradientSampler() {}

    /**
     * Returns a new {@link Climate.TargetPoint} identical to {@code original}
     * except that both temperature and humidity are replaced by
     * {@link GeoGradientClimate#sampleClimate(int, int)}.
     * Continentalness, erosion, depth, and weirdness are preserved unchanged.
     *
     * @param original the target point produced by vanilla biome sampling
     * @param blockX   block-coordinate X (biome-section X = blockX in mixin context)
     * @param blockZ   block-coordinate Z (biome-section Z = blockZ in mixin context)
     */
    public static Climate.TargetPoint transformClimate(
            Climate.TargetPoint original, int blockX, int blockZ) {
        long[] climate = GeoGradientClimate.sampleClimate(blockX, blockZ);
        return new Climate.TargetPoint(
                climate[0],                  // temperature  — replaced
                climate[1],                  // humidity     — replaced
                original.continentalness(),  // preserved
                original.erosion(),          // preserved
                original.depth(),            // preserved
                original.weirdness()         // preserved
        );
    }
}
```

- [ ] **Step 4: Update findBiomeForPreview in GeoGradientClimate.java**

Read `common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java`. Find the `findBiomeForPreview` method. Change the one line:

```java
// Before:
Climate.TargetPoint adjusted = GeoGradientSampler.transformTemperature(original, x, z);

// After:
Climate.TargetPoint adjusted = GeoGradientSampler.transformClimate(original, x, z);
```

Also remove the `// NOTE: transformTemperature is updated to transformClimate in Task 3.` comment on the line above it.

- [ ] **Step 5: Update MultiNoiseBiomeSourceMixin.java**

Read the file. Change only the one call-site: replace `transformTemperature` with `transformClimate`:

```java
// Before:
Climate.TargetPoint adjusted = GeoGradientSampler.transformTemperature(original, i, k);

// After:
Climate.TargetPoint adjusted = GeoGradientSampler.transformClimate(original, i, k);
```

No other changes to the mixin.

- [ ] **Step 6: Run the tests and confirm they pass**

```powershell
.\gradlew :common:test
```

Expected: All tests pass, including `replacesHumidity` and `replacesTemperature`.

- [ ] **Step 7: Commit**

```powershell
git add common/src/main/java/net/alkeari/geogradient/GeoGradientClimate.java `
        common/src/main/java/net/alkeari/geogradient/GeoGradientSampler.java `
        common/src/main/java/net/alkeari/geogradient/mixin/MultiNoiseBiomeSourceMixin.java `
        common/src/test/java/net/alkeari/geogradient/GeoGradientSamplerTest.java
git -c commit.gpgsign=false commit -m "feat: transformClimate overrides both temperature and humidity"
```

---

## Task 4: Update GeoGradientCommand to Show Humidity

**Run in parallel with Task 3 after Task 2 is complete.**

**Files:**
- Modify: `common/src/main/java/net/alkeari/geogradient/GeoGradientCommand.java`

**Context:** `ClimateInfo` now has a `humidity()` accessor (added in Task 2). The command's `sendInfo` method formats and sends the climate output. Add humidity to the display string.

- [ ] **Step 1: Update sendInfo in GeoGradientCommand.java**

Read the file. Replace the `sendInfo` method body (the `src.sendSuccess(...)` call):

```java
private static int sendInfo(CommandSourceStack src, int noiseX, int noiseZ) {
    if (!GeoGradientClimate.isInitialized()) {
        src.sendFailure(Component.literal("GeoGradient has not activated — load a world first."));
        return 0;
    }

    int blockX = noiseX << 2;
    int blockZ = noiseZ << 2;
    GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(blockX, blockZ);
    src.sendSuccess(() -> Component.literal(String.format(
            "§6[GeoGradient]§r at (%d, %d): §eTemp §r%.2f §eHumidity §r%.2f §7[%s]§r | NP %s | EQ %s | SP %s",
            blockX, blockZ,
            info.temp(), info.humidity(), info.zone(),
            formatDist(info.distNP()),
            formatDist(info.distEQ()),
            formatDist(info.distSP())
    )), false);
    return 1;
}
```

The only change from the old version: `"§eTemp §r%.2f §7[%s]§r"` becomes `"§eTemp §r%.2f §eHumidity §r%.2f §7[%s]§r"`, with `info.humidity()` inserted as the second `%.2f` argument.

- [ ] **Step 2: Verify compilation**

```powershell
.\gradlew :common:compileJava
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 3: Commit**

```powershell
git add common/src/main/java/net/alkeari/geogradient/GeoGradientCommand.java
git -c commit.gpgsign=false commit -m "feat: show humidity in /geogradient info output"
```

---

## Task 5: Build All Platforms and Deploy to Test Instance

**Run after Tasks 3 and 4 are both complete.**

**Files:** None modified.

- [ ] **Step 1: Run full test suite**

```powershell
.\gradlew :common:test
```

Expected: All tests pass.

- [ ] **Step 2: Build all platforms**

```powershell
.\gradlew build
```

Expected: `BUILD SUCCESSFUL`. Jars produced at:
- `forge\build\libs\geogradient-*-forge.jar` (not the `-sources` jar)
- `fabric\build\libs\geogradient-*-fabric.jar` (not the `-sources` jar)

If the build fails, check:
1. Did Task 3 update both `GeoGradientSampler.java` AND `MultiNoiseBiomeSourceMixin.java`?
2. Is `GeoGradientClimate.findBiomeForPreview` calling `transformClimate` (not `transformTemperature`)?
3. Does `GeoGradientCommand.java` reference `info.humidity()` which now exists in the updated `ClimateInfo` record?

- [ ] **Step 3: Find the exact Forge jar name**

```powershell
Get-ChildItem "forge\build\libs\" -Filter "*.jar" | Where-Object { $_.Name -notlike "*-sources*" }
```

Note the filename (e.g. `geogradient-1.0.0-forge.jar`).

- [ ] **Step 4: Copy Forge jar to test mods folder**

```powershell
$jar = Get-ChildItem "forge\build\libs\" -Filter "*.jar" | Where-Object { $_.Name -notlike "*-sources*" } | Select-Object -First 1
Copy-Item $jar.FullName "..\..\TEST - 1.20.1 - FORGE\mods\" -Force
Write-Host "Deployed: $($jar.Name)"
```

If the test folder is at a different relative path, adjust accordingly by listing `..\..\` to confirm the directory structure.

- [ ] **Step 5: Final commit (if any files were changed in this task)**

No files changed in Task 5. No commit needed.

---

## Self-Review Checklist (for plan executor)

Before marking implementation complete:
- [ ] All 11 files listed in the File Structure table have been modified
- [ ] `bandLookup` is package-private (no access modifier) so tests can call it
- [ ] `sampleTemperature` shim still exists (returns `sampleClimate(x, z)[0]`)
- [ ] `findBiomeForPreview` in `GeoGradientClimate.java` calls `transformClimate` (updated in Task 3 Step 4)
- [ ] `ClimateInfo` has 6 components: `temp, humidity, zone, distNP, distEQ, distSP`
- [ ] `CLIMATE_CACHE.clear()` is called in `reset()`
- [ ] `computeClimate` multiplies effectiveZ by `4.0` before the angle formula
- [ ] `blendFraction` config field is loaded in both Forge (`GeoGradientForge.java`) and Fabric (`GeoGradientFabric.java`) event handlers
- [ ] All tests pass: `.\gradlew :common:test`
- [ ] Build succeeds: `.\gradlew build`
- [ ] Forge jar deployed to test mods folder
