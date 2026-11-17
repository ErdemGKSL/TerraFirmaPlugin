/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

import dev.tfp.core.item.feature.ItemCount;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.ContentsPdc;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.tooltip.TooltipData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The item data the feature lines read (PAR-INFRA-50), from the packet copy's PDC records ({@link TooltipData}, Netty
 * thread, no Bukkit). TFC keeps each in a data component; here each is one versioned compound under our key. The
 * fluid ({@value FluidPdc#KEY}) and the item list ({@value ContentsPdc#KEY}) exist (PAR-INFRA-04); the others are
 * written by the features that create those items, in the layouts below:
 *
 * <pre>
 * tfp:egg     = { tfp:v: int 1, tfp:fertilized: byte (0/1), tfp:hatch_day: long (calendar day) }   TFC EggComponent
 * tfp:deposit = { tfp:v: int 1, tfp:item: string (item id) }                                       TFC DEPOSIT (pan)
 * tfp:plant   = { tfp:v: int 1, tfp:item: string (item id) }                                       TFC PLANT (cutting)
 * tfp:glass   = { tfp:v: int 1, tfp:batch: string (item id), tfp:steps: [string operation path] }   TFC GlassOperations
 * tfp:contents.tfp:view = [string "&lt;count&gt;\t&lt;item id&gt;\t&lt;identity&gt;" per non-empty slot, in slot order]
 * </pre>
 *
 * The {@code tfp:view} list is the tooltip summary of an item list: the slots themselves are serialized stacks, which
 * the Netty thread does not decode; the writer of the list adds it next to {@code tfp:items}. The identity is equal
 * for stacks TFC's barrel line merges (the item and its data). A record of a newer version than these reads as absent.
 */
public final class FeatureData {
    public static final String EGG = "tfp:egg";
    public static final String DEPOSIT = "tfp:deposit";
    public static final String PLANT = "tfp:plant";
    public static final String GLASS = "tfp:glass";
    public static final String VIEW = "tfp:view";
    public static final int VERSION = 1;

    static final String V = "tfp:v";
    static final String ITEM = "tfp:item";
    static final String FERTILIZED = "tfp:fertilized";
    static final String HATCH_DAY = "tfp:hatch_day";
    static final String BATCH = "tfp:batch";
    static final String STEPS = "tfp:steps";

    /** An egg: TFC {@code EggComponent} without the entity it hatches. */
    public record Egg(boolean fertilized, long hatchDay) {
    }

    /** A glass working record: the batch item and the operations so far. */
    public record Glass(String batch, List<String> steps) {
        public Glass {
            steps = List.copyOf(steps);
        }
    }

    private FeatureData() {
    }

    private static TooltipData record(TooltipData values, String key) {
        TooltipData r = values.compound(key);
        if (r == null) {
            return null;
        }
        Number v = r.number(V);
        return v == null || v.intValue() > VERSION ? null : r;
    }

    public static Optional<Egg> egg(TooltipData values) {
        TooltipData r = record(values, EGG);
        if (r == null) {
            return Optional.empty();
        }
        Number fertilized = r.number(FERTILIZED);
        Number day = r.number(HATCH_DAY);
        return Optional.of(new Egg(fertilized != null && fertilized.intValue() != 0, day == null ? 0 : day.longValue()));
    }

    /** The item id of a {@value #DEPOSIT} or {@value #PLANT} record. */
    public static Optional<String> itemRef(TooltipData values, String key) {
        TooltipData r = record(values, key);
        String item = r == null ? null : r.string(ITEM);
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(item);
    }

    public static Optional<Glass> glass(TooltipData values) {
        TooltipData r = record(values, GLASS);
        String batch = r == null ? null : r.string(BATCH);
        if (batch == null || batch.isEmpty()) {
            return Optional.empty();
        }
        List<String> steps = r.strings(STEPS);
        return Optional.of(new Glass(batch, steps == null ? List.of() : steps));
    }

    /** The fluid of a {@value FluidPdc#KEY} record, or {@link FluidAmount#EMPTY}. */
    public static FluidAmount fluid(TooltipData values) {
        TooltipData fluid = values.compound(FluidPdc.KEY);
        if (fluid == null) {
            return FluidAmount.EMPTY;
        }
        Number version = fluid.number(V);
        String id = fluid.string("tfp:id");
        Number amount = fluid.number("tfp:amount");
        if (version == null || version.intValue() > FluidPdc.VERSION || id == null || id.isEmpty() || amount == null
                || amount.intValue() <= 0) {
            return FluidAmount.EMPTY;
        }
        return new FluidAmount(id, amount.intValue());
    }

    /**
     * The tooltip summary of a {@value ContentsPdc#KEY} list, or empty when the item has none (no list, an older
     * list without the summary, or a newer version). Malformed entries are skipped.
     */
    public static Optional<List<ItemCount>> contents(TooltipData values) {
        TooltipData list = values.compound(ContentsPdc.KEY);
        if (list == null) {
            return Optional.empty();
        }
        Number v = list.number(V);
        List<String> view = list.strings(VIEW);
        if (v == null || v.intValue() > ContentsPdc.VERSION || view == null) {
            return Optional.empty();
        }
        List<ItemCount> out = new ArrayList<>(view.size());
        for (String entry : view) {
            parse(entry).ifPresent(out::add);
        }
        return Optional.of(out);
    }

    /** One {@code tfp:view} entry. */
    static Optional<ItemCount> parse(String entry) {
        String[] f = entry.split("\t", -1);
        if (f.length < 2 || f[1].isEmpty()) {
            return Optional.empty();
        }
        try {
            int count = Integer.parseInt(f[0]);
            if (count <= 0) {
                return Optional.empty();
            }
            String identity = f.length > 2 && !f[2].isEmpty() ? f[2] : f[1];
            return Optional.of(new ItemCount(f[1], identity, count));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The {@code tfp:view} entry of a slot (for the writers of item lists). */
    public static String viewEntry(ItemCount stack) {
        return stack.count() + "\t" + stack.itemId() + "\t" + stack.identity();
    }
}
