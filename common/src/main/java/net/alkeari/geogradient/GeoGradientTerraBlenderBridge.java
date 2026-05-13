package net.alkeari.geogradient;

import net.minecraft.world.level.biome.Climate;

import java.lang.reflect.Method;

/**
 * Soft dependency bridge for TerraBlender.
 * Uses reflection so GeoGradient compiles and runs without TerraBlender on the classpath.
 * When TerraBlender is present, biome lookups use its positional region logic
 * (IExtendedParameterList.findValuePositional) so that modded regions are respected.
 * When absent, falls back to the vanilla Climate.ParameterList.findValue.
 */
public final class GeoGradientTerraBlenderBridge {

    private static final Method FIND_VALUE_POSITIONAL;

    static {
        Method m = null;
        try {
            Class<?> iface = Class.forName("terrablender.worldgen.IExtendedParameterList");
            m = iface.getMethod("findValuePositional",
                    Climate.TargetPoint.class, int.class, int.class, int.class);
        } catch (ClassNotFoundException ignored) {
            // TerraBlender is not installed
        } catch (Exception e) {
            GeoGradient.LOGGER.warn("GeoGradient: unexpected error while probing TerraBlender API", e);
        }
        FIND_VALUE_POSITIONAL = m;
    }

    private GeoGradientTerraBlenderBridge() {}

    /**
     * Looks up the biome for the given adjusted target point.
     * Delegates to TerraBlender's positional lookup when available,
     * otherwise uses vanilla parameter-list search.
     *
     * @param params the parameter list from MultiNoiseBiomeSource
     * @param target the (already temperature-adjusted) target point
     * @param x      noise X coordinate (block >> 2)
     * @param y      noise Y coordinate (block >> 2)
     * @param z      noise Z coordinate (block >> 2)
     */
    @SuppressWarnings("unchecked")
    public static <T> T findBiome(Climate.ParameterList<T> params,
                                  Climate.TargetPoint target,
                                  int x, int y, int z) {
        if (FIND_VALUE_POSITIONAL != null) {
            try {
                return (T) FIND_VALUE_POSITIONAL.invoke(params, target, x, y, z);
            } catch (Exception ignored) {
                // fall through to vanilla
            }
        }
        return params.findValue(target);
    }
}
