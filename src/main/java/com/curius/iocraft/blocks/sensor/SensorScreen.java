package com.curius.iocraft.blocks.sensor;

import com.curius.iocraft.blocks.sensor.net.PacketGuardarConfig;
import com.curius.iocraft.blocks.sensor.net.SensorNetwork;
import com.curius.iocraft.iot.InboxIoT;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.Component;

public class SensorScreen extends Screen {
    // Tamaño
    private static final int WIDTH  = 280;
    private static final int HEIGHT = 210;

    // (EXISTENTES) Ajustes globales
    private static final int Y_SHIFT    = 7;  // mueve SOLO el título
    private static final int BUTTONS_UP = 5;  // sube Guardar/Cerrar

    // (EXISTENTES) Offsets exclusivos de etiquetas (si quieres afinar solo texto)
    private static final int LABEL_X_SHIFT = 3;
    private static final int LABEL_Y_SHIFT = 3;

    // NUEVO: Offsets para el CONJUNTO "labels + inputs asociados" (NO incluye Guardar/Cerrar)
    private static final int FIELDS_X_SHIFT = 0;  // mueve horizontalmente Pos/Nombre/Modo/... y sus inputs
    private static final int FIELDS_Y_SHIFT = 12;  // mueve verticalmente Pos/Nombre/Modo/... y sus inputs

    private final BlockPos pos;

    // Estado actual (ligado al BE)
    private String nombreBloque = "";
    private SensorBlockEntity.Modo modo = SensorBlockEntity.Modo.COINCIDENCIA;
    private String valorActivador = "";
    private SensorBlockEntity.Operador operador = SensorBlockEntity.Operador.LE;
    private double umbral = 0.0;
    private double valorMin = 0.0;
    private double valorMax = 0.0;

    // Widgets
    private EditBox txtNombreBloque;
    private CycleButton<SensorBlockEntity.Modo> cboModo;
    private EditBox txtValorActivador;
    private CycleButton<SensorBlockEntity.Operador> cboOperador;
    private EditBox txtUmbral;
    private EditBox txtMin;
    private EditBox txtMax;

    private Button btnGuardar;
    private Button btnCerrar;

    private String validationMsg = "";

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public SensorScreen(BlockPos pos) {
        super(tr("screen.iocraft.receptor.title"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new SensorScreen(pos)));
    }

    @Override
    protected void init() {
        super.init();

        var level = Minecraft.getInstance().level;
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SensorBlockEntity s) {
                this.nombreBloque   = s.getNombre();
                this.modo           = s.getModo();
                this.valorActivador = s.getValorActivador();
                this.operador       = s.getOperador();
                this.umbral         = s.getUmbral();
                this.valorMin       = s.getMin();
                this.valorMax       = s.getMax();
            }
        }

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // Base para el conjunto de CAMPOS (labels + inputs). NO afecta Guardar/Cerrar.
        int leftF = left + FIELDS_X_SHIFT;
        int topF  = top  + FIELDS_Y_SHIFT;

        // Nombre (INPUT)
        this.txtNombreBloque = new EditBox(this.font, leftF + 110, topF + 34, 150, 18,
                new TextComponent("Nombre del bloque"));
        this.txtNombreBloque.setMaxLength(64);
        this.txtNombreBloque.setValue(this.nombreBloque);
        this.addRenderableWidget(this.txtNombreBloque);

        // Modo (INPUT)
        this.cboModo = this.addRenderableWidget(
                CycleButton.builder((SensorBlockEntity.Modo m) -> new TextComponent(
                                m == SensorBlockEntity.Modo.COINCIDENCIA ? tr("screen.iocraft.receptor.txt_modo1").getString() :
                                        m == SensorBlockEntity.Modo.UMBRAL       ? tr("screen.iocraft.receptor.txt_modo2").getString()       : tr("screen.iocraft.receptor.txt_modo3").getString()))
                        .withValues(SensorBlockEntity.Modo.COINCIDENCIA,
                                SensorBlockEntity.Modo.UMBRAL,
                                SensorBlockEntity.Modo.RANGO)
                        .withInitialValue(this.modo)
                        .create(leftF + 110, topF + 58, 150, 20,
                                tr("screen.iocraft.receptor.modo"),
                                (btn, value) -> { this.modo = value; syncVisibility(); })
        );

        // Fila compartida (INPUTS)
        int sharedY = topF + 84;

        // Coincidencia
        this.txtValorActivador = new EditBox(this.font, leftF + 110, sharedY, 150, 18,
                tr("screen.iocraft.receptor.label_modo1"));
        this.txtValorActivador.setMaxLength(64);
        this.txtValorActivador.setValue(this.valorActivador);
        this.addRenderableWidget(this.txtValorActivador);

        // Umbral (operador + valor)
        this.cboOperador = this.addRenderableWidget(
                CycleButton.builder((SensorBlockEntity.Operador op) -> new TextComponent(
                                op == SensorBlockEntity.Operador.LT ? "<"  :
                                        op == SensorBlockEntity.Operador.LE ? "<=" :
                                                op == SensorBlockEntity.Operador.EQ ? "="  :
                                                        op == SensorBlockEntity.Operador.GE ? ">=" : ">"
                        ))
                        .withValues(SensorBlockEntity.Operador.LT,
                                SensorBlockEntity.Operador.LE,
                                SensorBlockEntity.Operador.EQ,
                                SensorBlockEntity.Operador.GE,
                                SensorBlockEntity.Operador.GT)
                        .withInitialValue(this.operador)
                        .create(leftF + 110, sharedY - 1, 75, 20,
                                tr("screen.iocraft.receptor.operador"),
                                (btn, value) -> this.operador = value)
        );
        this.txtUmbral = new EditBox(this.font, leftF + 190, sharedY, 70, 18,
                tr("screen.iocraft.receptor.txt_valor"));
        this.txtUmbral.setMaxLength(32);
        this.txtUmbral.setValue(Double.toString(this.umbral));
        this.addRenderableWidget(this.txtUmbral);

        // Rango (min + max)
        this.txtMin = new EditBox(this.font, (leftF + 3) + 127, sharedY + 3, 50, 18, tr("screen.iocraft.receptor.min"));
        this.txtMin.setMaxLength(32);
        this.txtMin.setValue(Double.toString(this.valorMin));
        this.addRenderableWidget(this.txtMin);

        this.txtMax = new EditBox(this.font, (leftF + 3) + 210, sharedY + 3, 50, 18, tr("screen.iocraft.receptor.max"));
        this.txtMax.setMaxLength(32);
        this.txtMax.setValue(Double.toString(this.valorMax));
        this.addRenderableWidget(this.txtMax);

        // Botones (NO usan FIELDS_*; permanecen en su sitio base + BUTTONS_UP)
        int buttonsY = top + HEIGHT - 26 - BUTTONS_UP;
        this.btnGuardar = this.addRenderableWidget(new Button(
                left + WIDTH / 2 - 82, buttonsY, 75, 20,
                tr("screen.iocraft.receptor.btn_guardar"), b -> onGuardar()
        ));
        this.btnCerrar = this.addRenderableWidget(new Button(
                left + WIDTH / 2 + 7, buttonsY, 75, 20,
                tr("screen.iocraft.receptor.btn_cerrar"), b -> onClose()
        ));

        syncVisibility();
    }

    private void syncVisibility() {
        boolean esCoin   = (modo == SensorBlockEntity.Modo.COINCIDENCIA);
        boolean esUmbral = (modo == SensorBlockEntity.Modo.UMBRAL);
        boolean esRango  = (modo == SensorBlockEntity.Modo.RANGO);

        if (this.txtValorActivador != null) this.txtValorActivador.visible = esCoin;

        if (this.cboOperador != null) this.cboOperador.visible = esUmbral;
        if (this.txtUmbral   != null) this.txtUmbral.visible   = esUmbral;

        if (this.txtMin != null) this.txtMin.visible = esRango;
        if (this.txtMax != null) this.txtMax.visible = esRango;
    }

    private void onGuardar() {
        String nombre = this.txtNombreBloque.getValue().trim();

        String match = (this.txtValorActivador != null && this.txtValorActivador.visible)
                ? this.txtValorActivador.getValue().trim() : "";

        double u  = this.umbral;
        double mi = this.valorMin;
        double ma = this.valorMax;

        try { if (this.txtUmbral != null && this.txtUmbral.visible) u  = Double.parseDouble(this.txtUmbral.getValue().trim()); } catch (Exception ignored) {}
        try { if (this.txtMin    != null && this.txtMin.visible)    mi = Double.parseDouble(this.txtMin.getValue().trim()); }    catch (Exception ignored) {}
        try { if (this.txtMax    != null && this.txtMax.visible)    ma = Double.parseDouble(this.txtMax.getValue().trim()); }    catch (Exception ignored) {}

        SensorNetwork.CHANNEL.sendToServer(
                new PacketGuardarConfig(
                        this.pos,
                        nombre,
                        this.modo.name(),
                        match,
                        this.operador.name(),
                        u, mi, ma
                )
        );

        this.nombreBloque   = nombre;
        this.valorActivador = match;
        this.umbral         = u;
        this.valorMin       = mi;
        this.valorMax       = ma;

        this.validationMsg = "Guardado";
    }

    // --- Estilo (idéntico a PantallaDatos) ---
    private static final ResourceLocation TEX_GUI =
            new ResourceLocation("iocraft", "textures/gui/textura-gui.png");
    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation("iocraft", "textures/gui/gui_frame_round.png");

    private static final int COLOR_TITULO = 0x2F9ADE;
    private static final int COLOR_LABEL  = 0x2F9ADE;

    private static final float FRAME1_R = 0x22 / 255f, FRAME1_G = 0x4E / 255f, FRAME1_B = 0x73 / 255f; // #224E73
    private static final float FRAME2_R = 0x11 / 255f, FRAME2_G = 0x2C / 255f, FRAME2_B = 0x47 / 255f; // #112C47

    private static final int FRAME_TEX_W = 32, FRAME_TEX_H = 32;
    private static final int FRAME_CORNER = 8;
    private static final int OUTER_THICK = 8;
    private static final int INNER_THICK = 8;
    private static final int SEAM_FIX    = 2;

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

        // Marco + fondo (no se mueven con FIELDS_*)
        RenderSystem.enableBlend();

        // 1) Borde exterior
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME1_R, FRAME1_G, FRAME1_B, 1.0f);
        blitNineSlice(pose, left, top, WIDTH, HEIGHT,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, OUTER_THICK);

        // 2) Borde interior (con solape)
        int innerBorderX = left - SEAM_FIX;
        int innerBorderY = top  - SEAM_FIX;
        int innerBorderW = WIDTH  + SEAM_FIX * 2;
        int innerBorderH = HEIGHT + SEAM_FIX * 2;

        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME2_R, FRAME2_G, FRAME2_B, 1.0f);
        blitNineSlice(pose, innerBorderX, innerBorderY, innerBorderW, innerBorderH,
                FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, INNER_THICK);

        // 3) Fondo dentro del borde 2
        int contentLeft = innerBorderX + INNER_THICK;
        int contentTop  = innerBorderY + INNER_THICK;
        int contentW    = innerBorderW - INNER_THICK * 2;
        int contentH    = innerBorderH - INNER_THICK * 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_GUI);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        this.blit(pose, contentLeft, contentTop, 0, 0, contentW, contentH, 1024, 1024);

        RenderSystem.disableBlend();

        // --- Labels (se mueven con LABEL_* y FIELDS_*) ---
        int leftF = left + FIELDS_X_SHIFT;
        int topF  = top  + FIELDS_Y_SHIFT;

        // Título — SOLO Y_SHIFT
        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2, top + 6 + Y_SHIFT, COLOR_TITULO);

        drawString(pose, this.font, tr("screen.iocraft.receptor.posicion"), leftF + 8 + LABEL_X_SHIFT,  topF + 18 + LABEL_Y_SHIFT, COLOR_LABEL);
        drawString(pose, this.font,
                String.format("X=%d Y=%d Z=%d", pos.getX(), pos.getY(), pos.getZ()),
                leftF + 40 + (LABEL_X_SHIFT  + 67), topF + 18 + LABEL_Y_SHIFT, 0xFFFFFF);

        drawString(pose, this.font, tr("screen.iocraft.receptor.nombre"), leftF + 8 + LABEL_X_SHIFT,  topF + 36 + LABEL_Y_SHIFT, COLOR_LABEL);
        drawString(pose, this.font, tr("screen.iocraft.receptor.modo"),   leftF + 8 + LABEL_X_SHIFT,  topF + 60 + LABEL_Y_SHIFT, COLOR_LABEL);

        if (modo == SensorBlockEntity.Modo.COINCIDENCIA) {
            drawString(pose, this.font, tr("screen.iocraft.receptor.label_modo1"),
                    leftF + 8 + LABEL_X_SHIFT, topF + 86 + LABEL_Y_SHIFT, COLOR_LABEL);
        } else if (modo == SensorBlockEntity.Modo.UMBRAL) {
            drawString(pose, this.font, tr("screen.iocraft.receptor.label_modo2"),
                    leftF + 8 + LABEL_X_SHIFT, topF + 86 + LABEL_Y_SHIFT, COLOR_LABEL);
        } else {
            drawString(pose, this.font, tr("screen.iocraft.receptor.label_modo3"),
                    leftF + 8 + LABEL_X_SHIFT, topF + 86 + LABEL_Y_SHIFT, COLOR_LABEL);
            drawString(pose, this.font, tr("screen.iocraft.receptor.min"),
                    leftF + 110 + LABEL_X_SHIFT, topF + 88 + LABEL_Y_SHIFT, 0xCCCCCC);
            drawString(pose, this.font, tr("screen.iocraft.receptor.max"),
                    leftF + 190 + LABEL_X_SHIFT, topF + 88 + LABEL_Y_SHIFT, 0xCCCCCC);
        }

        drawString(pose, this.font, tr("screen.iocraft.receptor.resumen"), leftF + 8 + LABEL_X_SHIFT, topF + 112 + LABEL_Y_SHIFT, COLOR_LABEL);
        var entrada = InboxIoT.get(this.pos);
        String linea1 = tr("screen.iocraft.receptor.origen").getString() + (entrada != null ? entrada.device : "—");
        String linea2 = tr("screen.iocraft.receptor.dato").getString() + (entrada != null ? entrada.data   : "—");
        drawString(pose, this.font, linea1, leftF + 10 + LABEL_X_SHIFT, topF + 126 + LABEL_Y_SHIFT, 0xFFD580);
        drawString(pose, this.font, linea2, leftF + 10 + LABEL_X_SHIFT, topF + 138 + LABEL_Y_SHIFT, 0xFFD580);

        // Widgets (inputs se posicionaron con FIELDS_*)
        super.render(pose, mouseX, mouseY, partialTick);

        if (!validationMsg.isEmpty()) {
            int color = validationMsg.startsWith(tr("screen.iocraft.receptor.guardado").getString()) ? 0x80FF80 : 0xFF8080;
            // Mensaje también lo movemos junto al grupo para acompañar inputs
            drawCenteredString(pose, this.font, validationMsg, this.width / 2,
                    topF + HEIGHT - 40 - BUTTONS_UP, color);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
