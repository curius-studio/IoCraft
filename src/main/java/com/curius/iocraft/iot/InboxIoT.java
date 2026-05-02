package com.curius.iocraft.iot;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Buzón cliente: guarda el ÚLTIMO mensaje recibido por cada BlockPos. */
public final class InboxIoT {
    private InboxIoT() {}

    public static final class Entrada {
        public final String device;
        public final String type;
        public final String data;
        public final String mundo;
        public final long   ts;

        public Entrada(String device, String type, String data, String mundo, long ts) {
            this.device = device;
            this.type   = type;
            this.data   = data;
            this.mundo  = mundo;
            this.ts     = ts;
        }
    }

    private static final Map<BlockPos, Entrada> LAST = new ConcurrentHashMap<>();

    public static void put(BlockPos pos, Entrada e) {
        if (pos != null && e != null) LAST.put(pos.immutable(), e);
    }

    public static Entrada get(BlockPos pos) {
        return pos == null ? null : LAST.get(pos);
    }
}
