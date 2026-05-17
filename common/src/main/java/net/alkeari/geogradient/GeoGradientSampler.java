package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;

public final class GeoGradientSampler {

    private GeoGradientSampler() {}

    public static Climate.TargetPoint transformClimate(
            Climate.TargetPoint original, int noiseX, int noiseZ) {
        long[] climate = GeoGradientClimate.sampleClimate(noiseX, noiseZ);
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
