package com.curius.iocraft;

import com.curius.iocraft.mensajeria.RegistroManejadores;
import com.curius.iocraft.ws.WsManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ModIoCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientLifecycle {

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // (opcional) cambiar host/puerto antes de iniciar
            // WsManager.setHost("192.168.1.2");
            // WsManager.setPort(8765);

            // Registrar manejadores por defecto del sistema nuevo
            RegistroManejadores.registrarPorDefecto();

            // Iniciar servidor WS
            WsManager.start();
        });
    }
}
