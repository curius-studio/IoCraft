package com.curius.iocraft.security.client;

import com.curius.iocraft.security.net.PacketGuardarDispositivo;
import com.curius.iocraft.security.net.SecurityNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Encola el secret/roles si no hay mundo, y lo envía automáticamente al entrar. */
@Mod.EventBusSubscriber(modid = "iocraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientAuthSync {
    private ClientAuthSync() {}

    private static volatile Pending pending;

    public static void queue(String device, String secret, boolean sensor, boolean cmd, boolean actuator) {
        pending = new Pending(device, secret, sensor, cmd, actuator);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;

        Pending p = pending;
        if (p == null) return;

        SecurityNetwork.CHANNEL.sendToServer(
                new PacketGuardarDispositivo(p.device, p.secret, p.canSensor, p.canCmd, p.readOnly)
        );
        pending = null;
    }

    private record Pending(String device, String secret, boolean canSensor, boolean canCmd, boolean readOnly) {}
}
