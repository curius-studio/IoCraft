package com.curius.iocraft.blocks.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ComputerBlock extends Block implements EntityBlock {

    // ---------- orientación ----------
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // ---------- shapes por orientación ----------
    // Medidas que ya ajustaste (NORTH por defecto)
    private static final double ALTURA_MONITOR = 8.0;
    private static final double NORTH = 3.0; // Norte - Listo
    private static final double SOUTH = 5.0; // Sur - Listo
    private static final double EAST = 2.0; // OESTE  - Listo
    private static final double WEST = 2.0; // ESTE  - Listo
    private static final VoxelShape SHAPE_N = Block.box(WEST, 0.0, NORTH, (16.0 - EAST), ALTURA_MONITOR, (16.0 - SOUTH));              //listo

    // Rotamos 90° el box para cada dirección. Como es un solo box, rotar es intercambiar ejes.
    //                                                          WEST                       NORTH          EAST                           SOUTH
    private static final VoxelShape SHAPE_E = Block.box(5.0, 0.0, 2.0, 13.0, ALTURA_MONITOR, 14.0); //listo
    private static final VoxelShape SHAPE_S = Block.box(2.0, 0.0, 5.0, 14.0, ALTURA_MONITOR, 13.0); //listo
    private static final VoxelShape SHAPE_W = Block.box(3.0, 0.0, 2.0, 11.0, ALTURA_MONITOR, 14.0);

    public ComputerBlock(Properties props) {
        super(props.noOcclusion());
        // Estado por defecto mirando al norte
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ---------- state container ----------
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Al colocar, orientar el frente opuesto a donde mira el jugador (como hornos, cofres, etc.)
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // Para rotaciones por comando/estructura
    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------- shapes ----------
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_N;
            case EAST  -> SHAPE_E;
            case SOUTH -> SHAPE_S;
            case WEST  -> SHAPE_W;
            default    -> Shapes.block();
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // ---------- BE & GUI ----------
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            ComputerScreen.abrir(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Si quieres reusar propiedades tipo hierro */
    public static Properties ironLike() {
        return Properties.copy(Blocks.IRON_BLOCK);
    }

    @Override public boolean isSignalSource(BlockState state) { return false; }
    @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) { return 0; }
}
