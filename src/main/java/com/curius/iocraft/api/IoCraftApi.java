package com.curius.iocraft.api;

import com.google.gson.JsonObject;

import java.util.Set;
import java.util.UUID;

/**
 * API pública mínima de IoCraft (Fase 1).
 * Contrato aditivo: no reemplaza internals existentes; los envuelve.
 */
public interface IoCraftApi {
    boolean isDeviceRegistered(String deviceId);
    Set<String> getDeviceRoles(String deviceId);
    int getActiveSessions(String deviceId);
    boolean isConnectionAuthenticated(UUID connectionId);

    void registerMessageHandler(String type, IoCraftMessageHandler handler);
    void registerMessageHandler(String type, int priority, String ownerModId, IoCraftMessageHandler handler);
    void unregisterMessageHandler(String type);
    void unregisterMessageHandler(String type, String ownerModId);

    boolean sendText(UUID to, String text);
    boolean sendTyped(UUID to, String type, JsonObject data);
    int broadcastText(String text);
}

