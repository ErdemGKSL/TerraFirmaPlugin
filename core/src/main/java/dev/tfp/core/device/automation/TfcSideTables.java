/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (constructor: sidedInventory),
 *       src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java @ c3b4d9536 (constructor),
 *       src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (constructor),
 *       src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (constructor),
 *       src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (constructor),
 *       src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (constructor),
 *       src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (constructor)
 */
package dev.tfp.core.device.automation;

/**
 * The item sides each TFC device block entity builds in its constructor when its {@code *EnableAutomation} config is on
 * (the fluid sides of the barrel and the pot are not here: no vanilla block moves fluids, ledger divergence). Slot
 * numbers are TFC's, which our device records keep.
 */
public final class TfcSideTables {
    /** TFC {@code BarrelBlockEntity}: three slots. */
    public static final int BARREL_SLOTS = 3;
    public static final int BARREL_FLUID_CONTAINER_IN = 0;
    public static final int BARREL_FLUID_CONTAINER_OUT = 1;
    public static final int BARREL_ITEM = 2;

    /** TFC {@code AbstractFirepitBlockEntity}: fuel slots 0..3, insertion into 3. */
    public static final int FUEL_CONSUME = 0;
    public static final int FUEL_INPUT = 3;
    public static final int FIREPIT_SLOTS = 7;
    public static final int FIREPIT_ITEM_INPUT = 4;
    public static final int FIREPIT_OUTPUT_1 = 5;
    public static final int FIREPIT_OUTPUT_2 = 6;
    /** TFC {@code GrillBlockEntity} and {@code PotBlockEntity}: fuel 0..3, extra slots 4..8. */
    public static final int GRILL_SLOTS = 9;
    public static final int POT_SLOTS = 9;

    public static final int QUERN_SLOTS = 3;
    public static final int QUERN_HANDSTONE = 0;
    public static final int QUERN_INPUT = 1;
    public static final int QUERN_OUTPUT = 2;

    /** TFC {@code ComposterBlockEntity}: {@code defaultInventory(1)}. */
    public static final int COMPOSTER_SLOTS = 1;
    /** TFC {@code PowderkegBlockEntity.SLOTS}. */
    public static final int POWDERKEG_SLOTS = 12;

    private TfcSideTables() {
    }

    /**
     * TFC {@code BarrelBlockEntity}: the fluid-container slots on the sides (all four for an upright barrel; for a barrel
     * on its side the two horizontal faces across its axis), the item slot in through {@code facing} and out through the
     * opposite face.
     *
     * @param facing the block state's {@code facing} ({@code UP} for an upright barrel, the default)
     */
    public static SideTable barrel(Face facing) {
        boolean vertical = facing == Face.UP;
        return SideTable.NONE
            .on(PartialSlots.of(BARREL_SLOTS).insert(BARREL_FLUID_CONTAINER_IN).extract(BARREL_FLUID_CONTAINER_OUT),
                vertical ? Face::horizontal : d -> d.axis() != facing.axis() && d.horizontal())
            .on(PartialSlots.of(BARREL_SLOTS).insert(BARREL_ITEM), facing)
            .on(PartialSlots.of(BARREL_SLOTS).extract(BARREL_ITEM), facing.opposite());
    }

    /** TFC {@code FirepitBlockEntity}: fuel in and both outputs out on the sides, the cooked item in from above. */
    public static SideTable firepit() {
        return SideTable.NONE
            .on(PartialSlots.of(FIREPIT_SLOTS).insert(FUEL_INPUT).extract(FIREPIT_OUTPUT_1, FIREPIT_OUTPUT_2),
                Face::horizontal)
            .on(PartialSlots.of(FIREPIT_SLOTS).insert(FIREPIT_ITEM_INPUT), Face.UP);
    }

    /** TFC {@code GrillBlockEntity}: fuel in and the five grill slots out on the sides, the five slots in from above. */
    public static SideTable grill() {
        return extraSlots(GRILL_SLOTS);
    }

    /** TFC {@code PotBlockEntity}: as the grill (the pot's fluid sides are a divergence). */
    public static SideTable pot() {
        return extraSlots(POT_SLOTS);
    }

    private static SideTable extraSlots(int slots) {
        return SideTable.NONE
            .on(PartialSlots.of(slots).insert(FUEL_INPUT).extract(4, 5, 6, 7, 8), Face::horizontal)
            .on(PartialSlots.of(slots).insert(4, 5, 6, 7, 8), Face.UP);
    }

    /** TFC {@code QuernBlockEntity}: input and handstone in on the sides, the output out below. */
    public static SideTable quern() {
        return SideTable.NONE
            .on(PartialSlots.of(QUERN_SLOTS).insert(QUERN_INPUT, QUERN_HANDSTONE), Face::horizontal)
            .on(PartialSlots.of(QUERN_SLOTS).extract(QUERN_OUTPUT), Face.DOWN);
    }

    /** TFC {@code ComposterBlockEntity}: everything out below. */
    public static SideTable composter() {
        return SideTable.NONE.on(PartialSlots.of(COMPOSTER_SLOTS).extractAll(), Face.DOWN);
    }

    /** TFC {@code PowderkegBlockEntity}: everything in from above. */
    public static SideTable powderkeg() {
        return SideTable.NONE.on(PartialSlots.of(POWDERKEG_SLOTS).insertAll(), Face.UP);
    }
}
