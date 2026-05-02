package com.curius.iocraft.blocks.emisor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BloqueEmisorRedstoneHandler extends BloqueEmisor {
    private static final Logger LOGGER = LogManager.getLogger("EMISOR");

    public BloqueEmisorRedstoneHandler(Properties properties) {
        super(properties);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;

        boolean hasSignal = level.hasNeighborSignal(pos);

        var be = level.getBlockEntity(pos);
        if (!(be instanceof BloqueEmisorEntity datos)) return;

        // Evita doble ejecución por mismo estado
        if (hasSignal == datos.getUltimoPowered()) return;
        datos.setUltimoPowered(hasSignal);

        String p1 = datos.getNombre();

        if (hasSignal) {
            // ON -> siempre ejecuta con param2 (contenido ON)
            EjecutorComandoIoc.enviar(level, pos, p1, datos.getContenido());
            LOGGER.debug("[EMISOR] sent mode=ON pos={} payload={}", pos, datos.getContenido());
        } else {
            // OFF -> solo si modo = Encendido/Apagado
            if (datos.getModo() == BloqueEmisorEntity.ModoEnvio.ENCENDIDO_Y_APAGADO) {
                EjecutorComandoIoc.enviar(level, pos, p1, datos.getContenidoOff());
                LOGGER.debug("[EMISOR] sent mode=OFF pos={} payload={}", pos, datos.getContenidoOff());
            }
        }
    }
}
