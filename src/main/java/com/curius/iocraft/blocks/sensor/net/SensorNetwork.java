package com.curius.iocraft.blocks.sensor.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;


public final class SensorNetwork {
    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("iocraft", "sensor"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    private static int ID = 0;
    private static int nextId() { return ID++; }

    public static void init() {
        CHANNEL.registerMessage(
                nextId(),
                PacketGuardarConfig.class,
                PacketGuardarConfig::encode,
                PacketGuardarConfig::decode,
                PacketGuardarConfig::handle
        );

        CHANNEL.registerMessage(
                nextId(),
                PacketSensorData.class,
                PacketSensorData::encode,
                PacketSensorData::decode,
                PacketSensorData::handle
        );
    }

    private SensorNetwork() {}
}
