package com.curius.iocraft.net;

import com.curius.iocraft.iot.InboxIoT;
import com.curius.iocraft.blocks.puerta.PuertaIoCraftBlock;
import com.curius.iocraft.blocks.puerta.net.PuertaNetwork;
import com.curius.iocraft.blocks.puerta.net.PacketPuertaComparar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketInboxUpdate {

    public final BlockPos pos;
    public final String device;
    public final String type;
    public final String data;
    public final String mundo; // dimension (p.ej. "minecraft:overworld")
    public final long ts;      // timestamp (ms). Si no se provee, será <= 0.

    /** Ctor sin timestamp: el cliente pondrá System.currentTimeMillis(). */
    public PacketInboxUpdate(BlockPos pos, String device, String type, String data, String mundo) {
        this(pos, device, type, data, mundo, -1L);
    }

    /** Ctor con timestamp (servidor puede enviar su propio tiempo). */
    public PacketInboxUpdate(BlockPos pos, String device, String type, String data, String mundo, long ts) {
        this.pos    = pos;
        this.device = device != null ? device : "";
        this.type   = type   != null ? type   : "";
        this.data   = data   != null ? data   : "";
        this.mundo  = mundo  != null ? mundo  : "";
        this.ts     = ts;
    }

    // ----- codec -----

    public static void encode(PacketInboxUpdate msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.device, 256);
        buf.writeUtf(msg.type,   64);
        buf.writeUtf(msg.data,   32767);
        buf.writeUtf(msg.mundo,  64);
        buf.writeLong(msg.ts); // añadimos timestamp
    }

    public static PacketInboxUpdate decode(FriendlyByteBuf buf) {
        BlockPos pos   = buf.readBlockPos();
        String device  = buf.readUtf();
        String type    = buf.readUtf();
        String data    = buf.readUtf();
        String mundo   = buf.readUtf();
        long ts        = buf.readLong(); // leemos timestamp
        return new PacketInboxUpdate(pos, device, type, data, mundo, ts);
    }

    // ----- handler (lado cliente) -----

    public static void handle(PacketInboxUpdate msg, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            // 1) Guardar en el buzón (lo mismo que usa la GUI)
            long when = (msg.ts > 0) ? msg.ts : System.currentTimeMillis();
            InboxIoT.put(
                    msg.pos,
                    new InboxIoT.Entrada(
                            msg.device,
                            msg.type,
                            msg.data,
                            msg.mundo,
                            when
                    )
            );


            var mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                var state = mc.level.getBlockState(msg.pos);
                if (state.getBlock() instanceof com.curius.iocraft.blocks.puerta.PuertaIoCraftBlock) {
                    var entrada = InboxIoT.get(msg.pos);
                    if (entrada != null) {
                        com.curius.iocraft.blocks.puerta.net.PuertaNetwork.CHANNEL.sendToServer(
                                new com.curius.iocraft.blocks.puerta.net.PacketPuertaComparar(
                                        msg.pos, entrada.device, entrada.data
                                )
                        );
                    }
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
