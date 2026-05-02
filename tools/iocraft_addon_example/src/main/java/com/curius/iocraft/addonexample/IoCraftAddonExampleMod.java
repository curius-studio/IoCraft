package com.curius.iocraft.addonexample;

import com.curius.iocraft.api.IoCraftApi;
import com.curius.iocraft.api.IoCraftApiProvider;
import com.curius.iocraft.api.events.IoCraftAuthResultEvent;
import com.curius.iocraft.api.events.IoCraftMessagePostProcessEvent;
import com.curius.iocraft.api.events.IoCraftMessagePreProcessEvent;
import com.google.gson.JsonObject;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(IoCraftAddonExampleMod.MOD_ID)
public final class IoCraftAddonExampleMod {
    public static final String MOD_ID = "iocraft_addon_example";
    private static final Logger LOGGER = LogManager.getLogger();

    public IoCraftAddonExampleMod() {
        if (!IoCraftApiProvider.isAvailable()) {
            LOGGER.warn("[{}] IoCraft API no disponible. Addon en modo degradado.", MOD_ID);
            return;
        }

        if (IoCraftApiProvider.apiMajor() != 1) {
            LOGGER.warn("[{}] API incompatible detectada: {}", MOD_ID, IoCraftApiProvider.apiVersion());
            return;
        }

        IoCraftApi api = IoCraftApiProvider.get().orElseThrow();

        api.registerMessageHandler("addon/ping", 200, MOD_ID, (msg, ctx) -> {
            if (msg.from() == null) return;
            JsonObject out = new JsonObject();
            out.addProperty("ok", true);
            out.addProperty("echo", "addon/pong");
            out.addProperty("apiVersion", IoCraftApiProvider.apiVersion());
            out.addProperty("device", ctx.device());
            api.sendTyped(msg.from(), "addon/pong", out);
        });

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[{}] Inicializado sobre API {}", MOD_ID, IoCraftApiProvider.apiVersion());
    }

    @SubscribeEvent
    public void onPreProcess(IoCraftMessagePreProcessEvent event) {
        if (event.getMessage() == null || event.getMessage().tipo() == null) return;
        if ("addon/blocked".equalsIgnoreCase(event.getMessage().tipo())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPostProcess(IoCraftMessagePostProcessEvent event) {
        LOGGER.debug("[{}] message post type={} device={}",
                MOD_ID,
                event.getMessage() != null ? event.getMessage().tipo() : "null",
                event.getDevice());
    }

    @SubscribeEvent
    public void onAuthResult(IoCraftAuthResultEvent event) {
        boolean ok = event.getAuthResult() != null
                && event.getAuthResult().has("ok")
                && event.getAuthResult().get("ok").getAsBoolean();
        if (!ok) {
            LOGGER.warn("[{}] auth fallida conn={} device={} result={}",
                    MOD_ID, event.getConnectionId(), event.getDeviceId(), event.getAuthResult());
        }
    }
}
