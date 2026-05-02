package com.curius.iocraft.ws;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceRegistry {
    private static final Map<UUID, DeviceInfo> devices = new ConcurrentHashMap<>();

    static void add(DeviceInfo info) {
        devices.put(info.id, info);
    }

    static void remove(UUID id) {
        devices.remove(id);
    }

    public static List<DeviceInfo> snapshot() {
        return new ArrayList<>(devices.values());
    }

    public static Optional<DeviceInfo> get(UUID id) {
        return Optional.ofNullable(devices.get(id));
    }

    public static void clear() {
        devices.clear();
    }
}
