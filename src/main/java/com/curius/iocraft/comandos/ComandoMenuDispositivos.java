package com.curius.iocraft.comandos;

import com.curius.iocraft.ui.MenuDispositivos;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ComandoMenuDispositivos {

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ioc")
                        .then(Commands.literal("menu")
                                .executes(ComandoMenuDispositivos::ejecutar)
                        )
        );
    }

    private static int ejecutar(CommandContext<CommandSourceStack> context) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new MenuDispositivos()));
        return Command.SINGLE_SUCCESS;
    }
}
