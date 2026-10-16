/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Minimal dynamic-proxy fakes of the few Bukkit interfaces the boot code reads. */
final class Fakes {
    private Fakes() {
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler h) {
        return (T) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {type}, h);
    }

    /** A server running {@code minecraft} with the given plugin name → version installed. */
    static Server server(String minecraft, Map<String, String> plugins) {
        PluginManager pm = proxy(PluginManager.class, (p, m, a) -> {
            if (m.getName().equals("getPlugin")) {
                String version = plugins.get((String) a[0]);
                return version == null ? null : plugin(version);
            }
            throw new UnsupportedOperationException(m.getName());
        });
        return proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getMinecraftVersion" -> minecraft;
            case "getPluginManager" -> pm;
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }

    static Plugin plugin(String version) {
        PluginMeta meta = proxy(PluginMeta.class, (p, m, a) -> {
            if (m.getName().equals("getVersion")) {
                return version;
            }
            throw new UnsupportedOperationException(m.getName());
        });
        return proxy(Plugin.class, (p, m, a) -> {
            if (m.getName().equals("getPluginMeta")) {
                return meta;
            }
            throw new UnsupportedOperationException(m.getName());
        });
    }

    /** A command source whose sender has {@code permissions} and records plain-text messages into {@code out}. */
    static CommandSourceStack source(Set<String> permissions, List<String> out) {
        CommandSender sender = proxy(CommandSender.class, (p, m, a) -> switch (m.getName()) {
            case "hasPermission" -> permissions.contains(String.valueOf(a[0]));
            case "sendMessage" -> {
                if (a.length == 1 && a[0] instanceof Component c) {
                    out.add(PlainTextComponentSerializer.plainText().serialize(c));
                } else if (a.length == 1 && a[0] instanceof String s) {
                    out.add(s);
                }
                yield null;
            }
            case "getName" -> "tester";
            default -> throw new UnsupportedOperationException(m.getName());
        });
        return proxy(CommandSourceStack.class, (p, m, a) -> {
            if (m.getName().equals("getSender")) {
                return sender;
            }
            throw new UnsupportedOperationException(m.getName());
        });
    }
}
