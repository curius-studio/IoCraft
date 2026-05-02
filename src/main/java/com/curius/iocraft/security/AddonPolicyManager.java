package com.curius.iocraft.security;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gobernanza operativa de addons externos (ownerModId).
 * Mantiene políticas persistentes y contadores runtime para cuarentena automática.
 */
public final class AddonPolicyManager {
    private static final Logger LOGGER = LogManager.getLogger("ADDON-POLICY");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CORE_OWNER = "iocraft-core";

    private static final Map<String, Policy> POLICIES = new ConcurrentHashMap<>();
    private static final Map<String, RuntimeCounters> RUNTIME = new ConcurrentHashMap<>();
    private static Path STORE_FILE;

    private AddonPolicyManager() {}

    public enum OwnerState {
        ENABLED,
        DISABLED,
        QUARANTINED
    }

    private static final class Policy {
        OwnerState state = OwnerState.ENABLED;
        Set<String> allowTypes = new HashSet<>();
        Set<String> denyTypes = new HashSet<>();
        int quarantineOnErrors = -1; // <=0 desactivado
        int quarantineOnSlow = -1;   // <=0 desactivado
    }

    private static final class RuntimeCounters {
        final AtomicLong errors = new AtomicLong();
        final AtomicLong slow = new AtomicLong();
    }

    public record PolicySnapshot(
            String ownerModId,
            String state,
            Set<String> allowTypes,
            Set<String> denyTypes,
            int quarantineOnErrors,
            int quarantineOnSlow
    ) {}

    public record PolicyDecision(boolean allowed, String reason) {}

    public static synchronized void initPersistence(Path storeFile) {
        STORE_FILE = storeFile;
        loadFromDisk();
        LOGGER.info("[ADDON-POLICY] loaded policies={} store={}", POLICIES.size(), STORE_FILE);
    }

    public static PolicyDecision canExecute(String ownerModId, String type) {
        String owner = normOwner(ownerModId);
        if (owner == null) return new PolicyDecision(false, "owner_empty");
        if (isCoreOwner(owner)) return new PolicyDecision(true, "core_owner");

        Policy p = POLICIES.get(owner);
        if (p == null) return new PolicyDecision(true, "default_allow");

        if (p.state == OwnerState.DISABLED) return new PolicyDecision(false, "owner_disabled");
        if (p.state == OwnerState.QUARANTINED) return new PolicyDecision(false, "owner_quarantined");

        String msgType = normType(type);
        if (msgType == null) return new PolicyDecision(false, "type_empty");

        if (matchesAny(msgType, p.denyTypes)) return new PolicyDecision(false, "type_denied");
        if (!p.allowTypes.isEmpty() && !matchesAny(msgType, p.allowTypes)) {
            return new PolicyDecision(false, "type_not_allowed");
        }
        return new PolicyDecision(true, "allowed");
    }

    public static void recordExecution(String ownerModId, boolean failed, boolean slow) {
        String owner = normOwner(ownerModId);
        if (owner == null || isCoreOwner(owner)) return;
        Policy p = POLICIES.get(owner);
        if (p == null) return;

        RuntimeCounters c = RUNTIME.computeIfAbsent(owner, k -> new RuntimeCounters());
        long errors = failed ? c.errors.incrementAndGet() : c.errors.get();
        long slows = slow ? c.slow.incrementAndGet() : c.slow.get();

        if (p.state == OwnerState.ENABLED) {
            boolean hitErrors = p.quarantineOnErrors > 0 && errors >= p.quarantineOnErrors;
            boolean hitSlow = p.quarantineOnSlow > 0 && slows >= p.quarantineOnSlow;
            if (hitErrors || hitSlow) {
                synchronized (AddonPolicyManager.class) {
                    Policy now = POLICIES.get(owner);
                    if (now != null && now.state == OwnerState.ENABLED) {
                        now.state = OwnerState.QUARANTINED;
                        saveToDisk();
                        LOGGER.warn("[ADDON-POLICY] state_change owner={} state=QUARANTINED trigger={} errors={} slow={}",
                                owner, hitErrors ? "errors" : "slow", errors, slows);
                    }
                }
            }
        }
    }

    public static synchronized PolicySnapshot upsertOwner(String ownerModId) {
        String owner = validOwner(ownerModId);
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized PolicySnapshot setState(String ownerModId, OwnerState state) {
        String owner = validOwner(ownerModId);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        p.state = Objects.requireNonNull(state, "state");
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized PolicySnapshot setLimits(String ownerModId, int quarantineOnErrors, int quarantineOnSlow) {
        String owner = validOwner(ownerModId);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        p.quarantineOnErrors = normalizeLimit(quarantineOnErrors);
        p.quarantineOnSlow = normalizeLimit(quarantineOnSlow);
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized PolicySnapshot allowType(String ownerModId, String type) {
        String owner = validOwner(ownerModId);
        String t = validType(type);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        p.denyTypes.remove(t);
        p.allowTypes.add(t);
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized PolicySnapshot denyType(String ownerModId, String type) {
        String owner = validOwner(ownerModId);
        String t = validType(type);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        p.allowTypes.remove(t);
        p.denyTypes.add(t);
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized PolicySnapshot clearRules(String ownerModId) {
        String owner = validOwner(ownerModId);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        Policy p = POLICIES.computeIfAbsent(owner, k -> new Policy());
        p.allowTypes.clear();
        p.denyTypes.clear();
        saveToDisk();
        return snapshotOf(owner, p);
    }

    public static synchronized boolean clearOwner(String ownerModId) {
        String owner = validOwner(ownerModId);
        if (isCoreOwner(owner)) throw new IllegalArgumentException("owner reservado");
        RUNTIME.remove(owner);
        boolean removed = POLICIES.remove(owner) != null;
        saveToDisk();
        return removed;
    }

    public static synchronized Map<String, PolicySnapshot> snapshotPolicies() {
        Map<String, PolicySnapshot> out = new TreeMap<>();
        for (Map.Entry<String, Policy> e : POLICIES.entrySet()) {
            out.put(e.getKey(), snapshotOf(e.getKey(), e.getValue()));
        }
        return out;
    }

    public static synchronized PolicySnapshot snapshotPolicy(String ownerModId) {
        String owner = validOwner(ownerModId);
        Policy p = POLICIES.get(owner);
        if (p == null) return null;
        return snapshotOf(owner, p);
    }

    private static PolicySnapshot snapshotOf(String owner, Policy p) {
        return new PolicySnapshot(
                owner,
                p.state.name(),
                Set.copyOf(new TreeSet<>(p.allowTypes)),
                Set.copyOf(new TreeSet<>(p.denyTypes)),
                p.quarantineOnErrors,
                p.quarantineOnSlow
        );
    }

    private static int normalizeLimit(int v) {
        if (v <= 0) return -1;
        return Math.min(v, 1_000_000);
    }

    private static boolean matchesAny(String type, Set<String> rules) {
        for (String r : rules) {
            if ("*".equals(r)) return true;
            if (r.endsWith("*")) {
                String prefix = r.substring(0, r.length() - 1);
                if (type.startsWith(prefix)) return true;
            } else if (type.equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }

    private static String validOwner(String ownerModId) {
        String owner = normOwner(ownerModId);
        if (owner == null) throw new IllegalArgumentException("ownerModId vacío");
        return owner;
    }

    private static String validType(String type) {
        String t = normType(type);
        if (t == null) throw new IllegalArgumentException("type vacío");
        return t;
    }

    private static String normOwner(String owner) {
        if (owner == null) return null;
        String v = owner.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private static String normType(String type) {
        if (type == null) return null;
        String v = type.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private static boolean isCoreOwner(String owner) {
        return CORE_OWNER.equalsIgnoreCase(owner);
    }

    private static synchronized void loadFromDisk() {
        POLICIES.clear();
        if (STORE_FILE == null || !Files.exists(STORE_FILE)) return;
        try (Reader r = Files.newBufferedReader(STORE_FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            JsonArray arr = root.has("owners") && root.get("owners").isJsonArray()
                    ? root.getAsJsonArray("owners")
                    : new JsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject node = el.getAsJsonObject();
                String owner = normOwner(node.has("owner") ? node.get("owner").getAsString() : null);
                if (owner == null || isCoreOwner(owner)) continue;
                Policy p = new Policy();
                if (node.has("state")) {
                    try {
                        p.state = OwnerState.valueOf(node.get("state").getAsString().toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {}
                }
                if (node.has("allowTypes") && node.get("allowTypes").isJsonArray()) {
                    for (JsonElement t : node.getAsJsonArray("allowTypes")) {
                        String v = normType(t.getAsString());
                        if (v != null) p.allowTypes.add(v);
                    }
                }
                if (node.has("denyTypes") && node.get("denyTypes").isJsonArray()) {
                    for (JsonElement t : node.getAsJsonArray("denyTypes")) {
                        String v = normType(t.getAsString());
                        if (v != null) p.denyTypes.add(v);
                    }
                }
                p.quarantineOnErrors = node.has("quarantineOnErrors")
                        ? normalizeLimit(node.get("quarantineOnErrors").getAsInt())
                        : -1;
                p.quarantineOnSlow = node.has("quarantineOnSlow")
                        ? normalizeLimit(node.get("quarantineOnSlow").getAsInt())
                        : -1;
                POLICIES.put(owner, p);
            }
        } catch (Exception e) {
            LOGGER.warn("[ADDON-POLICY] load_failed err={}", e.toString());
        }
    }

    private static synchronized void saveToDisk() {
        if (STORE_FILE == null) return;
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, Policy> e : new TreeMap<>(POLICIES).entrySet()) {
            JsonObject node = new JsonObject();
            node.addProperty("owner", e.getKey());
            node.addProperty("state", e.getValue().state.name());
            node.addProperty("quarantineOnErrors", e.getValue().quarantineOnErrors);
            node.addProperty("quarantineOnSlow", e.getValue().quarantineOnSlow);
            JsonArray allow = new JsonArray();
            for (String t : new TreeSet<>(e.getValue().allowTypes)) allow.add(t);
            JsonArray deny = new JsonArray();
            for (String t : new TreeSet<>(e.getValue().denyTypes)) deny.add(t);
            node.add("allowTypes", allow);
            node.add("denyTypes", deny);
            arr.add(node);
        }
        root.add("owners", arr);
        try {
            Files.createDirectories(STORE_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(STORE_FILE)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            LOGGER.warn("[ADDON-POLICY] save_failed err={}", e.toString());
        }
    }
}
