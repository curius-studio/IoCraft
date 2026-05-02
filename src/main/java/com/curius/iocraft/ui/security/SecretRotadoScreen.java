package com.curius.iocraft.ui.security;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

public class SecretRotadoScreen extends Screen {
    private final String device;
    private final String secret;
    private Button btnCopiar;

    public SecretRotadoScreen(String device, String secret) {
        super(new TextComponent("Secret rotado"));
        this.device = device == null ? "" : device;
        this.secret = secret == null ? "" : secret;
    }

    @Override
    protected void init() {
        int btnW = 150;
        int btnH = 20;
        int y = this.height / 2 + 34;
        int spacing = 10;
        int totalW = btnW * 2 + spacing;
        int x = (this.width - totalW) / 2;

        this.btnCopiar = this.addRenderableWidget(new Button(x, y, btnW, btnH,
                new TextComponent("Copiar secret"),
                b -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(secret);
                    b.setMessage(new TextComponent("¡Copiado!"));
                }));

        this.addRenderableWidget(new Button(x + btnW + spacing, y, btnW, btnH,
                new TextComponent("Cerrar"), b -> onClose()));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        drawCenteredString(pose, this.font, this.title, this.width / 2, this.height / 2 - 56, 0xFFFFFF);

        int left = this.width / 2 - 180;
        int y = this.height / 2 - 30;
        this.font.draw(pose, new TextComponent("Dispositivo: ").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        this.font.draw(pose, new TextComponent(device).withStyle(ChatFormatting.WHITE), left + 72, y, 0xFFFFFF);
        y += 14;
        this.font.draw(pose, new TextComponent("Estado: ").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        this.font.draw(pose, new TextComponent("Secret rotado. El anterior ya no es válido.")
                .withStyle(ChatFormatting.GREEN), left + 45, y, 0xFFFFFF);
        y += 16;
        this.font.draw(pose, new TextComponent("Nuevo secret:").withStyle(ChatFormatting.GRAY), left, y, 0xFFFFFF);
        y += 12;
        for (var line : this.font.split(new TextComponent(secret).withStyle(ChatFormatting.WHITE), 360)) {
            this.font.draw(pose, line, left, y, 0xFFFFFF);
            y += 10;
        }

        if (btnCopiar != null && btnCopiar.isHoveredOrFocused()) {
            renderTooltip(pose,
                    new TextComponent("Copia el nuevo secret para actualizar tu dispositivo IoT."),
                    mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
