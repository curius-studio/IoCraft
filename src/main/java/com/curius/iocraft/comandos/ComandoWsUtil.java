package com.curius.iocraft.comandos;

import com.curius.iocraft.ws.DeviceInfo;
import com.curius.iocraft.ws.DeviceRegistry;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;

import java.util.*;
import java.util.concurrent.CompletableFuture;

final class ComandoWsUtil {
    private ComandoWsUtil() {}

    static List<DeviceInfo> vivos() { return DeviceRegistry.snapshot(); }

    static CompletableFuture<Suggestions> sugerirTargets(SuggestionsBuilder builder) {
        List<DeviceInfo> list = vivos();
        for (int i = 0; i < list.size(); i++) {
            DeviceInfo d = list.get(i);
            String nombre = d.nombre != null ? d.nombre : "(sin-nombre)";
            String idx = "#" + (i + 1);
            String shortId = d.id.toString().substring(0, 8);

            maybeSuggest(builder, idx);
            maybeSuggest(builder, nombre);
            maybeSuggest(builder, shortId);
            if (d.ip != null) maybeSuggest(builder, d.ip);
        }
        return builder.buildFuture();
    }

    private static void maybeSuggest(SuggestionsBuilder b, String s) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        if (s.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(s);
    }

    /**
     * Resuelve el destino:
     * - "#N" índice 1-based
     * - UUID completo (si está conectado)
     * - Prefijo de UUID (match único)
     * - Nombre (exacto; si no, contiene) -> match único
     * - IP exacta -> match único
     */
    static Optional<UUID> resolverDestino(String token, CommandSourceStack src) {
        List<DeviceInfo> list = vivos();
        if (list.isEmpty()) {
            src.sendSuccess(new TextComponent("No hay dispositivos conectados."), false);
            return Optional.empty();
        }

        // #N
        if (token.startsWith("#")) {
            try {
                int n = Integer.parseInt(token.substring(1));
                if (n >= 1 && n <= list.size()) return Optional.of(list.get(n - 1).id);
                src.sendSuccess(new TextComponent("Índice fuera de rango: " + token), false);
                return Optional.empty();
            } catch (NumberFormatException ignored) {}
        }

        // UUID completo
        try {
            UUID id = UUID.fromString(token);
            for (DeviceInfo d : list) if (d.id.equals(id)) return Optional.of(id);
            src.sendSuccess(new TextComponent("UUID no pertenece a un conectado: " + token), false);
            return Optional.empty();
        } catch (IllegalArgumentException ignored) {}

        String low = token.toLowerCase(Locale.ROOT);

        // Prefijo de UUID
        List<DeviceInfo> porPref = new ArrayList<>();
        for (DeviceInfo d : list)
            if (d.id.toString().toLowerCase(Locale.ROOT).startsWith(low)) porPref.add(d);
        if (porPref.size() == 1) return Optional.of(porPref.get(0).id);
        if (porPref.size() > 1) {
            src.sendSuccess(new TextComponent("Prefijo ambiguo; candidatos: " + candidatos(porPref)), false);
            return Optional.empty();
        }

        // Nombre exacto
        List<DeviceInfo> exact = new ArrayList<>();
        for (DeviceInfo d : list)
            if (d.nombre != null && d.nombre.equalsIgnoreCase(token)) exact.add(d);
        if (exact.size() == 1) return Optional.of(exact.get(0).id);
        if (exact.size() > 1) {
            src.sendSuccess(new TextComponent("Nombre ambiguo; candidatos: " + candidatos(exact)), false);
            return Optional.empty();
        }

        // Nombre contiene
        List<DeviceInfo> contiene = new ArrayList<>();
        for (DeviceInfo d : list)
            if (d.nombre != null && d.nombre.toLowerCase(Locale.ROOT).contains(low)) contiene.add(d);
        if (contiene.size() == 1) return Optional.of(contiene.get(0).id);
        if (contiene.size() > 1) {
            src.sendSuccess(new TextComponent("Nombre ambiguo; candidatos: " + candidatos(contiene)), false);
            return Optional.empty();
        }

        // IP exacta
        for (DeviceInfo d : list) if (token.equals(d.ip)) return Optional.of(d.id);

        src.sendSuccess(new TextComponent("No se encontró destino para: " + token), false);
        return Optional.empty();
    }

    private static String candidatos(List<DeviceInfo> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            DeviceInfo d = list.get(i);
            String nombre = d.nombre != null ? d.nombre : "(sin-nombre)";
            String shortId = d.id.toString().substring(0, 8);
            if (i > 0) sb.append(", ");
            sb.append(nombre).append("/").append(shortId);
        }
        return sb.toString();
    }
}
