package com.curius.iocraft.ui;

import com.curius.iocraft.ModIoCraft;
import com.curius.iocraft.registro.NombresContenido;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModIoCraft.MOD_ID, value = Dist.CLIENT)
public class BotonMenuPrincipal {

    /** Botón con textura de fondo y borde blanco/negro según hover. */
    public static class BotonTexturizado extends Button {
        private static final ResourceLocation TEX =
                new ResourceLocation(ModIoCraft.MOD_ID, "textures/gui/boton_dispositivos.png");

        // Tamaño fuente del PNG (ajusta si tu imagen tiene otras dimensiones)
        private static final int TEX_W = 200;
        private static final int TEX_H = 20;

        public BotonTexturizado(int x, int y, int width, int height, TextComponent texto, OnPress onPress) {
            super(x, y, width, height, texto, onPress);
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
            Minecraft mc = Minecraft.getInstance();

            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, TEX);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, this.alpha);

            // Fondo texturizado
            blit(pose, this.x, this.y, 0, 0, this.width, this.height, TEX_W, TEX_H);

            // Borde: blanco si hover, negro si no
            int border = this.isHovered ? 0xFFFFFFFF : 0xFF000000;
            // top
            fill(pose, this.x, this.y, this.x + this.width, this.y + 1, border);
            // bottom
            fill(pose, this.x, this.y + this.height - 1, this.x + this.width, this.y + this.height, border);
            // left
            fill(pose, this.x, this.y, this.x + 1, this.y + this.height, border);
            // right
            fill(pose, this.x + this.width - 1, this.y, this.x + this.width, this.y + this.height, border);

            // Oscurecer si está desactivado (opcional)
            if (!this.active) {
                fill(pose, this.x, this.y, this.x + this.width, this.y + this.height, 0x60000000);
            }

            // Texto centrado
            int colorTexto = this.active ? 0xFFFFFF : 0xA0A0A0;
            drawCenteredString(pose, mc.font, this.getMessage(),
                    this.x + this.width / 2, this.y + (this.height - 8) / 2, colorTexto);
        }
    }

    @SubscribeEvent
    public static void alIniciarPantalla(ScreenEvent.InitScreenEvent.Post e) {
        Screen pantalla = e.getScreen();
        if (!(pantalla instanceof TitleScreen)) return;

        // Fila base del menú vanilla (donde cae "Un jugador")
        int baseY = pantalla.height / 4 + 48;

        // Cada fila/espaciado es de 24px en el menú vanilla
        int y = baseY + 24 + NombresContenido.UI_INICIAL.DESPLAZAMIENTO_LINEAS_BOTON;
        int x = pantalla.width / 2 - 100; // centrado como los botones vanilla

        BotonTexturizado btn = new BotonTexturizado(
                x, y, 200, 20,
                new TextComponent(NombresContenido.UI_INICIAL.BOTON_MENU),
                b -> Minecraft.getInstance().setScreen(new MenuDispositivos())
        );

        e.addListener(btn);
    }
}
