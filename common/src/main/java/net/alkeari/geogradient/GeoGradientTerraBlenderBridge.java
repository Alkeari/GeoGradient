package net.alkeari.geogradient;

import net.minecraft.core.RegistryAccess;
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
    private static volatile boolean tbInvokeFailed = false;

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
     * Called by Genesis via reflection when the world-preview screen is closed.
     * Clears GeoGradient state so the next preview starts fresh.
     */
    @SuppressWarnings("unused")
    public static void reset() {
        GeoGradientClimate.reset();
    }

    /**
     * Called by Genesis via reflection when opening the world-preview screen.
     * Genesis initialises the actual seed and biome source separately via
     * GeoGradientClimate.initialize(long, BiomeSource); this method exists
     * solely so Genesis can confirm the API is present via reflection.
     */
    @SuppressWarnings("unused")
    public static void tryInitialize(RegistryAccess registryAccess) {
        // intentional no-op: Genesis calls initialize(long, BiomeSource) directly
    }

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
            } catch (Exception e) {
                if (!tbInvokeFailed) {
                    tbInvokeFailed = true;
                    GeoGradient.LOGGER.warn("GeoGradient: TerraBlender findValuePositional failed; falling back to vanilla lookup", e);
                }
                // fall through to vanilla
            }
        }
        return params.findValue(target);
    }
}
