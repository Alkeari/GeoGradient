package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GeoGradientSampler}.
 *
 * <p>Adaptation note: {@link Climate.Sampler} is a final record (not an interface)
 * in MC 1.20.1, and implementing {@link net.minecraft.world.level.levelgen.DensityFunction}
 * triggers the MC registry bootstrap which is unavailable in unit tests.
 *
 * <p>Therefore {@link GeoGradientSampler} operates on {@link Climate.TargetPoint}
 * records directly via {@link GeoGradientSampler#transformTemperature}. Tests
 * construct {@link Climate.TargetPoint} instances directly — no DensityFunction
 * involved, no bootstrap required.
 */
class GeoGradientSamplerTest {

    // 99999L is outside GeoGradient's output range of [-10000, 10000], so it can
    // never be produced by sampleTemperature — the assertNotEquals is reliable.
    private static final long TEMPERATURE     = 99999L;
    private static final long HUMIDITY        = 1111L;
    private static final long CONTINENTALNESS = 2222L;
    private static final long EROSION         = 3333L;
    private static final long DEPTH           = 4444L;
    private static final long WEIRDNESS       = 5555L;

    @BeforeEach
    void initClimate() {
        GeoGradientClimate.initialize(42L);
    }

    private static Climate.TargetPoint fixedPoint() {
        return new Climate.TargetPoint(
                TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, DEPTH, WEIRDNESS
        );
    }

    @Test
    void replacesTemperature() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertNotEquals(TEMPERATURE, result.temperature());
    }

    @Test
    void preservesHumidity() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertEquals(HUMIDITY, result.humidity());
    }

    @Test
    void preservesContinentalness() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertEquals(CONTINENTALNESS, result.continentalness());
    }

    @Test
    void preservesErosion() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertEquals(EROSION, result.erosion());
    }

    @Test
    void preservesDepth() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertEquals(DEPTH, result.depth());
    }

    @Test
    void preservesWeirdness() {
        Climate.TargetPoint result = GeoGradientSampler.transformTemperature(fixedPoint(), 0, 0);
        assertEquals(WEIRDNESS, result.weirdness());
    }
}
