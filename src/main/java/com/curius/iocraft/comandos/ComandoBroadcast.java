package com.curius.iocraft.comandos;

import com.curius.iocraft.ws.WsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

public class ComandoBroadcast {
    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ioc")
                .then(Commands.literal("broadcast")
                        .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String mensaje = StringArgumentType.getString(ctx, "mensaje");
                                    int enviados = WsManager.broadcast(mensaje);

                                    ctx.getSource().sendSuccess(
                                            new TextComponent("§aMensaje enviado a " + enviados + " dispositivos."),
                                            false
                                    );
                                    return enviados;
                                })
                        )
                )
        );
    }
}
