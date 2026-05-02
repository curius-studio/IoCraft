// com/curius/iocraft/blocks/puerta/net/PuertaNetwork.java
package com.curius.iocraft.blocks.puerta.net;

import com.curius.iocraft.ModIoCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PuertaNetwork {
    private PuertaNetwork() {}

    private static final String PROTO = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModIoCraft.MOD_ID, "puerta"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    public static void init() {
        int id = 0;

        // C2S: guardar config
        CHANNEL.messageBuilder(PacketGuardarPuertaConfig.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketGuardarPuertaConfig::encode)
                .decoder(PacketGuardarPuertaConfig::decode)
                .consumer(PacketGuardarPuertaConfig::handle)
                .add();

        // C2S: comparar mensaje recibido con lo configurado en la puerta
        CHANNEL.messageBuilder(PacketPuertaComparar.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PacketPuertaComparar::encode)
                .decoder(PacketPuertaComparar::decode)
                .consumer(PacketPuertaComparar::handle)
                .add();
    }
}
