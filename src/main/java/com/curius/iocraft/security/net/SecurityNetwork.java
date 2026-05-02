package com.curius.iocraft.security.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SecurityNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("iocraft", "security"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private static int ID = 0;
    public static void init() {
        CHANNEL.registerMessage(ID++, PacketGuardarDispositivo.class,
                PacketGuardarDispositivo::encode,
                PacketGuardarDispositivo::decode,
                PacketGuardarDispositivo::handle
        );
        CHANNEL.registerMessage(ID++, PacketMostrarSecretRotado.class,
                PacketMostrarSecretRotado::encode,
                PacketMostrarSecretRotado::decode,
                PacketMostrarSecretRotado::handle
        );
    }
}
