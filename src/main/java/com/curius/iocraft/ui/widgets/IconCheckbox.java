package com.curius.iocraft.ui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Checkbox con icono PNG (16x16) y check azul (1.18.2).
 */
public class IconCheckbox extends AbstractWidget {
    private boolean selected;
    private final ResourceLocation icon;
    private final Consumer<Boolean> onChanged;

    // Colores (ARGB)
    private static final int COL_LABEL      = 0xFFE0E0E0;
    private static final int COL_LABEL_DIS  = 0xFF9A9A9A;
    private static final int COL_BOX_BG     = 0xFF1B1B1B;
    private static final int COL_BOX_BORDER = 0xFF3A3A3A;
    private static final int COL_BOX_HOVER  = 0xFF2A2A2A;
    private static final int COL_CHECK_BLUE = 0xFF2F9ADE; // azul del mod

    public IconCheckbox(int x, int y, int width, int height,
                        Component label,
                        boolean initialSelected,
                        ResourceLocation icon,
                        Consumer<Boolean> onChanged) {
        super(x, y, width, height, label);
        this.selected = initialSelected;
        this.icon = icon;
        this.onChanged = onChanged;
    }

    public boolean selected() { return selected; }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            if (onChanged != null) onChanged.accept(this.selected);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        setSelected(!selected);
    }

    /** 1.18.x: debe ser PUBLIC */
    @Override
    public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        int pad = 2;
        int boxSize = Math.min(14, this.height - 4);
        int boxX = this.x + pad;
        int boxY = this.y + (this.height - boxSize) / 2;

        // Hover en toda la fila
        if (this.isHovered) {
            fill(pose, this.x, this.y, this.x + this.width, this.y + this.height, 0x10101010);
        }

        // Cuadro del checkbox
        int bg = this.isHovered ? COL_BOX_HOVER : COL_BOX_BG;
        fill(pose, boxX, boxY, boxX + boxSize, boxY + boxSize, bg);
        // Borde
        fill(pose, boxX, boxY, boxX + boxSize, boxY + 1, COL_BOX_BORDER);
        fill(pose, boxX, boxY + boxSize - 1, boxX + boxSize, boxY + boxSize, COL_BOX_BORDER);
        fill(pose, boxX, boxY, boxX + 1, boxY + boxSize, COL_BOX_BORDER);
        fill(pose, boxX + boxSize - 1, boxY, boxX + boxSize, boxY + boxSize, COL_BOX_BORDER);

        // Check azul (✓) con la fuente de MC
        if (selected) {
            Font font = Minecraft.getInstance().font;
            float cx = boxX + 3;
            float cy = boxY + 2;
            font.draw(pose, "✓", cx, cy, COL_CHECK_BLUE);
        }

        // Icono PNG a la izquierda del texto
        int iconSize = 16;
        int iconX = boxX + boxSize + 6;
        int iconY = this.y + (this.height - iconSize) / 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, icon);
        RenderSystem.setShaderColor(1f, 1f, 1f, this.active ? 1f : 0.5f);
        blit(pose, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);

        // Texto
        Font font = Minecraft.getInstance().font;
        int tx = iconX + iconSize + 6;
        int ty = this.y + (this.height - 8) / 2;
        int color = this.active ? COL_LABEL : COL_LABEL_DIS;
        font.draw(pose, this.getMessage(), tx, ty, color);
    }

    /** 1.18.x: narración accesible */
    @Override
    public void updateNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, this.createNarrationMessage());
    }
}
