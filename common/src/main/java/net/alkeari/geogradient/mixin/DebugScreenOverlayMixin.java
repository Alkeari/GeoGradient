package net.alkeari.geogradient.mixin;

import net.alkeari.geogradient.GeoGradientClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void geogradient$appendDebugInfo(CallbackInfoReturnable<List<String>> cir) {
        if (!GeoGradientClimate.isInitialized()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int blockX = mc.player.getBlockX();
        int blockZ = mc.player.getBlockZ();
        GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(blockX, blockZ);
        String line = String.format("GeoGradient: %.2f [%s] | NP %s | EQ %s | SP %s",
                info.temp(), info.zone(),
                GeoGradientClimate.formatDist(info.distNP()),
                GeoGradientClimate.formatDist(info.distEQ()),
                GeoGradientClimate.formatDist(info.distSP()));
        cir.getReturnValue().add("");
        cir.getReturnValue().add(line);
    }
}
