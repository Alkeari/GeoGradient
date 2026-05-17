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
        // With amp=0, noiseZ=1250 → effectiveZ=1250 → effectiveZ*4=5000=globeSize/2 → d=0.0 → Tropical Wet center temp=0.40
        long[] result = GeoGradientClimate.sampleClimate(0, 1250);
        long expectedTemp = net.minecraft.world.level.biome.Climate.quantizeCoord(0.40f);
        assertEquals(expectedTemp, result[0], "temperature at equator");
    }

    @Test
    void sampleClimate_northPole_isInPolarTempRange() {
        // With amp=0, noiseZ=-1250 → effectiveZ=-1250 → effectiveZ*4=-5000=-globeSize/2 → d=1.0 → Polar center temp=-0.85
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
    void sampleClimate_negativeX_distinctZProducesDifferentResults() {
        // Regression: packKey sign-extension bug collapsed all (x<0, z) to one key.
        // At x=-100, z=0 (equator) and z=-1250 (pole) must be distinct climate values.
        long[] equator = GeoGradientClimate.sampleClimate(-100, 1250);
        long[] pole    = GeoGradientClimate.sampleClimate(-100, -1250);
        assertFalse(equator[0] == pole[0] && equator[1] == pole[1],
            "negative-X coords with different Z must produce distinct climate (cache key collision check)");
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
