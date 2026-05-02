package com.curius.iocraft.blocks.puerta;

import com.curius.iocraft.blocks.puerta.gui.PuertaScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.curius.iocraft.blocks.puerta.logic.AccionesPuerta;

import javax.annotation.Nullable;

public class PuertaIoCraftBlock extends DoorBlock implements EntityBlock {
    private static final Logger LOGGER = LogManager.getLogger("PUERTA");

    public PuertaIoCraftBlock(Properties props) {
        super(props);
    }

    private static BlockPos basePos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        // Shift + click derecho → abrir GUI
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                com.curius.iocraft.blocks.puerta.gui.PuertaScreen.abrir(
                        // Asegura apuntar a la mitad inferior para el BE
                        state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER
                                ? pos.below() : pos
                );
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Click normal → toggle abierto/cerrado
        if (!level.isClientSide) {
            boolean nueva = !state.getValue(OPEN);
            BlockState actualizado = state.setValue(OPEN, nueva);
            level.setBlock(pos, actualizado, 10);

            // Notificar a la lógica centralizada (usa pos clickeada, la clase se ocupa del BE)
            AccionesPuerta.alCambiarEstado(level, pos, nueva);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ❗ No sobrecargamos hasBlockEntity; controlamos desde aquí
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Solo la mitad inferior crea BE; la superior devuelve null
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? null : new PuertaBlockEntity(pos, state);
    }
}
