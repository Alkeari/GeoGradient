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
        // Equator is at blockZ = globeSize/2 from spawn
        float temp = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals(1.5f, temp, 0.001f);
    }

    @Test
    void negativeHalfGlobe_isNorthPole_coldest() {
        // North Pole is at blockZ = -globeSize/2 from spawn
        float temp = GeoGradientClimate.computeRawTemperature(-GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals(-1.5f, temp, 0.001f);
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
        assertTrue(result >= -15000L && result <= 15000L,
            "Expected quantized value in [-15000, 15000], got: " + result);
    }

    // --- zone boundary tests (globeSize=10000, band widths: Polar=1250, Cool=1250, Temperate=5000, Warm=1250, Tropical=1250) ---

    @Test
    void zone_spawn_isTemperate() {
        // z=0 → temp=0, deep inside Temperate
        assertEquals("Temperate", GeoGradientClimate.getZoneName(0.0f));
    }

    @Test
    void zone_atTemperateCoolBoundary_isCool() {
        // z=-2500 → temp=-1.061, just outside Temperate into Cool
        float temp = GeoGradientClimate.computeRawTemperature(-2500.0, GeoGradientConfig.globeSize);
        assertEquals("Cool", GeoGradientClimate.getZoneName(temp));
    }

    @Test
    void zone_atTemperateWarmBoundary_isWarm() {
        // z=+2500 → temp=+1.061, just outside Temperate into Warm
        float temp = GeoGradientClimate.computeRawTemperature(2500.0, GeoGradientConfig.globeSize);
        assertEquals("Warm", GeoGradientClimate.getZoneName(temp));
    }

    @Test
    void zone_atPole_isPolar() {
        // z=-5000 → temp=-1.5
        float temp = GeoGradientClimate.computeRawTemperature(-GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals("Polar", GeoGradientClimate.getZoneName(temp));
    }

    @Test
    void zone_atEquator_isTropical() {
        // z=+5000 → temp=+1.5
        float temp = GeoGradientClimate.computeRawTemperature(GeoGradientConfig.globeSize / 2.0, GeoGradientConfig.globeSize);
        assertEquals("Tropical", GeoGradientClimate.getZoneName(temp));
    }
}
