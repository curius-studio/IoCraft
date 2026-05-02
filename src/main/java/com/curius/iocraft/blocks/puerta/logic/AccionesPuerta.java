// com/curius/iocraft/blocks/puerta/logic/AccionesPuerta.java
package com.curius.iocraft.blocks.puerta.logic;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Lógica centralizada para reaccionar a abrir/cerrar la puerta. */
public final class AccionesPuerta {
    private static final Logger LOGGER = LogManager.getLogger("PUERTA-ACCIONES");

    private AccionesPuerta() {}

    /**
     * Fuerza la apertura/cierre de la puerta (ambas mitades) y notifica la acción unificada.
     * @param level mundo (server)
     * @param anyHalfPos posición de cualquier mitad de la puerta
     * @param abierta true => abrir; false => cerrar
     * @param porCoincidencia si viene de una coincidencia de mensaje (para log “coincidió”)
     */
    public static void forzarApertura(ServerLevel level, BlockPos anyHalfPos, boolean abierta, boolean porCoincidencia) {
        if (level == null || anyHalfPos == null) return;

        // Normalizar a la mitad inferior
        BlockPos basePos = anyHalfPos;
        BlockState st = level.getBlockState(anyHalfPos);
        if (st.getBlock() instanceof DoorBlock) {
            if (st.hasProperty(DoorBlock.HALF) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                basePos = anyHalfPos.below();
            }
        }

        // Ajustar estado OPEN en ambas mitades
        BlockState lower = level.getBlockState(basePos);
        if (lower.getBlock() instanceof DoorBlock && lower.hasProperty(DoorBlock.OPEN)) {
            BlockState lowerNew = lower.setValue(DoorBlock.OPEN, abierta);
            level.setBlock(basePos, lowerNew, 3);
        }
        BlockPos upPos = basePos.above();
        BlockState upper = level.getBlockState(upPos);
        if (upper.getBlock() instanceof DoorBlock && upper.hasProperty(DoorBlock.OPEN)) {
            BlockState upperNew = upper.setValue(DoorBlock.OPEN, abierta);
            level.setBlock(upPos, upperNew, 3);
        }

        if (porCoincidencia) {
            //LOGGER.info("[PuertaIoCraft] coincidió ({}) en {}", abierta ? "abrir" : "cerrar", basePos);
        }

        // Delegar en la acción “común” (logging de abierto/cerrado y /ioc enviar si procede)
        alCambiarEstado(level, basePos, abierta);
    }

    /**
     * Acción unificada al cambiar estado (no cambia el bloque):
     * - Resuelve BE (mitad inferior)
     * - Log “abierto/cerrado”
     * - Ejecuta /ioc enviar "<destino>" <mensaje> si hay configuración
     */
    public static void alCambiarEstado(Level level, BlockPos clickedPos, boolean abierta) {
        if (level == null || level.isClientSide) return;

        // Normalizar a la mitad inferior (donde está el BE)
        BlockPos bePos = clickedPos;
        BlockState st = level.getBlockState(clickedPos);
        if (st.getBlock() instanceof DoorBlock) {
            if (st.hasProperty(DoorBlock.HALF) && st.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
                bePos = clickedPos.below();
            }
        }

        BlockEntity be = level.getBlockEntity(bePos);
        if (!(be instanceof PuertaBlockEntity p)) {
            //LOGGER.warn("[PuertaIoCraft] No se encontró BlockEntity de puerta en {}", bePos);
            return;
        }

        String destino = safe(p.getDestino());
        String msg     = abierta ? safe(p.getMensajeAbrir()) : safe(p.getMensajeCerrar());

        // Log requerido
        //LOGGER.info("[PuertaIoCraft] {}", abierta ? "abierto" : "cerrado");

        // /ioc enviar "<destino>" <mensaje> si hay config
        if (!destino.isEmpty() && !msg.isEmpty()) {
            enviarViaComando(level, destino, msg);
        } else {
            //LOGGER.info("[PuertaIoCraft] sin destino o mensaje configurado para este estado.");
        }
    }

    /** Expuesto por si otra clase necesita enviar usando el mismo formato. */
    public static void enviarMensajeViaComando(net.minecraft.server.MinecraftServer server, String destino, String mensajeRaw) {
        if (server == null) return;
        String destinoQuoted = quoteForBrigadier(destino);      // destino entre comillas
        String mensaje       = sanitizeGreedyString(mensajeRaw); // mensaje sin comillas
        String cmd = "/ioc enviar " + destinoQuoted + " " + mensaje;
        server.getCommands().performCommand(server.createCommandSourceStack(), cmd);
        //LOGGER.info("[PuertaIoCraft] Ejecutado: {}", cmd);
    }

    // -------------------- helpers --------------------

    /** Ejecuta: /ioc enviar "<destino>" <mensaje> como “la máquina”. */
    private static void enviarViaComando(Level level, String destino, String mensajeRaw) {
        var server = level.getServer();
        if (server == null) return;
        String destinoQuoted = quoteForBrigadier(destino);
        String mensaje       = sanitizeGreedyString(mensajeRaw);
        String cmd = "/ioc enviar " + destinoQuoted + " " + mensaje;
        server.getCommands().performCommand(server.createCommandSourceStack(), cmd);
        //LOGGER.info("[PuertaIoCraft] Ejecutado: {}", cmd);
    }

    /** Envuelve en comillas y escapa \, " y saltos de línea (para el DESTINO). */
    private static String quoteForBrigadier(String s) {
        String esc = safe(s)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + esc + "\"";
    }

    /** Sanea el mensaje para usarlo como greedyString sin comillas. */
    private static String sanitizeGreedyString(String s) {
        if (s == null) return "";
        return s.replace("\r", "").replace("\n", "\\n");
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
