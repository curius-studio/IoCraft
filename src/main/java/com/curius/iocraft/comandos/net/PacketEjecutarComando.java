package com.curius.iocraft.comandos.net;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.Supplier;

public class PacketEjecutarComando {
    private final String comando;

    public PacketEjecutarComando(String comando) {
        this.comando = comando == null ? "" : comando;
    }

    public static void encode(PacketEjecutarComando m, FriendlyByteBuf buf) {
        buf.writeUtf(m.comando);
    }

    public static PacketEjecutarComando decode(FriendlyByteBuf buf) {
        return new PacketEjecutarComando(buf.readUtf());
    }

    public static void handle(PacketEjecutarComando m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            String cmd = m.comando.startsWith("/") ? m.comando.substring(1) : m.comando;
            if (cmd.isBlank()) return;

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            // Ejecutamos en el Overworld, posicionados en el spawn (útil si el comando usa @p o coords relativas).
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            Vec3 pos = new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);

            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(pos)
                    .withPermission(4)           // operador
                    .withSuppressedOutput();     // sin spam en el chat

            try {
                // Brigadier: ejecuta exactamente lo que llegó en data
                server.getCommands().getDispatcher().execute(cmd, source);
            } catch (Exception ignored) {}
        });
        ctx.get().setPacketHandled(true);
    }
}
