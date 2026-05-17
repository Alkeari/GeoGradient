package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
