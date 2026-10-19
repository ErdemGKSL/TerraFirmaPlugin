/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/CountBlockCommand.java (the block predicate argument,
 * {@code BlockPredicateArgument.blockPredicate}, which Brigadier reads as an id, a tag or a state filter) @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The block argument of {@code /tfp count}: {@code <id>}, {@code <id>[prop=value,...]} or {@code #<tag>}. The id is
 * {@code namespace:path}, or {@code path} in the {@code minecraft} namespace, as in Brigadier. A {@code #} names a block
 * tag. NBT ({@code {...}}) is not supported and is refused.
 *
 * <p>Matching is over strings the glue reads from the world, so this type knows no registry: the id, the tags and the
 * state properties of a block are passed in.
 *
 * @param kind       whether the selector names a block or a tag
 * @param name       the block id or the tag id, with its namespace
 * @param properties the state properties the block must have, sorted by name; empty matches any state
 */
public record BlockSelector(Kind kind, String name, Map<String, String> properties) {
    /** What the name refers to. */
    public enum Kind {
        BLOCK, TAG
    }

    /** Validates and copies the properties into a sorted, unmodifiable map. */
    public BlockSelector {
        if (kind == null || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("kind and name are required");
        }
        properties = Collections.unmodifiableMap(new TreeMap<>(properties));
    }

    /**
     * Parses one selector token.
     *
     * @return the selector, or {@link Error#BAD_SYNTAX} for a bad id, an unclosed or empty property, or NBT
     */
    public static Parsed<BlockSelector> parse(String token) {
        if (token.indexOf('{') >= 0) {
            return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
        }
        String rest = token;
        Kind kind = Kind.BLOCK;
        if (rest.startsWith("#")) {
            kind = Kind.TAG;
            rest = rest.substring(1);
        }
        Map<String, String> properties = new TreeMap<>();
        int open = rest.indexOf('[');
        if (open >= 0) {
            if (kind == Kind.TAG || !rest.endsWith("]")) {
                return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
            }
            String body = rest.substring(open + 1, rest.length() - 1);
            rest = rest.substring(0, open);
            if (!body.isEmpty()) {
                for (String pair : body.split(",", -1)) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0 || eq == pair.length() - 1 || properties.put(pair.substring(0, eq), pair.substring(eq + 1)) != null) {
                        return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
                    }
                }
            }
        }
        if (rest.isEmpty()) {
            return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
        }
        String name = rest.indexOf(':') < 0 ? "minecraft:" + rest : rest;
        if (name.indexOf(':') != name.lastIndexOf(':') || name.endsWith(":") || name.startsWith(":")) {
            return new Parsed.Refused<>(Error.BAD_SYNTAX, token);
        }
        return new Parsed.Ok<>(new BlockSelector(kind, name, properties));
    }

    /**
     * Whether a block matches the selector.
     *
     * @param blockId         the block's id with its namespace, such as {@code tfc:rock/raw/granite}
     * @param tags            the block's tags with their namespace, without the {@code #}
     * @param stateProperties the block state's property values, by name
     */
    public boolean matches(String blockId, Set<String> tags, Map<String, String> stateProperties) {
        if (kind == Kind.TAG) {
            return tags.contains(name);
        }
        if (!name.equals(blockId)) {
            return false;
        }
        for (Map.Entry<String, String> required : properties.entrySet()) {
            if (!required.getValue().equals(stateProperties.get(required.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
