package com.curius.iocraft;

import com.curius.iocraft.registro.RegistroContenido;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public final class ModTabs {
    private ModTabs() {}

    public static final CreativeModeTab IOCRAFT = new CreativeModeTab("iocraft") {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(RegistroContenido.ICONO_TAB.get());
        }

        // Control total del contenido y su orden dentro del tab
        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            // 1) Emisor
            items.add(new ItemStack(RegistroContenido.EMISOR.get()));

            // 2) Receptor
            items.add(new ItemStack(RegistroContenido.RECEPTOR.get()));

            // 3) Computer
            items.add(new ItemStack(RegistroContenido.COMPUTADORA.get()));

            // 4) Puerta
            items.add(new ItemStack(RegistroContenido.PUERTA.get()));
        }
    };
}
