package com.curius.iocraft.security;

import com.curius.iocraft.api.events.IoCraftAuthResultEvent;
import com.curius.iocraft.mensajeria.Mensaje;
import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.common.MinecraftForge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthManager {
    private static final Logger LOGGER = LogManager.getLogger("AUTH");

    /** Requerir sesión para cualquier mensaje que no sea "hello" */
    public static volatile boolean REQUIRE_AUTH = true;

    /** Tolerancia de reloj para el timestamp del handshake (ms) */
    private static final long ALLOWED_SKEW_MS = 60_000L; // 60s

    /** TTL de sesión (ms) */
    private static final long SESSION_TTL_MS = 10 * 60_000L; // 10 min

    /** Ventana anti-replay para nonces del hello (ms) */
    private static final long NONCE_WINDOW_MS = 2 * 60_000L; // 2 min

    /** connId (UUID WS) -> sesión */
    private static final Map<UUID, SessionInfo> SESSIONS = new ConcurrentHashMap<>();

    /** deviceId(normalizado) -> secret persistido */
    private static final Map<String, String> SECRETS = new ConcurrentHashMap<>();

    /** deviceId(normalizado) -> roles persistidos básicos (para guardar en disco) */
    private static final Map<String, Set<String>> ROLES = new ConcurrentHashMap<>();

    /** deviceId(normalizado) -> roles efectivos del servidor (autoritativos) */
    private static final Map<String, Set<String>> DEVICE_ROLES = new ConcurrentHashMap<>();

    /** nonce usado por device en handshake: "device|nonce" -> expiresAt */
    private static final Map<String, Long> USED_NONCES = new ConcurrentHashMap<>();

    /** Persistencia */
    private static final SecureRandom RNG = new SecureRandom();
    private static Path STORE_FILE; // se setea en init

    private AuthManager() {}

    // ======== Normalización ========

    private static String norm(String s) {
        return (s == null) ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    // ======== Init / Persistencia ========

    /** Llama una vez al iniciar el servidor o durante el setup común. */
    public static void initPersistence(Path storeFile) {
        STORE_FILE = storeFile;
        loadFromDisk(); // intenta cargar, si no existe sigue vacío
        // sincroniza DEVICE_ROLES con lo que había en disco
        DEVICE_ROLES.clear();
        ROLES.forEach((k,v) -> DEVICE_ROLES.put(k, Set.copyOf(v)));
        LOGGER.info("[AUTH] Store cargado. devices={}", SECRETS.size());
    }

    public static synchronized void loadFromDisk() {
        SECRETS.clear();
        ROLES.clear();
        if (STORE_FILE == null || !Files.exists(STORE_FILE)) return;
        try (Reader r = Files.newBufferedReader(STORE_FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("devices") && root.get("devices").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("devices")) {
                    JsonObject d = el.getAsJsonObject();
                    String idRaw = d.has("id") ? d.get("id").getAsString() : null;
                    String id = norm(idRaw);
                    String secret = d.has("secret") ? d.get("secret").getAsString() : null;
                    Set<String> roles = new HashSet<>();
                    if (d.has("roles")) {
                        JsonElement rr = d.get("roles");
                        if (rr.isJsonArray()) {
                            rr.getAsJsonArray().forEach(x -> roles.add(x.getAsString()));
                        } else {
                            for (String s : rr.getAsString().split(",")) {
                                String t = s.trim();
                                if (!t.isEmpty()) roles.add(t);
                            }
                        }
                    }
                    if (id != null && secret != null) {
                        SECRETS.put(id, secret);
                        ROLES.put(id, roles);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("No pude cargar auth store: {}", e.toString());
        }
    }

    private static synchronized void saveToDisk() {
        if (STORE_FILE == null) return;
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (var e : SECRETS.entrySet()) {
            String id = e.getKey(); // ya normalizado
            JsonObject d = new JsonObject();
            d.addProperty("id", id);
            d.addProperty("secret", e.getValue());
            JsonArray rr = new JsonArray();
            for (String r : ROLES.getOrDefault(id, Set.of())) rr.add(r);
            d.add("roles", rr);
            arr.add(d);
        }
        root.add("devices", arr);

        try {
            Files.createDirectories(STORE_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(STORE_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
        } catch (Exception e) {
            LOGGER.warn("No pude guardar auth store: {}", e.toString());
        }
    }

    // ======== API de Secrets / Roles ========

    /** Registra/actualiza el secreto de un deviceId (en memoria + persistencia). */
    public static synchronized void putSecret(String deviceId, String secret) {
        String key = norm(deviceId);
        if (key == null || secret == null) return;
        SECRETS.put(key, secret);
        ROLES.putIfAbsent(key, new HashSet<>());
        saveToDisk();
    }

    public static void clearSecrets() {
        SECRETS.clear();
    }

    public static synchronized void revokeSecret(String deviceId) {
        String key = norm(deviceId);
        if (key == null) return;
        SECRETS.remove(key);
        ROLES.remove(key);
        DEVICE_ROLES.remove(key);
        invalidateSessionsForDevice(key);
        saveToDisk();
    }

    public static synchronized boolean hasDevice(String deviceId) {
        String key = norm(deviceId);
        return key != null && SECRETS.containsKey(key);
    }

    private static String getSecret(String deviceId) {
        return SECRETS.get(norm(deviceId));
    }

    /** Genera una clave segura en HEX (64 chars ~ 256 bits) */
    public static String generateSecretHex() {
        byte[] buf = new byte[32]; // 256-bit
        RNG.nextBytes(buf);
        return bytesToHex(buf);
    }

    /** Crea o rota la clave y actualiza roles persistidos si se pasan. */
    public static synchronized String createOrRotateSecret(String deviceId, Set<String> roles, boolean rotate) {
        String key = norm(deviceId);
        if (key == null || key.isBlank()) throw new IllegalArgumentException("deviceId vacío");
        boolean exists = SECRETS.containsKey(key);
        if (!exists || rotate) {
            String secret = generateSecretHex();
            SECRETS.put(key, secret);
            if (roles != null) {
                ROLES.put(key, new HashSet<>(roles));
                DEVICE_ROLES.put(key, Set.copyOf(roles));
            }
            saveToDisk();
            invalidateSessionsForDevice(deviceId);
            return secret;
        } else {
            return SECRETS.get(key);
        }
    }

    /** Define (o reemplaza) los roles autorizados para un deviceId, persiste e invalida sesiones. */
    public static synchronized void putRoles(String deviceId, java.util.Collection<String> roles) {
        String key = norm(deviceId);
        if (key == null || key.isBlank()) return;

        // normalizar
        Set<String> set = new HashSet<>();
        if (roles != null) {
            for (String r : roles) {
                if (r != null) {
                    String rr = r.trim();
                    if (!rr.isEmpty()) set.add(rr);
                }
            }
        }
        DEVICE_ROLES.put(key, Collections.unmodifiableSet(set));
        ROLES.put(key, new HashSet<>(set));
        saveToDisk();
        invalidateSessionsForDevice(deviceId);

        LOGGER.info("[AUTH] Roles de '{}' -> {}", key, set);
    }

    /** Roles configurados en servidor (autoritativos). */
    public static Set<String> getRolesForDevice(String deviceId) {
        Set<String> r = DEVICE_ROLES.get(norm(deviceId));
        return (r != null) ? r : Set.of();
    }

    /** Roles persistidos (los del fichero), útil para inspección. */
    public static Set<String> rolesOf(String deviceId) {
        return ROLES.getOrDefault(norm(deviceId), Set.of());
    }

    private static void invalidateSessionsForDevice(String deviceIdRaw) {
        final String key = norm(deviceIdRaw);
        SESSIONS.entrySet().removeIf(e -> {
            SessionInfo s = e.getValue();
            return s != null && key.equals(norm(s.deviceId));
        });
    }

    // ======== Sesión ========

    public static final class SessionInfo {
        public final UUID connId;
        public final String deviceId;   // tal cual vino en el hello (no normalizado)
        public final Set<String> roles; // captura en ese momento
        public final long expiresAt;

        public SessionInfo(UUID connId, String deviceId, Set<String> roles, long expiresAt) {
            this.connId = connId;
            this.deviceId = deviceId;
            this.roles = roles != null ? Collections.unmodifiableSet(new HashSet<>(roles)) : Set.of();
            this.expiresAt = expiresAt;
        }

        public boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public static SessionInfo getSession(UUID connId) {
        if (connId == null) return null;
        SessionInfo s = SESSIONS.get(connId);
        if (s != null && s.expired()) {
            SESSIONS.remove(connId);
            return null;
        }
        return s;
    }

    public static boolean isAuthenticated(UUID connId) {
        return getSession(connId) != null;
    }

    /** Devuelve el deviceId autenticado para una conexión WS, o null si no hay sesión. */
    public static String getAuthenticatedDeviceId(UUID connId) {
        SessionInfo s = getSession(connId);
        return s != null ? s.deviceId : null;
    }

    /** Devuelve true si la conexión autenticada tiene el rol dado. */
    public static boolean hasRole(UUID connId, String role) {
        if (connId == null || role == null || role.isBlank()) return false;
        SessionInfo s = getSession(connId);
        if (s == null) return false;
        return hasRoleDevice(s.deviceId, role);
    }

    /** Devuelve true si el deviceId (string del hello) tiene el rol dado (server-autoritativo, case-insensitive). */
    public static boolean hasRoleDevice(String deviceId, String role) {
        if (deviceId == null || deviceId.isBlank() || role == null || role.isBlank()) return false;
        Set<String> eff = DEVICE_ROLES.get(norm(deviceId));
        if (eff == null || eff.isEmpty()) eff = ROLES.getOrDefault(norm(deviceId), Set.of());
        for (String r : eff) {
            if (role.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    public static void onDisconnect(UUID connId) {
        if (connId != null) SESSIONS.remove(connId);
    }

    /** Cuenta sesiones activas (no expiradas) para un deviceId. */
    public static synchronized int countActiveSessionsForDevice(String deviceId) {
        String key = norm(deviceId);
        if (key == null || key.isBlank()) return 0;
        long now = System.currentTimeMillis();
        int count = 0;
        Iterator<Map.Entry<UUID, SessionInfo>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SessionInfo> e = it.next();
            SessionInfo s = e.getValue();
            if (s == null || s.expiresAt <= now) {
                it.remove();
                continue;
            }
            if (key.equals(norm(s.deviceId))) count++;
        }
        return count;
    }

    // ======== Handshake ========

    /** Procesa el "hello" (handshake). */
    public static JsonObject processHello(Mensaje msg) {
        try {
            JsonObject data = null;
            try {
                if (msg.data() != null) data = msg.data();
                else {
                    String raw = msg.contenidoComoTexto();
                    data = JsonParser.parseString(raw != null ? raw : "{}").getAsJsonObject();
                }
            } catch (Throwable ignore) {}

            if (data == null) {
                return failAndPublish(msg, null, "bad_format", "Data inválida");
            }

            String device = optString(data, "device", null);
            String sig    = optString(data, "sig", null);
            String nonce  = optString(data, "nonce", null);
            long ts       = optLong(data, "ts", 0L);

            if (device == null || device.isEmpty() || sig == null || sig.isEmpty() || nonce == null || nonce.isBlank() || ts <= 0) {
                return failAndPublish(msg, device, "missing_fields", "Faltan campos device/sig/ts/nonce");
            }

            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > ALLOWED_SKEW_MS) {
                return failAndPublish(msg, device, "ts_skew", "Timestamp fuera de ventana");
            }

            String secret = getSecret(device);
            if (secret == null) {
                return failAndPublish(msg, device, "unknown_device", "Device no registrado");
            }

            String expected = hmacSha256Hex(secret, device + ":" + ts + ":" + nonce);
            if (!constantTimeEquals(expected, sig)) {
                return failAndPublish(msg, device, "bad_sig", "Firma inválida");
            }

            if (!registerNonce(device, nonce, now)) {
                return failAndPublish(msg, device, "replay_nonce", "Nonce reutilizado");
            }

            // Roles efectivos: prioriza configurados en servidor. Si no hay, como fallback lee los sugeridos (no persistentes).
            Set<String> roles = getRolesForDevice(device);
            if (roles.isEmpty()) {
                roles = parseRoles(data); // opcional
            }

            UUID connId = msg.from();
            long exp = now + SESSION_TTL_MS;
            SessionInfo session = new SessionInfo(connId, device, roles, exp);
            SESSIONS.put(connId, session);

            JsonObject ok = new JsonObject();
            ok.addProperty("ok", true);
            ok.addProperty("device", device);
            ok.addProperty("expiresAt", exp);
            ok.addProperty("nonceWindowMs", NONCE_WINDOW_MS);

            // incluir roles efectivos en el ack (para depurar)
            Set<String> eff = getRolesForDevice(device);
            JsonArray rr = new JsonArray();
            for (String r : eff) rr.add(r);
            ok.add("roles", rr);

            MinecraftForge.EVENT_BUS.post(new IoCraftAuthResultEvent(connId, device, ok.deepCopy()));
            return ok;
        } catch (Throwable t) {
            LOGGER.warn("HELLO error: {}", t.toString());
            return failAndPublish(msg, null, "server_error", "Error procesando hello");
        }
    }

    public static boolean isMessageAllowed(String tipo, UUID from) {
        if ("hello".equalsIgnoreCase(tipo)) return true; // siempre permitir handshake
        if (from == null) return true;                   // mensajes internos
        if (!REQUIRE_AUTH) return true;
        return isAuthenticated(from);
    }

    // ======== Util ========

    private static JsonObject fail(String code, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("code", code);
        o.addProperty("message", msg);
        return o;
    }

    private static JsonObject failAndPublish(Mensaje rawMsg, String deviceId, String code, String message) {
        JsonObject err = fail(code, message);
        MinecraftForge.EVENT_BUS.post(new IoCraftAuthResultEvent(
                rawMsg != null ? rawMsg.from() : null,
                deviceId,
                err.deepCopy()
        ));
        return err;
    }

    private static String optString(JsonObject o, String k, String def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; }
        catch (Throwable ignore) { return def; }
    }

    private static long optLong(JsonObject o, String k, long def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : def; }
        catch (Throwable ignore) { return def; }
    }

    private static Set<String> parseRoles(JsonObject data) {
        Set<String> roles = new HashSet<>();
        try {
            if (data.has("roles")) {
                if (data.get("roles").isJsonArray()) {
                    data.getAsJsonArray("roles").forEach(e -> {
                        try { roles.add(e.getAsString()); } catch (Throwable ignore) {}
                    });
                } else {
                    String csv = data.get("roles").getAsString();
                    for (String r : csv.split(",")) {
                        String rr = r.trim();
                        if (!rr.isEmpty()) roles.add(rr);
                    }
                }
            }
        } catch (Throwable ignore) {}
        return roles;
    }

    private static String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(out);
    }

    private static String bytesToHex(byte[] b) {
        char[] HEX = "0123456789abcdef".toCharArray();
        char[] out = new char[b.length * 2];
        for (int i = 0, j = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int res = 0;
        for (int i = 0; i < a.length(); i++) {
            res |= a.charAt(i) ^ b.charAt(i);
        }
        return res == 0;
    }

    private static synchronized boolean registerNonce(String deviceId, String nonceRaw, long now) {
        cleanupExpiredNonces(now);
        String device = norm(deviceId);
        String nonce = (nonceRaw == null) ? null : nonceRaw.trim();
        if (device == null || nonce == null || nonce.isEmpty()) return false;
        String key = device + "|" + nonce;
        Long exp = USED_NONCES.get(key);
        if (exp != null && exp > now) return false;
        USED_NONCES.put(key, now + NONCE_WINDOW_MS);
        return true;
    }

    private static void cleanupExpiredNonces(long now) {
        USED_NONCES.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= now);
    }
}
