package com.curius.iocraft.blocks.computer.net;

import com.curius.iocraft.blocks.computer.ComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/** Lado cliente -> servidor: llegó un mensaje por socket y hay que persistirlo en el BE. */
public class PacketSocketMensaje {
    private static final Logger LOGGER = LogManager.getLogger("COMPUTER-SOCKET");
    private final BlockPos pos;
    private final String autor;
    private final String texto;

    public PacketSocketMensaje(BlockPos pos, String autor, String texto) {
        this.pos = pos;
        this.autor = autor;
        this.texto = texto;
    }

    public static void encode(PacketSocketMensaje msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.autor != null ? msg.autor : "");
        buf.writeUtf(msg.texto != null ? msg.texto : "");
    }

    public static PacketSocketMensaje decode(FriendlyByteBuf buf) {
        BlockPos p = buf.readBlockPos();
        String a   = buf.readUtf();
        String t   = buf.readUtf();
        return new PacketSocketMensaje(p, a, t);
    }

    public static void handle(PacketSocketMensaje msg, Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            // Requiere un sender (mundo del servidor)
            var sender = context.getSender();
            if (sender == null) return; // seguridad (dedicado sin jugador no debería pasar)

            ServerLevel level = sender.getLevel();
            BlockEntity be = level.getBlockEntity(msg.pos);
            LOGGER.debug("[COMPUTER-SOCKET] rx pos={} author={} text={}", msg.pos, msg.autor, msg.texto);

            // Publicar al bus con contexto del bloque computer.
            com.curius.iocraft.mensajeria.MensajeriaBus.publicarTextoComoData(
                    "sensor",                       // tipo
                    msg.texto,                      // {"text": "..."}  (misma forma que ya usas)
                    null,                           // from (UUID no necesario aquí)
                    com.curius.iocraft.mensajeria.MensajeriaBus.Contexto.of(level, msg.pos, msg.autor) // contexto
            );


            if (!(be instanceof ComputerBlockEntity cbe2)) {
                return;
            }
            if (be instanceof ComputerBlockEntity cbe) {
                // Formato igual al que ya usas en el cliente
                String autorFmt = (msg.autor == null || msg.autor.isEmpty()) ? "remoto" : msg.autor;
                String linea = "§a" + autorFmt + "§f: " + (msg.texto != null ? msg.texto : "");
                cbe.pushMessage(linea); // server: persiste + sync a clientes
            }
        });
        context.setPacketHandled(true);
    }
}
