package com.curius.iocraft.net;

import com.curius.iocraft.ModIoCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CoreNetwork {
    private CoreNetwork() {}

    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModIoCraft.MOD_ID, "core"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    private static int id = 0;

    public static void init() {
        CHANNEL.messageBuilder(PacketInboxUpdate.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PacketInboxUpdate::encode)
                .decoder(PacketInboxUpdate::decode)
                // Usa el handler estático con Supplier<Context>
                .consumer(PacketInboxUpdate::handle)
                .add();
    }
}
