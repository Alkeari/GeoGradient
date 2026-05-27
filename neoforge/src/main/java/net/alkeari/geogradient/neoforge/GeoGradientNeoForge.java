package net.alkeari.geogradient.neoforge;

import net.alkeari.geogradient.GeoGradient;
import net.alkeari.geogradient.GeoGradientClimate;
import net.alkeari.geogradient.config.GeoGradientConfig;
import net.alkeari.geogradient.neoforge.config.GeoGradientNeoForgeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(GeoGradient.MOD_ID)
public class GeoGradientNeoForge {

    public GeoGradientNeoForge(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, GeoGradientNeoForgeConfig.COMMON_SPEC);
        NeoForge.EVENT_BUS.register(this);
        GeoGradient.init();
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        GeoGradientConfig.globeSize      = GeoGradientNeoForgeConfig.GLOBE_SIZE.get();
        GeoGradientConfig.borderAmplitude = GeoGradientNeoForgeConfig.BORDER_AMPLITUDE.get();
        GeoGradientConfig.borderFrequency = GeoGradientNeoForgeConfig.BORDER_FREQUENCY.get();
        GeoGradientConfig.spawnLatitude   = GeoGradientNeoForgeConfig.SPAWN_LATITUDE.get();
        GeoGradientConfig.blendFraction   = GeoGradientNeoForgeConfig.BLEND_FRACTION.get();

        GeoGradientClimate.initialize(level.getSeed());
        applySpawnLatitude(level);
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        GeoGradientClimate.reset();
    }

    private void applySpawnLatitude(ServerLevel level) {
        double spawnLatitude = GeoGradientConfig.spawnLatitude;
        if (spawnLatitude == 0.0) return;

        Path marker = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("geogradient_spawn_applied.flag");
        if (Files.exists(marker)) return;

        int spawnZ = (int) (-spawnLatitude * GeoGradientConfig.globeSize / 2.0);
        BlockPos target = new BlockPos(0, 0, spawnZ);
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, target);
        level.setDefaultSpawnPos(surface, 0.0f);
        GeoGradient.LOGGER.info("GeoGradient: spawn set to {} (latitude {})", surface, spawnLatitude);

        try {
            Files.createFile(marker);
        } catch (IOException e) {
            GeoGradient.LOGGER.warn("GeoGradient: could not write spawn marker file", e);
        }
    }
}
