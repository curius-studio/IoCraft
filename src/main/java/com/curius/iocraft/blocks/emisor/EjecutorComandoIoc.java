package com.curius.iocraft.blocks.emisor;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Ejecuta el comando prediseñado:
 *   /ioc enviar "<param1>" "<param2>"
 *
 * Úsalo SOLO en lado servidor (Level#isClientSide == false).
 */
public final class EjecutorComandoIoc {

    private EjecutorComandoIoc() {}

    /** Ejecuta /ioc enviar "<param1>" "<param2>" desde la posición dada. */
    public static void enviar(Level level, BlockPos pos, String param1, String param2) {
        if (level == null || level.isClientSide) return;
        if (!(level instanceof ServerLevel sl)) return;

        // Sanea params (escapa comillas)
        String p1 = param1 == null ? "" : param1.replace("\"", "\\\"");
        String p2 = param2 == null ? "" : param2.replace("\"", "\\\"");

        String comando = String.format("ioc enviar \"%s\" %s", p1, p2);

        CommandSourceStack src = new CommandSourceStack(
                CommandSource.NULL,
                Vec3.atCenterOf(pos),
                Vec2.ZERO,
                sl,
                /* permissionLevel */ 2,
                "IoCraft",
                new TextComponent("IoCraft"),
                sl.getServer(),
                null
        );

        // performCommand devuelve int (resultado de ejecución)
        sl.getServer().getCommands().performCommand(src, comando);
    }
}
