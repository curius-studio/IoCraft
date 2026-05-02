package com.curius.iocraft.comandos;

import com.curius.iocraft.ws.DeviceInfo;
import com.curius.iocraft.ws.DeviceRegistry;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

import java.util.List;

public class ComandoListaDispositivos {
    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ioc")
                .then(Commands.literal("lista")
                        .executes(ctx -> {
                            List<DeviceInfo> dispositivos = DeviceRegistry.snapshot();
                            ctx.getSource().sendSuccess(new TextComponent("Dispositivos conectados: " + dispositivos.size()), false);

                            if (dispositivos.isEmpty()) return 0;

                            for (int i = 0; i < dispositivos.size(); i++) {
                                DeviceInfo d = dispositivos.get(i);
                                String nombre = d.nombre != null ? d.nombre : "(sin-nombre)";
                                String ip = d.ip != null ? d.ip : "-";
                                String shortId = d.id.toString().substring(0, 8);
                                ctx.getSource().sendSuccess(
                                        new TextComponent("#" + (i + 1) + " | " + nombre + " | " + ip + " | " + shortId),
                                        false
                                );
                            }
                            return dispositivos.size();
                        })
                )
        );
    }
}
