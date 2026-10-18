/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java @ c3b4d9536 (the LOOSE and MOSSY_LOOSE block types of each rock)
 */
package dev.tfp.core.gather;

import java.util.Locale;

/**
 * The 21 rocks that have a loose rock and a mossy loose rock block in TFC. The serialized rock name is the lower-case constant
 * name, which is the path of the TFC item ({@code tfc:rock/loose/<rock>}, {@code tfc:rock/mossy_loose/<rock>}).
 */
public enum LooseRockType {
    ANDESITE,
    BASALT,
    CHALK,
    CHERT,
    CLAYSTONE,
    CONGLOMERATE,
    DACITE,
    DIORITE,
    DOLOMITE,
    GABBRO,
    GNEISS,
    GRANITE,
    LIMESTONE,
    MARBLE,
    PHYLLITE,
    QUARTZITE,
    RHYOLITE,
    SCHIST,
    SHALE,
    SLATE,
    TUFF;

    /** The rock's serialized name, for example {@code granite}. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The item of the loose rock of this rock, {@code tfc:rock/loose/<rock>}. */
    public String itemId() {
        return "tfc:rock/loose/" + serializedName();
    }

    /** The item of the mossy loose rock of this rock, {@code tfc:rock/mossy_loose/<rock>}. */
    public String mossyItemId() {
        return "tfc:rock/mossy_loose/" + serializedName();
    }
}
