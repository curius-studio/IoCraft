package com.curius.iocraft.iot;

import com.curius.iocraft.blocks.sensor.net.PacketSensorData;
import com.curius.iocraft.blocks.sensor.net.SensorNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Notifica al jugador (lado cliente) mensajes IoT con color según el tipo.
 * - "sensor" -> AQUA
 * - "interruptor" -> GOLD
 * - otro/desconocido -> GRAY
 *
 * Además mantiene un pequeño buffer con los últimos mensajes formateados
 * para que la GUI del bloque pueda mostrar un "Resumen".
 *
 * Seguro de invocar desde hilos no-Render: salta al hilo del cliente con Minecraft#execute.
 */
public final class NotificadorIoT {

    private NotificadorIoT() {}

    // Buffer circular de mensajes recientes para la GUI
    private static final int MAX_LOG = 50;
    private static final Deque<String> LOG = new ArrayDeque<>(MAX_LOG);

    private static synchronized void pushLog(String line) {
        if (LOG.size() >= MAX_LOG) LOG.removeFirst();
        LOG.addLast(line);
    }

    /** Devuelve una copia de los últimos N mensajes (más recientes al final). */
    public static synchronized List<String> ultimos(int max) {
        var all = new ArrayList<>(LOG);
        int from = Math.max(0, all.size() - max);
        return all.subList(from, all.size());
    }

    public static void mostrarAlJugador(MensajeIoTParser.MensajeIoT m) {
        if (m == null) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        // Texto base que también guardaremos en el log
        final String base = String.format(
                "[%s] %s @ %s (%d,%d,%d): %s",
                m.type != null ? m.type : "iot",
                m.device != null ? m.device : "desconocido",
                m.mundo != null ? m.mundo : "minecraft:overworld",
                m.x, m.y, m.z,
                m.data != null ? m.data : ""
        );

        mc.execute(() -> {
            if (mc.player == null) return;

            ChatFormatting color = ChatFormatting.GRAY;
            if ("sensor".equalsIgnoreCase(m.type)) {
                color = ChatFormatting.AQUA;
            } else if ("interruptor".equalsIgnoreCase(m.type)) {
                color = ChatFormatting.GOLD;
            }

            Component comp = new TextComponent(base).withStyle(color);

            // Muestra en el chat del cliente (similar a sendSuccess en comandos)
            //mc.player.displayClientMessage(comp, false);

            // También guardamos en el log para que la GUI pueda mostrar el “Resumen”
            pushLog(base);

            // Depositar en el buzón por posición para que la GUI del bloque muestre Origen/Dato
            try {
                BlockPos pos = new BlockPos(m.x, m.y, m.z);
                InboxIoT.Entrada entrada =
                        new InboxIoT.Entrada(
                                m.device != null ? m.device : "desconocido",
                                m.type   != null ? m.type   : "iot",
                                m.data   != null ? m.data   : "",
                                m.mundo  != null ? m.mundo  : "overworld",
                                System.currentTimeMillis()
                        );
                InboxIoT.put(pos, entrada);
            } catch (Throwable ignore) {}
        });

        // tras parsear MensajeIoT m
        SensorNetwork.CHANNEL.sendToServer(
                new PacketSensorData(
                        new net.minecraft.core.BlockPos(m.x, m.y, m.z),
                        m.type, String.valueOf(m.data), m.device
                )
        );

    }


}
