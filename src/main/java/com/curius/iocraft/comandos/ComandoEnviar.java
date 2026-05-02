package com.curius.iocraft.comandos;

import com.curius.iocraft.ws.WsManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.Optional;
import java.util.UUID;

public class ComandoEnviar {

    private static final SuggestionProvider<CommandSourceStack> SUGERENCIAS_DESTINO =
            (ctx, builder) -> ComandoWsUtil.sugerirTargets(builder);

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ioc")
                .then(Commands.literal("enviar")
                        .then(Commands.argument("destino", StringArgumentType.string())
                                .suggests(SUGERENCIAS_DESTINO)
                                .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String destino = StringArgumentType.getString(ctx, "destino");
                                            String mensaje = StringArgumentType.getString(ctx, "mensaje");

                                            Optional<UUID> id = ComandoWsUtil.resolverDestino(destino, ctx.getSource());
                                            if (id.isEmpty()) return 0;

                                            boolean ok = WsManager.send(id.get(), mensaje);
                                            return ok ? 1 : 0;
                                        })
                                )))
        );
    }
}
