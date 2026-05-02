package com.curius.iocraft.blocks.sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class SensorBlock extends Block implements EntityBlock {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private static final double GAP_N = 0.0;
    private static final double GAP_S = 0.0;
    private static final double GAP_W = 0.0;
    private static final double GAP_E = 0.0;
    private static final double ALTURA = 17.0;

    private static final VoxelShape SHAPE = Block.box(
            GAP_W, 0.0, GAP_N,
            16.0 - GAP_E, ALTURA, 16.0 - GAP_S
    );

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** Ahora SIEMPRE requiere props desde RegistroContenido. */
    public SensorBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(POWERED);
    }

    // GUI
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            SensorScreen.abrir(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Redstone
    @Override public boolean isSignalSource(BlockState state) { return true; }
    @Override public int getSignal(BlockState s, BlockGetter w, BlockPos p, net.minecraft.core.Direction d) {
        return s.getValue(POWERED) ? 15 : 0;
    }
    @Override public int getDirectSignal(BlockState s, BlockGetter w, BlockPos p, net.minecraft.core.Direction d) {
        return s.getValue(POWERED) ? 15 : 0;
    }

    // BlockEntity
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SensorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
