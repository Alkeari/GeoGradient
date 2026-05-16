package net.alkeari.geogradient;

import net.alkeari.geogradient.config.GeoGradientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoGradientClimateTest {

    @BeforeEach
    void resetConfig() {
        GeoGradientConfig.globeSize = 10000;
        GeoGradientClimate.reset();
    }

    @Test
    void zeroZ_isTemperateZero() {
        float temp = GeoGradientClimate.computeRawTemperature(0.0, GeoGradientConfig.globeSize);
        assertEquals(0.0f, temp, 0.001f);
    }

    @Test
    void halfGlobe_isEquator_hottest() {
        // Equator is at blockZ = globeSize/2 from spawn; max vanilla temperature = 1.0
        float temp = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals(1.0f, temp, 0.001f);
    }

    @Test
    void negativeHalfGlobe_isNorthPole_coldest() {
        // North Pole is at blockZ = -globeSize/2 from spawn; min vanilla temperature = -1.0
        float temp = GeoGradientClimate.computeRawTemperature(-GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals(-1.0f, temp, 0.001f);
    }

    @Test
    void fullGlobe_isNeutralAgain() {
        // Full pole-to-equator distance lands back at temperate (half the sine period)
        float temp = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize * 1.0, GeoGradientConfig.globeSize);
        assertEquals(0.0f, temp, 0.001f);
    }

    @Test
    void repeatsAfterTwoGlobes() {
        // Full sine period = 2 * globeSize
        float tempAtZero = GeoGradientClimate.computeRawTemperature(0.0, GeoGradientConfig.globeSize);
        float tempAtTwo  = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize * 2.0, GeoGradientConfig.globeSize);
        assertEquals(tempAtZero, tempAtTwo, 0.001f);
    }

    @Test
    void sampleTemperature_returnsQuantizedValueInRange_afterInit() {
        GeoGradientClimate.initialize(42L);
        long result = GeoGradientClimate.sampleTemperature(0, 0);
        assertTrue(result >= -10000L && result <= 10000L,
            "Expected quantized value in [-10000, 10000], got: " + result);
    }

    // --- zone tests — thresholds are vanilla tier boundaries: -0.45, -0.15, +0.20, +0.55 ---

    @Test
    void zone_spawn_isTemperate() {
        assertEquals("Temperate", GeoGradientClimate.getZoneName(0.0f));
    }

    @Test
    void zone_atPole_isPolar() {
        // z=-5000 → temp=-1.0
        float temp = GeoGradientClimate.computeRawTemperature(-GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals("Polar", GeoGradientClimate.getZoneName(temp));
    }

    @Test
    void zone_atEquator_isTropical() {
        // z=+5000 → temp=+1.0
        float temp = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals("Tropical", GeoGradientClimate.getZoneName(temp));
    }

    @Test
    void zone_subarcticThreshold() {
        // Just inside Subarctic (Cold tier: -0.45 to -0.15)
        assertEquals("Subarctic", GeoGradientClimate.getZoneName(-0.44f));
    }

    @Test
    void zone_subtropicalThreshold() {
        // Just inside Subtropical (Warm tier: 0.20 to 0.55)
        assertEquals("Subtropical", GeoGradientClimate.getZoneName(0.21f));
    }

    @Test
    void zone_tropicalThreshold() {
        // Just inside Tropical (Hot tier: 0.55 to 1.0)
        assertEquals("Tropical", GeoGradientClimate.getZoneName(0.56f));
    }
}
