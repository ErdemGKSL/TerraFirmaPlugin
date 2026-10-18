/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java (lines 718-769, the *Config = ...Config.build(...) calls)
 * and src/main/java/net/dries007/tfc/config/animals/{AnimalConfig,MammalConfig,OviparousAnimalConfig}.java @ c3b4d9536
 */
package dev.tfp.core.livestock;

/**
 * TFC's default per-species livestock numbers for the familiarity, age, feeding and breeding rules. The values are
 * the {@code ServerConfig} defaults, which are the only numbers TFC ships for these rules.
 *
 * <p>Only the fields the M5-LIV-01 rules read are kept. The produce ticks and minimum produce familiarity belong to
 * M5-LIV-02 and are not here yet.
 */
public enum LivestockSpecies {
    // Mammals (ServerConfig 718-733). Familiarity cap is 0.35 for all, see familiarityCap().
    PIG(Kind.MAMMAL, 80, 60, true, 19, 10, 0),
    DONKEY(Kind.MAMMAL, 80, 60, false, 19, 1, 0),
    MULE(Kind.MAMMAL, 80, 60, false, 19, 1, 0),
    HORSE(Kind.MAMMAL, 80, 60, false, 19, 1, 0),
    DROMEDARY_CAMEL(Kind.MAMMAL, 80, 60, false, 19, 1, 0),
    CAT(Kind.MAMMAL, 50, 60, false, 19, 6, 0),
    DOG(Kind.MAMMAL, 50, 60, true, 19, 2, 0),
    RABBIT(Kind.MAMMAL, 30, 40, true, 19, 6, 0),
    // Producing mammals (ServerConfig 742-760)
    COW(Kind.MAMMAL, 192, 128, true, 58, 2, 0),
    GOAT(Kind.MAMMAL, 96, 60, true, 32, 2, 0),
    YAK(Kind.MAMMAL, 180, 230, false, 64, 1, 0),
    ALPACA(Kind.MAMMAL, 98, 128, false, 36, 2, 0),
    SHEEP(Kind.MAMMAL, 56, 60, false, 32, 2, 0),
    MUSK_OX(Kind.MAMMAL, 168, 160, false, 64, 1, 0),
    BACTRIAN_CAMEL(Kind.MAMMAL, 80, 80, false, 24, 1, 0),
    // Oviparous (ServerConfig 763-769); hatch days are calendar days
    CHICKEN(Kind.OVIPAROUS, 24, 100, true, 0, 0, 8),
    DUCK(Kind.OVIPAROUS, 32, 72, false, 0, 0, 8),
    QUAIL(Kind.OVIPAROUS, 22, 48, true, 0, 0, 8);

    /** Which TFC base the species extends: {@code Mammal} family (pregnancy) or {@code OviparousAnimal} (eggs). */
    public enum Kind {
        MAMMAL,
        OVIPAROUS
    }

    /** TFC {@code AnimalConfig.familiarityCap} default, passed as 0.35 for every species in {@code ServerConfig}. */
    public static final float DEFAULT_FAMILIARITY_CAP = 0.35f;

    private final Kind kind;
    private final int adulthoodDays;
    private final int usesToElderly;
    private final boolean eatsRottenFood;
    private final int gestationDays;
    private final int childCount;
    private final int hatchDays;

    LivestockSpecies(Kind kind, int adulthoodDays, int usesToElderly, boolean eatsRottenFood, int gestationDays, int childCount, int hatchDays) {
        this.kind = kind;
        this.adulthoodDays = adulthoodDays;
        this.usesToElderly = usesToElderly;
        this.eatsRottenFood = eatsRottenFood;
        this.gestationDays = gestationDays;
        this.childCount = childCount;
        this.hatchDays = hatchDays;
    }

    public Kind kind() {
        return kind;
    }

    /** TFC {@code AnimalConfig.familiarityCap}: the maximum familiarity an adult wild-born animal reaches by feeding. */
    public float familiarityCap() {
        return DEFAULT_FAMILIARITY_CAP;
    }

    /** TFC {@code AnimalConfig.adulthoodDays}: days (in player-day units) until adulthood. */
    public int adulthoodDays() {
        return adulthoodDays;
    }

    /** TFC {@code AnimalConfig.uses}: the uses count above which the animal may become old. */
    public int usesToElderly() {
        return usesToElderly;
    }

    /** TFC {@code AnimalConfig.eatsRottenFood}. */
    public boolean eatsRottenFood() {
        return eatsRottenFood;
    }

    /** TFC {@code MammalConfig.gestationDays}; 0 for oviparous species. */
    public int gestationDays() {
        return gestationDays;
    }

    /** TFC {@code MammalConfig.childCount}: the upper bound of the litter size; 0 for oviparous species. */
    public int childCount() {
        return childCount;
    }

    /** TFC {@code OviparousAnimalConfig.hatchDays}; 0 for mammals. */
    public int hatchDays() {
        return hatchDays;
    }

    /**
     * TFC {@code TFCAnimalProperties.isFood} for the rot check: rotten food is refused unless the species eats it.
     *
     * @param rotten whether the food item is rotten
     */
    public boolean canEat(boolean rotten) {
        return eatsRottenFood || !rotten;
    }
}
