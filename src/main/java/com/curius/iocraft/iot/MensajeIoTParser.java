package com.curius.iocraft.iot;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class MensajeIoTParser {

    private static final Gson gson = new Gson();

    /** Objeto resultante del parseo */
    public static class MensajeIoT {
        public String type;     // "sensor" | "interruptor" | "comando"
        public String mundo;    // "minecraft:overworld", "minecraft:nether", "minecraft:the_end"
        public int x;
        public int y;
        public int z;
        public String data;     // Valor o comando (si vino objeto, lo serializamos a texto)
        public String device;   // Nombre del dispositivo
    }

    /**
     * Parsea un string JSON recibido por WebSocket y lo convierte en MensajeIoT.
     * @param raw Texto recibido desde el dispositivo.
     * @return MensajeIoT o null si el formato es inválido o si es un tipo que este parser no maneja (p.ej. "hello").
     */
    public static MensajeIoT parse(String raw) {
        try {
            JsonObject json = gson.fromJson(raw, JsonObject.class);
            if (json == null || !json.has("type")) {
                System.err.println("[IoTParser] Mensaje inválido: falta campo 'type'");
                return null;
            }

            // Si es "hello", NO lo procesa este parser (lo maneja MensajeriaBus/AuthManager)
            String type = json.get("type").getAsString();
            if ("hello".equalsIgnoreCase(type)) {
                return null; // ignoramos silenciosamente para no ensuciar el log
            }

            MensajeIoT msg = new MensajeIoT();
            msg.type  = type;
            msg.mundo = json.has("mundo") ? json.get("mundo").getAsString() : "overworld";
            msg.x     = json.has("x") ? json.get("x").getAsInt() : 0;
            msg.y     = json.has("y") ? json.get("y").getAsInt() : 0;
            msg.z     = json.has("z") ? json.get("z").getAsInt() : 0;

            // data puede ser string u objeto: soportar ambos
            if (json.has("data") && !json.get("data").isJsonNull()) {
                JsonElement de = json.get("data");
                if (de.isJsonPrimitive()) {
                    // Caso legado: data es string
                    msg.data = de.getAsString();
                } else if (de.isJsonObject()) {
                    // Si es objeto y trae "text" como primitivo, úsalo; si no, serializa el objeto
                    JsonObject dobj = de.getAsJsonObject();
                    if (dobj.has("text") && dobj.get("text").isJsonPrimitive()) {
                        msg.data = dobj.get("text").getAsString();
                    } else {
                        msg.data = dobj.toString();
                    }
                } else {
                    // arrays u otros: serializar
                    msg.data = de.toString();
                }
            } else {
                msg.data = "";
            }

            msg.device = json.has("device") ? json.get("device").getAsString() : "desconocido";

            // Normalización mundo a formato Minecraft
            if (!msg.mundo.startsWith("minecraft:")) {
                msg.mundo = "minecraft:" + msg.mundo;
            }

            // Validar type que SÍ maneja este parser
            if (!msg.type.equals("sensor") && !msg.type.equals("interruptor") && !msg.type.equals("comando")) {
                // Otros tipos (p.ej. "pong", etc.) no son de este parser: ignorar sin error
                return null;
            }

            return msg;

        } catch (JsonSyntaxException e) {
            System.err.println("[IoTParser] Error parseando JSON: " + e.getMessage());
            return null;
        } catch (UnsupportedOperationException e) {
            // Por si algún getAsX vuelve a quejarse, devolver null en vez de romper el pipeline
            System.err.println("[IoTParser] Tipo inesperado en 'data': " + e.getMessage());
            return null;
        }
    }
}
