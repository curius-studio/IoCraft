package com.curius.iocraft.mensajeria;

import com.curius.iocraft.api.events.IoCraftMessagePostProcessEvent;
import com.curius.iocraft.api.events.IoCraftMessagePreProcessEvent;
import com.curius.iocraft.ws.WsManager;
import com.curius.iocraft.ws.DeviceRegistry;
import com.curius.iocraft.ws.DeviceInfo;
import com.curius.iocraft.security.AddonPolicyManager;
import com.curius.iocraft.security.AuthManager;
import com.curius.iocraft.security.AuthManager.SessionInfo;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Objects;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import net.minecraftforge.common.MinecraftForge;

/**
 * # MensajeriaBus
 * - Recibe frames entrantes (WS) → {@link Mensaje} y los enruta por "tipo".
 * - Utilidades para enviar/responder (texto/JSON tipado) a 1 o todos.
 * - Permite publicar mensajes internamente con un {@link Contexto} (nivel/pos/device).
 *
 * Soporta:
 *  - Texto plano (tipo="texto")
 *  - JSON tipado: {"type":"xxx","data":{...},"to":"...","from":"..."}
 */
public final class MensajeriaBus {
    private static final Logger LOGGER = LogManager.getLogger("MENSAJERIA-BUS");

    /** tipo → handlers ordenados por prioridad DESC y orden de registro ASC */
    private static final Map<String, CopyOnWriteArrayList<HandlerEntry>> HANDLERS = new ConcurrentHashMap<>();
    private static final AtomicLong HANDLER_SEQ = new AtomicLong(0);
    private static final int PRIORITY_MIN = -1000;
    private static final int PRIORITY_MAX = 1000;
    private static final long SLOW_HANDLER_WARN_NS = 50_000_000L; // 50ms
    private static final Map<String, OwnerMetrics> OWNER_METRICS = new ConcurrentHashMap<>();

    /** Tipo del mensaje de handshake (debe coincidir con AuthManager.isMessageAllowed). */
    private static final String TIPO_AUTH_HELLO = "hello";

    private static final class HandlerEntry {
        final String ownerModId;
        final int priority;
        final long order;
        final BiConsumer<Mensaje, Contexto> handler;

        HandlerEntry(String ownerModId, int priority, long order, BiConsumer<Mensaje, Contexto> handler) {
            this.ownerModId = ownerModId;
            this.priority = priority;
            this.order = order;
            this.handler = handler;
        }
    }

    private static final class TypeMetrics {
        final AtomicLong handled = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final AtomicLong slow = new AtomicLong();
        final AtomicLong totalTimeNs = new AtomicLong();
    }

    private static final class OwnerMetrics {
        final AtomicLong handled = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        final AtomicLong slow = new AtomicLong();
        final AtomicLong totalTimeNs = new AtomicLong();
        final Map<String, TypeMetrics> byType = new ConcurrentHashMap<>();
    }

    public record TypeMetricSnapshot(
            String type,
            long handled,
            long errors,
            long slow,
            double avgMs
    ) {}

    public record OwnerMetricSnapshot(
            String ownerModId,
            int activeHandlers,
            long handled,
            long errors,
            long slow,
            double avgMs,
            Map<String, TypeMetricSnapshot> byType
    ) {}

    /**
     * Contexto para handlers: desde dónde/qué bloque del mundo provino el mensaje,
     * y el nombre del dispositivo lógico que lo originó (si se conoce).
     */
    public static final class Contexto {
        private final ServerLevel level;   // puede ser null
        private final BlockPos pos;        // puede ser null
        private final String device;       // puede ser null

        private Contexto(ServerLevel level, BlockPos pos, String device) {
            this.level  = level;
            this.pos    = (pos == null ? null : pos.immutable());
            this.device = device;
        }

        /** Crea un contexto con nivel/pos/device (cualquiera puede ser null). */
        public static Contexto of(ServerLevel level, BlockPos pos, String device) {
            return new Contexto(level, pos, device);
        }

        /** Contexto vacío (sin nivel/pos/device). */
        public static Contexto vacio() { return new Contexto(null, null, null); }

        /** Mundo del servidor (puede ser null). */
        public ServerLevel level() { return level; }

        /** Posición del bloque en el mundo (puede ser null). */
        public BlockPos pos() { return pos; }

        /** Nombre del dispositivo lógico/remitente (puede ser null). */
        public String device() { return device; }
    }

    private MensajeriaBus() {}

    // ---------------------------
    // 1) Registro de manejadores
    // ---------------------------

    /** Registra o reemplaza el manejador para un tipo dado (ej: "ping", "comando", "sensor", "texto"). */
    public static void registrarManejador(String tipo, BiConsumer<Mensaje, Contexto> handler) {
        String t = Objects.requireNonNull(tipo, "tipo");
        BiConsumer<Mensaje, Contexto> h = Objects.requireNonNull(handler, "handler");
        // Compatibilidad con versión actual: este método reemplaza handlers existentes del tipo.
        CopyOnWriteArrayList<HandlerEntry> list = new CopyOnWriteArrayList<>();
        list.add(new HandlerEntry("iocraft-core", 0, HANDLER_SEQ.incrementAndGet(), h));
        HANDLERS.put(t, list);
    }

    /** Registra un manejador externo sin reemplazar otros, con prioridad y ownerModId. */
    public static void registrarManejadorExterno(String tipo, int prioridad, String ownerModId, BiConsumer<Mensaje, Contexto> handler) {
        String t = Objects.requireNonNull(tipo, "tipo");
        String owner = Objects.requireNonNull(ownerModId, "ownerModId").trim();
        BiConsumer<Mensaje, Contexto> h = Objects.requireNonNull(handler, "handler");
        if (owner.isEmpty()) throw new IllegalArgumentException("ownerModId vacío");
        int p = Math.max(PRIORITY_MIN, Math.min(PRIORITY_MAX, prioridad));

        CopyOnWriteArrayList<HandlerEntry> list = HANDLERS.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>());
        // Reemplaza registro previo de ese owner en ese tipo para evitar duplicados accidentales.
        list.removeIf(e -> owner.equalsIgnoreCase(e.ownerModId));
        list.add(new HandlerEntry(owner, p, HANDLER_SEQ.incrementAndGet(), h));
        list.sort(Comparator
                .comparingInt((HandlerEntry e) -> e.priority).reversed()
                .thenComparingLong(e -> e.order));
    }

    /** Desregistra el manejador de un tipo. */
    public static void desregistrarManejador(String tipo) {
        HANDLERS.remove(Objects.requireNonNull(tipo, "tipo"));
    }

    /** Desregistra el manejador de un owner en un tipo concreto. */
    public static void desregistrarManejadorExterno(String tipo, String ownerModId) {
        String t = Objects.requireNonNull(tipo, "tipo");
        String owner = Objects.requireNonNull(ownerModId, "ownerModId").trim();
        CopyOnWriteArrayList<HandlerEntry> list = HANDLERS.get(t);
        if (list == null) return;
        list.removeIf(e -> owner.equalsIgnoreCase(e.ownerModId));
        if (list.isEmpty()) HANDLERS.remove(t);
    }

    /** Limpia todos los manejadores. */
    public static void limpiarManejadores() {
        HANDLERS.clear();
        OWNER_METRICS.clear();
    }

    /** ¿Hay manejador para un tipo? Útil para tests/diagnóstico. */
    public static boolean tieneManejador(String tipo) {
        List<HandlerEntry> list = HANDLERS.get(tipo);
        return list != null && !list.isEmpty();
    }

    // -----------------------
    // 2) Entrada de mensajes
    // -----------------------

    /** Llamado por el server WS cuando llega un frame de texto. */
    public static void onReceive(UUID from, String raw) {
        onReceive(from, raw, Contexto.vacio());
    }

    /** Igual a {@link #onReceive(UUID, String)} pero permitiendo inyectar un {@link Contexto}. */
    public static void onReceive(UUID from, String raw, Contexto ctx) {
        if (raw == null) {
            LOGGER.warn("[MSG] reject reason=raw_null from={}", from);
            return;
        }

        Mensaje msg = ProtocoloMensajeria.parse(from, raw);

        // ===== AUTH: handshake y gateo por sesión =====
        if (TIPO_AUTH_HELLO.equalsIgnoreCase(msg.tipo())) {
            // Procesar handshake; responder al cliente con el resultado
            JsonObject resp = AuthManager.processHello(msg);
            // respondemos con un tipo de ack (no afecta tus tipos de juego)
            responderTipado(msg, "hello/ack", resp);
            return; // no propagar a handlers
        }

        if (AuthManager.REQUIRE_AUTH && !AuthManager.isAuthenticated(from)) {
            LOGGER.warn("[AUTH] reject reason=unauthenticated from={} type={}", from, msg.tipo());
            return;
        }

        // Inyectar device en contexto si hay sesión y no vino seteado
        Contexto base = (ctx == null ? Contexto.vacio() : ctx);
        SessionInfo s = AuthManager.getSession(from);
        if (s != null && base.device() == null) {
            ctx = Contexto.of(base.level(), base.pos(), s.deviceId);
        } else {
            ctx = base;
        }
        // ===== FIN AUTH =====

        IoCraftMessagePreProcessEvent pre = new IoCraftMessagePreProcessEvent(
                msg, ctx.level(), ctx.pos(), ctx.device()
        );
        if (MinecraftForge.EVENT_BUS.post(pre)) {
            return;
        }
        Mensaje msgEff = pre.getMessage();
        Contexto ctxEff = Contexto.of(pre.getLevel(), pre.getPos(), pre.getDevice());

        dispatch(msgEff, ctxEff);

        MinecraftForge.EVENT_BUS.post(new IoCraftMessagePostProcessEvent(
                msgEff, ctxEff.level(), ctxEff.pos(), ctxEff.device()
        ));
    }

    // ------------------------
    // 2b) Publicación interna
    // ------------------------

    /** Enruta directamente un {@link Mensaje} ya construido, con contexto. */
    public static void publicar(Mensaje msg, Contexto ctx) {
        if (msg == null) return;
        dispatch(msg, (ctx == null ? Contexto.vacio() : ctx));
    }

    /**
     * Publica un mensaje TIPADO (JSON) hacia los handlers, sin enviar por WS.
     * No cambia el formato del JSON: tú pasas el `data` que quieras (p.ej. {"text":"abre"}).
     */
    public static void publicarTipado(String tipo, JsonObject data, UUID from, Contexto ctx) {
        Mensaje m = Mensaje.crearTipado(tipo, data, from, null);
        dispatch(m, (ctx == null ? Contexto.vacio() : ctx));
    }

    /**
     * Publica un mensaje "sensor" o similar a partir de un texto simple.
     * Internamente arma un data {"text": "..."} para respetar tu JSON actual.
     */
    public static void publicarTextoComoData(String tipo, String texto, UUID from, Contexto ctx) {
        JsonObject data = new JsonObject();
        data.addProperty("text", texto != null ? texto : "");
        publicarTipado(tipo, data, from, ctx);
    }

    /** Enruta a su handler según el tipo, con try/catch y logging. */
    private static void dispatch(Mensaje msg, Contexto ctx) {
        if (msg == null) return;

        // 1) Torniquete: solo dejamos pasar "hello" sin sesión
        if (!AuthManager.isMessageAllowed(msg.tipo(), msg.from())) {
            JsonObject err = new JsonObject();
            err.addProperty("ok", false);
            err.addProperty("code", "unauthorized");
            err.addProperty("message", "Debes autenticar con 'hello' primero.");
            MensajeriaBus.responderTipado(msg, "error", err);
            LOGGER.warn("[AUTH] reject reason=unauthorized from={} type={}", msg.from(), msg.tipo());
            return;
        }

        List<HandlerEntry> handlers = HANDLERS.get(msg.tipo());
        if (handlers == null || handlers.isEmpty()) {
            LOGGER.info("[MSG] no_handler type={} payload={}", msg.tipo(), msg.contenidoComoTexto());
            return;
        }

        Contexto effectiveCtx = (ctx == null ? Contexto.vacio() : ctx);
        for (HandlerEntry entry : handlers) {
            AddonPolicyManager.PolicyDecision policy = AddonPolicyManager.canExecute(entry.ownerModId, msg.tipo());
            if (!policy.allowed()) {
                LOGGER.warn("[ADDON-POLICY] blocked type={} owner={} reason={}",
                        msg.tipo(), entry.ownerModId, policy.reason());
                continue;
            }

            long started = System.nanoTime();
            boolean failed = false;
            try {
                entry.handler.accept(msg, effectiveCtx);
            } catch (Throwable t) {
                failed = true;
                LOGGER.error("[MSG] handler_error type={} owner={} err={}",
                        msg.tipo(), entry.ownerModId, t.toString(), t);
            } finally {
                long elapsed = System.nanoTime() - started;
                registrarMetricas(entry.ownerModId, msg.tipo(), elapsed, failed);
                if (elapsed > SLOW_HANDLER_WARN_NS) {
                    LOGGER.warn("[MSG] handler_slow type={} owner={} elapsedMs={}",
                            msg.tipo(), entry.ownerModId, (elapsed / 1_000_000.0));
                }
                AddonPolicyManager.recordExecution(entry.ownerModId, failed, elapsed > SLOW_HANDLER_WARN_NS);
            }
        }
    }

    private static void registrarMetricas(String ownerModId, String tipo, long elapsedNs, boolean failed) {
        String owner = (ownerModId == null || ownerModId.isBlank()) ? "unknown" : ownerModId;
        String type = (tipo == null || tipo.isBlank()) ? "<null>" : tipo;

        OwnerMetrics ownerMetrics = OWNER_METRICS.computeIfAbsent(owner, k -> new OwnerMetrics());
        TypeMetrics typeMetrics = ownerMetrics.byType.computeIfAbsent(type, k -> new TypeMetrics());

        ownerMetrics.handled.incrementAndGet();
        ownerMetrics.totalTimeNs.addAndGet(elapsedNs);
        typeMetrics.handled.incrementAndGet();
        typeMetrics.totalTimeNs.addAndGet(elapsedNs);

        if (failed) {
            ownerMetrics.errors.incrementAndGet();
            typeMetrics.errors.incrementAndGet();
        }
        if (elapsedNs > SLOW_HANDLER_WARN_NS) {
            ownerMetrics.slow.incrementAndGet();
            typeMetrics.slow.incrementAndGet();
        }
    }

    /** Snapshot de métricas por owner y por tipo para diagnóstico en runtime. */
    public static List<OwnerMetricSnapshot> snapshotOwnerMetrics() {
        return snapshotOwnerMetrics(null);
    }

    /** Snapshot de métricas por owner (opcionalmente filtrado por ownerModId). */
    public static List<OwnerMetricSnapshot> snapshotOwnerMetrics(String ownerFilter) {
        Map<String, Integer> activeHandlers = new HashMap<>();
        for (Map.Entry<String, CopyOnWriteArrayList<HandlerEntry>> e : HANDLERS.entrySet()) {
            for (HandlerEntry he : e.getValue()) {
                activeHandlers.merge(he.ownerModId, 1, Integer::sum);
            }
        }

        String ownerWanted = (ownerFilter == null) ? null : ownerFilter.trim();
        if (ownerWanted != null && ownerWanted.isEmpty()) ownerWanted = null;

        Map<String, OwnerMetricSnapshot> out = new HashMap<>();
        for (Map.Entry<String, OwnerMetrics> e : OWNER_METRICS.entrySet()) {
            String owner = e.getKey();
            if (ownerWanted != null && !owner.equalsIgnoreCase(ownerWanted)) continue;
            OwnerMetrics m = e.getValue();
            Map<String, TypeMetricSnapshot> typeSnapshots = new HashMap<>();
            for (Map.Entry<String, TypeMetrics> tm : m.byType.entrySet()) {
                TypeMetrics t = tm.getValue();
                long handled = t.handled.get();
                double avgMs = handled == 0 ? 0.0 : (t.totalTimeNs.get() / 1_000_000.0) / handled;
                typeSnapshots.put(tm.getKey(), new TypeMetricSnapshot(
                        tm.getKey(),
                        handled,
                        t.errors.get(),
                        t.slow.get(),
                        avgMs
                ));
            }
            long handled = m.handled.get();
            double avgMs = handled == 0 ? 0.0 : (m.totalTimeNs.get() / 1_000_000.0) / handled;
            out.put(owner, new OwnerMetricSnapshot(
                    owner,
                    activeHandlers.getOrDefault(owner, 0),
                    handled,
                    m.errors.get(),
                    m.slow.get(),
                    avgMs,
                    Map.copyOf(typeSnapshots)
            ));
        }

        for (Map.Entry<String, Integer> ownerHandlers : activeHandlers.entrySet()) {
            String owner = ownerHandlers.getKey();
            if (ownerWanted != null && !owner.equalsIgnoreCase(ownerWanted)) continue;
            out.computeIfAbsent(owner, k -> new OwnerMetricSnapshot(
                    owner,
                    ownerHandlers.getValue(),
                    0,
                    0,
                    0,
                    0.0,
                    Map.of()
            ));
        }

        return out.values().stream()
                .sorted(Comparator.comparing(OwnerMetricSnapshot::ownerModId))
                .toList();
    }

    /** Limpia todas las métricas acumuladas de handlers externos. */
    public static void clearOwnerMetrics() {
        OWNER_METRICS.clear();
    }

    /** Limpia métricas de un owner específico. */
    public static boolean clearOwnerMetrics(String ownerModId) {
        String owner = Objects.requireNonNull(ownerModId, "ownerModId").trim();
        if (owner.isEmpty()) throw new IllegalArgumentException("ownerModId vacío");
        return OWNER_METRICS.remove(owner) != null;
    }


    // ---------------------
    // 3) Salida de mensajes
    // ---------------------

    /** Envía **texto plano** a un dispositivo específico (vía WS). */
    public static boolean enviarTexto(UUID to, String texto) {
        if (to == null) {
            LOGGER.warn("[MSG] send_text_failed reason=destination_null");
            return false;
        }
        return WsManager.send(to, texto != null ? texto : "");
    }

    /** Envía **JSON tipado** a un dispositivo específico (vía WS). */
    public static boolean enviarTipado(UUID to, String tipo, JsonObject data) {
        if (to == null) {
            LOGGER.warn("[MSG] send_typed_failed reason=destination_null type={}", tipo);
            return false;
        }
        String payload = ProtocoloMensajeria.serializar(Mensaje.crearTipado(tipo, data, to, null));
        return WsManager.send(to, payload);
    }

    /** Helper: responde al **emisor** de un mensaje con **texto plano** (vía WS). */
    public static boolean responderTexto(Mensaje msg, String texto) {
        if (msg == null || msg.from() == null) {
            LOGGER.warn("[MSG] reply_text_failed reason=message_or_from_null");
            return false;
        }
        return enviarTexto(msg.from(), texto);
    }

    /** Helper: responde al **emisor** de un mensaje con **JSON tipado** (vía WS). */
    public static boolean responderTipado(Mensaje msg, String tipo, JsonObject data) {
        if (msg == null || msg.from() == null) {
            LOGGER.warn("[MSG] reply_typed_failed reason=message_or_from_null type={}", tipo);
            return false;
        }
        return enviarTipado(msg.from(), tipo, data);
    }

    /** Broadcast de **texto plano** a todos (vía WS). Devuelve cuántos lo recibieron. */
    public static int broadcastTexto(String texto) {
        return WsManager.broadcast(texto != null ? texto : "");
    }

    /** Broadcast **JSON tipado** a todos (vía WS). Devuelve cuántos lo recibieron. */
    public static int broadcastTipado(String tipo, JsonObject data) {
        int ok = 0;
        for (DeviceInfo d : DeviceRegistry.snapshot()) {
            if (enviarTipado(d.id, tipo, data)) ok++;
        }
        return ok;
    }
}
