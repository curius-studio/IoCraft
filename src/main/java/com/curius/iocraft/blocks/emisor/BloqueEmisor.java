package com.curius.iocraft.blocks.emisor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BloqueEmisor extends Block implements EntityBlock {

    // Forma aproximada según tu JSON (todo en 1/16 de bloque)
    // Base 2..14 x 0..3 x 2..14, cuerpo 5..11 x 3..14, tope 7..9 x 14..16
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 3, 14),
            Block.box(5, 3, 5, 11, 14, 11),
            Block.box(7, 14, 7, 9, 16, 9)
    );

    public BloqueEmisor() {
        this(BlockBehaviour.Properties
                .copy(Blocks.IRON_BLOCK)
                .sound(SoundType.METAL)
                .noOcclusion() // <- clave: no ocluir caras vecinas (evita el “agujero”)
        );
    }
    public BloqueEmisor(Properties properties) {
        super(properties);
    }

    // Hitbox / selección del bloque (líneas negras)
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // Colisión (opcional; si quieres que el jugador “toque” la forma real)
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BloqueEmisorEntity(pos, state);
    }

    // Click derecho -> abrir GUI (solo cliente)
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            PantallaEmisor.abrir(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
