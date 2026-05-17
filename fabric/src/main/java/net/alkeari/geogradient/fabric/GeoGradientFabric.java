package net.alkeari.geogradient.fabric;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.alkeari.geogradient.GeoGradient;
import net.alkeari.geogradient.GeoGradientClimate;
import net.alkeari.geogradient.config.GeoGradientConfig;
import net.alkeari.geogradient.fabric.config.GeoGradientFabricConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GeoGradientFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoConfig.register(GeoGradientFabricConfig.class, GsonConfigSerializer::new);

        ServerWorldEvents.LOAD.register((server, level) -> {
            if (!level.dimension().equals(Level.OVERWORLD)) return;

            GeoGradientFabricConfig cfg =
                    AutoConfig.getConfigHolder(GeoGradientFabricConfig.class).getConfig();

            GeoGradientConfig.globeSize       = cfg.globe_size;
            GeoGradientConfig.borderAmplitude  = cfg.border_amplitude;
            GeoGradientConfig.borderFrequency  = cfg.border_frequency;
            GeoGradientConfig.spawnLatitude    = cfg.spawn_latitude;
            GeoGradientConfig.blendFraction   = cfg.blend_fraction;

            GeoGradientClimate.initialize(level.getSeed());
            applySpawnLatitude(level);
        });

        ServerWorldEvents.UNLOAD.register((server, level) -> {
            if (!level.dimension().equals(Level.OVERWORLD)) return;
            GeoGradientClimate.reset();
        });

        GeoGradient.init();
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
