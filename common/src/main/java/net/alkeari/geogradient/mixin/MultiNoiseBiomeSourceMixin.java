package net.alkeari.geogradient.mixin;

import net.alkeari.geogradient.GeoGradientClimate;
import net.alkeari.geogradient.GeoGradientSampler;
import net.alkeari.geogradient.GeoGradientTerraBlenderBridge;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Priority 500 ensures this injector is applied before TerraBlender's default-priority (1000)
 * injector, so it executes AFTER TerraBlender's callback at runtime and wins the
 * setReturnValue race. This is required for GeoGradient's temperature override to take
 * effect when TerraBlender is installed.
 * <p>
 * When TerraBlender is absent, the bridge falls back to vanilla Climate.ParameterList.findValue.
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 500)
public abstract class MultiNoiseBiomeSourceMixin {

    @Shadow
    protected abstract Climate.ParameterList<Holder<Biome>> parameters();

    @Inject(method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), cancellable = true)
    private void geogradient$injectSampler(
            int i, int j, int k,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> cir
    ) {
        if (!GeoGradientClimate.isInitialized()) return;
        Climate.TargetPoint original = sampler.sample(i, j, k);
        Climate.TargetPoint adjusted = GeoGradientSampler.transformClimate(original, i, k);
        cir.setReturnValue(GeoGradientTerraBlenderBridge.findBiome(this.parameters(), adjusted, i, j, k));
    }
}
