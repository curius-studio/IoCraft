package com.curius.iocraft.api.events;

import com.google.gson.JsonObject;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/**
 * Evento emitido al terminar processHello.
 */
public class IoCraftAuthResultEvent extends Event {
    private final UUID connectionId;
    private final String deviceId;
    private final JsonObject result;

    public IoCraftAuthResultEvent(UUID connectionId, String deviceId, JsonObject result) {
        this.connectionId = connectionId;
        this.deviceId = deviceId;
        this.result = result;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public JsonObject getAuthResult() {
        return result;
    }
}

