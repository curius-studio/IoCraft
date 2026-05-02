package com.curius.iocraft.ui.security;

import com.curius.iocraft.security.client.ClientAuthStore;
import com.curius.iocraft.ui.MenuDispositivos;
import com.curius.iocraft.ui.widgets.IconCheckbox;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class ConfigurarDispositivoScreen extends Screen {
    private final Screen parent;
    private final MenuDispositivos.Dispositivo disp;

    // Widgets
    private EditBox txtDevice;
    private EditBox txtSecret;
    private IconCheckbox chkSensor, chkCmd, chkActuator;
    private Button btnGenerar, btnCopiar, btnGuardar, btnCerrar;

    // Layout cache
    private int contentW;
    private int left;
    private int yDevice;   // Y del campo device
    private int ySecret;   // Y del campo secret
    private int yRoles;    // Y del primer checkbox de roles

    // Iconos
    private static final ResourceLocation ICON_SENSOR   =
            new ResourceLocation("iocraft","textures/gui/icons/sensor.png");
    private static final ResourceLocation ICON_CMD      =
            new ResourceLocation("iocraft","textures/gui/icons/cmd.png");
    private static final ResourceLocation ICON_ACTUATOR =
            new ResourceLocation("iocraft","textures/gui/icons/actuador.png");

    public ConfigurarDispositivoScreen(Screen parent, MenuDispositivos.Dispositivo disp) {
        super(new TextComponent("Configurar dispositivo"));
        this.parent = parent;
        this.disp = disp;
    }

    @Override
    protected void init() {
        // --- Medidas base (responden a cualquier tamaño de pantalla) ---
        contentW = Math.min(440, this.width - 40); // deja margen lateral
        left     = (this.width - contentW) / 2;
        int rowH = 22;
        int gapY = 10;
        int y    = 52; // contenido arranca un poco debajo del título

        // --- Device ID ---
        yDevice = y;
        this.txtDevice = new EditBox(this.font, left, yDevice, contentW, 20, new TextComponent("deviceId"));
        this.txtDevice.setMaxLength(128);
        String initialDevice = (disp.nombre != null && !disp.nombre.isBlank()) ? disp.nombre : "device-" + disp.id;
        this.txtDevice.setValue(initialDevice);
        this.addRenderableWidget(this.txtDevice);
        y += rowH + gapY + 5; // deja sitio para el label encima

        // --- Secret + botones a la derecha ---
        ySecret = y;
        int btnW = 90;
        int btnGap = 8;
        int secretW = contentW - (btnW * 2 + btnGap * 2);
        this.txtSecret = new EditBox(this.font, left, ySecret, secretW, 20, new TextComponent("secret"));
        this.txtSecret.setMaxLength(128);
        this.addRenderableWidget(this.txtSecret);

        int xBtn = left + secretW + btnGap;
        this.btnGenerar = this.addRenderableWidget(new Button(xBtn, ySecret, btnW, 20, new TextComponent("Generar"),
                b -> this.txtSecret.setValue(generarHex(32))));
        this.btnCopiar   = this.addRenderableWidget(new Button(xBtn + btnW + btnGap, ySecret, btnW, 20, new TextComponent("Copiar"),
                b -> Minecraft.getInstance().keyboardHandler.setClipboard(this.txtSecret.getValue())));

        y += rowH + gapY + 6;

        // --- Roles ---
        yRoles = y;
        int chkH = 22;

        this.chkSensor = this.addRenderableWidget(new IconCheckbox(
                left, yRoles, contentW, chkH,
                new TextComponent("Sensor / UI"), false, ICON_SENSOR, null));
        y += chkH + 6;

        this.chkCmd = this.addRenderableWidget(new IconCheckbox(
                left, y, contentW, chkH,
                new TextComponent("Comandos (cmd)"), false, ICON_CMD, null));
        y += chkH + 6;

        this.chkActuator = this.addRenderableWidget(new IconCheckbox(
                left, y, contentW, chkH,
                new TextComponent("Actuador"), false, ICON_ACTUATOR, null));
        y += chkH + 14;

        // --- Botones inferiores (centrados) ---
        int btnW2 = 120;
        int btnGap2 = 12;
        int totalW = btnW2 * 2 + btnGap2;
        int startX = left + (contentW - totalW) / 2;

        this.btnGuardar = this.addRenderableWidget(new Button(startX, y, btnW2, 20, new TextComponent("Guardar"), b -> onGuardar()));
        this.btnCerrar  = this.addRenderableWidget(new Button(startX + btnW2 + btnGap2, y, btnW2, 20, new TextComponent("Cerrar"),
                b -> onClose()));

        // Prefill desde el store del cliente (si existe)
        prefillFromStore(initialDevice);
    }

    /** Genera HEX seguro (64 chars para 32 bytes). */
    private static String generarHex(int bytes) {
        SecureRandom r = new SecureRandom();
        byte[] buf = new byte[bytes];
        r.nextBytes(buf);
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[buf.length * 2];
        for (int i = 0, j = 0; i < buf.length; i++) {
            int v = buf[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** Carga secret + roles del store local y marca los checkboxes. */
    private void prefillFromStore(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return;
        var entry = ClientAuthStore.get(deviceId);
        if (entry == null) return;

        if (entry.secret != null && !entry.secret.isBlank()) {
            this.txtSecret.setValue(entry.secret);
        }

        boolean rSensor   = hasAny(entry.roles, "sensor", "ui", "emisor", "emit");
        boolean rCmd      = hasAny(entry.roles, "cmd", "command", "comando");
        boolean rActuator = hasAny(entry.roles, "actuator", "actuador");

        if (this.chkSensor   != null) this.chkSensor.setSelected(rSensor);
        if (this.chkCmd      != null) this.chkCmd.setSelected(rCmd);
        if (this.chkActuator != null) this.chkActuator.setSelected(rActuator);
    }

    private static boolean hasAny(Set<String> roles, String... keys) {
        if (roles == null || roles.isEmpty()) return false;
        for (String k : keys) if (roles.contains(k)) return true;
        return false;
    }

    private void onGuardar() {
        String device = txtDevice.getValue().trim();
        String secret = txtSecret.getValue().trim();
        if (device.isEmpty() || secret.isEmpty()) {
            return; // podrías mostrar un toast si quieres
        }

        Set<String> roles = new HashSet<>();
        if (chkSensor.selected())   roles.add("sensor");
        if (chkCmd.selected())      roles.add("cmd");
        if (chkActuator.selected()) roles.add("actuator");

        // Persistir local para prefill futuro
        ClientAuthStore.put(device, secret, roles);

        // Delegar al menú (él decide si enviar al server o encolar)
        if (parent instanceof MenuDispositivos menu) {
            menu.onGuardarSecret(device, secret, roles);
        }

        this.btnGuardar.setMessage(new TextComponent("¡Guardado!"));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);

        // Título
        drawCenteredString(pose, this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        // Labels grises sobre cada sección
        int labelColor = 0xFFA0A0A0;
        drawString(pose, this.font, "Device ID (nombre lógico)", left, yDevice - 12, labelColor);
        drawString(pose, this.font, "Secret (HMAC-SHA256)",       left, ySecret - 12, labelColor);

        // Separador antes de roles
        int sepY = ySecret + 14;
        fill(pose, left, sepY, left + contentW, sepY + 1, 0x14FFFFFF);

        drawString(pose, this.font, "Roles:", left, yRoles - 12, labelColor);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { Minecraft.getInstance().setScreen(parent); }
}
