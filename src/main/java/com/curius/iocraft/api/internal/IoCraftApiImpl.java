package com.curius.iocraft.api.internal;

import com.curius.iocraft.api.IoCraftApi;
import com.curius.iocraft.api.IoCraftContext;
import com.curius.iocraft.api.IoCraftMessage;
import com.curius.iocraft.api.IoCraftMessageHandler;
import com.curius.iocraft.mensajeria.MensajeriaBus;
import com.curius.iocraft.security.AuthManager;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class IoCraftApiImpl implements IoCraftApi {
    @Override
    public boolean isDeviceRegistered(String deviceId) {
        return AuthManager.hasDevice(deviceId);
    }

    @Override
    public Set<String> getDeviceRoles(String deviceId) {
        return AuthManager.getRolesForDevice(deviceId);
    }

    @Override
    public int getActiveSessions(String deviceId) {
        return AuthManager.countActiveSessionsForDevice(deviceId);
    }

    @Override
    public boolean isConnectionAuthenticated(UUID connectionId) {
        return AuthManager.isAuthenticated(connectionId);
    }

    @Override
    public void registerMessageHandler(String type, IoCraftMessageHandler handler) {
        registerMessageHandler(type, 0, "external", handler);
    }

    @Override
    public void registerMessageHandler(String type, int priority, String ownerModId, IoCraftMessageHandler handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(handler, "handler");
        MensajeriaBus.registrarManejadorExterno(type, priority, ownerModId, (msg, ctx) -> {
            IoCraftMessage outMsg = new IoCraftMessage(msg.tipo(), msg.data(), msg.texto(), msg.to(), msg.from());
            IoCraftContext outCtx = new IoCraftContext(ctx.level(), ctx.pos(), ctx.device());
            handler.handle(outMsg, outCtx);
        });
    }

    @Override
    public void unregisterMessageHandler(String type) {
        unregisterMessageHandler(type, "external");
    }

    @Override
    public void unregisterMessageHandler(String type, String ownerModId) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ownerModId, "ownerModId");
        MensajeriaBus.desregistrarManejadorExterno(type, ownerModId);
    }

    @Override
    public boolean sendText(UUID to, String text) {
        return MensajeriaBus.enviarTexto(to, text);
    }

    @Override
    public boolean sendTyped(UUID to, String type, JsonObject data) {
        return MensajeriaBus.enviarTipado(to, type, data);
    }

    @Override
    public int broadcastText(String text) {
        return MensajeriaBus.broadcastTexto(text);
    }
}

