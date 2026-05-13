package net.alkeari.geogradient.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes MultiNoiseBiomeSource.parameters() (which is private)
 * so non-mixin code can access the parameter list for preview lookups.
 */
@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseBiomeSourceAccessor {
    @Invoker("parameters")
    Climate.ParameterList<Holder<Biome>> geogradient$parameters();
}
