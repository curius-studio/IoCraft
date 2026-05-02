package com.curius.iocraft.blocks.emisor;

import com.curius.iocraft.blocks.emisor.net.EmisorNetwork;
import com.curius.iocraft.blocks.emisor.net.PacketGuardarEmisor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

public class PantallaEmisor extends Screen {

    private static final int WIDTH  = 260;
    private static final int HEIGHT = 170;

    // ↓ Ajustes de posicionamiento
    private static final int SHIFT_DOWN = 10;   // baja título/labels/inputs esta cantidad
    private static final int BUTTONS_UP = 8;    // sube los botones esta cantidad

    private final BlockPos pos;

    private EditBox txtNombre;
    private EditBox txtContenido;     // Param 2 (ON)
    private EditBox txtContenidoOff;  // Param 2 (OFF)
    private CycleButton<BloqueEmisorEntity.ModoEnvio> cboModo;

    private Button btnGuardar;
    private Button btnCerrar;

    private String status = "";

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public PantallaEmisor(BlockPos pos) {
        super(tr("screen.iocraft.emisor.title"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new PantallaEmisor(pos)));
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // Prefill desde el BE (si ya sincronizó)
        String nombre = "", contenido = "", contenidoOff = "";
        BloqueEmisorEntity.ModoEnvio modo = BloqueEmisorEntity.ModoEnvio.SOLO_ENCENDIDO;

        var be = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        if (be instanceof BloqueEmisorEntity e) {
            nombre       = e.getNombre();
            contenido    = e.getContenido();
            contenidoOff = e.getContenidoOff();
            modo         = e.getModo();
        }

        // Nombre (Param1)
        this.txtNombre = new EditBox(this.font, left + 100, top + 28 + (SHIFT_DOWN - 3), 140, 18, new TextComponent("Nombre"));
        this.txtNombre.setMaxLength(128);
        this.txtNombre.setValue(nombre);
        this.addRenderableWidget(this.txtNombre);

        // Modo (displayOnlyValue para quitar "Modo:")
        this.cboModo = this.addRenderableWidget(
                CycleButton.builder((BloqueEmisorEntity.ModoEnvio m) ->
                                (m == BloqueEmisorEntity.ModoEnvio.SOLO_ENCENDIDO
                                        ? tr("screen.iocraft.emisor.modo1")
                                        : tr("screen.iocraft.emisor.modo2")))
                        .withValues(
                                BloqueEmisorEntity.ModoEnvio.SOLO_ENCENDIDO,
                                BloqueEmisorEntity.ModoEnvio.ENCENDIDO_Y_APAGADO
                        )
                        .withInitialValue(modo)
                        .displayOnlyValue()
                        .create(left + 100, top + 52 + (SHIFT_DOWN - 3), 140, 20, TextComponent.EMPTY,
                                (btn, value) -> syncVisibility())
        );

        // Contenido ON (Param2 ON)
        this.txtContenido = new EditBox(this.font, left + 100, top + 78 + (SHIFT_DOWN - 3), 140, 18, new TextComponent("Contenido ON"));
        this.txtContenido.setMaxLength(256);
        this.txtContenido.setValue(contenido);
        this.addRenderableWidget(this.txtContenido);

        // Contenido OFF (Param2 OFF) — se oculta en SOLO_ENCENDIDO
        this.txtContenidoOff = new EditBox(this.font, left + 100, top + 102 + (SHIFT_DOWN - 3), 140, 18, new TextComponent("Contenido OFF"));
        this.txtContenidoOff.setMaxLength(256);
        this.txtContenidoOff.setValue(contenidoOff);
        this.addRenderableWidget(this.txtContenidoOff);

        // Botones (subidos un poco)
        int buttonsY = top + HEIGHT - 24 - BUTTONS_UP;
        this.btnGuardar = this.addRenderableWidget(new Button(
                left + WIDTH/2 - 82, buttonsY, 75, 20,
                tr("screen.iocraft.emisor.btn_guardar"), b -> onGuardar()
        ));
        this.btnCerrar = this.addRenderableWidget(new Button(
                left + WIDTH/2 + 7, buttonsY, 75, 20,
                tr("screen.iocraft.emisor.btn_cerrar"), b -> onClose()
        ));

        syncVisibility();
    }

    private void syncVisibility() {
        BloqueEmisorEntity.ModoEnvio modoSel = this.cboModo.getValue();
        boolean ambos = (modoSel == BloqueEmisorEntity.ModoEnvio.ENCENDIDO_Y_APAGADO);
        if (this.txtContenidoOff != null) this.txtContenidoOff.visible = ambos;
    }

    private void onGuardar() {
        String nombre       = this.txtNombre.getValue().trim();
        String contenido    = this.txtContenido.getValue();
        String contenidoOff = this.txtContenidoOff.getValue();
        String modo         = this.cboModo.getValue().name();

        // Aplica en cliente (si el BE está en memoria)
        var be = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        if (be instanceof BloqueEmisorEntity e) {
            e.setNombre(nombre);
            e.setContenido(contenido);
            e.setContenidoOff(contenidoOff);
            e.setModo(this.cboModo.getValue());
        }

        // Envía al servidor
        EmisorNetwork.CHANNEL.sendToServer(
                new PacketGuardarEmisor(this.pos, nombre, contenido, contenidoOff, modo)
        );

        status = tr("screen.iocraft.emisor.guardado").getString();
    }

    // --------- Texturas ---------
    private static final ResourceLocation TEX_GUI =
            new ResourceLocation("iocraft", "textures/gui/textura-gui.png");
    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation("iocraft", "textures/gui/gui_frame_round.png");

    // --------- Colores & geometría ---------
    private static final int COLOR_TITULO = 0x2F9ADE;
    private static final int COLOR_LABEL  = 0x2F9ADE;

    private static final float FRAME1_R = 0x22 / 255f, FRAME1_G = 0x4E / 255f, FRAME1_B = 0x73 / 255f; // #224E73
    private static final float FRAME2_R = 0x11 / 255f, FRAME2_G = 0x2C / 255f, FRAME2_B = 0x47 / 255f; // #112C47

    private static final int FRAME_TEX_W = 32, FRAME_TEX_H = 32;
    private static final int FRAME_CORNER = 8;
    private static final int OUTER_THICK = 8;   // grosor borde 1
    private static final int INNER_THICK = 8;   // grosor borde 2
    private static final int SEAM_FIX    = 2;   // solape para evitar “hilo”

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
        this.blit(pose, x, y,                         u0, v0, corner, corner, texW, texH); // TL
        this.blit(pose, x + w - corner, y,            u2, v0, corner, corner, texW, texH); // TR
        this.blit(pose, x, y + h - corner,            u0, v2, corner, corner, texW, texH); // BL
        this.blit(pose, x + w - corner, y + h - corner, u2, v2, corner, corner, texW, texH); // BR

        // Bordes
        // Top
        blitStretched(pose, x + corner, y, innerW, edgeThickness,
                u1, v0, texW - 2*corner, corner, texW, texH);
        // Bottom
        blitStretched(pose, x + corner, y + h - edgeThickness, innerW, edgeThickness,
                u1, v2, texW - 2*corner, corner, texW, texH);
        // Left
        blitStretched(pose, x, y + corner, edgeThickness, innerH,
                u0, v1, corner, texH - 2*corner, texW, texH);
        // Right
        blitStretched(pose, x + w - edgeThickness, y + corner, edgeThickness, innerH,
                u2, v1, corner, texH - 2*corner, texW, texH);
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

        RenderSystem.enableBlend();

        // 1) Borde exterior
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME1_R, FRAME1_G, FRAME1_B, 1.0f);
        blitNineSlice(pose, left, top, WIDTH, HEIGHT,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, OUTER_THICK);

        // 2) Borde interior (pegado con pequeño solape)
        int innerBorderX = left - SEAM_FIX;
        int innerBorderY = top  - SEAM_FIX;
        int innerBorderW = WIDTH  + SEAM_FIX * 2;
        int innerBorderH = HEIGHT + SEAM_FIX * 2;

        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME2_R, FRAME2_G, FRAME2_B, 1.0f);
        blitNineSlice(pose, innerBorderX, innerBorderY, innerBorderW, innerBorderH,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, INNER_THICK);

        // 3) Fondo: justo dentro del borde 2 (sin el solape)
        int contentLeft = innerBorderX + INNER_THICK;
        int contentTop  = innerBorderY + INNER_THICK;
        int contentW    = innerBorderW - INNER_THICK * 2;
        int contentH    = innerBorderH - INNER_THICK * 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_GUI);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        this.blit(pose, contentLeft, contentTop, 0, 0, contentW, contentH, 1024, 1024);

        RenderSystem.disableBlend();

        // Título y labels (desplazados hacia abajo)
        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2, top + 6 + (SHIFT_DOWN - 3), COLOR_TITULO);
        drawString(pose, this.font, tr("screen.iocraft.emisor.dest").getString() + ":", left + 12, top + 30 + (SHIFT_DOWN - 1), COLOR_LABEL);
        drawString(pose, this.font, tr("screen.iocraft.emisor.modo").getString() + ":",   left + 12, top + 56 + (SHIFT_DOWN - 1), COLOR_LABEL);
        drawString(pose, this.font, tr("screen.iocraft.emisor.msg_on").getString() + ":",     left + 12, top + 80 + (SHIFT_DOWN - 1), COLOR_LABEL);
        if (this.cboModo.getValue() == BloqueEmisorEntity.ModoEnvio.ENCENDIDO_Y_APAGADO) {
            drawString(pose, this.font, tr("screen.iocraft.emisor.msg_off").getString() + ":", left + 12, top + 104 + (SHIFT_DOWN - 1), COLOR_LABEL);
        }

        // Widgets
        super.render(pose, mouseX, mouseY, partialTick);

        if (!status.isEmpty()) {
            drawCenteredString(pose, this.font, status, this.width/2, top + HEIGHT - 40 - BUTTONS_UP, 0x80FF80);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
