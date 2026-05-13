package net.alkeari.geogradient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class GeoGradientCommand {

    private GeoGradientCommand() {}

    public static void register() {
        CommandRegistrationEvent.EVENT.register(GeoGradientCommand::registerCommands);
    }

    @SuppressWarnings("unused")
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                         CommandBuildContext registry,
                                         Commands.CommandSelection selection) {
        dispatcher.register(
            Commands.literal("geogradient")
                .then(Commands.literal("info")
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        BlockPos pos = BlockPos.containing(src.getPosition());
                        return sendInfo(src, pos.getX() >> 2, pos.getZ() >> 2);
                    })
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(ctx -> {
                                int x = IntegerArgumentType.getInteger(ctx, "x");
                                int z = IntegerArgumentType.getInteger(ctx, "z");
                                return sendInfo(ctx.getSource(), x >> 2, z >> 2);
                            })
                        )
                    )
                )
        );
    }

    private static int sendInfo(CommandSourceStack src, int noiseX, int noiseZ) {
        if (!GeoGradientClimate.isInitialized()) {
            src.sendFailure(Component.literal("GeoGradient has not activated — load a world first."));
            return 0;
        }

        int blockX = noiseX << 2;
        int blockZ = noiseZ << 2;
        GeoGradientClimate.ClimateInfo info = GeoGradientClimate.sampleAt(blockX, blockZ);
        src.sendSuccess(() -> Component.literal(String.format(
                "§6[GeoGradient]§r at (%d, %d): §eTemp §r%.2f §7[%s]§r | NP %s | EQ %s | SP %s",
                blockX, blockZ,
                info.temp(), info.zone(),
                formatDist(info.distNP()),
                formatDist(info.distEQ()),
                formatDist(info.distSP())
        )), false);
        return 1;
    }

    private static String formatDist(int diff) {
        if (diff == 0) return "§aHere";
        // diff = playerZ - targetZ; positive means player is south of target → target is N
        return String.format("§b%d%s", Math.abs(diff), diff > 0 ? "N" : "S");
    }
}
