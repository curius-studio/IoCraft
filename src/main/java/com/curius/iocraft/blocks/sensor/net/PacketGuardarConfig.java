package com.curius.iocraft.blocks.sensor.net;

import com.curius.iocraft.blocks.sensor.SensorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketGuardarConfig {
    public final BlockPos pos;
    public final String nombre;
    public final String modo;     // COINCIDENCIA / UMBRAL / RANGO
    public final String match;    // para coincidencia
    public final String operador; // LE/GE/LT/GT/EQ (si usas tus enums, adapta)
    public final double umbral;
    public final double min;
    public final double max;

    public PacketGuardarConfig(BlockPos pos, String nombre, String modo,
                               String match, String operador,
                               double umbral, double min, double max) {
        this.pos = pos;
        this.nombre = nombre;
        this.modo = modo;
        this.match = match;
        this.operador = operador;
        this.umbral = umbral;
        this.min = min;
        this.max = max;
    }

    public static void encode(PacketGuardarConfig msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.nombre == null ? "" : msg.nombre);
        buf.writeUtf(msg.modo == null ? "" : msg.modo);
        buf.writeUtf(msg.match == null ? "" : msg.match);
        buf.writeUtf(msg.operador == null ? "" : msg.operador);
        buf.writeDouble(msg.umbral);
        buf.writeDouble(msg.min);
        buf.writeDouble(msg.max);
    }

    public static PacketGuardarConfig decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String nombre = buf.readUtf();
        String modo = buf.readUtf();
        String match = buf.readUtf();
        String operador = buf.readUtf();
        double umbral = buf.readDouble();
        double min = buf.readDouble();
        double max = buf.readDouble();
        return new PacketGuardarConfig(pos, nombre, modo, match, operador, umbral, min, max);
    }

    public static void handle(PacketGuardarConfig msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender(); // lado servidor
            if (sender == null) return;

            var level = sender.level;
            if (level == null || !level.isLoaded(msg.pos)) return;

            var be = level.getBlockEntity(msg.pos);
            if (be instanceof SensorBlockEntity sensor) {
                // mapear strings -> enums de tu BE
                SensorBlockEntity.Modo modoEnum;
                try { modoEnum = SensorBlockEntity.Modo.valueOf(msg.modo); }
                catch (Exception e) { modoEnum = SensorBlockEntity.Modo.COINCIDENCIA; }

                SensorBlockEntity.Operador opEnum;
                try { opEnum = SensorBlockEntity.Operador.valueOf(msg.operador); }
                catch (Exception e) { opEnum = SensorBlockEntity.Operador.LE; }

                // aplicar todo de una vez
                sensor.applyConfig(
                        msg.nombre != null ? msg.nombre : "",
                        modoEnum,
                        msg.match != null ? msg.match : "",
                        opEnum,
                        msg.umbral,
                        msg.min,
                        msg.max
                );

                // ya marca/avisa dentro de applyConfig -> markAndDispatch()
                // (si no lo hiciera, podrías dejar estas dos líneas)
                // sensor.setChanged();
                // level.sendBlockUpdated(msg.pos, level.getBlockState(msg.pos), level.getBlockState(msg.pos), 3);
            }
        });
        ctx.setPacketHandled(true);
    }

}
