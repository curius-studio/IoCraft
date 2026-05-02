package com.curius.iocraft.blocks.sensor.net;

import com.curius.iocraft.blocks.sensor.SensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSensorData {
    public final BlockPos pos;
    public final String type;   // "sensor" / "interruptor" (por ahora decorativo)
    public final String data;   // dato recibido (texto)
    public final String device; // quién lo envió (para logs, opcional)

    public PacketSensorData(BlockPos pos, String type, String data, String device) {
        this.pos = pos;
        this.type = type;
        this.data = data;
        this.device = device;
    }

    public static void encode(PacketSensorData msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.type == null ? "" : msg.type);
        buf.writeUtf(msg.data == null ? "" : msg.data);
        buf.writeUtf(msg.device == null ? "" : msg.device);
    }

    public static PacketSensorData decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String type = buf.readUtf();
        String data = buf.readUtf();
        String device = buf.readUtf();
        return new PacketSensorData(pos, type, data, device);
    }

    public static void handle(PacketSensorData msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender(); // servidor integrado/real
            if (sender == null) return;
            var level = sender.level;
            if (level == null || !level.isLoaded(msg.pos)) return;

            var be = level.getBlockEntity(msg.pos);
            if (be instanceof SensorBlockEntity sensor) {
                sensor.onIncomingFromNetwork(msg.type, msg.data, msg.device);
            }
        });
        ctx.setPacketHandled(true);
    }
}
