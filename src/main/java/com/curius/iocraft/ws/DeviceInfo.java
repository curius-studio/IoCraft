package com.curius.iocraft.ws;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

public class DeviceInfo {
    public final UUID id;
    public final String nombre;
    public final String ip;
    public final int puerto;
    public final Instant conectadoEn;

    public DeviceInfo(UUID id, String nombre, InetSocketAddress addr) {
        this.id = id;
        this.nombre = (nombre == null || nombre.isBlank())
                ? (addr != null ? addr.getHostString() : "desconocido")
                : nombre;
        this.ip = addr != null ? addr.getHostString() : "";
        this.puerto = addr != null ? addr.getPort() : -1;
        this.conectadoEn = Instant.now();
    }
}
