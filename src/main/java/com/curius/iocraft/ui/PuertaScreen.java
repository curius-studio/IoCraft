// com/curius/iocraft/blocks/puerta/ui/PuertaScreen.java
package com.curius.iocraft.blocks.puerta.ui;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import com.curius.iocraft.blocks.puerta.net.PacketGuardarPuertaConfig;
import com.curius.iocraft.blocks.puerta.net.PuertaNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PuertaScreen extends Screen {
    private static final int WIDTH = 280, HEIGHT = 160;

    private final BlockPos pos;

    private EditBox txtDestino;
    private EditBox txtMsgAbrir;
    private EditBox txtMsgCerrar;
    private Button btnGuardar, btnCerrar;

    private String destino = "";
    private String msgAbrir = "";
    private String msgCerrar = "";

    public PuertaScreen(BlockPos pos) {
        super(new TextComponent("§lPuerta IoCraft"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new PuertaScreen(pos)));
    }

    @Override
    protected void init() {
        super.init();

        var level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PuertaBlockEntity p) {
                this.destino   = p.getDestino();
                this.msgAbrir  = p.getMensajeAbrir();
                this.msgCerrar = p.getMensajeCerrar();
            }
        }

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // Inputs
        this.txtDestino = new EditBox(this.font, left + 92, top + 36, 170, 18, new TextComponent("Destino"));
        this.txtDestino.setMaxLength(128);
        this.txtDestino.setValue(this.destino);
        addRenderableWidget(this.txtDestino);

        this.txtMsgAbrir = new EditBox(this.font, left + 92, top + 60, 170, 18, new TextComponent("Mensaje (abrir)"));
        this.txtMsgAbrir.setMaxLength(512);
        this.txtMsgAbrir.setValue(this.msgAbrir);
        addRenderableWidget(this.txtMsgAbrir);

        this.txtMsgCerrar = new EditBox(this.font, left + 92, top + 84, 170, 18, new TextComponent("Mensaje (cerrar)"));
        this.txtMsgCerrar.setMaxLength(512);
        this.txtMsgCerrar.setValue(this.msgCerrar);
        addRenderableWidget(this.txtMsgCerrar);

        // Botones
        this.btnGuardar = addRenderableWidget(new Button(left + WIDTH/2 - 82, top + HEIGHT - 28, 75, 20,
                new TextComponent("Guardar"), b -> onGuardar()));
        this.btnCerrar = addRenderableWidget(new Button(left + WIDTH/2 + 7, top + HEIGHT - 28, 75, 20,
                new TextComponent("Cerrar"), b -> onClose()));
    }

    private void onGuardar() {
        String d  = txtDestino.getValue().trim();
        String ma = txtMsgAbrir.getValue().trim();
        String mc = txtMsgCerrar.getValue().trim();

        PuertaNetwork.CHANNEL.sendToServer(new PacketGuardarPuertaConfig(this.pos, d, ma, mc));

        // Refrescar valores locales por si reabrimos sin recargar
        this.destino = d;
        this.msgAbrir = ma;
        this.msgCerrar = mc;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // (Marco y fondo: usa el mismo sistema/estilo que tus otras pantallas)

        // Labels
        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2, top + 6, 0x2F9ADE);
        drawString(pose, this.font, "Posición:", left + 8,  top + 20, 0x2F9ADE);
        drawString(pose, this.font, String.format("X=%d Y=%d Z=%d", pos.getX(), pos.getY(), pos.getZ()),
                left + 72, top + 20, 0xFFFFFF);

        drawString(pose, this.font, "Destino:", left + 8, top + 40, 0x2F9ADE);
        drawString(pose, this.font, "Mensaje (abrir):", left + 8, top + 64, 0x2F9ADE);
        drawString(pose, this.font, "Mensaje (cerrar):", left + 8, top + 88, 0x2F9ADE);

        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
