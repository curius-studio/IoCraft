package com.curius.iocraft.mensajeria;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Source “virtual” para ejecutar comandos y capturar su salida (como la máquina). */
public final class WsCommandSource implements CommandSource {
    private final MinecraftServer server;
    private final List<String> lineas = new ArrayList<>();

    public WsCommandSource(MinecraftServer server) {
        this.server = server;
    }

    /** En 1.18.x CommandSource pide este método. Guardamos el texto en un buffer. */
    @Override
    public void sendMessage(Component component, UUID senderUUID) {
        lineas.add(component.getString());
    }

    // Acepta tanto éxito como fallo; no informa a administradores.
    @Override public boolean acceptsSuccess() { return true; }
    @Override public boolean acceptsFailure() { return true; }
    @Override public boolean shouldInformAdmins() { return false; }

    /** Crea el CommandSourceStack con permiso máximo (4) anclado al spawn del overworld. */
    public CommandSourceStack asStack() {
        ServerLevel overworld = server.overworld();
        return asStack(overworld);
    }

    /** Versión que permite elegir el mundo (ServerLevel) donde “se ejecuta” el comando. */
    public CommandSourceStack asStack(ServerLevel level) {
        Vec3 pos = Vec3.atCenterOf(level.getSharedSpawnPos());
        return new CommandSourceStack(
                this,                 // sigue siendo “la máquina”, no un jugador
                pos,
                Vec2.ZERO,
                level,
                4,                    // permiso máximo (admin)
                "iocraft-ws",
                new TextComponent("IoCraft-WS"),
                server,
                null                  // sin entidad asociada
        );
    }

    /** Devuelve la salida acumulada y limpia el buffer. */
    public String drainOutput() {
        String out = String.join("\n", lineas);
        lineas.clear();
        return out;
    }
}
