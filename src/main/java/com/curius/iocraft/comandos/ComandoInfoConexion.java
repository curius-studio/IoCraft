package com.curius.iocraft.comandos;

import com.curius.iocraft.ws.WsManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ComandoInfoConexion {

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("ioc")
                        .then(Commands.literal("info")
                                .executes(ctx -> mostrarInfo(ctx.getSource()))
                        )
        );
    }

    private static int mostrarInfo(CommandSourceStack source) {
        String hostConfig = WsManager.getHost(); // puede ser 0.0.0.0
        int port = WsManager.getPort();

        // Detectar IPs LAN IPv4 útiles (no loopback, no virtual, interfaz activa)
        List<String> lanIps = detectarIpsLan();

        // Encabezado
        source.sendSuccess(new TextComponent("Información de conexión WebSocket:")
                .withStyle(ChatFormatting.YELLOW), false);

        // Host configurado actualmente en el servidor WS
        source.sendSuccess(new TextComponent("Host configurado: ")
                .withStyle(ChatFormatting.GRAY)
                .append(new TextComponent(hostConfig).withStyle(ChatFormatting.WHITE)), false);

        source.sendSuccess(new TextComponent("Puerto: ")
                .withStyle(ChatFormatting.GRAY)
                .append(new TextComponent(String.valueOf(port)).withStyle(ChatFormatting.WHITE)), false);

        // Sugerencias de URL para otros dispositivos
        if (!lanIps.isEmpty()) {
            if ("0.0.0.0".equals(hostConfig)) {
                source.sendSuccess(new TextComponent("Sugerencias para conectar desde otro dispositivo:")
                        .withStyle(ChatFormatting.GRAY), false);
            } else {
                source.sendSuccess(new TextComponent("URL según host configurado:")
                        .withStyle(ChatFormatting.GRAY)
                        .append(new TextComponent(" ws://" + hostConfig + ":" + port + "/")
                                .withStyle(ChatFormatting.WHITE)), false);
                source.sendSuccess(new TextComponent("Otras IPs LAN detectadas en este equipo:")
                        .withStyle(ChatFormatting.GRAY), false);
            }

            for (String ip : lanIps) {
                source.sendSuccess(new TextComponent(" - ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(new TextComponent("ws://" + ip + ":" + port + "/").withStyle(ChatFormatting.WHITE)), false);
            }
        } else {
            // Sin IP LAN encontrada (raro, pero puede pasar en VMs/VPNs)
            source.sendSuccess(new TextComponent("No se detectaron IPs LAN IPv4 disponibles.")
                    .withStyle(ChatFormatting.RED), false);
            source.sendSuccess(new TextComponent("Intenta revisar tu adaptador de red o desactivar VPN/VM.")
                    .withStyle(ChatFormatting.GRAY), false);
            // Aún así, muestra la URL con el host configurado (por si no es 0.0.0.0)
            source.sendSuccess(new TextComponent("URL: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(new TextComponent("ws://" + hostConfig + ":" + port + "/").withStyle(ChatFormatting.WHITE)), false);
        }

        // Nota útil
        if ("0.0.0.0".equals(hostConfig)) {
            source.sendSuccess(new TextComponent("Nota: '0.0.0.0' indica que el servidor escucha en todas las interfaces;")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(new TextComponent("usa una de las URL sugeridas arriba desde el celular/otro equipo.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Devuelve una lista de IPs LAN IPv4 candidatas para que otros dispositivos se conecten.
     * Filtra: interfaz activa, no virtual, no loopback; dirección IPv4, no loopback, no link-local.
     */
    private static List<String> detectarIpsLan() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || ni.isPointToPoint()) continue;
                } catch (Exception ignored) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isAnyLocalAddress()
                            && !addr.isLinkLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (SecurityException se) {
            // Sin permisos para enumerar interfaces (poco común en Java de escritorio)
        } catch (Exception e) {
            // Cualquier otro fallo de enumeración
        }
        // Orden sencillo: prioriza privadas típicas (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        ips.sort((a, b) -> {
            int pa = prioridadPrivada(a);
            int pb = prioridadPrivada(b);
            if (pa != pb) return Integer.compare(pa, pb);
            return a.compareTo(b);
        });
        return ips;
    }

    private static int prioridadPrivada(String ip) {
        if (ip.startsWith("192.168.")) return 0;
        if (ip.startsWith("10.")) return 1;
        // 172.16.0.0 – 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                int seg = Integer.parseInt(ip.split("\\.")[1]);
                if (seg >= 16 && seg <= 31) return 2;
            } catch (Exception ignored) {}
        }
        return 3; // otras (menos probables como salida LAN)
    }
}
