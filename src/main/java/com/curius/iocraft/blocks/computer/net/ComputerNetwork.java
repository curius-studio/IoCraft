// ComputerNetwork.java
package com.curius.iocraft.blocks.computer.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ComputerNetwork {
    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("iocraft", "computer"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private static int ID = 0;
    private static int id() { return ID++; }

    public static void init() {
        // Ya existentes…
        CHANNEL.registerMessage(
                id(), PacketGuardarDestino.class,
                PacketGuardarDestino::encode,
                PacketGuardarDestino::decode,
                PacketGuardarDestino::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                id(), PacketEnviarMensaje.class,
                PacketEnviarMensaje::encode,
                PacketEnviarMensaje::decode,
                PacketEnviarMensaje::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // NUEVO: mensaje entrante desde el socket (cliente -> servidor)
        CHANNEL.registerMessage(
                id(), PacketSocketMensaje.class,
                PacketSocketMensaje::encode,
                PacketSocketMensaje::decode,
                PacketSocketMensaje::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }
}
