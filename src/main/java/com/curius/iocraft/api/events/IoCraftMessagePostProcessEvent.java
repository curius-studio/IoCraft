package com.curius.iocraft.api.events;

import com.curius.iocraft.mensajeria.Mensaje;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

/**
 * Evento posterior al dispatch del mensaje.
 */
public class IoCraftMessagePostProcessEvent extends Event {
    private final Mensaje message;
    private final ServerLevel level;
    private final BlockPos pos;
    private final String device;

    public IoCraftMessagePostProcessEvent(Mensaje message, ServerLevel level, BlockPos pos, String device) {
        this.message = message;
        this.level = level;
        this.pos = pos;
        this.device = device;
    }

    public Mensaje getMessage() {
        return message;
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
}

