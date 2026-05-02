package com.curius.iocraft.comandos.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ComandosNetwork {
    private ComandosNetwork() {}

    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("iocraft", "comandos"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    public static void init() {
        int id = 0;
        CHANNEL.registerMessage(id++,
                PacketEjecutarComando.class,
                PacketEjecutarComando::encode,
                PacketEjecutarComando::decode,
                PacketEjecutarComando::handle);
    }
}
