package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;

public final class GeoGradientSampler {

    private GeoGradientSampler() {}

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
