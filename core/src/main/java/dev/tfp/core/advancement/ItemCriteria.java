/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/advancement/story/find_rock.json @ c3b4d9536 (inventory_changed, #tfc:rock_knapping)
 */
package dev.tfp.core.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * PAR-DATA-02: criteria that TFC grants with {@code minecraft:inventory_changed} when an item of a tag enters the
 * player's inventory. The vanilla trigger cannot see our CraftEngine items' tags, so the plugin evaluates the tag on each
 * slot change and awards the criterion by name. This table holds the first story step ({@link #FIND_ROCK}); the rest of
 * TFC's criteria are wired by PAR-DATA-03.
 *
 * <p>Immutable; any thread.
 */
public final class ItemCriteria {
    /** One criterion: granted when an item in {@code itemTag} (our tag id) enters the inventory. */
    public record Entry(CriterionRef criterion, String itemTag) {
        public Entry {
            Objects.requireNonNull(criterion, "criterion");
            Objects.requireNonNull(itemTag, "itemTag");
        }
    }

    /** {@code tfc:story/find_rock}, "Just a Rock": any item of {@code #tfc:rock_knapping} (loose rocks, obsidian shards). */
    public static final Entry FIND_ROCK = new Entry(new CriterionRef("tfp:story/find_rock", "rock_knapping"),
        "tfp:rock_knapping");

    /** The table the plugin listens for. */
    public static final ItemCriteria DEFAULT = new ItemCriteria(List.of(FIND_ROCK));

    private final List<Entry> entries;

    public ItemCriteria(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * The criteria an item earns: those whose tag the item is in ({@code inTag} answers for one tag id) and that are not
     * {@code done} yet, in table order. An empty slot earns nothing; the caller does not ask for it.
     */
    public List<CriterionRef> earned(Predicate<String> inTag, Predicate<CriterionRef> done) {
        List<CriterionRef> out = new ArrayList<>(1);
        for (Entry e : entries) {
            if (!done.test(e.criterion()) && inTag.test(e.itemTag())) {
                out.add(e.criterion());
            }
        }
        return out;
    }
}
