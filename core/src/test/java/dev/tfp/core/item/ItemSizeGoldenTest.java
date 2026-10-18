/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M3-SURV-05 golden values: TFC's size and weight enums, the default size classes, the configured container limits,
 * the bundle and barrel predicates and {@code Helpers.getCarryCount}, at c3b4d9536. The expected rows were produced by
 * a scratch oracle that reads the constants and expressions from the TFC source.
 *
 * <p>Rows:
 * <ul>
 *   <li>{@code W|weight|stackSize}: TFC {@code Weight} stack sizes.</li>
 *   <li>{@code G|size|weight|stackSize}: the stack size grid for every size and weight. The stack size depends on
 *       weight only.</li>
 *   <li>{@code H|class|size|weight}: the default sizing of each item class.</li>
 *   <li>{@code C|size|fits}, {@code V|size|fits}, {@code L|size|fits}: chest, small vessel and large vessel
 *       acceptance, with TFC's default limits.</li>
 *   <li>{@code P|size|slot}: placed-on-ground slot with TFC's default limits.</li>
 *   <li>{@code B|size|weight|barrel|bundle}: barrel and bundle acceptance (1 = accepted).</li>
 *   <li>{@code O|items|burden}: carry count of an inventory, items as {@code size:weight}.</li>
 * </ul>
 */
class ItemSizeGoldenTest {
    // from TFC at c3b4d9536
    private static final String GOLDEN = """
            W|VERY_LIGHT|64
            W|LIGHT|32
            W|MEDIUM|16
            W|HEAVY|4
            W|VERY_HEAVY|1
            G|TINY|VERY_LIGHT|64
            G|VERY_SMALL|VERY_LIGHT|64
            G|SMALL|VERY_LIGHT|64
            G|NORMAL|VERY_LIGHT|64
            G|LARGE|VERY_LIGHT|64
            G|VERY_LARGE|VERY_LIGHT|64
            G|HUGE|VERY_LIGHT|64
            G|TINY|LIGHT|32
            G|VERY_SMALL|LIGHT|32
            G|SMALL|LIGHT|32
            G|NORMAL|LIGHT|32
            G|LARGE|LIGHT|32
            G|VERY_LARGE|LIGHT|32
            G|HUGE|LIGHT|32
            G|TINY|MEDIUM|16
            G|VERY_SMALL|MEDIUM|16
            G|SMALL|MEDIUM|16
            G|NORMAL|MEDIUM|16
            G|LARGE|MEDIUM|16
            G|VERY_LARGE|MEDIUM|16
            G|HUGE|MEDIUM|16
            G|TINY|HEAVY|4
            G|VERY_SMALL|HEAVY|4
            G|SMALL|HEAVY|4
            G|NORMAL|HEAVY|4
            G|LARGE|HEAVY|4
            G|VERY_LARGE|HEAVY|4
            G|HUGE|HEAVY|4
            G|TINY|VERY_HEAVY|1
            G|VERY_SMALL|VERY_HEAVY|1
            G|SMALL|VERY_HEAVY|1
            G|NORMAL|VERY_HEAVY|1
            G|LARGE|VERY_HEAVY|1
            G|VERY_LARGE|VERY_HEAVY|1
            G|HUGE|VERY_HEAVY|1
            H|TOOL|LARGE|MEDIUM
            H|ARMOR|LARGE|VERY_HEAVY
            H|BLOCK|SMALL|LIGHT
            H|OTHER|VERY_SMALL|VERY_LIGHT
            C|TINY|1
            C|VERY_SMALL|1
            C|SMALL|1
            C|NORMAL|1
            C|LARGE|1
            C|VERY_LARGE|0
            C|HUGE|0
            V|TINY|1
            V|VERY_SMALL|1
            V|SMALL|1
            V|NORMAL|0
            V|LARGE|0
            V|VERY_LARGE|0
            V|HUGE|0
            L|TINY|1
            L|VERY_SMALL|1
            L|SMALL|1
            L|NORMAL|1
            L|LARGE|0
            L|VERY_LARGE|0
            L|HUGE|0
            P|TINY|GROUP
            P|VERY_SMALL|GROUP
            P|SMALL|GROUP
            P|NORMAL|GROUP
            P|LARGE|GROUP
            P|VERY_LARGE|SINGLE
            P|HUGE|SINGLE
            B|TINY|VERY_LIGHT|1|1
            B|TINY|LIGHT|1|1
            B|TINY|MEDIUM|1|0
            B|TINY|HEAVY|1|0
            B|TINY|VERY_HEAVY|1|0
            B|VERY_SMALL|VERY_LIGHT|1|1
            B|VERY_SMALL|LIGHT|1|1
            B|VERY_SMALL|MEDIUM|1|0
            B|VERY_SMALL|HEAVY|1|0
            B|VERY_SMALL|VERY_HEAVY|1|0
            B|SMALL|VERY_LIGHT|1|1
            B|SMALL|LIGHT|1|1
            B|SMALL|MEDIUM|1|0
            B|SMALL|HEAVY|1|0
            B|SMALL|VERY_HEAVY|1|0
            B|NORMAL|VERY_LIGHT|1|1
            B|NORMAL|LIGHT|1|1
            B|NORMAL|MEDIUM|1|0
            B|NORMAL|HEAVY|1|0
            B|NORMAL|VERY_HEAVY|1|0
            B|LARGE|VERY_LIGHT|1|0
            B|LARGE|LIGHT|1|0
            B|LARGE|MEDIUM|1|0
            B|LARGE|HEAVY|1|0
            B|LARGE|VERY_HEAVY|1|0
            B|VERY_LARGE|VERY_LIGHT|1|0
            B|VERY_LARGE|LIGHT|1|0
            B|VERY_LARGE|MEDIUM|1|0
            B|VERY_LARGE|HEAVY|1|0
            B|VERY_LARGE|VERY_HEAVY|1|0
            B|HUGE|VERY_LIGHT|1|0
            B|HUGE|LIGHT|1|0
            B|HUGE|MEDIUM|1|0
            B|HUGE|HEAVY|1|0
            B|HUGE|VERY_HEAVY|0|0
            O|-|NONE
            O|TINY:VERY_LIGHT|NONE
            O|LARGE:VERY_HEAVY|NONE
            O|HUGE:HEAVY|NONE
            O|HUGE:VERY_HEAVY|ONE
            O|HUGE:VERY_HEAVY,HUGE:VERY_HEAVY|MORE_THAN_ONE
            O|HUGE:VERY_HEAVY,HUGE:HEAVY,VERY_LARGE:VERY_HEAVY|ONE
            O|HUGE:HEAVY,HUGE:HEAVY,HUGE:HEAVY|NONE
            O|VERY_LARGE:VERY_HEAVY,VERY_LARGE:VERY_HEAVY|NONE
            O|HUGE:VERY_HEAVY,TINY:VERY_LIGHT,HUGE:VERY_HEAVY,HUGE:VERY_HEAVY|MORE_THAN_ONE
            """;

    private static final ItemSizeLimits LIMITS = ItemSizeLimits.DEFAULT;

    @Test
    void goldenRowsMatchTfc() {
        List<String> failures = new ArrayList<>();
        int rows = 0;
        for (String line : GOLDEN.strip().lines().toList()) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            rows++;
            String expected = line;
            String actual = evaluate(line);
            if (!expected.equals(actual)) {
                failures.add("expected " + expected + " got " + actual);
            }
        }
        assertThat(failures).isEmpty();
        assertThat(rows).isEqualTo(117);
    }

    private static String evaluate(String row) {
        String[] f = row.split("\\|");
        return switch (f[0]) {
            case "W" -> "W|" + ItemWeight.valueOf(f[1]).name() + "|" + ItemWeight.valueOf(f[1]).stackSize();
            case "G" -> "G|" + f[1] + "|" + f[2] + "|" + new ItemSizing(ItemSize.valueOf(f[1]), ItemWeight.valueOf(f[2])).maxStackSize();
            case "H" -> "H|" + f[1] + "|" + ItemSizing.ItemClass.valueOf(f[1]).defaultSizing().size() + "|"
                + ItemSizing.ItemClass.valueOf(f[1]).defaultSizing().weight();
            case "C" -> "C|" + f[1] + "|" + bit(ContainerRules.chest(sized(f[1], ItemWeight.VERY_LIGHT), LIMITS));
            case "V" -> "V|" + f[1] + "|" + bit(ContainerRules.smallVessel(sized(f[1], ItemWeight.VERY_LIGHT), LIMITS));
            case "L" -> "L|" + f[1] + "|" + bit(ContainerRules.largeVessel(sized(f[1], ItemWeight.VERY_LIGHT)));
            case "P" -> "P|" + f[1] + "|" + ContainerRules.placed(sized(f[1], ItemWeight.VERY_LIGHT), LIMITS);
            case "B" -> "B|" + f[1] + "|" + f[2] + "|" + bit(ContainerRules.barrel(sizedW(f[1], f[2])))
                + "|" + bit(ContainerRules.bundle(sizedW(f[1], f[2])));
            case "O" -> "O|" + f[1] + "|" + Burden.of(parseItems(f[1]));
            default -> throw new IllegalArgumentException(row);
        };
    }

    private static ItemSizing sized(String size, ItemWeight weight) {
        return new ItemSizing(ItemSize.valueOf(size), weight);
    }

    private static ItemSizing sizedW(String size, String weight) {
        return new ItemSizing(ItemSize.valueOf(size), ItemWeight.valueOf(weight));
    }

    private static List<ItemSizing> parseItems(String tokens) {
        List<ItemSizing> items = new ArrayList<>();
        if (tokens.equals("-")) {
            return items;
        }
        for (String token : tokens.split(",")) {
            String[] sw = token.split(":");
            items.add(sizedW(sw[0], sw[1]));
        }
        return items;
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }
}
