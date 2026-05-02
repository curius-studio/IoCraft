package com.curius.iocraft.comandos;

import com.curius.iocraft.mensajeria.MensajeriaBus;
import com.curius.iocraft.security.AddonPolicyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ComandoAddons {
    private ComandoAddons() {}

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> policy = Commands.literal("policy")
                .requires(ComandoAddons::hasAdminPermission)
                .then(Commands.literal("list")
                        .executes(ctx -> policyList(ctx.getSource())))
                .then(Commands.literal("show")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(ctx -> policyShow(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("set-state")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("state", StringArgumentType.string())
                                        .executes(ctx -> policySetState(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "owner"),
                                                StringArgumentType.getString(ctx, "state"))))))
                .then(Commands.literal("allow-type")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .executes(ctx -> policyAllowType(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "owner"),
                                                StringArgumentType.getString(ctx, "type"))))))
                .then(Commands.literal("deny-type")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("type", StringArgumentType.string())
                                        .executes(ctx -> policyDenyType(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "owner"),
                                                StringArgumentType.getString(ctx, "type"))))))
                .then(Commands.literal("clear-rules")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(ctx -> policyClearRules(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("set-limits")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .then(Commands.argument("errors", IntegerArgumentType.integer(-1, 1_000_000))
                                        .then(Commands.argument("slow", IntegerArgumentType.integer(-1, 1_000_000))
                                                .executes(ctx -> policySetLimits(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "owner"),
                                                        IntegerArgumentType.getInteger(ctx, "errors"),
                                                        IntegerArgumentType.getInteger(ctx, "slow")))))))
                .then(Commands.literal("clear-owner")
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(ctx -> policyClearOwner(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))));

        LiteralArgumentBuilder<CommandSourceStack> addons = Commands.literal("addons")
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), null))
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(ctx -> status(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(Commands.literal("reset")
                        .executes(ctx -> resetAll(ctx.getSource()))
                        .then(Commands.argument("owner", StringArgumentType.string())
                                .executes(ctx -> resetOne(ctx.getSource(), StringArgumentType.getString(ctx, "owner")))))
                .then(policy);

        dispatcher.register(Commands.literal("ioc").then(addons));
    }

    private static boolean hasAdminPermission(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static int status(CommandSourceStack source, String ownerFilter) {
        List<MensajeriaBus.OwnerMetricSnapshot> snapshots = ownerFilter == null
                ? MensajeriaBus.snapshotOwnerMetrics()
                : MensajeriaBus.snapshotOwnerMetrics(ownerFilter);
        source.sendSuccess(new TextComponent(
                "§b[IoCraft Addons] Owners activos/métricas: " + snapshots.size()
                        + (ownerFilter == null ? "" : " §7(filtro=" + ownerFilter + ")")
        ), false);

        if (snapshots.isEmpty()) {
            source.sendSuccess(new TextComponent("§7No hay addons registrados ni métricas aún."), false);
            return 0;
        }

        for (MensajeriaBus.OwnerMetricSnapshot owner : snapshots) {
            source.sendSuccess(new TextComponent(
                    "§e- " + owner.ownerModId()
                            + " §7handlers=" + owner.activeHandlers()
                            + " §7handled=" + owner.handled()
                            + " §7errors=" + owner.errors()
                            + " §7slow=" + owner.slow()
                            + " §7avgMs=" + String.format("%.2f", owner.avgMs())
            ), false);

            owner.byType().values().stream()
                    .sorted(Comparator.comparing(MensajeriaBus.TypeMetricSnapshot::type))
                    .forEach(type -> source.sendSuccess(new TextComponent(
                            "  §8* " + type.type()
                                    + " §7handled=" + type.handled()
                                    + " §7errors=" + type.errors()
                                    + " §7slow=" + type.slow()
                                    + " §7avgMs=" + String.format("%.2f", type.avgMs())
                    ), false));
        }
        return snapshots.size();
    }

    private static int resetAll(CommandSourceStack source) {
        MensajeriaBus.clearOwnerMetrics();
        source.sendSuccess(new TextComponent("§aMétricas de addons reiniciadas (global)."), false);
        return 1;
    }

    private static int resetOne(CommandSourceStack source, String owner) {
        try {
            boolean removed = MensajeriaBus.clearOwnerMetrics(owner);
            if (removed) {
                source.sendSuccess(new TextComponent("§aMétricas reiniciadas para owner: " + owner), false);
                return 1;
            }
            source.sendSuccess(new TextComponent("§eNo existían métricas para owner: " + owner), false);
            return 0;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cowner inválido: " + owner));
            return 0;
        }
    }

    private static int policyList(CommandSourceStack source) {
        var policies = AddonPolicyManager.snapshotPolicies();
        source.sendSuccess(new TextComponent("§b[IoCraft Addons] Policies: " + policies.size()), false);
        if (policies.isEmpty()) {
            source.sendSuccess(new TextComponent("§7No hay políticas configuradas."), false);
            return 0;
        }
        policies.values().forEach(p -> printPolicyLine(source, p));
        return policies.size();
    }

    private static int policyShow(CommandSourceStack source, String owner) {
        try {
            var p = AddonPolicyManager.snapshotPolicy(owner);
            if (p == null) {
                source.sendSuccess(new TextComponent("§eSin policy para owner: " + owner), false);
                return 0;
            }
            printPolicyLine(source, p);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cowner inválido: " + owner));
            return 0;
        }
    }

    private static int policySetState(CommandSourceStack source, String owner, String stateRaw) {
        try {
            AddonPolicyManager.OwnerState state = AddonPolicyManager.OwnerState.valueOf(
                    stateRaw.trim().toUpperCase(Locale.ROOT)
            );
            var p = AddonPolicyManager.setState(owner, state);
            source.sendSuccess(new TextComponent("§aState actualizado para " + owner + " -> " + p.state()), false);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cParámetros inválidos. state: ENABLED|DISABLED|QUARANTINED"));
            return 0;
        }
    }

    private static int policyAllowType(CommandSourceStack source, String owner, String type) {
        try {
            var p = AddonPolicyManager.allowType(owner, type);
            source.sendSuccess(new TextComponent("§aallow-type aplicado. owner=" + p.ownerModId() + " type=" + type), false);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cParámetros inválidos."));
            return 0;
        }
    }

    private static int policyDenyType(CommandSourceStack source, String owner, String type) {
        try {
            var p = AddonPolicyManager.denyType(owner, type);
            source.sendSuccess(new TextComponent("§adeny-type aplicado. owner=" + p.ownerModId() + " type=" + type), false);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cParámetros inválidos."));
            return 0;
        }
    }

    private static int policyClearRules(CommandSourceStack source, String owner) {
        try {
            var p = AddonPolicyManager.clearRules(owner);
            source.sendSuccess(new TextComponent("§aReglas limpiadas para owner=" + p.ownerModId()), false);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cowner inválido."));
            return 0;
        }
    }

    private static int policySetLimits(CommandSourceStack source, String owner, int errors, int slow) {
        try {
            var p = AddonPolicyManager.setLimits(owner, errors, slow);
            source.sendSuccess(new TextComponent("§aLímites actualizados owner=" + p.ownerModId()
                    + " errors=" + p.quarantineOnErrors() + " slow=" + p.quarantineOnSlow()), false);
            return 1;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cowner inválido."));
            return 0;
        }
    }

    private static int policyClearOwner(CommandSourceStack source, String owner) {
        try {
            boolean removed = AddonPolicyManager.clearOwner(owner);
            source.sendSuccess(new TextComponent(
                    removed ? "§aPolicy eliminada para owner=" + owner : "§eNo existía policy para owner=" + owner
            ), false);
            return removed ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            source.sendFailure(new TextComponent("§cowner inválido."));
            return 0;
        }
    }

    private static void printPolicyLine(CommandSourceStack source, AddonPolicyManager.PolicySnapshot p) {
        source.sendSuccess(new TextComponent(
                "§e- " + p.ownerModId()
                        + " §7state=" + p.state()
                        + " §7allow=" + p.allowTypes()
                        + " §7deny=" + p.denyTypes()
                        + " §7qErr=" + p.quarantineOnErrors()
                        + " §7qSlow=" + p.quarantineOnSlow()
        ), false);
    }
}
