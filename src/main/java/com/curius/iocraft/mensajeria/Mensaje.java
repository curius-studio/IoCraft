package com.curius.iocraft.mensajeria;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * # Mensaje
 * Representa un mensaje entrante/saliente en la capa de mensajería.
 *
 * Dos formas:
 * - Texto plano:  tipo="texto", texto != null, data == null
 * - JSON tipado:  tipo="<algo>", data != null, texto == null
 */
public final class Mensaje {
    private final String tipo;
    private final JsonObject data;  // nulo si es texto
    private final String texto;     // nulo si es tipado
    private final UUID to;
    private final UUID from;

    private Mensaje(String tipo, JsonObject data, String texto, UUID to, UUID from) {
        this.tipo = tipo;
        this.data = data;
        this.texto = texto;
        this.to = to;
        this.from = from;
    }

    // Fábricas

    public static Mensaje crearTexto(String texto, UUID to, UUID from) {
        return new Mensaje("texto", null, texto, to, from);
    }

    public static Mensaje crearTipado(String tipo, JsonObject data, UUID to, UUID from) {
        return new Mensaje(tipo, data, null, to, from);
    }

    // Getters

    public String tipo() { return tipo; }
    public JsonObject data() { return data; }
    public String texto() { return texto; }
    public UUID to() { return to; }
    public UUID from() { return from; }

    // Helpers

    /** Devuelve contenido como texto (si es tipado, compone "type:data"). */
    public String contenidoComoTexto() {
        if (texto != null) return texto;
        return data != null ? ("<" + tipo + "> " + data.toString()) : "";
    }
}
