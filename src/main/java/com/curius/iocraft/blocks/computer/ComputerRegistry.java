package com.curius.iocraft.blocks.computer;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.NombresContenido;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Objects;

public final class ComputerRegistry {

    public static final DeferredRegister<BlockEntityType<?>> BE_REG =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ModIoCraft.MOD_ID);

    private static Block computerBlock() {
        Block b = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation(ModIoCraft.MOD_ID, NombresContenido.Bloques.COMPUTADORA));
        return Objects.requireNonNull(b,
                "Bloque 'iocraft:computer' no encontrado. Ajusta el id si es distinto.");
    }

    public static final RegistryObject<BlockEntityType<ComputerBlockEntity>> COMPUTER_BE =
            BE_REG.register(NombresContenido.COMPUTADORA_BE_V,
                    () -> BlockEntityType.Builder.of(ComputerBlockEntity::new, computerBlock()).build(null)
            );

    private ComputerRegistry() {}
}
