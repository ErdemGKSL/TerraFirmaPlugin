/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java @ c3b4d9536 (colours, block types, needsItem,
 *       lootFrom), src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java @ c3b4d9536
 *       (canSurvive, scanForWater, tryScheduleDieTick), LivingCoralPlantBlock.java and LivingCoralWallFanBlock.java
 *       @ c3b4d9536 (tick: the dead block without fluid, keeping the facing), CoralWallFanBlock.java @ c3b4d9536
 *       (canSurvive, updateShape), TFCSeaPickleBlock.java @ c3b4d9536 (canSurvive/mayPlaceOn, getStateForPlacement,
 *       isDead) and src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (SEA_PICKLE lightLevel)
 */
package dev.tfp.core.coral;

import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * TFC's coral plants (5 colours x 6 block types, {@code tfp:coral/<colour>_<type>}) and its sea pickle
 * ({@code tfp:sea_pickle}): which block a living coral dies into, when it dies, what holds it up, the sea pickle's
 * support, light and placement count. Pure and thread-safe; the platform layer reads the world into the arguments.
 *
 * <p>TFC's {@code fluid} property ({@code empty} or {@code salt_water}) is our {@link #WATERLOGGED} property: salt water
 * is vanilla water in a salt-water biome (NP-FLUID-01), and CraftEngine's hardcoded {@code waterlogged} behaviour does the
 * bucket and placement handling (ledger divergence).
 */
public final class CoralRules {
    /** The block id namespace. */
    public static final String NS = "tfp:";
    /** The coral id folder ({@code tfp:coral/tube_coral}). */
    public static final String CORAL_PREFIX = NS + "coral/";
    public static final String SEA_PICKLE = NS + "sea_pickle";
    /** The fluid property: TFC {@code fluid=salt_water} is {@code waterlogged=true}. */
    public static final String WATERLOGGED = "waterlogged";
    public static final String FACING = "facing";
    public static final String PICKLES = "pickles";
    /** {@code tryScheduleDieTick}: {@code 60 + random.nextInt(40)} ticks. */
    public static final int DIE_DELAY_MIN = 60;
    public static final int DIE_DELAY_SPREAD = 40;
    /** {@code BlockStateProperties.PICKLES}: 1..4. */
    public static final int MAX_PICKLES = 4;

    /** {@code Coral}, in TFC's order. */
    public enum Colour {
        TUBE, BRAIN, BUBBLE, FIRE, HORN;

        public String serial() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** {@code Coral.BlockType}, in TFC's order. */
    public enum Type {
        DEAD_CORAL, CORAL, DEAD_CORAL_FAN, CORAL_FAN, DEAD_CORAL_WALL_FAN, CORAL_WALL_FAN;

        public String serial() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean living() {
            return this == CORAL || this == CORAL_FAN || this == CORAL_WALL_FAN;
        }

        public boolean wall() {
            return this == DEAD_CORAL_WALL_FAN || this == CORAL_WALL_FAN;
        }

        /** The {@code deadBlock} of a living type; a dead type is its own. */
        public Type dead() {
            return switch (this) {
                case CORAL -> DEAD_CORAL;
                case CORAL_FAN -> DEAD_CORAL_FAN;
                case CORAL_WALL_FAN -> DEAD_CORAL_WALL_FAN;
                default -> this;
            };
        }

        /** {@code BlockType.needsItem}: only the corals (live and dead) have a block item of their own. */
        public boolean needsItem() {
            return this == DEAD_CORAL || this == CORAL;
        }

        /** The type whose loot table this type uses ({@code lootFrom}: a wall fan drops its standing fan). */
        public Type lootFrom() {
            return switch (this) {
                case DEAD_CORAL_WALL_FAN -> DEAD_CORAL_FAN;
                case CORAL_WALL_FAN -> CORAL_FAN;
                default -> this;
            };
        }

        /**
         * TFC's {@code requiresCorrectToolForDrops()}: the dead corals drop only with the correct tool; the living ones always
         * drop.
         */
        public boolean requiresCorrectTool() {
            return !living();
        }
    }

    /** One coral block. */
    public record Coral(Colour colour, Type type) {
        /** {@code tfp:coral/<colour>_<type>}, as TFCBlocks registers it ({@code tfp:coral/tube_dead_coral_wall_fan}). */
        public String id() {
            return CORAL_PREFIX + colour.serial() + "_" + type.serial();
        }

        public Coral dead() {
            return new Coral(colour, type.dead());
        }

        /** The block whose loot this one drops. */
        public Coral lootFrom() {
            return new Coral(colour, type.lootFrom());
        }

        /** The coral of an id, if it is one. */
        public static Optional<Coral> parse(String id) {
            if (id == null || !id.startsWith(CORAL_PREFIX)) {
                return Optional.empty();
            }
            final String rest = id.substring(CORAL_PREFIX.length());
            for (Colour c : Colour.values()) {
                if (rest.startsWith(c.serial() + "_")) {
                    final String type = rest.substring(c.serial().length() + 1);
                    for (Type t : Type.values()) {
                        if (t.serial().equals(type)) {
                            return Optional.of(new Coral(c, t));
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    private CoralRules() {}

    /** Every coral block, colour-major, in TFC's registration order (30). */
    public static List<Coral> all() {
        final List<Coral> out = new ArrayList<>(30);
        for (Colour c : Colour.values()) {
            for (Type t : Type.values()) {
                out.add(new Coral(c, t));
            }
        }
        return List.copyOf(out);
    }

    /**
     * {@code scanForWater}: the block's own fluid is infinite water, or any of the six neighbours holds infinite water
     * ({@code tfc:any_infinite_water}, which lists flowing water too).
     *
     * @param ownWater       the block is waterlogged
     * @param neighbourWater whether the fluid of each neighbour is water, by {@link Direction#ordinal()} (null = unknown)
     * @return true when water was found; empty when no water was found and a neighbour is unknown
     */
    public static Optional<Boolean> scanForWater(boolean ownWater, Boolean[] neighbourWater) {
        if (ownWater) {
            return Optional.of(true);
        }
        boolean unknown = false;
        for (Direction d : Direction.values()) {
            final Boolean w = neighbourWater[d.ordinal()];
            if (w == null) {
                unknown = true;
            } else if (w) {
                return Optional.of(true);
            }
        }
        return unknown ? Optional.empty() : Optional.of(false);
    }

    /** The delay of {@code tryScheduleDieTick}: one draw of {@code nextInt(40)}. */
    public static int dieDelay(RandomGenerator random) {
        return DIE_DELAY_MIN + random.nextInt(DIE_DELAY_SPREAD);
    }

    /**
     * The neighbour that holds a coral up: the block below for a standing coral or fan, the block behind
     * ({@code facing.getOpposite()}) for a wall fan.
     */
    public static Direction support(Type type, Direction facing) {
        return type.wall() ? facing.opposite() : Direction.DOWN;
    }

    /**
     * The face of the {@link #support} block that must be sturdy: its top for a standing coral, the face toward the wall
     * fan ({@code facing}) for a wall fan.
     */
    public static Direction supportFace(Type type, Direction facing) {
        return type.wall() ? facing : Direction.UP;
    }

    /**
     * {@code canSurvive}: {@code isFaceSturdy} of the {@link #supportFace} of the support block. TFC checks it only when the
     * update came from the support side; a check on any update is the same result, as the support has not changed otherwise.
     */
    public static boolean canSurvive(boolean supportFaceSturdy) {
        return supportFaceSturdy;
    }

    /** The state a living coral dies into: the dead block, fluid empty ({@code waterlogged=false}), the facing kept. */
    public static Coral dieInto(Coral living) {
        return living.dead();
    }

    /**
     * Sea pickle {@code canSurvive} / {@code mayPlaceOn}: the block below has a non-empty top face of its collision shape,
     * or a sturdy top face.
     */
    public static boolean seaPickleCanSurvive(boolean belowTopFaceNonEmpty, boolean belowTopSturdy) {
        return belowTopFaceNonEmpty || belowTopSturdy;
    }

    /** {@code TFCSeaPickleBlock.isDead}: a sea pickle without fluid. */
    public static boolean seaPickleDead(boolean waterlogged) {
        return !waterlogged;
    }

    /** TFC's sea pickle {@code lightLevel}: 0 when dead, else {@code 3 + 3 * pickles}. */
    public static int seaPickleLight(int pickles, boolean waterlogged) {
        return seaPickleDead(waterlogged) ? 0 : 3 + 3 * pickles;
    }

    /** {@code getStateForPlacement} on an existing sea pickle: one more, at most four. */
    public static int seaPickleAdd(int pickles) {
        return Math.min(MAX_PICKLES, pickles + 1);
    }

    /** {@code canBeReplaced}: a sea pickle item may be added while the block holds fewer than four. */
    public static boolean seaPickleAccepts(int pickles) {
        return pickles < MAX_PICKLES;
    }

    /** The sea pickle loot table: as many sea pickles as the block holds. */
    public static int seaPickleDrops(int pickles) {
        return Math.clamp(pickles, 1, MAX_PICKLES);
    }
}
