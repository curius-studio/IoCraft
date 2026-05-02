package com.curius.iocraft.mensajeria;

import com.curius.iocraft.net.InboxS2C;
import com.curius.iocraft.blocks.puerta.logic.PuertaMensajes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;
import com.curius.iocraft.security.AuthManager;

public class RegistroManejadores {
    private static final Logger LOGGER = LogManager.getLogger("REG-MANEJADORES");

    public static void registrarPorDefecto() {

        // --- SENSOR: usa level/pos desde el Contexto; el JSON trae {"data":"..."} o {"data":{"text":"..."}} ---
        MensajeriaBus.registrarManejador("sensor", (msg, ctx) -> {

            // Requiere rol 'sensor' para aceptar/mostrar
            if (!AuthManager.hasRole(msg.from(), "sensor")) {
                // LOGGER.warn("[AUTH] Sensor bloqueado de {}: sin rol 'sensor'", msg.from());
                return;
            }

            ServerLevel level = ctx.level();
            BlockPos    pos   = ctx.pos();
            if (level == null || pos == null) {
                // LOGGER.warn("[sensor] No se pudo determinar nivel/pos del mensaje. data={}", msg.data());
                return;
            }

            // === Parseo ROBUSTO del contenido ===
            String data = null;
            JsonObject d = msg.data();
            if (d != null) {
                // data puede ser objeto con "text" o primitivo string
                if (d.has("text") && !d.get("text").isJsonNull()) {
                    data = d.get("text").getAsString();
                } else if (d.has("data")) {
                    JsonElement inner = d.get("data");
                    if (inner.isJsonPrimitive()) {
                        data = inner.getAsString();
                    } else if (inner.isJsonObject() && inner.getAsJsonObject().has("text")) {
                        data = inner.getAsJsonObject().get("text").getAsString();
                    }
                }
            }
            // Fallbacks (por si tu Mensaje expone texto legacy)
            if (data == null && msg.texto() != null) data = msg.texto();
            if (data == null) data = "";

            String device = (ctx.device() != null) ? ctx.device()
                    : (msg.from() != null ? msg.from().toString() : "remoto");

            String mundo = level.dimension().location().getPath(); // overworld|the_nether|the_end

            // 1) Pintar en GUI (Origen/Dato) — solo si tiene 'sensor'
            InboxS2C.send(level, pos, device, "sensor", data, mundo);

            // 2) Actuar en bloques (puerta) solo si además tiene 'actuator'
            if (AuthManager.hasRole(msg.from(), "actuator")) {
                PuertaMensajes.procesar(level, pos, device, data);
            } else {
                // LOGGER.warn("[AUTH] Actuación bloqueada de {}: sin rol 'actuator'", msg.from());
            }
        });

        // --- texto (opcional) ---
        MensajeriaBus.registrarManejador("texto", (msg, ctx) -> {
            // LOGGER.info("[texto] {} -> {}", msg.from(), msg.contenidoComoTexto());
        });

        // --- ping (opcional) ---
        MensajeriaBus.registrarManejador("ping", (msg, ctx) -> {
            JsonObject data = new JsonObject();
            data.addProperty("reply", "pong");
            MensajeriaBus.enviarTipado(msg.from(), "pong", data);
        });

        // --- echo (opcional) ---
        MensajeriaBus.registrarManejador("echo", (msg, ctx) -> {
            MensajeriaBus.enviarTipado(msg.from(), "echo", msg.data());
        });

        // Handshake hello (autenticación)
        MensajeriaBus.registrarManejador("hello", (msg, ctx) -> {
            var resp = com.curius.iocraft.security.AuthManager.processHello(msg);
            MensajeriaBus.responderTipado(msg, "hello/ack", resp);
        });

        // === CMD / COMANDO (alias) ===
        BiConsumer<Mensaje, MensajeriaBus.Contexto> cmdHandler = (msg, c) -> {

            // Requiere 'cmd'
            if (!AuthManager.hasRole(msg.from(), "cmd")) {
                // LOGGER.warn("[AUTH] Comando bloqueado de {}: sin rol 'cmd'", msg.from());
                return;
            }

            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || msg.from() == null) return;

            String comando = null;
            JsonObject d = msg.data();
            if (d != null) {
                if (d.has("text") && !d.get("text").isJsonNull()) {
                    comando = d.get("text").getAsString();
                } else if (d.has("data") && d.get("data").isJsonPrimitive()) {
                    comando = d.get("data").getAsString();
                }
            }
            if (comando == null && msg.texto() != null) {
                comando = msg.texto();
            }
            if (comando == null || comando.isBlank()) return;

            var src = new WsCommandSource(server);
            server.getCommands().performCommand(src.asStack(), comando);
            var out = src.drainOutput();

            String destino = msg.from().toString();
            String quoted  = quoteForBrigadier(out == null ? "" : out);
            String cmd = "/ioc enviar " + destino + " " + quoted;
            server.getCommands().performCommand(server.createCommandSourceStack(), cmd);
        };

        MensajeriaBus.registrarManejador("cmd", cmdHandler);
        MensajeriaBus.registrarManejador("comando", cmdHandler); // alias en español
    }

    public static void limpiar() {
        MensajeriaBus.limpiarManejadores();
    }

    private static String quoteForBrigadier(String s) {
        String esc = (s == null ? "" : s)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "\"" + esc + "\"";
    }
}
