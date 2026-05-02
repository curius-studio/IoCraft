// com/curius/iocraft/blocks/puerta/net/PacketGuardarPuertaConfig.java
package com.curius.iocraft.blocks.puerta.net;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketGuardarPuertaConfig {
    private final BlockPos pos;
    private final String destino;
    private final String msgAbrir;
    private final String msgCerrar;

    public PacketGuardarPuertaConfig(BlockPos pos, String destino, String msgAbrir, String msgCerrar) {
        this.pos = pos;
        this.destino = destino;
        this.msgAbrir = msgAbrir;
        this.msgCerrar = msgCerrar;
    }

    public static void encode(PacketGuardarPuertaConfig pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.destino, 256);
        buf.writeUtf(pkt.msgAbrir, 1024);
        buf.writeUtf(pkt.msgCerrar, 1024);
    }

    public static PacketGuardarPuertaConfig decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String destino = buf.readUtf(256);
        String abrir = buf.readUtf(1024);
        String cerrar = buf.readUtf(1024);
        return new PacketGuardarPuertaConfig(pos, destino, abrir, cerrar);
    }

    public static void handle(PacketGuardarPuertaConfig pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level.getBlockEntity(pkt.pos);
            if (be instanceof PuertaBlockEntity pbe) {
                pbe.setDestino(pkt.destino);
                pbe.setMensajeAbrir(pkt.msgAbrir);
                pbe.setMensajeCerrar(pkt.msgCerrar);

                pbe.setChangedAndSync();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
