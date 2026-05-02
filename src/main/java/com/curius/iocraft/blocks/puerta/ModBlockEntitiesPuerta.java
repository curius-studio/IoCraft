// com/curius/iocraft/blocks/puerta/ModBlockEntitiesPuerta.java
package com.curius.iocraft.blocks.puerta;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.RegistroContenido;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntitiesPuerta {
    private ModBlockEntitiesPuerta() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, ModIoCraft.MOD_ID);

    public static final RegistryObject<BlockEntityType<PuertaBlockEntity>> TIPO_PUERTA =
            BLOCK_ENTITIES.register("puerta",
                    () -> BlockEntityType.Builder.of(
                            PuertaBlockEntity::new,
                            RegistroContenido.PUERTA.get()   // el bloque puerta que ya registraste ahí
                    ).build(null));
}
