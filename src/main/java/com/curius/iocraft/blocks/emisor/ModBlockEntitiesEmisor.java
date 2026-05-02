package com.curius.iocraft.blocks.emisor;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.RegistroContenido;
import com.curius.iocraft.registro.NombresContenido;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntitiesEmisor {
    private ModBlockEntitiesEmisor() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ModIoCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<BloqueEmisorEntity>> EMISOR_BE =
            BLOCK_ENTITIES.register(NombresContenido.EMISOR_BE_V,
                    () -> BlockEntityType.Builder.of(BloqueEmisorEntity::new, RegistroContenido.RECEPTOR.get())
                            .build(null));
}
