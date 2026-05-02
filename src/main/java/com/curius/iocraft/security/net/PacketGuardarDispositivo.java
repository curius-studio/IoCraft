package com.curius.iocraft.security.net;

import com.curius.iocraft.security.AuthManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class PacketGuardarDispositivo {
    public final String device;
    public final String secret;
    public final boolean sensor;
    public final boolean cmd;
    public final boolean actuator;

    public PacketGuardarDispositivo(String device, String secret, boolean sensor, boolean cmd, boolean actuator) {
        this.device = device;
        this.secret = secret;
        this.sensor = sensor;
        this.cmd = cmd;
        this.actuator = actuator;
    }

    public static void encode(PacketGuardarDispositivo m, FriendlyByteBuf buf) {
        buf.writeUtf(m.device);
        buf.writeUtf(m.secret);
        buf.writeBoolean(m.sensor);
        buf.writeBoolean(m.cmd);
        buf.writeBoolean(m.actuator);
    }

    public static PacketGuardarDispositivo decode(FriendlyByteBuf buf) {
        String d = buf.readUtf();
        String s = buf.readUtf();
        boolean se = buf.readBoolean();
        boolean c  = buf.readBoolean();
        boolean a  = buf.readBoolean();
        return new PacketGuardarDispositivo(d, s, se, c, a);
    }

    public static void handle(PacketGuardarDispositivo msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            // 1) Persistir el secreto EXACTO que envía la UI (no generar uno nuevo)
            if (msg.device != null && !msg.device.isBlank() && msg.secret != null && !msg.secret.isBlank()) {
                AuthManager.putSecret(msg.device, msg.secret);
            }

            // 2) Mapear booleans -> roles y persistir roles autoritativos del servidor
            Set<String> roles = new HashSet<>();
            if (msg.sensor)   roles.add("sensor");
            if (msg.cmd)      roles.add("cmd");
            if (msg.actuator) roles.add("actuator");

            AuthManager.putRoles(msg.device, roles); // persiste + invalida sesiones del device
        });
        ctx.setPacketHandled(true);
    }
}
