package com.curius.iocraft.blocks.computer.net;

import com.curius.iocraft.blocks.computer.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketGuardarDestino {
    public final BlockPos pos;
    public final String destino;

    public PacketGuardarDestino(BlockPos pos, String destino) {
        this.pos = pos;
        this.destino = destino;
    }

    public static void encode(PacketGuardarDestino msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.destino == null ? "" : msg.destino);
    }

    public static PacketGuardarDestino decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String destino = buf.readUtf();
        return new PacketGuardarDestino(pos, destino);
    }

    public static void handle(PacketGuardarDestino msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            var level = sender.level;
            if (level == null || !level.isLoaded(msg.pos)) return;

            var be = level.getBlockEntity(msg.pos);
            if (be instanceof ComputerBlockEntity cbe) {
                cbe.setDestino(msg.destino);
            }
        });
        ctx.setPacketHandled(true);
    }
}
