/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import kr.toxicity.hud.api.placeholder.HudPlaceholder;
import kr.toxicity.hud.api.player.HudPlayer;
import kr.toxicity.hud.api.update.UpdateEvent;

/**
 * Evaluates a number placeholder from BetterHud's own registry ({@code getNumberContainer().getAllPlaceholders()})
 * for a player id, as BetterHud does on its update thread: {@code invoke(args, event).apply(hudPlayer)}. The
 * {@link HudPlayer} is a proxy that answers only {@code uuid()} and {@code name()}: our placeholders read
 * nothing else (M1-INT-04), so any other call fails loudly instead of touching a world.
 */
final class PlaceholderProbe {
    private PlaceholderProbe() {
    }

    static OptionalDouble evaluate(Map<String, HudPlaceholder<?>> placeholders, String name, UUID player) {
        HudPlaceholder<?> p = placeholders.get(name);
        if (p == null) {
            return OptionalDouble.empty();
        }
        Function<HudPlayer, ?> f = p.invoke(List.of(), UpdateEvent.EMPTY);
        Object value = f.apply(player(player));
        if (!(value instanceof Number n)) {
            throw new IllegalStateException("placeholder " + name + " returned " + value + " instead of a number");
        }
        return OptionalDouble.of(n.doubleValue());
    }

    static HudPlayer player(UUID id) {
        InvocationHandler h = (proxy, method, args) -> switch (method.getName()) {
            case "uuid" -> id;
            case "name" -> "tfp-probe";
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "HudPlayer probe " + id;
            default -> throw new UnsupportedOperationException("the placeholder probe player has no " + method.getName()
                    + "(): a tfp placeholder must only read the snapshot cache by uuid");
        };
        return (HudPlayer) Proxy.newProxyInstance(HudPlayer.class.getClassLoader(), new Class<?>[] {HudPlayer.class}, h);
    }
}
