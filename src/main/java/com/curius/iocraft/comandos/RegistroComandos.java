package com.curius.iocraft.comandos;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class RegistroComandos {
    public static void registrarTodos(CommandDispatcher<CommandSourceStack> dispatcher) {
        ComandoListaDispositivos.registrar(dispatcher);
        ComandoEnviar.registrar(dispatcher);
        ComandoBroadcast.registrar(dispatcher);
        ComandoInfoConexion.registrar(dispatcher);
        ComandoMenuDispositivos.registrar(dispatcher);
        ComandoSecurity.registrar(dispatcher);
        ComandoAddons.registrar(dispatcher);
    }
}
