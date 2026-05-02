package com.curius.iocraft.ui;

import com.curius.iocraft.registro.NombresContenido;
import com.curius.iocraft.security.AuthManager;
import com.curius.iocraft.security.BlacklistManager;
import com.curius.iocraft.security.client.ClientAuthStore;
import com.curius.iocraft.security.client.ClientAuthSync;
import com.curius.iocraft.security.net.PacketGuardarDispositivo;
import com.curius.iocraft.security.net.SecurityNetwork;
import com.curius.iocraft.ui.security.ConfigurarDispositivoScreen;
import com.curius.iocraft.ws.DeviceInfo;
import com.curius.iocraft.ws.DeviceRegistry;
import com.curius.iocraft.ws.WsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MenuDispositivos extends Screen {
    private static final Logger LOGGER = LogManager.getLogger("UI-MENU-DISPOSITIVOS");
    private ListaDispositivos lista;
    private Button btnActualizar;
    private Button btnCerrar;
    private Button btnConectar;
    private Button btnEnviarTodos;
    private Button btnInfoConexion;
    private Button btnConfigurar;
    private Button btnBloquear;
    private Button btnBloqueados;

    public static class Dispositivo {
        public final UUID id;          // id real del cliente WS
        public final String nombre;
        public final String ip;
        public final boolean disponible; // conectado = true (por ahora)

        public Dispositivo(UUID id, String nombre, String ip, boolean disponible) {
            this.id = id;
            this.nombre = nombre;
            this.ip = ip;
            this.disponible = disponible;
        }
    }

    private final List<Dispositivo> dispositivos = new ArrayList<>();

    public MenuDispositivos() {
        super(new TextComponent(NombresContenido.UI_INICIAL.TITULO_MENU));
    }

    public static void abrir() {
        Minecraft.getInstance().setScreen(new MenuDispositivos());
    }

    // Mapea aliases de roles del cliente a flags de permisos del paquete de sync.
    private static boolean hasRole(java.util.Set<String> roles, String... aliases) {
        if (roles == null) return false;
        for (String a : aliases) {
            if (roles.contains(a)) return true;
        }
        return false;
    }


    @Override
    protected void init() {
        int top = 40;
        int bottom = this.height - 92;

        this.lista = new ListaDispositivos(this.minecraft, this.width, this.height, top, bottom, 24);
        this.addRenderableWidget(this.lista);

        // Fila 1: Actualizar – Conectar – Enviar a todos
        int btnW = 120, btnH = 20, spacing = 10;
        int totalWidth = btnW * 3 + spacing * 2;
        int startX = (this.width - totalWidth) / 2;
        int y1 = this.height - 82;

        this.btnActualizar = this.addRenderableWidget(new Button(
                startX, y1, btnW, btnH, new TextComponent(NombresContenido.UI_INICIAL.BOTON_ACTUALIZAR),
                b -> cargarDesdeWS()));

        this.btnConectar = this.addRenderableWidget(new Button(
                startX + btnW + spacing, y1, btnW, btnH, new TextComponent(NombresContenido.UI_INICIAL.BOTON_UNICAST),
                b -> conectarSeleccionado()));
        this.btnConectar.active = false;

        this.btnEnviarTodos = this.addRenderableWidget(new Button(
                startX + (btnW + spacing) * 2, y1, btnW, btnH, new TextComponent(NombresContenido.UI_INICIAL.BOTON_BROADCAST),
                b -> enviarATodos()));
        this.btnEnviarTodos.active = false;

        // Fila 2: Configurar – Info – Cerrar (3 botones)
        int y2 = y1 + btnH + 8;
        int totalWidth2 = btnW * 3 + spacing * 2;
        int startX2 = (this.width - totalWidth2) / 2;

        this.btnConfigurar = this.addRenderableWidget(new Button(
                startX2, y2, btnW, btnH, new TextComponent("Configurar..."),
                b -> abrirConfigurar()));
        this.btnConfigurar.active = false;

        this.btnInfoConexion = this.addRenderableWidget(new Button(
                startX2 + btnW + spacing, y2, btnW, btnH, new TextComponent(NombresContenido.UI_INICIAL.BOTON_INFO),
                b -> abrirInfoConexion()));

        this.btnCerrar = this.addRenderableWidget(new Button(
                startX2 + (btnW + spacing) * 2, y2, btnW, btnH, new TextComponent(NombresContenido.UI_INICIAL.BOTON_CERRAR),
                b -> onClose()));

        // Fila 3: Bloquear – Bloqueados
        int y3 = y2 + btnH + 8;
        int totalWidth3 = btnW * 2 + spacing;
        int startX3 = (this.width - totalWidth3) / 2;
        this.btnBloquear = this.addRenderableWidget(new Button(
                startX3, y3, btnW, btnH, new TextComponent("Bloquear"),
                b -> bloquearSeleccionado()));
        this.btnBloquear.active = false;

        this.btnBloqueados = this.addRenderableWidget(new Button(
                startX3 + btnW + spacing, y3, btnW, btnH, new TextComponent("Bloqueados"),
                b -> abrirBloqueados()));

        cargarDesdeWS();
    }

    /** Lee los dispositivos conectados por WebSocket y repinta la lista. */
    private void cargarDesdeWS() {
        dispositivos.clear();

        List<DeviceInfo> vivos = DeviceRegistry.snapshot();
        for (DeviceInfo d : vivos) {
            dispositivos.add(new Dispositivo(d.id, d.nombre, d.ip, true));
        }

        this.lista.setDispositivos(dispositivos, this);
        actualizarEstadoBotonConectar();
        this.btnEnviarTodos.active = !dispositivos.isEmpty();
    }

    public void onGuardarSecret(String device, String secret, java.util.Set<String> roles) {
        // 1) Persistir local siempre (para recordar en la UI)
        com.curius.iocraft.security.client.ClientAuthStore.put(device, secret, roles);

        // 1.5) Habilitar YA para el WS local (aunque estés en menú principal)
        com.curius.iocraft.security.AuthManager.putSecret(device, secret);
        com.curius.iocraft.security.AuthManager.putRoles(device, roles);

        // 2) Mapear roles a flags de permisos compatibles con el paquete de red.
        boolean canSensor   = hasRole(roles, "sensor", "emit", "emisor");
        boolean canCmd      = hasRole(roles, "cmd", "command", "comando");
        boolean canActuator = hasRole(roles, "actuator", "actuador");

        // 3) ¿hay mundo/conexión para mandar al servidor ahora mismo?
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean hayMundo = (mc != null && mc.level != null && mc.player != null && mc.getConnection() != null);

        if (!hayMundo) {
            // encolar para cuando entres a un mundo
            com.curius.iocraft.security.client.ClientAuthSync.queue(device, secret, canSensor, canCmd, canActuator);
            return;
        }

        // 4) Enviar inmediatamente al servidor (persistencia real y roles autoritativos)
        com.curius.iocraft.security.net.SecurityNetwork.CHANNEL.sendToServer(
                new com.curius.iocraft.security.net.PacketGuardarDispositivo(
                        device, secret, canSensor, canCmd, canActuator
                )
        );
    }




    /** Lo llama la lista cuando cambia selección. */
    public void actualizarEstadoBotonConectar() {
        boolean haySel = (lista.getSeleccionado() != null);
        this.btnConectar.active = haySel;
        this.btnConfigurar.active = haySel;
        this.btnBloquear.active = haySel;
        this.btnEnviarTodos.active = !dispositivos.isEmpty();
    }

    private void abrirConfigurar() {
        Dispositivo sel = lista.getSeleccionado();
        if (sel == null) return;
        Minecraft.getInstance().setScreen(new ConfigurarDispositivoScreen(this, sel));
    }

    private void conectarSeleccionado() {
        Dispositivo seleccionado = lista.getSeleccionado();
        if (seleccionado != null) {
            WsManager.send(seleccionado.id, NombresContenido.UI_INICIAL.MSG_UNICAST);
        }
    }

    private void enviarATodos() {
        if (dispositivos.isEmpty()) return;
        int enviados = WsManager.broadcast(NombresContenido.UI_INICIAL.MSG_BROADCAST);
        LOGGER.info("[UI] broadcast_sent count={}", enviados);
    }

    private void abrirBloqueados() {
        Minecraft.getInstance().setScreen(new BlacklistScreen(this));
    }

    private void bloquearSeleccionado() {
        Dispositivo seleccionado = lista.getSeleccionado();
        if (seleccionado == null) return;
        String deviceId = AuthManager.getAuthenticatedDeviceId(seleccionado.id);
        if (deviceId != null && !deviceId.isBlank()) {
            BlacklistManager.addDevice(deviceId);
            AuthManager.revokeSecret(deviceId);
        }
        if (seleccionado.ip != null && !seleccionado.ip.isBlank()) {
            BlacklistManager.addIp(seleccionado.ip);
        }
        WsManager.close(seleccionado.id);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.execute(this::cargarDesdeWS));
    }

    /** Abre la ventanita con host/puerto/URL (host resuelto a IP LAN si es 0.0.0.0). */
    private void abrirInfoConexion() {
        Minecraft.getInstance().setScreen(new InfoConexionScreen(this));
    }

    /** Llamado por la lista cuando se pulsa la "X". */
    public void expulsarDispositivo(Dispositivo disp) {
        // 0) Feedback visual inmediato (optimista): quita la fila ya
        dispositivos.removeIf(d -> d.id.equals(disp.id));
        this.lista.setDispositivos(dispositivos, this);
        actualizarEstadoBotonConectar();

        // 1) Revocar SECRET (invalidar)
        AuthManager.revokeSecret(disp.nombre);

        // 2) Cerrar conexión real
        WsManager.close(disp.id);

        // 3) Refrescar 1-2 ticks después (tras channelInactive -> DeviceRegistry.remove)
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.execute(this::cargarDesdeWS)); // doble execute da un colchon
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);

        // Lista primero (pinta sus overlays)
        this.lista.render(pose, mouseX, mouseY, partialTick);

        // Luego los demás widgets (botones)
        super.render(pose, mouseX, mouseY, partialTick);

        // Título por encima de todo
        pose.pushPose();
        pose.translate(0, 0, 400);
        drawCenteredString(pose, this.font, NombresContenido.UI_INICIAL.TITULO_MENU, this.width / 2, 12, 0xFFFFFF);
        pose.popPose();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        List<Dispositivo> backup = new ArrayList<>(dispositivos);
        super.resize(mc, w, h);
        this.dispositivos.clear();
        this.dispositivos.addAll(backup);
        this.lista.setDispositivos(dispositivos, this);
        actualizarEstadoBotonConectar();
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
