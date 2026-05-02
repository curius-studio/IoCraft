package com.curius.iocraft.ui;

import com.curius.iocraft.registro.NombresContenido;
import com.curius.iocraft.ws.WsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class InfoConexionScreen extends Screen {

    private final Screen padre;
    private String host;
    private int port;
    private String url;

    public InfoConexionScreen(Screen padre) {
        super(new TextComponent(NombresContenido.UI_INICIAL.BOTON_INFO));
        this.padre = padre;
    }

    @Override
    protected void init() {
        String configuredHost = WsManager.getHost();
        this.host = getDisplayHost(configuredHost);
        this.port = WsManager.getPort();
        this.url  = "ws://" + host + ":" + port + "/";

        // === Botones: ancho personalizado ===
        int btnW = 160;
        int btnH = 20;
        int spacing = 10;
        int y = this.height / 2 + 44;

        // Centrado en dos columnas
        int total = btnW * 2 + spacing;
        int startX = (this.width - total) / 2;

        // Copiar URL
        this.addRenderableWidget(new Button(startX, y, btnW, btnH,
                new TextComponent(NombresContenido.UI_INICIAL.BOTON_COPIARURL),
                b -> {
                    try {
                        Minecraft.getInstance().keyboardHandler.setClipboard(url);
                        var p = Minecraft.getInstance().player;
                        if (p != null) {
                            p.displayClientMessage(
                                    new TextComponent("URL copiada: " + url).withStyle(ChatFormatting.GREEN),
                                    false
                            );
                        }
                    } catch (Throwable ignored) {}
                }
        ));

        // Cerrar
        this.addRenderableWidget(new Button(startX + btnW + spacing, y, btnW, btnH,
                new TextComponent(NombresContenido.UI_INICIAL.BOTON_CERRAR),
                b -> onClose()
        ));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(pose);

        // Título
        drawCenteredString(pose, this.font, this.getTitle(), this.width / 2, this.height / 2 - 64, 0xFFFFFF);

        // Datos
        int cx = this.width / 2 - 180;
        int y  = this.height / 2 - 40;

        this.font.draw(pose, new TextComponent(NombresContenido.UI_INICIAL.LABEL_SubtituloHOST).withStyle(ChatFormatting.GRAY), cx, y, 0xFFFFFF);
        this.font.draw(pose, new TextComponent(host).withStyle(ChatFormatting.WHITE), cx + 45, y, 0xFFFFFF);
        y += 14;

        this.font.draw(pose, new TextComponent(NombresContenido.UI_INICIAL.LABEL_SubtituloPUERTO).withStyle(ChatFormatting.GRAY), cx, y, 0xFFFFFF);
        this.font.draw(pose, new TextComponent(String.valueOf(port)).withStyle(ChatFormatting.WHITE), cx + 55, y, 0xFFFFFF);
        y += 14;

        this.font.draw(pose, new TextComponent(NombresContenido.UI_INICIAL.LABEL_SubtituloURL).withStyle(ChatFormatting.GRAY), cx, y, 0xFFFFFF);
        this.font.draw(pose, new TextComponent(url).withStyle(ChatFormatting.WHITE), cx + 35, y, 0xFFFFFF);
        y += 18;

        // === Nota en forma de párrafo (word wrap) ===
        if ("0.0.0.0".equals(WsManager.getHost())) {
            var nota = new TextComponent(
                    NombresContenido.UI_INICIAL.LABEL_NOTA1 +
                            NombresContenido.UI_INICIAL.LABEL_NOTA2
            ).withStyle(ChatFormatting.DARK_GRAY);

            int maxWidth = 360; // ancho máximo del párrafo
            int notaX = cx;     // alineado con el resto del texto
            for (var line : this.font.split(nota, maxWidth)) {
                this.font.draw(pose, line, notaX, y, 0xFFFFFF);
                y += 10; // interlineado
            }
        }

        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(padre);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ----- Utilidades para mostrar IP LAN cuando el host configurado es 0.0.0.0/localhost -----

    private static String getDisplayHost(String configuredHost) {
        if (configuredHost == null) return "localhost";

        String h = configuredHost.trim();
        if (h.equals("0.0.0.0") || h.equalsIgnoreCase("localhost") || h.equals("127.0.0.1")) {
            String lan = findFirstSiteLocalIPv4();
            return (lan != null) ? lan : "127.0.0.1";
        }
        return h;
    }

    private static String findFirstSiteLocalIPv4() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en != null && en.hasMoreElements();) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                for (Enumeration<InetAddress> addrs = ni.getInetAddresses(); addrs.hasMoreElements();) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}
        try {
            InetAddress a = InetAddress.getLocalHost();
            if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                return a.getHostAddress();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
