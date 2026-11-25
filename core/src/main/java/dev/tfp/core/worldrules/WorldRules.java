/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFluidPlaceBlock, onFluidCreateSource,
 * onMount, onCropsGrow), mixin/SnowLayerBlockMixin.java, mixin/TadpoleMixin.java, same commit
 */
package dev.tfp.core.worldrules;

import java.util.List;
import java.util.Optional;

/**
 * PAR-DATA-14: the pure decisions of TFC's hooks into vanilla world rules that no feature owns. Stateless and
 * thread-safe; the Paper glue ({@code paper/worldrules}) snapshots the world, asks here and applies the answer.
 */
public final class WorldRules {
    private WorldRules() {
    }

    /** TFC's {@code plantLongGrowthChance} default, which vanilla bamboo also uses ({@code onCropsGrow}). */
    public static final double PLANT_LONG_GROWTH_CHANCE = dev.tfp.core.plant.PlantGrowth.LONG_GROWTH_CHANCE;

    /** Highest snow layer count of a vanilla snow layer block. */
    public static final int MAX_SNOW_LAYERS = 8;

    // ---- lava + water ---------------------------------------------------------------------------------------------

    /**
     * TFC {@code onFluidPlaceBlock}: the block a fluid interaction forms becomes TFC's hardened rock: stone (lava flowing
     * onto water) is hardened gabbro, cobblestone (water reaching flowing lava) hardened rhyolite, basalt (lava over soul
     * soil by blue ice) hardened basalt. Anything else (obsidian included) is kept.
     *
     * @param vanillaBlock the namespaced id of the vanilla block the fluids form
     * @return our CraftEngine block id, or empty to keep the vanilla block
     */
    public static Optional<String> hardenedRock(String vanillaBlock) {
        return switch (vanillaBlock) {
            case "minecraft:stone" -> Optional.of("tfp:rock/hardened/gabbro");
            case "minecraft:cobblestone" -> Optional.of("tfp:rock/hardened/rhyolite");
            case "minecraft:basalt" -> Optional.of("tfp:rock/hardened/basalt");
            default -> Optional.empty();
        };
    }

    // ---- snow layers ----------------------------------------------------------------------------------------------

    /**
     * TFC {@code SnowLayerBlockMixin.onDestroyedByPlayer}: a player out of creative breaking a snow layer block of more than
     * one layer removes one layer (the drops are those of the whole block, as TFC lets the harvest run).
     *
     * @return the layers left, 0 when the block is removed
     */
    public static int snowLayersAfterBreak(int layers, boolean creative) {
        if (layers < 1 || layers > MAX_SNOW_LAYERS) {
            throw new IllegalArgumentException("layers must be 1 to 8: " + layers);
        }
        return layers > 1 && !creative ? layers - 1 : 0;
    }

    /**
     * TFC {@code SnowLayerBlockMixin.canSurvive}: on a TFC leaves block (no solid top in TFC) only a single snow layer
     * survives, so placing a snow layer that would make two or more layers there is refused ({@code BlockItem.canPlace}).
     *
     * @param layers       the layers the placement would leave
     * @param onTfcLeaves  the block below is a TFC leaves block ({@code ILeavesBlock})
     */
    public static boolean snowPlacementAllowed(int layers, boolean onTfcLeaves) {
        return layers <= 1 || !onTfcLeaves;
    }

    // ---- entities -------------------------------------------------------------------------------------------------

    /** TFC {@code onMount}: a predator never rides a boat. */
    public static boolean mayMount(boolean vehicleIsBoat, boolean riderIsPredator) {
        return !(vehicleIsBoat && riderIsPredator);
    }

    /** TFC {@code TadpoleMixin.isFood}: nothing feeds a tadpole (vanilla's slime ball included). */
    public static boolean tadpoleFood() {
        return false;
    }

    /** TFC {@code onCropsGrow}: a vanilla bamboo stalk grows on a random tick only if {@code nextFloat() <= chance}. */
    public static boolean bambooGrows(float roll, double chance) {
        return !(roll > chance);
    }

    // ---- fluid sources ---------------------------------------------------------------------------------------------

    /**
     * One horizontal neighbour of a block whose fluid is being recomputed, as vanilla {@code FlowingFluid.getNewLiquid}
     * sees it.
     *
     * @param sameFluid  it holds the same fluid (water)
     * @param amount     its fluid amount (8 for a source or a falling fluid, 1 to 7 flowing; 0 without the fluid)
     * @param source     its fluid is a source
     * @param aqueduct   it is a waterlogged TFC aqueduct ({@code AqueductBlock})
     * @param sluiceFed  a sluice's lower half one block above it, on a horizontal side, faces it (TFC's check of
     *                   {@code SluiceBlock.UPPER == false} and {@code FACING == direction.getOpposite()})
     */
    public record Neighbour(boolean sameFluid, int amount, boolean source, boolean aqueduct, boolean sluiceFed) {
        public static final Neighbour NONE = new Neighbour(false, 0, false, false, false);

        /** Whether TFC counts it towards a new source ({@code onFluidCreateSource} leaves {@code canConvert} true). */
        public boolean convertsTfc() {
            return sameFluid && source && !aqueduct && !sluiceFed;
        }

        /** Whether vanilla counts it. */
        public boolean convertsVanilla() {
            return sameFluid && source;
        }
    }

    /** What {@code getNewLiquid} returns. {@code amount} is 8 for a source or a falling fluid, 0 for none. */
    public record Liquid(Kind kind, int amount) {
        public enum Kind { SOURCE, FALLING, FLOWING, EMPTY }

        public static final Liquid SOURCE = new Liquid(Kind.SOURCE, 8);
        public static final Liquid FALLING = new Liquid(Kind.FALLING, 8);
        public static final Liquid EMPTY = new Liquid(Kind.EMPTY, 0);

        /** The Bukkit {@code Levelled} level: 0 for a source, 8 for a falling fluid, {@code 8 - amount} flowing. */
        public int level() {
            return switch (kind) {
                case SOURCE -> 0;
                case FALLING -> 8;
                case FLOWING -> 8 - amount;
                case EMPTY -> throw new IllegalStateException("no fluid");
            };
        }
    }

    /**
     * Vanilla 1.21.1 {@code FlowingFluid.getNewLiquid} for water (drop-off 1, source conversion on), with TFC's {@code
     * onFluidCreateSource} applied to each neighbour when {@code tfc} is true.
     *
     * @param horizontal     the four horizontal neighbours
     * @param belowHolds     the block below is solid or a source of the same fluid
     * @param aboveSameFluid the block above holds the same fluid
     */
    public static Liquid newLiquid(List<Neighbour> horizontal, boolean belowHolds, boolean aboveSameFluid, boolean tfc) {
        int maxAmount = 0;
        int sources = 0;
        for (Neighbour n : horizontal) {
            if (!n.sameFluid()) {
                continue;
            }
            if (tfc ? n.convertsTfc() : n.convertsVanilla()) {
                sources++;
            }
            maxAmount = Math.max(maxAmount, n.amount());
        }
        if (sources >= 2 && belowHolds) {
            return Liquid.SOURCE;
        }
        if (aboveSameFluid) {
            return Liquid.FALLING;
        }
        int k = maxAmount - 1;
        return k <= 0 ? Liquid.EMPTY : new Liquid(Liquid.Kind.FLOWING, k);
    }

    /**
     * Whether TFC's source rule changes vanilla's answer here: vanilla would make a source and TFC would not.
     *
     * @return TFC's liquid when it differs from a vanilla source, else empty (vanilla's answer stands)
     */
    public static Optional<Liquid> deniedSource(List<Neighbour> horizontal, boolean belowHolds, boolean aboveSameFluid) {
        if (newLiquid(horizontal, belowHolds, aboveSameFluid, false).kind() != Liquid.Kind.SOURCE) {
            return Optional.empty();
        }
        Liquid ours = newLiquid(horizontal, belowHolds, aboveSameFluid, true);
        return ours.kind() == Liquid.Kind.SOURCE ? Optional.empty() : Optional.of(ours);
    }
}
