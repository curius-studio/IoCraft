package com.curius.iocraft.mensajeria;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.UUID;

/**
 * # ProtocoloMensajeria
 * Encapsula las reglas de parseo/serialización:
 * - Entrante: si es JSON con "type", se considera tipado; en otro caso, texto.
 * - Saliente: si es tipado, arma JSON; si es texto, sale texto plano.
 *
 * Estructura JSON esperada:
 * {
 *   "type": "nombreTipo",
 *   "data": { ... },      // opcional
 *   "to":   "uuid",       // opcional
 *   "from": "uuid"        // opcional
 * }
 */
public class ProtocoloMensajeria {
    private static final Gson GSON = new Gson();

    /** Intenta parsear raw → Mensaje; si no es JSON válido con "type", cae a texto. */
    public static Mensaje parse(UUID from, String raw) {
        try {
            JsonObject obj = GSON.fromJson(raw, JsonObject.class);
            if (obj != null && obj.has("type")) {
                String tipo = obj.get("type").getAsString();

                JsonObject data = null;
                if (obj.has("data")) {
                    JsonElement de = obj.get("data");
                    if (de.isJsonObject()) {
                        data = de.getAsJsonObject();
                    } else if (de.isJsonPrimitive() && de.getAsJsonPrimitive().isString()) {
                        // <-- barra libre: data como string
                        JsonObject wrap = new JsonObject();
                        wrap.addProperty("text", de.getAsString());
                        data = wrap;
                    }
                } else {
                    // Soporta JSON plano: {"type":"hello","device":"...","ts":...}
                    JsonObject flat = obj.deepCopy();
                    flat.remove("type");
                    flat.remove("to");
                    flat.remove("from");
                    data = flat;
                }

                UUID to = obj.has("to") ? parseUUIDSafe(obj.get("to").getAsString()) : null;
                UUID fr = obj.has("from") ? parseUUIDSafe(obj.get("from").getAsString()) : from;
                return Mensaje.crearTipado(tipo, data, to, fr);
            }
        } catch (JsonParseException ignored) {}
        return Mensaje.crearTexto(raw, null, from);
    }

    /** Serializa un mensaje a texto para el socket. */
    public static String serializar(Mensaje msg) {
        if ("texto".equals(msg.tipo())) {
            return msg.texto() != null ? msg.texto() : "";
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", msg.tipo());
        if (msg.data() != null) obj.add("data", msg.data());
        if (msg.to() != null) obj.addProperty("to", msg.to().toString());
        if (msg.from() != null) obj.addProperty("from", msg.from().toString());
        return GSON.toJson(obj);
    }

    private static UUID parseUUIDSafe(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
