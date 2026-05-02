package com.curius.iocraft.security.client;

import com.google.gson.*;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientAuthStore {

    public static final class Entry {
        public final String device;
        public final String secret;
        public final Set<String> roles;

        public Entry(String device, String secret, Set<String> roles) {
            this.device = device;
            this.secret = secret;
            this.roles  = (roles == null) ? Set.of() : Set.copyOf(roles);
        }
    }

    private static final Map<String, Entry> MEM = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORE = FMLPaths.CONFIGDIR.get().resolve("iocraft_client_secrets.json");

    static { load(); }

    public static synchronized void put(String device, String secret, Collection<String> roles) {
        if (device == null || device.isBlank()) return;
        Set<String> rs = new HashSet<>();
        if (roles != null) roles.forEach(r -> { if (r != null && !r.isBlank()) rs.add(r.trim()); });
        MEM.put(device, new Entry(device, secret, rs));
        save();
    }

    public static synchronized Entry get(String device) {
        return device == null ? null : MEM.get(device);
    }

    public static synchronized String secretOf(String device) {
        Entry e = get(device);
        return e == null ? null : e.secret;
    }

    public static synchronized Set<String> rolesOf(String device) {
        Entry e = get(device);
        return e == null ? Set.of() : e.roles;
    }

    public static synchronized void load() {
        MEM.clear();
        if (!Files.exists(STORE)) return;
        try (Reader r = Files.newBufferedReader(STORE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("devices") && root.get("devices").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("devices")) {
                    JsonObject d = el.getAsJsonObject();
                    String id  = d.has("id")     ? d.get("id").getAsString()     : null;
                    String sec = d.has("secret") ? d.get("secret").getAsString() : null;
                    Set<String> roles = new HashSet<>();
                    if (d.has("roles")) {
                        JsonElement rr = d.get("roles");
                        if (rr.isJsonArray()) rr.getAsJsonArray().forEach(x -> roles.add(x.getAsString()));
                        else for (String s : rr.getAsString().split(",")) { String t=s.trim(); if (!t.isEmpty()) roles.add(t); }
                    }
                    if (id != null) MEM.put(id, new Entry(id, sec, roles));
                }
            }
        } catch (Exception ignore) {}
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(STORE.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (Entry e : MEM.values()) {
                JsonObject d = new JsonObject();
                d.addProperty("id", e.device);
                if (e.secret != null) d.addProperty("secret", e.secret);
                JsonArray rr = new JsonArray();
                for (String r : e.roles) rr.add(r);
                d.add("roles", rr);
                arr.add(d);
            }
            root.add("devices", arr);
            try (Writer w = Files.newBufferedWriter(STORE)) {
                GSON.toJson(root, w);
            }
        } catch (Exception ignore) {}
    }

    private ClientAuthStore() {}
}
