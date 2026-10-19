/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/horse/TFCHorse.java (applyGenes: the variant and
 * markings draws) @ c3b4d9536
 */
package dev.tfp.core.mount;

/**
 * A horse's coat: its variant (base colour) and markings, as the vanilla horse enums index them. TFC only inherits these
 * for horse foals with two horse parents.
 *
 * @param variant  the base colour index, in [0, {@link #VARIANT_COUNT})
 * @param markings the markings index, in [0, {@link #MARKINGS_COUNT})
 */
public record HorseColour(int variant, int markings) {

    /** The number of vanilla {@code Horse.Variant} values (WHITE to DARK_BROWN), the range of {@code Util.getRandom}. */
    public static final int VARIANT_COUNT = 7;

    /** The number of vanilla {@code Horse.Markings} values (NONE to BLACK_DOTS). */
    public static final int MARKINGS_COUNT = 5;

    /** Validates the indices. */
    public HorseColour {
        if (variant < 0 || variant >= VARIANT_COUNT || markings < 0 || markings >= MARKINGS_COUNT) {
            throw new IllegalArgumentException("horse colour out of range: variant " + variant + ", markings " + markings);
        }
    }

    /**
     * TFC {@code TFCHorse.applyGenes}, the colour part. The variant draw {@code i = nextInt(9)} takes the father's variant
     * when {@code i < 4}, the mother's when {@code i < 8}, and otherwise a random variant ({@code nextInt(7)}). Then the
     * markings draw {@code j = nextInt(5)} takes the father's markings when {@code j < 2}, the mother's when {@code j < 4},
     * and otherwise a random one ({@code nextInt(5)}). The draws are taken from the mother's random source, in that order.
     *
     * @param mother the female's colour, TFC's {@code variant2} and {@code markings2}
     * @param father the male's colour, TFC's {@code variant1} and {@code markings1}
     */
    public static HorseColour inherit(HorseColour mother, HorseColour father, MountRandom random) {
        final int i = random.nextInt(9);
        final int variant;
        if (i < 4) {
            variant = father.variant;
        } else if (i < 8) {
            variant = mother.variant;
        } else {
            variant = random.nextInt(VARIANT_COUNT);
        }
        final int j = random.nextInt(5);
        final int markings;
        if (j < 2) {
            markings = father.markings;
        } else if (j < 4) {
            markings = mother.markings;
        } else {
            markings = random.nextInt(MARKINGS_COUNT);
        }
        return new HorseColour(variant, markings);
    }
}
