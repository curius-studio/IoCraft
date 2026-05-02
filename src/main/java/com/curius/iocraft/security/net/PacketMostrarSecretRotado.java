package com.curius.iocraft.security.net;

import com.curius.iocraft.ui.security.SecretRotadoScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketMostrarSecretRotado {
    public final String device;
    public final String secret;

    public PacketMostrarSecretRotado(String device, String secret) {
        this.device = device == null ? "" : device;
        this.secret = secret == null ? "" : secret;
    }

    public static void encode(PacketMostrarSecretRotado m, FriendlyByteBuf buf) {
        buf.writeUtf(m.device);
        buf.writeUtf(m.secret);
    }

    public static PacketMostrarSecretRotado decode(FriendlyByteBuf buf) {
        return new PacketMostrarSecretRotado(buf.readUtf(), buf.readUtf());
    }

    public static void handle(PacketMostrarSecretRotado msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreen(new SecretRotadoScreen(msg.device, msg.secret)));
            }
        });
        ctx.setPacketHandled(true);
    }
}
