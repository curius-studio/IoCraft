package com.curius.iocraft.blocks.computer;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.RegistroContenido;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntitiesComputer {
    private ModBlockEntitiesComputer() {}

    // 1.18.2 -> BLOCK_ENTITIES (NO usar BLOCK_ENTITY_TYPES)
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ModIoCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<ComputerBlockEntity>> COMPUTER_BE =
            BLOCK_ENTITIES.register("computer_be",
                    () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, RegistroContenido.COMPUTADORA.get())
                            .build(null));
}
