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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class BlacklistManager {
    private static final Logger LOGGER = LogManager.getLogger("BLACKLIST");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Set<String> BLOCKED_DEVICES = new HashSet<>();
    private static final Set<String> BLOCKED_IPS = new HashSet<>();
    private static Path STORE_FILE;

    private BlacklistManager() {}

    public static synchronized void initPersistence(Path storeFile) {
        STORE_FILE = storeFile;
        loadFromDisk();
        LOGGER.info("[BLACKLIST] Cargada. devices={} ips={}", BLOCKED_DEVICES.size(), BLOCKED_IPS.size());
    }

    public static synchronized void addDevice(String deviceId) {
        String v = normDevice(deviceId);
        if (v == null) return;
        BLOCKED_DEVICES.add(v);
        saveToDisk();
    }

    public static synchronized void removeDevice(String deviceId) {
        String v = normDevice(deviceId);
        if (v == null) return;
        BLOCKED_DEVICES.remove(v);
        saveToDisk();
    }

    public static synchronized void addIp(String ip) {
        String v = normIp(ip);
        if (v == null) return;
        BLOCKED_IPS.add(v);
        saveToDisk();
    }

    public static synchronized void removeIp(String ip) {
        String v = normIp(ip);
        if (v == null) return;
        BLOCKED_IPS.remove(v);
        saveToDisk();
    }

    public static synchronized boolean isDeviceBlocked(String deviceId) {
        String v = normDevice(deviceId);
        return v != null && BLOCKED_DEVICES.contains(v);
    }

    public static synchronized boolean isIpBlocked(String ip) {
        String v = normIp(ip);
        return v != null && BLOCKED_IPS.contains(v);
    }

    public static synchronized Set<String> snapshotDevices() {
        return new TreeSet<>(BLOCKED_DEVICES);
    }

    public static synchronized Set<String> snapshotIps() {
        return new TreeSet<>(BLOCKED_IPS);
    }

    private static String normDevice(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private static String normIp(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static synchronized void loadFromDisk() {
        BLOCKED_DEVICES.clear();
        BLOCKED_IPS.clear();
        if (STORE_FILE == null || !Files.exists(STORE_FILE)) return;
        try (Reader r = Files.newBufferedReader(STORE_FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            addAll(root.get("devices"), true);
            addAll(root.get("ips"), false);
        } catch (Exception e) {
            LOGGER.warn("No pude cargar blacklist: {}", e.toString());
        }
    }

    private static void addAll(JsonElement node, boolean devices) {
        if (node == null || !node.isJsonArray()) return;
        for (JsonElement el : node.getAsJsonArray()) {
            String raw = el.isJsonNull() ? null : el.getAsString();
            if (devices) {
                String v = normDevice(raw);
                if (v != null) BLOCKED_DEVICES.add(v);
            } else {
                String v = normIp(raw);
                if (v != null) BLOCKED_IPS.add(v);
            }
        }
    }

    private static synchronized void saveToDisk() {
        if (STORE_FILE == null) return;
        JsonObject root = new JsonObject();
        JsonArray devices = new JsonArray();
        for (String d : new TreeSet<>(BLOCKED_DEVICES)) devices.add(d);
        JsonArray ips = new JsonArray();
        for (String ip : new TreeSet<>(BLOCKED_IPS)) ips.add(ip);
        root.add("devices", devices);
        root.add("ips", ips);
        try {
            Files.createDirectories(STORE_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(STORE_FILE)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            LOGGER.warn("No pude guardar blacklist: {}", e.toString());
        }
    }
}
