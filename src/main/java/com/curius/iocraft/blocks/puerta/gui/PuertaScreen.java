package com.curius.iocraft.blocks.puerta.gui;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import com.curius.iocraft.blocks.puerta.net.PacketGuardarPuertaConfig;
import com.curius.iocraft.blocks.puerta.net.PuertaNetwork;
import com.curius.iocraft.iot.InboxIoT; // ← para leer el mensaje entrante
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.Component;

public class PuertaScreen extends Screen {
    // Tamaño (ligeramente mayor para el nuevo label)
    private static final int WIDTH  = 280;
    private static final int HEIGHT = 190;

    // Offsets estilo SensorScreen
    private static final int Y_SHIFT    = 7;   // mueve SOLO el título
    private static final int BUTTONS_UP = 5;   // sube Guardar/Cerrar

    // Offsets de etiquetas
    private static final int LABEL_X_SHIFT = 3;
    private static final int LABEL_Y_SHIFT = 3;

    // Offsets del conjunto (labels + inputs)
    private static final int FIELDS_X_SHIFT = 0;
    private static final int FIELDS_Y_SHIFT = 12;

    // Separación label → valor (posición/destino/mensajes)
    private static final int SEP_LABEL_VAL = 64;

    // Y base para el label de “Mensaje entrante”
    private static final int MSG_ENTRANTE_Y = 108;

    // Texturas (mismas que usas en receptor/emisor/computer)
    private static final ResourceLocation TEX_GUI   =
            new ResourceLocation("iocraft", "textures/gui/textura-gui.png");
    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation("iocraft", "textures/gui/gui_frame_round.png");

    // Colores
    private static final int COLOR_TITULO = 0x2F9ADE;
    private static final int COLOR_LABEL  = 0x2F9ADE;

    // Parametrización del marco (nine-slice)
    private static final float FRAME1_R = 0x22 / 255f, FRAME1_G = 0x4E / 255f, FRAME1_B = 0x73 / 255f; // #224E73
    private static final float FRAME2_R = 0x11 / 255f, FRAME2_G = 0x2C / 255f, FRAME2_B = 0x47 / 255f; // #112C47
    private static final int FRAME_TEX_W = 32, FRAME_TEX_H = 32;
    private static final int FRAME_CORNER = 8;
    private static final int OUTER_THICK  = 8;
    private static final int INNER_THICK  = 8;
    private static final int SEAM_FIX     = 2;

    private final BlockPos pos;

    // Estado (se carga del BlockEntity)
    private String destino   = "";
    private String msgAbrir  = "";
    private String msgCerrar = "";

    // Widgets
    private EditBox txtDestino;
    private EditBox txtMsgAbrir;
    private EditBox txtMsgCerrar;
    private Button btnGuardar;
    private Button btnCerrar;

    private String validationMsg = "";

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public PuertaScreen(BlockPos pos) {
        super(tr("screen.iocraft.puerta.title"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new PuertaScreen(pos)));
    }

    @Override
    protected void init() {
        super.init();

        // Leer del BE para mostrar lo persistido
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

        int leftF = left + FIELDS_X_SHIFT;
        int topF  = top  + FIELDS_Y_SHIFT;

        // Inputs
        this.txtDestino = new EditBox(this.font, leftF + SEP_LABEL_VAL + 16, topF + 32, 160, 18,
                tr("screen.iocraft.puerta.destino"));
        this.txtDestino.setMaxLength(128);
        this.txtDestino.setValue(this.destino);
        addRenderableWidget(this.txtDestino);

        this.txtMsgAbrir = new EditBox(this.font, leftF + SEP_LABEL_VAL + 16, topF + 56, 160, 18,
                tr("screen.iocraft.puerta.abierto"));
        this.txtMsgAbrir.setMaxLength(512);
        this.txtMsgAbrir.setValue(this.msgAbrir);
        addRenderableWidget(this.txtMsgAbrir);

        this.txtMsgCerrar = new EditBox(this.font, leftF + SEP_LABEL_VAL + 16, topF + 80, 160, 18,
                tr("screen.iocraft.puerta.cerrado"));
        this.txtMsgCerrar.setMaxLength(512);
        this.txtMsgCerrar.setValue(this.msgCerrar);
        addRenderableWidget(this.txtMsgCerrar);

        // Botones (misma posición relativa que en SensorScreen)
        int buttonsY = top + HEIGHT - 26 - BUTTONS_UP;
        this.btnGuardar = addRenderableWidget(new Button(
                left + WIDTH / 2 - 82, buttonsY, 75, 20,
                tr("screen.iocraft.puerta.btn_guardar"), b -> onGuardar()
        ));
        this.btnCerrar = addRenderableWidget(new Button(
                left + WIDTH / 2 + 7, buttonsY, 75, 20,
                tr("screen.iocraft.puerta.btn_cerrar"), b -> onClose()
        ));
    }

    private void onGuardar() {
        String d  = txtDestino.getValue().trim();
        String ma = txtMsgAbrir.getValue().trim();
        String mc = txtMsgCerrar.getValue().trim();

        // Enviar al servidor → BE (persistencia)
        PuertaNetwork.CHANNEL.sendToServer(
                new PacketGuardarPuertaConfig(this.pos, d, ma, mc)
        );

        // Refrescar cache local y feedback
        this.destino   = d;
        this.msgAbrir  = ma;
        this.msgCerrar = mc;
        this.validationMsg = tr("screen.iocraft.puerta.guardado").getString();
    }

    // ---------- Dibujo con marco/fondo ----------
    private void blitNineSlice(PoseStack pose,
                               int x, int y, int w, int h,
                               int texW, int texH,
                               int corner, int edgeThickness) {
        int innerW = w - corner*2;
        int innerH = h - corner*2;
        if (innerW < 0 || innerH < 0) return;

        int u0=0, v0=0;
        int u1=corner, v1=corner;
        int u2=texW-corner, v2=texH-corner;

        // Esquinas
        this.blit(pose, x, y,                         u0, v0, corner, corner, texW, texH);
        this.blit(pose, x + w - corner, y,            u2, v0, corner, corner, texW, texH);
        this.blit(pose, x, y + h - corner,            u0, v2, corner, corner, texW, texH);
        this.blit(pose, x + w - corner, y + h - corner, u2, v2, corner, corner, texW, texH);

        // Bordes
        blitStretched(pose, x + corner, y, w - corner*2, edgeThickness,
                u1, v0, texW - 2*corner, corner, texW, texH); // Top
        blitStretched(pose, x + corner, y + h - edgeThickness, w - corner*2, edgeThickness,
                u1, v2, texW - 2*corner, corner, texW, texH); // Bottom
        blitStretched(pose, x, y + corner, edgeThickness, h - corner*2,
                u0, v1, corner, texH - 2*corner, texW, texH); // Left
        blitStretched(pose, x + w - edgeThickness, y + corner, edgeThickness, h - corner*2,
                u2, v1, corner, texH - 2*corner, texW, texH); // Right
    }

    private void blitStretched(PoseStack pose, int x, int y, int w, int h,
                               int u, int v, int srcW, int srcH,
                               int texW, int texH) {
        this.blit(pose, x, y, w, h, u, v, srcW, srcH, texW, texH);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // Marco + fondo
        RenderSystem.enableBlend();

        // 1) Borde exterior
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME1_R, FRAME1_G, FRAME1_B, 1.0f);
        blitNineSlice(pose, left, top, WIDTH, HEIGHT,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, OUTER_THICK);

        // 2) Borde interior con pequeño solape
        int innerBorderX = left - SEAM_FIX;
        int innerBorderY = top  - SEAM_FIX;
        int innerBorderW = WIDTH  + SEAM_FIX * 2;
        int innerBorderH = HEIGHT + SEAM_FIX * 2;

        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME2_R, FRAME2_G, FRAME2_B, 1.0f);
        blitNineSlice(pose, innerBorderX, innerBorderY, innerBorderW, innerBorderH,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, INNER_THICK);

        // 3) Fondo interno
        int contentLeft = innerBorderX + INNER_THICK;
        int contentTop  = innerBorderY + INNER_THICK;
        int contentW    = innerBorderW - INNER_THICK * 2;
        int contentH    = innerBorderH - INNER_THICK * 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_GUI);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        this.blit(pose, contentLeft, contentTop, 0, 0, contentW, contentH, 1024, 1024);

        RenderSystem.disableBlend();

        // --- Labels
        int leftF = left + FIELDS_X_SHIFT;
        int topF  = top  + FIELDS_Y_SHIFT;

        // Título — solo Y_SHIFT
        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2, top + 6 + Y_SHIFT, COLOR_TITULO);

        // Posición
        int lx = leftF + 8 + LABEL_X_SHIFT;
        int vx = leftF + SEP_LABEL_VAL + LABEL_X_SHIFT;
        drawString(pose, this.font, tr("screen.iocraft.puerta.posicion"), lx, topF + 18 + LABEL_Y_SHIFT, COLOR_LABEL);
        drawString(pose, this.font,
                String.format("X=%d Y=%d Z=%d", pos.getX(), pos.getY(), pos.getZ()),
                vx, topF + 18 + LABEL_Y_SHIFT, 0xFFFFFF);

        // Destino / Mensajes (inputs)
        drawString(pose, this.font, tr("screen.iocraft.puerta.destino").getString(),          lx, topF + 36 + LABEL_Y_SHIFT, COLOR_LABEL);
        drawString(pose, this.font, tr("screen.iocraft.puerta.abierto"),  lx, topF + 60 + LABEL_Y_SHIFT, COLOR_LABEL);
        drawString(pose, this.font, tr("screen.iocraft.puerta.cerrado"), lx, topF + 84 + LABEL_Y_SHIFT, COLOR_LABEL);

        // Mensaje entrante (2 líneas, estilo receptor)
        var entrada = InboxIoT.get(this.pos);
        String l1 = tr("screen.iocraft.puerta.origen").getString() + ((entrada != null && entrada.device != null && !entrada.device.isEmpty()) ? entrada.device : "—");
        String l2 = tr("screen.iocraft.puerta.dato").getString() + ((entrada != null && entrada.data   != null && !entrada.data.isEmpty())   ? entrada.data   : "—");

        int baseX = leftF + 10 + LABEL_X_SHIFT;
        int baseY = topF + MSG_ENTRANTE_Y + LABEL_Y_SHIFT; // usa tu constante para ubicar el bloque

        drawString(pose, this.font, l1, baseX, baseY,               0xFFD580);
        drawString(pose, this.font, l2, baseX, baseY + 12 /*+ΔY*/,  0xFFD580);


        // Widgets
        super.render(pose, mouseX, mouseY, partialTick);

        // Mensaje “Guardado”
        if (!validationMsg.isEmpty()) {
            drawCenteredString(pose, this.font, validationMsg, this.width / 2,
                    top + HEIGHT - 26 - BUTTONS_UP - 14, 0x80FF80);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
