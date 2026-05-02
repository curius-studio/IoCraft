package com.curius.iocraft.ws;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;                           // <-- y este

import java.util.UUID;

public class WsManager {
    private static final Logger LOGGER = LogManager.getLogger("WS-MANAGER");
    private static LanWebSocketServer server;
    private static int port = 8765; // provisional
    private static String host = "0.0.0.0";

    public static synchronized void start() {
        if (server != null) return;
        try {
            server = new LanWebSocketServer(host, port);
            // Arrancamos en hilo aparte para no bloquear el setup
            new Thread(() -> {
                try {
                    server.start();
                } catch (InterruptedException e) {
                    LOGGER.error("No se pudo iniciar WS Netty: {}", e.toString(), e);
                }
            }, "ws-netty-start").start();

            LOGGER.info("WS Manager: escuchando en ws://{}:{}", host, port);
        } catch (Exception e) {
            LOGGER.error("No se pudo iniciar WS: {}", e.toString(), e);
        }
    }

    public static synchronized void stop() {
        if (server == null) return;
        try {
            server.stop();
        } catch (Exception ignored) {}
        server = null;
        DeviceRegistry.clear();
        LOGGER.info("WS Manager: detenido");
    }

    public static synchronized void close(UUID id) {
        if (server == null) return;
        server.close(id);
    }


    public static synchronized boolean send(UUID id, String text) {
        return server != null && server.sendTo(id, text);
    }

    /** Envía un texto a todos los dispositivos conectados. Devuelve cuántos lo recibieron. */
    public static synchronized int broadcast(String text) {
        if (server == null) return 0;
        int ok = 0;
        List<DeviceInfo> vivos = DeviceRegistry.snapshot();
        for (DeviceInfo d : vivos) {
            if (send(d.id, text)) ok++;
        }
        return ok;
    }



    public static int getPort() { return port; }
    public static void setPort(int p) { port = p; }
    public static String getHost() { return host; }
    public static void setHost(String h) { host = h; }
}
