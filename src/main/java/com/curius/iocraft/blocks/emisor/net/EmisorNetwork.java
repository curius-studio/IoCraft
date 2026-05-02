package com.curius.iocraft.blocks.emisor.net;

import com.curius.iocraft.ModIoCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class EmisorNetwork {
    private EmisorNetwork(){}

    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModIoCraft.MOD_ID, "datos_net"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    private static boolean registered = false;

    /** LLAMAR SOLO UNA VEZ desde ModIoCraft.commonSetup (enqueueWork). */
    public static void init() {
        if (registered) return;
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                PacketGuardarEmisor.class,
                PacketGuardarEmisor::encode,
                PacketGuardarEmisor::decode,
                PacketGuardarEmisor::handle
        );
        registered = true;
    }
}
