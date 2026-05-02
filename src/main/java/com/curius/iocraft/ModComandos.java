package com.curius.iocraft;

import com.curius.iocraft.comandos.RegistroComandos;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModIoCraft.MOD_ID)
public class ModComandos {

    @SubscribeEvent
    public static void alRegistrarComandos(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        RegistroComandos.registrarTodos(dispatcher);
    }
}
