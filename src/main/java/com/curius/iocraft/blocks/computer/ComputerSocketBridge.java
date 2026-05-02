// ComputerSocketBridge.java
package com.curius.iocraft.blocks.computer;

import com.curius.iocraft.blocks.computer.net.ComputerNetwork;
import com.curius.iocraft.blocks.computer.net.PacketSocketMensaje;
import com.curius.iocraft.iot.MensajeIoTParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ComputerSocketBridge {
    private ComputerSocketBridge() {}

    public static void onIncoming(MensajeIoTParser.MensajeIoT m, Level level) {
        if (m == null || level == null) return;

        BlockPos pos = new BlockPos(m.x, m.y, m.z);

        // Enviar al servidor para persistirlo allí (no modificar BE del cliente)
        String autor = (m.device != null ? m.device : "remoto");
        String texto = (m.data   != null ? m.data   : "");
        ComputerNetwork.CHANNEL.sendToServer(new PacketSocketMensaje(pos, autor, texto));
    }
}
