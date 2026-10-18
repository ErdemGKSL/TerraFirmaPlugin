/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/GroundcoverBlockType.java @ c3b4d9536 (the item of each type, from its
 * vanilla item or its own block item; the loot tables of src/main/resources/data/tfc/loot_table/blocks/groundcover/)
 */
package dev.tfp.core.gather;

/**
 * TFC's groundcover pieces ({@code GroundcoverBlockType}). Each piece yields exactly one item when it is broken or picked up:
 * a vanilla item where TFC names one (bone, feather, flint, rotten flesh, stick), salt powder for the salt lick, and its own
 * {@code tfc:groundcover/} item otherwise.
 */
public enum GroundcoverType {
    BONE("minecraft:bone"),
    CLAM("tfc:groundcover/clam"),
    DRIFTWOOD("tfc:groundcover/driftwood"),
    FEATHER("minecraft:feather"),
    FLINT("minecraft:flint"),
    GUANO("tfc:groundcover/guano"),
    HUMUS("tfc:groundcover/humus"),
    MOLLUSK("tfc:groundcover/mollusk"),
    MUSSEL("tfc:groundcover/mussel"),
    OBSIDIAN_SHARD("tfc:groundcover/obsidian_shard"),
    PINECONE("tfc:groundcover/pinecone"),
    PUMICE("tfc:groundcover/pumice"),
    ROTTEN_FLESH("minecraft:rotten_flesh"),
    SALT_LICK("tfc:powder/salt"),
    SEAWEED("tfc:groundcover/seaweed"),
    SEA_URCHIN("tfc:groundcover/sea_urchin"),
    STICK("minecraft:stick");

    private final String itemId;

    GroundcoverType(String itemId) {
        this.itemId = itemId;
    }

    /** The item this piece yields (count 1). */
    public String itemId() {
        return itemId;
    }
}
