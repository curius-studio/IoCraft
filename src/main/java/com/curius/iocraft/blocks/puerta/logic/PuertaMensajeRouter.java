package com.curius.iocraft.blocks.puerta.logic;

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

public final class PuertaMensajeRouter {
    private static final Logger LOGGER = LogManager.getLogger("PUERTA-MSG");

    private PuertaMensajeRouter() {}

    /** Intenta comparar el mensaje entrante con la puerta en (mundo,x,y,z). No depende de 'type'. */
    public static void tryCompareForDoor(Mensaje msg) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        JsonObject d = msg.data();
        if (d == null) return; // si no hay JSON, no tenemos coords a qué puerta aplicar

        // Coordenadas y mundo (como ya los envías en tus mensajes)
        String mundo = str(d, "mundo", "overworld");
        Integer x = intOrNull(d, "x");
        Integer y = intOrNull(d, "y");
        Integer z = intOrNull(d, "z");
        if (x == null || y == null || z == null) return; // sin coords, no hay puerta objetivo

        // Texto entrante: preferimos d.data; si no, el texto plano del Mensaje
        String texto = d.has("data") ? safe(d.get("data").getAsString()) : safe(msg.texto());
        texto = texto.trim();

        ServerLevel level = resolveLevel(server, mundo);
        if (level == null) return;

        BlockPos pos = new BlockPos(x, y, z);

        // Buscar BE de la puerta (si clic arriba, el BE suele estar abajo)
        PuertaBlockEntity be = findPuertaBe(level, pos);
        if (be == null) return;

        String abrir  = safe(be.getMensajeAbrir()).trim();
        String cerrar = safe(be.getMensajeCerrar()).trim();

        if (!abrir.isEmpty() && equalsNormalized(texto, abrir)) {
            LOGGER.info("[PUERTA-MSG] match action=open pos={} text={}", pos, texto);
            // Aquí puedes invocar acciones si quieres, p.ej. abrir/cerrar o enviar por /ioc enviar:
            // AccionesPuerta.enviarMensajeViaComando(server, be.getDestino(), texto);
            // AccionesPuerta.forzarApertura(level, pos, true);
            return;
        }
        if (!cerrar.isEmpty() && equalsNormalized(texto, cerrar)) {
            LOGGER.info("[PUERTA-MSG] match action=close pos={} text={}", pos, texto);
            // AccionesPuerta.enviarMensajeViaComando(server, be.getDestino(), texto);
            // AccionesPuerta.forzarApertura(level, pos, false);
        }
    }

    // --------- helpers ---------

    private static PuertaBlockEntity findPuertaBe(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be instanceof PuertaBlockEntity p) return p;
        var beDown = level.getBlockEntity(pos.below());
        return (beDown instanceof PuertaBlockEntity pd) ? pd : null;
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String mundo) {
        ResourceKey<Level> key;
        switch (mundo) {
            case "overworld":   key = Level.OVERWORLD; break;
            case "nether":
            case "the_nether":  key = Level.NETHER;    break;
            case "the_end":
            case "end":         key = Level.END;       break;
            default:
                try {
                    key = ResourceKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation(mundo));
                } catch (Exception e) { return null; }
        }
        return server.getLevel(key);
    }

    private static boolean equalsNormalized(String a, String b) {
        return a.equalsIgnoreCase(b); // cámbialo a equals() si prefieres sensible a mayúsculas
    }

    private static String str(JsonObject o, String k, String def) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : def;
    }

    private static Integer intOrNull(JsonObject o, String k) {
        try {
            return (o.has(k) && o.get(k).isJsonPrimitive()) ? o.get(k).getAsInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
