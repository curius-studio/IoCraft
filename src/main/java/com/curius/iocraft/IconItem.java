package com.curius.iocraft;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public class IconItem extends Item {
    public IconItem(Properties props) { super(props); }

    // No se añade a ningún Creative Tab (incluye el tab de búsqueda)
    @Override
    public void fillItemCategory(CreativeModeTab tab, NonNullList<ItemStack> items) {
        // vacío a propósito
    }
}
