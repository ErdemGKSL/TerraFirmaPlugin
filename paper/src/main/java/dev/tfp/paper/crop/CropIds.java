/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/{DefaultCropBlock,DoubleCropBlock,ClimbingCropBlock}.java @ c3b4d9536
 *       (postGrowthTick, die: the block states a crop shows and dies into)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.crop.CropType;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Locale;
import java.util.Optional;

/** Block ids and visual states of the crops (PAR-FARM-03 blocks): pure string work. */
public final class CropIds {
    public static final String LIVING = "tfp:crop";
    public static final String DEAD = "tfp:dead_crop";
    public static final String AGE = "age";
    public static final String PART = "part";
    public static final String STICK = "stick";
    public static final String MATURE = "mature";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";

    private CropIds() {
    }

    /** The crop of a living block id ({@code tfp:crop/wheat}). */
    public static Optional<CropType> type(String blockId) {
        if (!blockId.startsWith(LIVING + "/")) {
            return Optional.empty();
        }
        String name = blockId.substring(LIVING.length() + 1).toUpperCase(Locale.ROOT);
        try {
            return Optional.of(CropType.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static String livingId(CropType type) {
        return LIVING + "/" + type.serializedName();
    }

    public static String deadId(CropType type) {
        return DEAD + "/" + type.serializedName();
    }

    /** Whether the block is the top part of a two-block crop. */
    public static boolean isTop(BlockStateRef state) {
        return TOP.equals(state.properties().get(PART));
    }

    public static int age(BlockStateRef state) {
        String age = state.properties().get(AGE);
        return age == null ? 0 : Integer.parseInt(age);
    }

    public static boolean hasStick(BlockStateRef state) {
        return "true".equals(state.properties().get(STICK));
    }

    /** The living state with a new age (every other property kept). */
    public static BlockStateRef withAge(BlockStateRef state, int age) {
        return state.with(AGE, Integer.toString(age));
    }

    /** The top part of a two-block crop at {@code age}, from the bottom's state. */
    public static BlockStateRef top(BlockStateRef bottom, int age) {
        return withAge(bottom, age).with(PART, TOP);
    }

    /**
     * TFC {@code die}: the dead block with {@code mature} set; a two-block crop's dead block has a part, a climbing crop's also copies the
     * stick.
     */
    public static BlockStateRef dead(CropType type, BlockStateRef living, boolean mature, boolean topPart) {
        BlockStateRef dead = BlockStateRef.of(deadId(type)).with(MATURE, Boolean.toString(mature));
        switch (type.shape()) {
            case DOUBLE -> dead = dead.with(PART, topPart ? TOP : BOTTOM);
            case CLIMBING, PICKABLE_CLIMBING -> dead = dead.with(PART, topPart ? TOP : BOTTOM)
                .with(STICK, Boolean.toString(hasStick(living)));
            default -> {
            }
        }
        return dead;
    }

    /** Whether the crop has a top block. */
    public static boolean doubled(CropType type) {
        return type.stages().doubled();
    }
}
