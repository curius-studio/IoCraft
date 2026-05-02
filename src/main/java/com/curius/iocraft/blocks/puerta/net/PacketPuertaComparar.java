// com/curius/iocraft/blocks/puerta/net/PacketPuertaComparar.java
package com.curius.iocraft.blocks.puerta.net;

import com.curius.iocraft.blocks.puerta.logic.PuertaMensajes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPuertaComparar {
    public final BlockPos pos;
    public final String device; // opcional (quien envió) — si no lo usas, puedes borrarlo
    public final String data;

    public PacketPuertaComparar(BlockPos pos, String device, String data) {
        this.pos = pos;
        this.device = device;
        this.data = data;
    }

    public static void encode(PacketPuertaComparar msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.device == null ? "" : msg.device);
        buf.writeUtf(msg.data   == null ? "" : msg.data);
    }

    public static PacketPuertaComparar decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String device = buf.readUtf();
        String data   = buf.readUtf();
        return new PacketPuertaComparar(pos, device, data);
    }

    public static void handle(PacketPuertaComparar msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sp = context.getSender();
            if (sp == null) return; // seguridad: C2S debe venir de un jugador
            ServerLevel level = sp.getLevel();

            // ✅ Llama a la firma correcta (4 parámetros)
            PuertaMensajes.procesar(level, msg.pos, msg.device, msg.data);
        });
        context.setPacketHandled(true);
    }
}
