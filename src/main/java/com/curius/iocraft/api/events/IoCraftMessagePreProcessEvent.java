package com.curius.iocraft.api.events;

import com.curius.iocraft.mensajeria.Mensaje;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Evento previo al dispatch de un mensaje entrante desde WS.
 * Es cancelable y permite ajustar mensaje/contexto.
 */
@Cancelable
public class IoCraftMessagePreProcessEvent extends Event {
    private Mensaje message;
    private ServerLevel level;
    private BlockPos pos;
    private String device;

    public IoCraftMessagePreProcessEvent(Mensaje message, ServerLevel level, BlockPos pos, String device) {
        this.message = message;
        this.level = level;
        this.pos = pos;
        this.device = device;
    }

    public Mensaje getMessage() {
        return message;
    }

    public void setMessage(Mensaje message) {
        this.message = message;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getDevice() {
        return device;
    }

    public void setContext(ServerLevel level, BlockPos pos, String device) {
        this.level = level;
        this.pos = pos;
        this.device = device;
    }
}

