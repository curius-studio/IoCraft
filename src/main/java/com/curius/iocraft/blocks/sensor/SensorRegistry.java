// SensorRegistry.java
package com.curius.iocraft.blocks.sensor;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.NombresContenido;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Objects;

public final class SensorRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BE_REG =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ModIoCraft.MOD_ID);

    private static Block sensorBlock() {
        Block b = ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation(ModIoCraft.MOD_ID, NombresContenido.Bloques.RECEPTOR)); // ← id correcto
        return Objects.requireNonNull(b, "Bloque 'iocraft:" + NombresContenido.Bloques.RECEPTOR + " no encontrado.");
    }

    public static final RegistryObject<BlockEntityType<SensorBlockEntity>> RECEPTOR_BE =
            BE_REG.register(NombresContenido.RECEPTOR_BE_V,
                    () -> BlockEntityType.Builder.of(SensorBlockEntity::new, sensorBlock()).build(null));

    private SensorRegistry() {}
}
