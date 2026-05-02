package com.curius.iocraft.comandos;

import com.curius.iocraft.security.AuthManager;
import com.curius.iocraft.security.net.SecurityNetwork;
import com.curius.iocraft.security.net.PacketMostrarSecretRotado;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

import java.util.Set;

public final class ComandoSecurity {
    private ComandoSecurity() {}

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ioc")
                .then(Commands.literal("security")
                        .requires(ComandoSecurity::hasAdminPermission)
                        .then(Commands.literal("rotate")
                                .then(Commands.argument("device", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String device = StringArgumentType.getString(ctx, "device");
                                            if (!AuthManager.hasDevice(device)) {
                                                ctx.getSource().sendFailure(new TextComponent("§cDevice no registrado: " + device));
                                                return 0;
                                            }
                                            Set<String> roles = AuthManager.getRolesForDevice(device);
                                            String newSecret = AuthManager.createOrRotateSecret(device, roles, true);
                                            ctx.getSource().sendSuccess(new TextComponent("§aSecret rotado para '" + device + "'."), false);
                                            Entity entity = ctx.getSource().getEntity();
                                            ServerPlayer player = (entity instanceof ServerPlayer sp) ? sp : null;
                                            if (player != null) {
                                                SecurityNetwork.CHANNEL.send(
                                                        PacketDistributor.PLAYER.with(() -> player),
                                                        new PacketMostrarSecretRotado(device, newSecret)
                                                );
                                            } else {
                                                ctx.getSource().sendSuccess(new TextComponent("§eNuevo secret: " + newSecret), false);
                                            }
                                            return 1;
                                        })))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("device", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String device = StringArgumentType.getString(ctx, "device");
                                            if (!AuthManager.hasDevice(device)) {
                                                ctx.getSource().sendFailure(new TextComponent("§cDevice no registrado: " + device));
                                                return 0;
                                            }
                                            AuthManager.revokeSecret(device);
                                            ctx.getSource().sendSuccess(new TextComponent("§aSecret revocado para '" + device + "'."), false);
                                            return 1;
                                        })))
                        .then(Commands.literal("status")
                                .then(Commands.argument("device", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String device = StringArgumentType.getString(ctx, "device");
                                            boolean exists = AuthManager.hasDevice(device);
                                            var roles = AuthManager.getRolesForDevice(device);
                                            int activeSessions = AuthManager.countActiveSessionsForDevice(device);

                                            ctx.getSource().sendSuccess(
                                                    new TextComponent("§b[IoCraft Security] Device: §f" + device), false);
                                            ctx.getSource().sendSuccess(
                                                    new TextComponent(" - Registrado: " + (exists ? "§aSí" : "§cNo")), false);
                                            ctx.getSource().sendSuccess(
                                                    new TextComponent(" - Roles: §e" + (roles.isEmpty() ? "(sin roles)" : String.join(", ", roles))), false);
                                            ctx.getSource().sendSuccess(
                                                    new TextComponent(" - Sesiones activas: §d" + activeSessions), false);
                                            return exists ? 1 : 0;
                                        })))
                ));
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(2);
    }
}
