package com.curius.iocraft.blocks.computer.net;

import com.curius.iocraft.blocks.computer.ComputerBlockEntity;
import com.curius.iocraft.blocks.emisor.EjecutorComandoIoc; // <- ya lo usabas antes
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketEnviarMensaje {
    public final BlockPos pos;
    public final String destino;
    public final String texto;

    public PacketEnviarMensaje(BlockPos pos, String destino, String texto) {
        this.pos = pos;
        this.destino = destino != null ? destino : "";
        this.texto = texto != null ? texto : "";
    }

    public static void encode(PacketEnviarMensaje pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.destino);
        buf.writeUtf(pkt.texto);
    }

    public static PacketEnviarMensaje decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String destino = buf.readUtf();
        String texto = buf.readUtf();
        return new PacketEnviarMensaje(pos, destino, texto);
    }

    public static void handle(PacketEnviarMensaje pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!(ctx.get().getSender() != null && ctx.get().getSender().level instanceof ServerLevel sl)) return;

            var be = sl.getBlockEntity(pkt.pos);
            if (be instanceof ComputerBlockEntity cbe) {
                // Actualiza destino si vino distinto
                if (!pkt.destino.isEmpty()) cbe.setDestino(pkt.destino);

                // Ejecuta comando en el servidor
                EjecutorComandoIoc.enviar(sl, pkt.pos, pkt.destino, pkt.texto);

                // Persiste el mensaje "Yo: ..."
                cbe.pushMessage("§bYo§f: " + pkt.texto); // usa tu pushMessage(String line)
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
