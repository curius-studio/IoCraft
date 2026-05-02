package com.curius.iocraft.net;

import com.curius.iocraft.blocks.puerta.PuertaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class InboxS2C {
    private static final Logger LOGGER = LogManager.getLogger("INBOX-S2C");

    private InboxS2C() {}

    /**
     * Enviar a clientes el “último mensaje” para un BlockPos concreto
     * y, si ese BlockPos es una Puerta, comprobar coincidencia con los mensajes guardados.
     */
    // lado servidor
    public static void send(ServerLevel level,
                            BlockPos pos,
                            String device,
                            String type,
                            String data,
                            String mundo) {

        CoreNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> level.dimension()),
                new PacketInboxUpdate(pos, device, type, data, mundo)
        );
    }



    // ----------------- helpers -----------------

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static ServerLevel levelFrom(MinecraftServer server, String mundo) {
        ResourceLocation rl = normalizeDim(mundo);
        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, rl);
        return server.getLevel(key);
    }

    /** Devuelve "minecraft:overworld" / "minecraft:the_nether" / "minecraft:the_end" o la entrada namespaced. */
    private static String normalizeDimString(String mundo) {
        return normalizeDim(mundo).toString();
    }



    private static ResourceLocation normalizeDim(String mundo) {
        if (mundo == null || mundo.isBlank()) return new ResourceLocation("minecraft", "overworld");
        String m = mundo.toLowerCase().trim();
        switch (m) {
            case "overworld":   return new ResourceLocation("minecraft", "overworld");
            case "nether":
            case "the_nether":  return new ResourceLocation("minecraft", "the_nether");
            case "the_end":
            case "end":         return new ResourceLocation("minecraft", "the_end");
            default:
                // si ya viene namespaced (e.g. mymod:custom_dim), lo usamos tal cual
                return m.contains(":") ? new ResourceLocation(m) : new ResourceLocation(m);
        }
    }
}
