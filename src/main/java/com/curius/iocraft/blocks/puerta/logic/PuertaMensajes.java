// com/curius/iocraft/blocks/puerta/logic/PuertaMensajes.java
package com.curius.iocraft.blocks.puerta.logic;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import com.curius.iocraft.security.AuthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PuertaMensajes {
    private static final Logger LOGGER = LogManager.getLogger("PUERTA-MSGS");
    private PuertaMensajes() {}

    /** Solo compara y acciona. La GUI ya fue actualizada por el handler. */
    public static void procesar(ServerLevel level, BlockPos pos, String device, String data) {
        if (!AuthManager.hasRoleDevice(device, "sensor") || !AuthManager.hasRoleDevice(device, "actuator")) {
            // Opcional: log de diagnóstico
            LOGGER.debug("[Puerta] Ignorado por permisos. device={} roles necesarios: sensor+actuator", device);
            return;
        }

        if (level == null || pos == null) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PuertaBlockEntity puerta)) return;

        String abrir  = safe(puerta.getMensajeAbrir());
        String cerrar = safe(puerta.getMensajeCerrar());
        String in     = safe(data);

        if (!abrir.isEmpty() && in.equals(abrir)) {
            //LOGGER.info("[PuertaIoCraft] coincidió (abrir) en {}", pos);
            AccionesPuerta.forzarApertura(level, pos, true, true);
            return;
        }
        if (!cerrar.isEmpty() && in.equals(cerrar)) {
            //LOGGER.info("[PuertaIoCraft] coincidió (cerrar) en {}", pos);
            AccionesPuerta.forzarApertura(level, pos, false, true);
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
}
