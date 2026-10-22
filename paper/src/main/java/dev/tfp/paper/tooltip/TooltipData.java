/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import java.util.List;
import java.util.Map;

/**
 * A read-only view of one NBT compound of an outgoing item (PAR-INFRA-05), free of the packet library: the packet layer
 * adapts the stack's {@code custom_data.PublicBukkitValues} (our PDC records, such as {@code tfp:heat} and
 * {@code tfp:food}) to this. Every getter returns {@code null} when the key is missing or holds another type.
 *
 * <p>Valid only during the contributor call on the Netty thread; never keep it.
 */
public interface TooltipData {
    /** No values. */
    TooltipData EMPTY = of(Map.of());

    /** A number tag (byte, short, int, long, float or double). */
    Number number(String key);

    /** A string tag. */
    String string(String key);

    /** A nested compound. */
    TooltipData compound(String key);

    /** A list of strings. */
    List<String> strings(String key);

    /**
     * A view over plain Java values (tests and callers without NBT): {@link Number}, {@link String},
     * {@code Map<String, ?>} for a compound and {@code List<String>} for a string list.
     */
    static TooltipData of(Map<String, ?> values) {
        return new TooltipData() {
            @Override
            public Number number(String key) {
                return values.get(key) instanceof Number n ? n : null;
            }

            @Override
            public String string(String key) {
                return values.get(key) instanceof String s ? s : null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public TooltipData compound(String key) {
                return values.get(key) instanceof Map<?, ?> m ? of((Map<String, ?>) m) : null;
            }

            @Override
            public List<String> strings(String key) {
                if (!(values.get(key) instanceof List<?> list)) {
                    return null;
                }
                for (Object o : list) {
                    if (!(o instanceof String)) {
                        return null;
                    }
                }
                return list.stream().map(String.class::cast).toList();
            }
        };
    }
}
