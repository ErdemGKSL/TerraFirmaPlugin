/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Vanilla-block suppression rules (M1-INST-04, ARCHITECTURE.md §9.1): in a TFC world no repurposed vanilla
 * block may be created, because its client-side look is one of our blocks. The Paper listeners
 * ({@code dev.tfp.paper.suppression}) classify each event into a {@link Path} and apply the {@link Verdict}
 * this class returns. Pure and thread-safe.
 */
public final class Suppression {
    /** What the vanilla game does when a block is about to be created. */
    public enum Path {
        /** Lava meets water (or soul soil + blue ice): stone, cobblestone, obsidian, basalt. */
        FLUID_FORM,
        /** Concrete powder touching water. */
        CONCRETE_HARDENING,
        /** Copper oxidising (random tick). */
        WEATHERING,
        /** Any other block formation (mud → clay under dripstone, snow, ice, ...). */
        FORM,
        /** A block dying or drying (coral death). */
        FADE,
        /** Grass, mycelium, sculk, moss spreading. */
        SPREAD,
        /** Bone meal growth (moss). */
        FERTILIZE,
        /** An entity changing a block: axe scraping, honeycomb waxing, endermen, falling blocks. */
        ENTITY_CHANGE,
        /** A player placing a block item (incl. a wet sponge that dries at once in an ultrawarm world). */
        PLACE,
        /** A dispenser placing or using a block item. Best effort. */
        DISPENSE,
        /** {@code /setblock}, {@code /fill}. Best effort. */
        COMMAND
    }

    /** What the listener does. */
    public sealed interface Verdict {
        Verdict ALLOW = new Allow();
        Verdict CANCEL = new Cancel();

        /** Not a repurposed block: leave the event alone. */
        record Allow() implements Verdict {
        }

        /** Cancel the event (the old block stays). */
        record Cancel() implements Verdict {
        }

        /** Let the event happen but create {@code state} instead (a block shown the same way, not repurposed). */
        record Replace(String state) implements Verdict {
        }
    }

    /** Last resort for lava + water when the allocation maps the block to nothing usable. */
    public static final String FALLBACK_STONE = "minecraft:stone";

    private final SuppressionSet set;

    public Suppression(SuppressionSet set) {
        this.set = set;
    }

    public SuppressionSet set() {
        return set;
    }

    /**
     * @param path the creation path
     * @param from the state before (may be null when unknown)
     * @param to   the state that would be created
     */
    public Verdict decide(Path path, String from, String to) {
        if (to == null || !set.suppressed(to)) {
            return Verdict.ALLOW;
        }
        if (path == Path.FLUID_FORM) {
            // Cancelling would leave the lava flowing forever: create the block it is shown as instead.
            Optional<String> look = set.lookAlike(to).filter(s -> !set.suppressed(s));
            if (look.isPresent()) {
                return new Verdict.Replace(look.get());
            }
            return set.suppressed(FALLBACK_STONE) ? Verdict.CANCEL : new Verdict.Replace(FALLBACK_STONE);
        }
        return Verdict.CANCEL;
    }

    /** True if the item (block item id) would place a repurposed block. */
    public boolean suppressedItem(String itemId) {
        return set.suppressed(itemId);
    }

    /** Classifies a {@code BlockFormEvent} by the block before and the state it would become. */
    public static Path classifyForm(String from, String to) {
        String f = from == null ? "" : SuppressionSet.blockId(from);
        String t = to == null ? "" : SuppressionSet.blockId(to);
        if (f.equals("minecraft:lava") || f.equals("minecraft:water")) {
            return Path.FLUID_FORM;
        }
        if (f.endsWith("_concrete_powder") && t.endsWith("_concrete")) {
            return Path.CONCRETE_HARDENING;
        }
        if (f.contains("copper") && t.contains("copper")) {
            return Path.WEATHERING;
        }
        return Path.FORM;
    }

    /**
     * Which of our listener paths cover a datagen creation path (empty: not covered). {@code item} covers
     * player placement, dispensers and commands; recipes and loot are handled by {@link #recipesToDisable}
     * and the drop/loot hooks.
     */
    public static Set<Path> coverage(String creationPath) {
        return switch (creationPath) {
            case "lava_water", "lava_soul_soil_blue_ice" -> EnumSet.of(Path.FLUID_FORM);
            case "concrete_powder_water" -> EnumSet.of(Path.CONCRETE_HARDENING, Path.ENTITY_CHANGE);
            case "copper_weathering_scraping_waxing" -> EnumSet.of(Path.WEATHERING, Path.ENTITY_CHANGE);
            case "coral_death" -> EnumSet.of(Path.FADE);
            case "block_spread", "sculk_spread" -> EnumSet.of(Path.SPREAD);
            case "moss_bonemeal" -> EnumSet.of(Path.FERTILIZE, Path.SPREAD);
            case "mud_drying_under_dripstone" -> EnumSet.of(Path.FORM);
            case "wet_sponge_drying" -> EnumSet.of(Path.PLACE);
            case "item" -> EnumSet.of(Path.PLACE, Path.DISPENSE, Path.COMMAND);
            default -> EnumSet.noneOf(Path.class);
        };
    }

    /**
     * The vanilla ({@code minecraft:}) recipes whose result is a repurposed block: the CraftEngine
     * {@code recipe.disable-vanilla-recipes.list}. Sorted.
     *
     * @param recipeResults recipe key → result item id, as the server lists them
     */
    public List<String> recipesToDisable(Map<String, String> recipeResults) {
        Set<String> out = new TreeSet<>();
        recipeResults.forEach((key, result) -> {
            if (key.startsWith("minecraft:") && result != null && set.suppressed(result)) {
                out.add(key);
            }
        });
        return List.copyOf(out);
    }

    /** The items of {@code itemIds} that are repurposed blocks (loot and drops to strip). */
    public List<String> suppressedItems(Collection<String> itemIds) {
        List<String> out = new ArrayList<>();
        for (String id : itemIds) {
            if (set.suppressed(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The repurposed block a {@code /setblock} or {@code /fill} (also behind {@code execute ... run}) would
     * place, if any. Best effort: other commands ({@code /clone}, {@code /place}, functions) are not seen.
     */
    public Optional<String> commandPlaces(String commandLine) {
        if (commandLine == null) {
            return Optional.empty();
        }
        List<String> t = tokens(commandLine.strip().startsWith("/") ? commandLine.strip().substring(1) : commandLine.strip());
        for (int i = 0; i < t.size(); i++) {
            String name = t.get(i).toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            if (i > 0 && !t.get(i - 1).equalsIgnoreCase("run")) {
                continue;
            }
            int blockAt = switch (name) {
                case "setblock" -> i + 4;
                case "fill" -> i + 7;
                default -> -1;
            };
            if (blockAt > 0 && blockAt < t.size()) {
                String block = t.get(blockAt);
                if (block.startsWith("#")) {
                    return Optional.empty();
                }
                return set.suppressed(block) ? Optional.of(SuppressionSet.blockId(block)) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Splits on spaces outside brackets/braces (block states and NBT may contain spaces). */
    private static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '[' || c == '{') {
                depth++;
            } else if ((c == ']' || c == '}') && depth > 0) {
                depth--;
            }
            if (c == ' ' && depth == 0) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) {
            out.add(cur.toString());
        }
        return out;
    }
}
