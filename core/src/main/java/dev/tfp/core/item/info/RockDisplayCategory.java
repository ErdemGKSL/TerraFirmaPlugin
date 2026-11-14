/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockDisplayCategory.java @ c3b4d9536
 * (with the rock table of src/main/java/net/dries007/tfc/common/blocks/rock/Rock.java)
 */
package dev.tfp.core.item.info;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * TFC's {@code RockDisplayCategory}: the finer rock category shown as lore on rock block items and bricks, and the
 * display category of each of TFC's 21 rocks ({@code Rock}'s constructor arguments).
 */
public enum RockDisplayCategory {
    FELSIC_IGNEOUS_EXTRUSIVE,
    INTERMEDIATE_IGNEOUS_EXTRUSIVE,
    MAFIC_IGNEOUS_EXTRUSIVE,
    FELSIC_IGNEOUS_INTRUSIVE,
    INTERMEDIATE_IGNEOUS_INTRUSIVE,
    MAFIC_IGNEOUS_INTRUSIVE,
    METAMORPHIC,
    SEDIMENTARY;

    /** TFC {@code Rock}: each rock's display category, in TFC's enum order. */
    public static final Map<String, RockDisplayCategory> ROCKS = Map.ofEntries(
            Map.entry("granite", FELSIC_IGNEOUS_INTRUSIVE),
            Map.entry("diorite", INTERMEDIATE_IGNEOUS_INTRUSIVE),
            Map.entry("gabbro", MAFIC_IGNEOUS_INTRUSIVE),
            Map.entry("shale", SEDIMENTARY),
            Map.entry("claystone", SEDIMENTARY),
            Map.entry("limestone", SEDIMENTARY),
            Map.entry("conglomerate", SEDIMENTARY),
            Map.entry("dolomite", SEDIMENTARY),
            Map.entry("chert", SEDIMENTARY),
            Map.entry("chalk", SEDIMENTARY),
            Map.entry("tuff", SEDIMENTARY),
            Map.entry("rhyolite", FELSIC_IGNEOUS_EXTRUSIVE),
            Map.entry("basalt", MAFIC_IGNEOUS_EXTRUSIVE),
            Map.entry("andesite", INTERMEDIATE_IGNEOUS_EXTRUSIVE),
            Map.entry("dacite", INTERMEDIATE_IGNEOUS_EXTRUSIVE),
            Map.entry("quartzite", METAMORPHIC),
            Map.entry("slate", METAMORPHIC),
            Map.entry("phyllite", METAMORPHIC),
            Map.entry("schist", METAMORPHIC),
            Map.entry("gneiss", METAMORPHIC),
            Map.entry("marble", METAMORPHIC));

    /** The display category of rock {@code rock} (such as {@code granite}). */
    public static Optional<RockDisplayCategory> ofRock(String rock) {
        return Optional.ofNullable(ROCKS.get(rock));
    }

    /** TFC {@code category()}. */
    public RockCategory category() {
        return switch (this) {
            case FELSIC_IGNEOUS_EXTRUSIVE, INTERMEDIATE_IGNEOUS_EXTRUSIVE, MAFIC_IGNEOUS_EXTRUSIVE ->
                    RockCategory.IGNEOUS_EXTRUSIVE;
            case FELSIC_IGNEOUS_INTRUSIVE, INTERMEDIATE_IGNEOUS_INTRUSIVE, MAFIC_IGNEOUS_INTRUSIVE ->
                    RockCategory.IGNEOUS_INTRUSIVE;
            case METAMORPHIC -> RockCategory.METAMORPHIC;
            case SEDIMENTARY -> RockCategory.SEDIMENTARY;
        };
    }

    /** The lower-case name ({@code Helpers.translateEnum} key suffix). */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
