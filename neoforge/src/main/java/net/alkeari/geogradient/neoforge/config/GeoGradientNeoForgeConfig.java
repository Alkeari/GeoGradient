package net.alkeari.geogradient.neoforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class GeoGradientNeoForgeConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.IntValue GLOBE_SIZE;
    public static final ModConfigSpec.IntValue BORDER_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue BORDER_FREQUENCY;
    public static final ModConfigSpec.DoubleValue SPAWN_LATITUDE;
    public static final ModConfigSpec.DoubleValue BLEND_FRACTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("GeoGradient Climate Settings").push("climate");

        //noinspection GrazieInspection – globe_size is a config key name (technical underscore, intentional)
        GLOBE_SIZE = builder
                .comment("Distance in blocks from any pole (the coldest point) to the nearest equator (the hottest point). The spawn sits at the midpoint. The full climate cycle repeats every 2 x globe_size blocks.")
                .defineInRange("globe_size", 10000, 1000, 1000000);

        BORDER_AMPLITUDE = builder
                .comment("Maximum Z-axis warp applied by border noise, in blocks. Higher values = more jagged biome borders.")
                .defineInRange("border_amplitude", 200, 0, 10000);

        BORDER_FREQUENCY = builder
                .comment("Border noise frequency. Lower = broader waves; higher = tighter ripples.")
                .defineInRange("border_frequency", 0.002, 0.0001, 0.1);

        SPAWN_LATITUDE = builder
                .comment("Controls spawn override latitude. -1.0 = equator (Tropical Dry), 1.0 = north pole (Polar). 0.0 = no override (spawn stays at world default, which is near the equator/Tropical Dry zone).")
                .defineInRange("spawn_latitude", 0.0, -1.0, 1.0);

        BLEND_FRACTION = builder
                .comment("Width of cosine-blended transition zones at each climate band boundary, as a fraction of the narrower adjacent band. Range [0.0, 0.5].")
                .defineInRange("blend_fraction", 0.15, 0.0, 0.5);

        builder.pop();
        COMMON_SPEC = builder.build();
    }
}
