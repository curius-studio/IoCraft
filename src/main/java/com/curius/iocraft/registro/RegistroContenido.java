package com.curius.iocraft.registro;

import com.curius.iocraft.IconItem;
import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.blocks.computer.ComputerBlock;
import com.curius.iocraft.blocks.emisor.BloqueEmisorRedstoneHandler;
import com.curius.iocraft.blocks.sensor.SensorBlock;
import com.curius.iocraft.blocks.puerta.PuertaIoCraftBlock;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro centralizado de bloques e items de IoCraft.
 * - Los IDs (nombres de registro) se definen en {@link NombresContenido}.
 * - Este archivo expone los RegistryObject para uso en el mod.
 */
public final class RegistroContenido {
    private RegistroContenido() {}

    // Registros diferidos
    public static final DeferredRegister<Block> BLOQUES =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ModIoCraft.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ModIoCraft.MOD_ID);

    // Pestaña creativa del mod (icono tomado del item "icono_tab")
    public static final CreativeModeTab PESTANIA_IOCRAFT = new CreativeModeTab(ModIoCraft.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return ICONO_TAB.get().getDefaultInstance();
        }
    };

    // Propiedades comunes para bloques "sólidos" del mod
    private static final BlockBehaviour.Properties PROPS_COMUNES =
            BlockBehaviour.Properties.
                    of(Material.METAL)
                    .strength(3.0f)
                    .noOcclusion();

    private static final BlockBehaviour.Properties PROPS_RECEPTOR =
            BlockBehaviour.Properties
                    .of(Material.METAL)
                    .strength(1.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion();


    // -----------------
    // Bloques
    // -----------------
    public static final RegistryObject<ComputerBlock> COMPUTADORA =
            BLOQUES.register(NombresContenido.Bloques.COMPUTADORA,
                    () -> new ComputerBlock(PROPS_COMUNES));

    public static final RegistryObject<BloqueEmisorRedstoneHandler> EMISOR =
            BLOQUES.register(NombresContenido.Bloques.EMISOR,
                    () -> new BloqueEmisorRedstoneHandler(PROPS_COMUNES));

    public static final RegistryObject<SensorBlock> RECEPTOR =
            BLOQUES.register(NombresContenido.Bloques.RECEPTOR,
                    () -> new SensorBlock(PROPS_RECEPTOR));

    public static final RegistryObject<PuertaIoCraftBlock> PUERTA =
            BLOQUES.register(NombresContenido.Bloques.PUERTA,
                    () -> new PuertaIoCraftBlock(BlockBehaviour.Properties
                            .of(net.minecraft.world.level.material.Material.WOOD)
                            .strength(3.0F, 6.0F) // lo que prefieras
                            .noOcclusion()));


    // -----------------
    // Items
    // -----------------
    public static final RegistryObject<Item> ICONO_TAB =
            ITEMS.register(NombresContenido.Items.ICONO_TAB,
                    () -> new IconItem(new Item.Properties()));

    public static final RegistryObject<Item> COMPUTADORA_ITEM =
            ITEMS.register(NombresContenido.Items.COMPUTADORA,
                    () -> new BlockItem(COMPUTADORA.get(), new Item.Properties().tab(PESTANIA_IOCRAFT)));

    public static final RegistryObject<Item> EMISOR_ITEM =
            ITEMS.register(NombresContenido.Items.EMISOR,
                    () -> new BlockItem(EMISOR.get(), new Item.Properties().tab(PESTANIA_IOCRAFT)));

    public static final RegistryObject<Item> RECEPTOR_ITEM =
            ITEMS.register(NombresContenido.Items.RECEPTOR,
                    () -> new BlockItem(RECEPTOR.get(), new Item.Properties().tab(PESTANIA_IOCRAFT)));

    public static final RegistryObject<Item> PUERTA_ITEM =
            ITEMS.register(NombresContenido.Items.PUERTA,
                    () -> new DoubleHighBlockItem(PUERTA.get(), new Item.Properties().tab(PESTANIA_IOCRAFT)));

    // Hook para ModIoCraft
    public static void registrar(IEventBus bus) {
        BLOQUES.register(bus);
        ITEMS.register(bus);
    }
}
