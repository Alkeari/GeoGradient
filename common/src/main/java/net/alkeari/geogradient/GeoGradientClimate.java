package net.alkeari.geogradient;

import net.alkeari.geogradient.config.GeoGradientConfig;
import net.alkeari.geogradient.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class GeoGradientClimate {

    private static volatile SimplexNoise noise;
    private static volatile boolean initialized = false;
    private static volatile BiomeSource storedBiomeSource;

    public static synchronized void initialize(long seed) {
        if (initialized) return;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed ^ 0x47656F47L));
        noise = new SimplexNoise(random);
        initialized = true;
    }

    /**
     * Variant called by external mods (e.g. Genesis) for preview/off-thread use.
     * Always reinitializes so the seed and biome source stay in sync with the caller.
     */
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
    }

    /**
     * Looks up the biome that GeoGradient would select at the given noise coordinates.
     * Intended for use by external mods (e.g. Genesis world-preview) via reflection.
     * Returns null if GeoGradient has not been initialized or no biome source is stored.
     *
     * @param x       noise X coordinate (block >> 2)
     * @param y       noise Y coordinate (block >> 2)
     * @param z       noise Z coordinate (block >> 2)
     * @param sampler the world's climate sampler
     */
    @SuppressWarnings("unused")
    public static Holder<Biome> findBiomeForPreview(int x, int y, int z, Climate.Sampler sampler) {
        if (noise == null) return null;
        Climate.TargetPoint original = sampler.sample(x, y, z);
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

    public static float getTemperatureAt(int x, int z) {
        if (noise == null) return 0.0f;
        double effectiveZNoise = z + noise.getValue(
                x * GeoGradientConfig.borderFrequency,
                z * GeoGradientConfig.borderFrequency
        ) * GeoGradientConfig.borderAmplitude;
        return computeRawTemperature(effectiveZNoise * 4, GeoGradientConfig.globeSize);
    }

    public record ClimateInfo(float temp, String zone, int distNP, int distEQ, int distSP) {}

    public static ClimateInfo sampleAt(int blockX, int blockZ) {
        int noiseX = blockX >> 2;
        int noiseZ = blockZ >> 2;
        float temp = getTemperatureAt(noiseX, noiseZ);
        String zone = getZoneName(temp);
        int gs = GeoGradientConfig.globeSize;
        int period = gs * 2;
        int coldOffset = -(gs / 2);
        int hotOffset  =   gs / 2;
        int npBlock = nearestLandmarkNorth(blockZ, coldOffset, period);
        int eqBlock = nearestLandmark(blockZ, hotOffset, period);
        int spBlock = nearestLandmarkSouth(blockZ, coldOffset, period);
        return new ClimateInfo(temp, zone, blockZ - npBlock, blockZ - eqBlock, blockZ - spBlock);
    }

    public static String formatDist(int diff) {
        if (diff == 0) return "Here";
        return Math.abs(diff) + (diff > 0 ? "N" : "S");
    }

    public static String getZoneName(float temp) {
        // Thresholds are the exact vanilla MultiNoise tier boundaries so every
        // zone transition is a visible terrain change.
        if (temp >  0.55f) return "Tropical";
        if (temp >  0.20f) return "Subtropical";
        if (temp > -0.15f) return "Temperate";
        if (temp > -0.45f) return "Subarctic";
        return "Polar";
    }

    public static int nearestLandmark(int z, int landmarkOffset, int period) {
        long diff = (long) z - landmarkOffset;
        long k = Math.round((double) diff / period);
        return (int) (landmarkOffset + k * period);
    }

    // Returns the nearest landmark strictly to the north (negative Z).
    // If z is on a landmark, returns the next one north.
    public static int nearestLandmarkNorth(int z, int offset, int period) {
        long k = Math.floorDiv((long) z - offset, period);
        int atOrNorth = (int) (offset + k * period);
        return (atOrNorth < z) ? atOrNorth : (int) (offset + (k - 1) * period);
    }

    // Returns the nearest landmark strictly to the south (positive Z).
    // If z is on a landmark, returns the next one south.
    public static int nearestLandmarkSouth(int z, int offset, int period) {
        long k = Math.floorDiv((long) z - offset, period);
        return (int) (offset + (k + 1) * period);
    }

    public static long sampleTemperature(int x, int z) {
        if (noise == null) {
            throw new IllegalStateException("GeoGradientClimate.initialize() must be called before sampleTemperature()");
        }
        double effectiveZNoise = z + noise.getValue(
                x * GeoGradientConfig.borderFrequency,
                z * GeoGradientConfig.borderFrequency
        ) * GeoGradientConfig.borderAmplitude;
        return Climate.quantizeCoord(computeRawTemperature(effectiveZNoise * 4, GeoGradientConfig.globeSize));
    }

    static float computeRawTemperature(double z, int globeSize) {
        // globeSize = pole-to-equator distance in blocks; full period = 2 * globeSize
        // Output range [-1.0, +1.0] matches the vanilla MultiNoise temperature range exactly.
        double angle = (z / (globeSize * 2.0)) * 2.0 * Math.PI;
        return (float) Math.sin(angle);
    }
}
