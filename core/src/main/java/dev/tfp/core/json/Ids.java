/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import dev.tfp.core.Tfp;

/**
 * Namespaced ids as plain strings ({@code namespace:path}), with the same character rules as
 * Minecraft's {@code ResourceLocation}. An id without a namespace means {@code minecraft:}.
 */
public final class Ids {
    private Ids() {
    }

    /**
     * Validates and normalises {@code raw} to {@code namespace:path}.
     *
     * @throws IllegalArgumentException if it is not a valid id
     */
    public static String parse(String raw) {
        int colon = raw.indexOf(':');
        String ns = colon < 0 ? "minecraft" : raw.substring(0, colon);
        String path = colon < 0 ? raw : raw.substring(colon + 1);
        if (ns.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("empty namespace or path in id '" + raw + "'");
        }
        for (int i = 0; i < ns.length(); i++) {
            if (!validNamespaceChar(ns.charAt(i))) {
                throw new IllegalArgumentException("invalid character '" + ns.charAt(i) + "' in namespace of id '" + raw + "'");
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!validNamespaceChar(c) && c != '/') {
                throw new IllegalArgumentException("invalid character '" + c + "' in path of id '" + raw + "'");
            }
        }
        return colon < 0 ? ns + ':' + path : raw;
    }

    /** The namespace datagen renames {@code tfc} to (see {@link Tfp#NAMESPACE}). */
    public static final String TFC = "tfc";

    /**
     * TFC's own id for {@code id}: {@code tfp:x} → {@code tfc:x}, others unchanged. Datagen renames
     * every {@code tfc:} id in the data, including registry type ids ({@code tfc:and} → {@code tfp:and});
     * our type registries keep TFC's names so they stay diffable against TFC, and accept both.
     */
    public static String toTfc(String id) {
        return namespace(id).equals(Tfp.NAMESPACE) ? TFC + ':' + path(id) : id;
    }

    public static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    public static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    private static boolean validNamespaceChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
    }
}
