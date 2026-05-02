package com.curius.iocraft.blocks.computer;

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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.curius.iocraft.blocks.computer.net.ComputerNetwork;
import com.curius.iocraft.blocks.computer.net.PacketGuardarDestino;
import com.curius.iocraft.blocks.computer.net.PacketEnviarMensaje;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.Component;

import java.util.LinkedList;

public class ComputerScreen extends Screen {

    private int lastSeenRevision = -1;

    // Tamaño de la ventana
    private static final int WIDTH  = 250;
    private static final int HEIGHT = 200;

    // ========= Variables de Layout =========
    // Grupo 1 (título, pos, destino, set, cerrar)
    private static final int GROUP1_OFFSET_X = 0;
    private static final int GROUP1_OFFSET_Y = 4;

    // Grupo 2 (chat, input mensaje, botón enviar)
    private static final int GROUP2_OFFSET_X = 0;
    private static final int GROUP2_OFFSET_Y = -2;

    // Ajustes individuales dentro del grupo 1
    private static final int TITLE_OFFSET_Y   = 2;   // solo el título
    private static final int POS_OFFSET_Y     = 22;   // "Pos" + XYZ
    private static final int DESTINO_OFFSET_Y = 0;   // input destino + botón "Set"

    // Área de chat (ajustes específicos del viewport del chat)
    private static final int CHAT_OFFSET_Y     = 12;  // desplaza verticalmente el área de chat
    private static final int CHAT_HEIGHT_EXTRA = -11;  // aumenta/reduce la altura del chat

    // Texturas y estilo
    private static final ResourceLocation TEX_GUI =
            new ResourceLocation("iocraft", "textures/gui/textura-gui.png");
    private static final ResourceLocation TEX_FRAME =
            new ResourceLocation("iocraft", "textures/gui/gui_frame_round.png");

    private static final float FRAME1_R = 0x22 / 255f, FRAME1_G = 0x4E / 255f, FRAME1_B = 0x73 / 255f; // #224E73
    private static final float FRAME2_R = 0x11 / 255f, FRAME2_G = 0x2C / 255f, FRAME2_B = 0x47 / 255f; // #112C47

    private static final int FRAME_TEX_W = 32, FRAME_TEX_H = 32;
    private static final int FRAME_CORNER = 8;
    private static final int OUTER_THICK = 8;
    private static final int INNER_THICK = 8;
    private static final int SEAM_FIX    = 2;

    // Mensajes del chat
    private final LinkedList<String> messages = new LinkedList<>();
    private static final int MAX_MSG = 20;
    private static final int LINE_H  = 12;

    // Scroll del área de chat (en píxeles)
    private int scrollY = 0;

    // Widgets
    private EditBox destinoInput;
    private Button saveDestinoBtn;
    private String destinoActual = "";

    private EditBox input;
    private Button sendBtn;
    private Button closeBtn;

    private final BlockPos pos;

    private static Component tr(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public ComputerScreen(BlockPos pos) {
        super(tr("screen.iocraft.computer.title"));
        this.pos = pos;
    }

    public static void abrir(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) mc.execute(() -> mc.setScreen(new ComputerScreen(pos)));
    }

    @Override
    protected void init() {
        super.init();

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        // --- Grupo 1 (superior)
        int g1Left = left + GROUP1_OFFSET_X;
        int g1Top  = top  + GROUP1_OFFSET_Y;

        // --- Grupo 2 (chat + input mensaje)
        int g2Left = left + GROUP2_OFFSET_X;
        int g2Top  = top  + GROUP2_OFFSET_Y;

        // Cargar estado inicial desde el BE (si existe y ya sincronizó)
        BlockEntity be = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getBlockEntity(pos) : null;
        if (be instanceof ComputerBlockEntity cbe) {
            this.destinoActual = cbe.getDestino();
            this.lastSeenRevision = cbe.getRevision();
            for (String s : cbe.getMessagesSnapshot()) {
                pushMsg(s); // precarga chat local
            }
        }

        // Input de destino (pequeño) + botón "Set"
        destinoInput = new EditBox(this.font, g1Left + 12, g1Top + 22 + DESTINO_OFFSET_Y, 120, 14, new TextComponent("Destino"));
        destinoInput.setMaxLength(64);
        destinoInput.setValue(destinoActual);
        this.addRenderableWidget(destinoInput);

        saveDestinoBtn = this.addRenderableWidget(new Button(
                g1Left + 136, g1Top + 22 + DESTINO_OFFSET_Y, 32, 14,
                new TextComponent("Set"), b -> onSaveDestino()
        ));

        // Input de mensaje + botón enviar (parte inferior, grupo 2)
        input = new EditBox(this.font, g2Left + 12, g2Top + HEIGHT - 26, WIDTH - 12 - 12 - 32, 18, new TextComponent("Escriba mensaje..."));
        input.setMaxLength(256);
        this.addRenderableWidget(input);

        sendBtn = this.addRenderableWidget(new Button(
                g2Left + WIDTH - 12 - 28, g2Top + HEIGHT - 26, 28, 18,
                new TextComponent(">"), b -> onSend()
        ));

        // Botón cerrar (grupo 1)
        closeBtn = this.addRenderableWidget(new Button(
                g1Left + WIDTH - 58, g1Top + 8, 46, 16,
                tr("screen.iocraft.computer.btn_cerrar"), b -> onClose()
        ));
    }

    private void syncFromBEIfChanged() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var be = level.getBlockEntity(pos);
        if (!(be instanceof ComputerBlockEntity cbe)) return;

        int rev = cbe.getRevision();
        if (rev != lastSeenRevision) {
            // Trae el snapshot del BE y refléjalo en la lista local
            var snapshot = cbe.getMessagesSnapshot();
            this.messages.clear();
            this.messages.addAll(snapshot);
            // Auto-scroll al final
            int contentH = messages.size() * LINE_H;
            int[] rc = chatRect();
            int viewH = rc[3];
            int maxScroll = Math.max(0, contentH - viewH);
            scrollY = maxScroll;

            lastSeenRevision = rev;
        }
    }


    private void onSaveDestino() {
        this.destinoActual = destinoInput.getValue().trim();
        if (!destinoActual.isEmpty()) {
            // Persistir en servidor
            ComputerNetwork.CHANNEL.sendToServer(new PacketGuardarDestino(this.pos, destinoActual));
            // Feedback local opcional
            pushMsg("§7Destino set: " + destinoActual);
        }
    }

    private void onSend() {
        String msg = input.getValue().trim();
        if (!msg.isEmpty()) {
            // Envía al servidor para persistir en el BE (y ejecutar /ioc enviar ...)
            ComputerNetwork.CHANNEL.sendToServer(new PacketEnviarMensaje(this.pos, destinoActual, msg));
            // Feedback instantáneo (el BE también lo sincronizará)
            pushMsg(tr("screen.iocraft.computer.yo") + ": " + msg);
            input.setValue("");
        }
    }

    private void pushMsg(String s) {
        messages.add(s);
        while (messages.size() > MAX_MSG) messages.removeFirst();

        // Auto-scroll al final
        int contentH = messages.size() * LINE_H;
        int[] rc = chatRect();
        int viewH = rc[3];
        int maxScroll = Math.max(0, contentH - viewH);
        scrollY = maxScroll;
    }

    // --- Render del marco nineslice ---
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
        blitStretched(pose, x + corner, y, innerW, edgeThickness, u1, v0, texW - 2*corner, corner, texW, texH); // Top
        blitStretched(pose, x + corner, y + h - edgeThickness, innerW, edgeThickness, u1, v2, texW - 2*corner, corner, texW, texH); // Bottom
        blitStretched(pose, x, y + corner, edgeThickness, innerH, u0, v1, corner, texH - 2*corner, texW, texH); // Left
        blitStretched(pose, x + w - edgeThickness, y + corner, edgeThickness, innerH, u2, v1, corner, texH - 2*corner, texW, texH); // Right
    }

    private void blitStretched(PoseStack pose, int x, int y, int w, int h,
                               int u, int v, int srcW, int srcH,
                               int texW, int texH) {
        this.blit(pose, x, y, w, h, u, v, srcW, srcH, texW, texH);
    }

    // --- Scissor helper ---
    private void enableScissor(int x, int y, int w, int h) {
        var win = Minecraft.getInstance().getWindow();
        double scale = win.getGuiScale();
        int sx = (int) Math.round(x * scale);
        int sy = (int) Math.round((win.getGuiScaledHeight() - (y + h)) * scale);
        int sw = (int) Math.round(w * scale);
        int sh = (int) Math.round(h * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    private void disableScissor() {
        RenderSystem.disableScissor();
    }

    // Rect del área de chat: x,y,w,h (en coords GUI) — usa offsets del grupo 2 y del chat
    private int[] chatRect() {
        int left = (this.width - WIDTH) / 2 + GROUP2_OFFSET_X;
        int top  = (this.height - HEIGHT) / 2 + GROUP2_OFFSET_Y;
        int x = left + 12;
        int y = top + 44 + CHAT_OFFSET_Y;            // debajo del destino + offset
        int w = WIDTH - 24;
        int h = HEIGHT - 44 - 34 + CHAT_HEIGHT_EXTRA; // alto base +/- ajuste
        return new int[]{x, y, w, h};
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] rc = chatRect();
        int x = rc[0], y = rc[1], w = rc[2], h = rc[3];
        // Solo si el cursor está sobre el área de chat
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            int contentH = messages.size() * LINE_H;
            int maxScroll = Math.max(0, contentH - h);
            // delta>0 rueda arriba -> subir (disminuir scroll)
            scrollY = Mth.clamp(scrollY - (int)(delta * 16), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // Enter envía mensaje
        if (this.input != null && this.input.isFocused() && (key == 257 || key == 335)) {
            onSend();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);

        syncFromBEIfChanged();

        int left = (this.width - WIDTH) / 2;
        int top  = (this.height - HEIGHT) / 2;

        RenderSystem.enableBlend();

        // 1) Borde exterior
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME1_R, FRAME1_G, FRAME1_B, 1.0f);
        blitNineSlice(pose, left, top, WIDTH, HEIGHT, FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, OUTER_THICK);

        // 2) Borde interior (pegado con leve solape)
        int innerBorderX = left - SEAM_FIX;
        int innerBorderY = top  - SEAM_FIX;
        int innerBorderW = WIDTH  + SEAM_FIX * 2;
        int innerBorderH = HEIGHT + SEAM_FIX * 2;

        RenderSystem.setShaderTexture(0, TEX_FRAME);
        RenderSystem.setShaderColor(FRAME2_R, FRAME2_G, FRAME2_B, 1.0f);
        blitNineSlice(pose, innerBorderX, innerBorderY, innerBorderW, innerBorderH, FRAME_TEX_W, FRAME_TEX_H, FRAME_CORNER, INNER_THICK);

        // 3) Fondo (textura) dentro del borde 2
        int contentLeft = innerBorderX + INNER_THICK;
        int contentTop  = innerBorderY + INNER_THICK;
        int contentW    = innerBorderW - INNER_THICK * 2;
        int contentH    = innerBorderH - INNER_THICK * 2;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TEX_GUI);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        this.blit(pose, contentLeft, contentTop, 0, 0, contentW, contentH, 1024, 1024);

        RenderSystem.disableBlend();

        // --- Grupo 1: Título + Pos (con offsets de grupo y específicos)
        int g1Left = left + GROUP1_OFFSET_X;
        int g1Top  = top  + GROUP1_OFFSET_Y;

        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2 + GROUP1_OFFSET_X, g1Top + 6 + TITLE_OFFSET_Y, 0x2F9ADE);

        drawString(pose, this.font, "Pos:", g1Left + 12, g1Top + 18 + POS_OFFSET_Y, 0x2F9ADE);
        drawString(pose, this.font,
                String.format("X=%d Y=%d Z=%d", pos.getX(), pos.getY(), pos.getZ()),
                g1Left + 40, g1Top + 18 + POS_OFFSET_Y, 0xFFFFFF);

        // --- Área de chat scrolleable (usa offsets del grupo 2 + chat)
        int[] rc = chatRect();
        int cx = rc[0], cy = rc[1], cw = rc[2], ch = rc[3];

        // Fondo oscuro tenue para el chat (#0C171D ~70% opacidad)
        fill(pose, cx, cy, cx + cw, cy + ch, 0xB3000000);

        // Clip (scissor) para que el texto no se dibuje fuera del "viewport")
        enableScissor(cx, cy, cw, ch);

        // Altura total del contenido
        int contentHeight = messages.size() * LINE_H;
        int maxScroll = Math.max(0, contentHeight - ch);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);

        // Dibujo de líneas (solo las visibles)
        int firstLine = Math.max(0, scrollY / LINE_H);
        int yOffset = cy - (scrollY % LINE_H);
        for (int i = firstLine; i < messages.size(); i++) {
            int y = yOffset + (i - firstLine) * LINE_H;
            if (y > cy + ch) break;
            if (y + LINE_H < cy) continue;
            drawString(pose, this.font, messages.get(i), cx + 2, y, 0xFFFFFF);
        }

        disableScissor();

        // Barra de scroll simple (si hace falta)
        if (maxScroll > 0) {
            int barW = 3;
            int barX = cx + cw - barW;
            int barH = Math.max(10, (int)(ch * (ch / (float)contentHeight)));
            int barY = cy + (int)((scrollY / (float)maxScroll) * (ch - barH));
            fill(pose, barX, barY, barX + barW, barY + barH, 0x80FFFFFF);
        }

        // Widgets (inputs y botones)
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(null); }
}
