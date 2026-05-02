package com.curius.iocraft.blocks.puerta.net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import com.curius.iocraft.blocks.puerta.logic.AccionesPuerta;

public class PacketPuertaCoincidencia {
    public final BlockPos pos;
    public final boolean abrir; // true = abrir, false = cerrar

    public PacketPuertaCoincidencia(BlockPos pos, boolean abrir) {
        this.pos = pos;
        this.abrir = abrir;
    }

    public static void encode(PacketPuertaCoincidencia msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.abrir);
    }

    public static PacketPuertaCoincidencia decode(FriendlyByteBuf buf) {
        return new PacketPuertaCoincidencia(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PacketPuertaCoincidencia msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            ServerLevel level = sender.getLevel();
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (be instanceof PuertaBlockEntity) {
                // Log de coincidencia y acción de servidor
                AccionesPuerta.forzarApertura(level, msg.pos, msg.abrir, true); // true => fue por “coincidencia”
            }
        });
        ctx.setPacketHandled(true);
    }
}
