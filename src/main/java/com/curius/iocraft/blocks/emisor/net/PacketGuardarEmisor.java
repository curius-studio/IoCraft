package com.curius.iocraft.blocks.emisor.net;

import com.curius.iocraft.blocks.emisor.BloqueEmisorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketGuardarEmisor {
    public final BlockPos pos;
    public final String nombre;
    public final String contenidoOn;
    public final String contenidoOff;
    public final String modo; // SOLO_ENCENDIDO | ENCENDIDO_Y_APAGADO

    public PacketGuardarEmisor(BlockPos pos, String nombre, String on, String off, String modo) {
        this.pos = pos;
        this.nombre = nombre;
        this.contenidoOn = on;
        this.contenidoOff = off;
        this.modo = modo;
    }

    public static void encode(PacketGuardarEmisor m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeUtf(m.nombre == null ? "" : m.nombre);
        buf.writeUtf(m.contenidoOn == null ? "" : m.contenidoOn);
        buf.writeUtf(m.contenidoOff == null ? "" : m.contenidoOff);
        buf.writeUtf(m.modo == null ? "" : m.modo);
    }

    public static PacketGuardarEmisor decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String nombre = buf.readUtf();
        String on = buf.readUtf();
        String off = buf.readUtf();
        String modo = buf.readUtf();
        return new PacketGuardarEmisor(pos, nombre, on, off, modo);
    }

    public static void handle(PacketGuardarEmisor msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            var level = sender.level;
            if (level == null || !level.isLoaded(msg.pos)) return;

            var be = level.getBlockEntity(msg.pos);
            if (be instanceof BloqueEmisorEntity e) {
                BloqueEmisorEntity.ModoEnvio modoEnum = BloqueEmisorEntity.ModoEnvio.SOLO_ENCENDIDO;
                try { modoEnum = BloqueEmisorEntity.ModoEnvio.valueOf(msg.modo); } catch (Exception ignored) {}
                e.apply(msg.nombre, msg.contenidoOn, msg.contenidoOff, modoEnum);
                e.setChanged();
                level.sendBlockUpdated(msg.pos, level.getBlockState(msg.pos), level.getBlockState(msg.pos), 3);
            }
        });
        ctx.setPacketHandled(true);
    }
}
