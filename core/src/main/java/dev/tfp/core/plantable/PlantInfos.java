/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/SeedItem.java @ c3b4d9536 (nutrients and climate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (FruitTreeSaplingItem, BushBlockItem),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java @ c3b4d9536 (TFCSaplingBlockItem: growth time only)
 */
package dev.tfp.core.plantable;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.berry.SpreadingSpecies;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.sapling.SaplingRules;
import dev.tfp.core.worldgen.tree.Wood;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** The {@link PlantInfo} of every item TFC gives {@code PlantableInfo}, by our item id. Pure data, any thread. */
public final class PlantInfos {
    public static final String BANANA_SAPLING = "tfp:plant/banana_sapling";
    private static final Map<String, PlantInfo> ALL = build();

    private PlantInfos() {}

    /** The info of an item id, empty for an item that is not plantable. */
    public static Optional<PlantInfo> of(String itemId) {
        return Optional.ofNullable(itemId == null ? null : ALL.get(itemId));
    }

    /** Every id with an info (tests). */
    public static Map<String, PlantInfo> all() {
        return ALL;
    }

    /** The item id of a crop's seeds. */
    public static String seedId(CropType crop) {
        return "tfp:seeds/" + crop.serializedName();
    }

    /** The item id of a fruit tree's sapling. */
    public static String fruitSaplingId(FruitTree tree) {
        return "tfp:plant/" + tree.name().toLowerCase(Locale.ROOT) + "_sapling";
    }

    /** The item id of a stationary bush (the cranberry is listed with the spreading bushes, as its block is). */
    public static String bushId(BushSpecies species) {
        return (species == BushSpecies.CRANBERRY ? "tfp:spreading_bush/" : "tfp:berry_bush/") + species.name().toLowerCase(Locale.ROOT);
    }

    /** The item id of a spreading bush. */
    public static String spreadingBushId(SpreadingSpecies species) {
        return "tfp:spreading_bush/" + species.name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, PlantInfo> build() {
        Map<String, PlantInfo> out = new java.util.LinkedHashMap<>();
        for (CropType crop : CropType.values()) {
            out.put(seedId(crop), new PlantInfo(Optional.of(crop.climate()),
                Optional.of(new PlantInfo.Nutrients(crop.nitrogen(), crop.phosphorus(), crop.potassium())), Optional.empty(), -1));
        }
        for (Wood wood : Wood.values()) {
            out.put(SaplingRules.blockId(wood), new PlantInfo(Optional.empty(), Optional.empty(), Optional.empty(),
                (int) SaplingRules.ticksToGrow(wood, SaplingRules.DEFAULT_MODIFIER)));
        }
        for (FruitTree tree : FruitTree.values()) {
            out.put(fruitSaplingId(tree), new PlantInfo(Optional.of(tree.climate()), Optional.empty(),
                Optional.of(months(tree::lifecycleFor)), tree.defaultTicksToGrow()));
        }
        out.put(BANANA_SAPLING, new PlantInfo(Optional.of(BananaRules.CLIMATE), Optional.empty(),
            Optional.of(months(BananaRules::lifecycleFor)), (int) BananaRules.SAPLING_GROWTH_TICKS));
        for (BushSpecies bush : BushSpecies.values()) {
            out.put(bushId(bush), new PlantInfo(Optional.of(bush.climate()), Optional.empty(), Optional.of(months(bush::lifecycleFor)), -1));
        }
        for (SpreadingSpecies bush : SpreadingSpecies.values()) {
            out.put(spreadingBushId(bush), new PlantInfo(Optional.of(bush.climate()), Optional.empty(),
                Optional.of(months(bush::lifecycleFor)), -1));
        }
        return Map.copyOf(out);
    }

    private static List<Lifecycle> months(Function<Month, Lifecycle> table) {
        List<Lifecycle> out = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            out.add(table.apply(Month.valueOf(i)));
        }
        return out;
    }
}
