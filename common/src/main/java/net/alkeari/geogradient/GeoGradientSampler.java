package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;

/**
 * Transforms a vanilla {@link Climate.TargetPoint}, replacing its temperature
 * with GeoGradient's latitude-based temperature model.
 *
 * <p>Usage in a mixin:
 * <pre>{@code
 *   Climate.TargetPoint original = ...;
 *   Climate.TargetPoint adjusted = GeoGradientSampler.transformTemperature(original, blockX, blockZ);
 * }</pre>
 *
 * <p>This class deliberately operates on already-sampled {@link Climate.TargetPoint}
 * records rather than on {@link net.minecraft.world.level.levelgen.DensityFunction}
 * objects, keeping it free of the MC bootstrap requirement so it can be tested
 * without a running Minecraft instance.
 */
public final class GeoGradientSampler {

    private GeoGradientSampler() {}

    /**
     * Returns a new {@link Climate.TargetPoint} identical to {@code original}
     * except that its temperature is replaced by
     * {@link GeoGradientClimate#sampleTemperature(int, int)}.
     *
     * @param original the target point produced by vanilla biome sampling
     * @param blockX   block-coordinate X
     * @param blockZ   block-coordinate Z
     */
    public static Climate.TargetPoint transformTemperature(
            Climate.TargetPoint original, int blockX, int blockZ) {
        return new Climate.TargetPoint(
                GeoGradientClimate.sampleTemperature(blockX, blockZ),
                original.humidity(),
                original.continentalness(),
                original.erosion(),
                original.depth(),
                original.weirdness()
        );
    }
}
