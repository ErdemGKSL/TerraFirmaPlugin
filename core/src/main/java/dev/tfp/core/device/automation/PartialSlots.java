/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/capabilities/PartialItemHandler.java @ c3b4d9536 (insert, extract,
 *       insertAll, extractAll)
 */
package dev.tfp.core.device.automation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * TFC {@code PartialItemHandler}: which slots of a device's inventory one side may insert into and extract from. The
 * lists are ascending and free of duplicates, the order in which a hopper tries the slots (NeoForge's
 * {@code VanillaInventoryCodeHooks} walks {@code 0 .. getSlots()}; a slot outside the lists answers "no"). Immutable.
 *
 * @param slots   the inventory size (TFC {@code internal.getSlots()})
 * @param insert  slots that accept insertion
 * @param extract slots that give out items
 */
public record PartialSlots(int slots, List<Integer> insert, List<Integer> extract) {
    public PartialSlots {
        if (slots < 0) {
            throw new IllegalArgumentException("slots < 0");
        }
        insert = normalised(slots, insert);
        extract = normalised(slots, extract);
    }

    /** {@code new PartialItemHandler(inventory)}: nothing in, nothing out. */
    public static PartialSlots of(int slots) {
        return new PartialSlots(slots, List.of(), List.of());
    }

    /** TFC {@code insert(slots...)}: adds insertion slots. */
    public PartialSlots insert(int... more) {
        return new PartialSlots(slots, concat(insert, more), extract);
    }

    /** TFC {@code extract(slots...)}: adds extraction slots. */
    public PartialSlots extract(int... more) {
        return new PartialSlots(slots, insert, concat(extract, more));
    }

    /** TFC {@code insertAll()}. */
    public PartialSlots insertAll() {
        return new PartialSlots(slots, IntStream.range(0, slots).boxed().toList(), extract);
    }

    /** TFC {@code extractAll()}. */
    public PartialSlots extractAll() {
        return new PartialSlots(slots, insert, IntStream.range(0, slots).boxed().toList());
    }

    public boolean canInsert(int slot) {
        return insert.contains(slot);
    }

    public boolean canExtract(int slot) {
        return extract.contains(slot);
    }

    private static List<Integer> concat(List<Integer> a, int[] b) {
        return IntStream.concat(a.stream().mapToInt(Integer::intValue), Arrays.stream(b)).boxed().toList();
    }

    private static List<Integer> normalised(int slots, List<Integer> in) {
        for (int s : in) {
            if (s < 0 || s >= slots) {
                // TFC: ArrayIndexOutOfBoundsException on insertSlots[slot]
                throw new IllegalArgumentException("slot " + s + " outside 0.." + (slots - 1));
            }
        }
        return in.stream().distinct().sorted().toList();
    }
}
