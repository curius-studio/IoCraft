package com.curius.iocraft.blocks.puerta;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import com.curius.iocraft.mensajeria.Mensaje;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PuertaMensajes {
    private static final Logger LOGGER = LogManager.getLogger("PUERTA-MSG");

    private PuertaMensajes() {}

    /** Llamar desde un handler de MensajeriaBus para tipo "sensor". */
    public static void procesarSensor(Mensaje msg) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) { LOGGER.warn("[PUERTA-MSG] server == null"); return; }

        JsonObject data = msg.data();
        if (data == null) {
            LOGGER.warn("[PUERTA-MSG] mensaje 'sensor' sin data JSON");
            return;
        }

        String mundo = str(data, "mundo", "overworld");
        int x = intOr(data, "x", Integer.MIN_VALUE);
        int y = intOr(data, "y", Integer.MIN_VALUE);
        int z = intOr(data, "z", Integer.MIN_VALUE);
        String dato = data.has("data") ? data.get("data").getAsString() : (msg.texto() == null ? "" : msg.texto());
        String device = str(data, "device", "");

        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            LOGGER.warn("[PUERTA-MSG] faltan coordenadas: {}", data);
            return;
        }

        ServerLevel level = resolveLevel(server, mundo);
        if (level == null) {
            LOGGER.warn("[PUERTA-MSG] mundo no encontrado: {}", mundo);
            return;
        }

        BlockPos pos = new BlockPos(x, y, z);
        PuertaBlockEntity puerta = findPuertaBe(level, pos);
        if (puerta == null) {
            LOGGER.debug("[PUERTA-MSG] no hay PuertaIoCraft en {}", pos);
            return;
        }

        LOGGER.info("[PUERTA-MSG] RX en {} de='{}' dato='{}'", pos, device, dato);

        String abrir  = safe(puerta.getMensajeAbrir());
        String cerrar = safe(puerta.getMensajeCerrar());

        if (!dato.isEmpty()) {
            if (!abrir.isEmpty() && dato.equals(abrir)) {
                LOGGER.info("[PUERTA-MSG] match action=open pos={} device={}", pos, device);
                // si quieres accionar aquí: AccionesPuerta.enviarMensajeViaComando(server, puerta.getDestino(), abrir);
                return;
            }
            if (!cerrar.isEmpty() && dato.equals(cerrar)) {
                LOGGER.info("[PUERTA-MSG] match action=close pos={} device={}", pos, device);
                // si quieres accionar aquí: AccionesPuerta.enviarMensajeViaComando(server, puerta.getDestino(), cerrar);
                return;
            }
        }
        LOGGER.info("[PUERTA-MSG] no coincide con abrir/cerrar guardados");
    }

    private static PuertaBlockEntity findPuertaBe(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof PuertaBlockEntity p) return p;
        var beDown = level.getBlockEntity(pos.below()); // por si clickean la mitad superior
        return (beDown instanceof PuertaBlockEntity pd) ? pd : null;
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String mundo) {
        ResourceKey<Level> key;
        switch (mundo) {
            case "overworld":     key = Level.OVERWORLD; break;
            case "nether":
            case "the_nether":    key = Level.NETHER; break;
            case "the_end":
            case "end":           key = Level.END; break;
            default:
                try {
                    key = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(mundo));
                } catch (Exception e) { return null; }
        }
        return server.getLevel(key);
    }

    private static String str(JsonObject o, String k, String def) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : def;
    }
    private static int intOr(JsonObject o, String k, int def) {
        return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsInt() : def;
    }
    private static String safe(String s) { return s == null ? "" : s; }
}
