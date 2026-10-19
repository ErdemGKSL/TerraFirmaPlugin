/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java @ c3b4d9536 (the class declaration; its state is inherited from FirepitBlock),
 *       src/main/resources/assets/tfc/blockstates/grill.json @ c3b4d9536 (the multipart parts of the lit x axis states)
 */
package dev.tfp.datagen;

import java.util.List;
import java.util.Map;

/**
 * M3-HEAT-03 (block data slice): the TFC grill as one CraftEngine block entry, {@code tfp:grill}. {@code GrillBlock} extends
 * {@code FirepitBlock} and adds no state of its own, so its states are the firepit's: {@code lit}, {@code smoke_level} (0 to 4)
 * and {@code axis}, 2 x 5 x 2 = 20 server states. The properties are read through {@link Firepit#inheritedProps}, which
 * refuses a grill that stops extending the firepit or declares its own state.
 *
 * <p>TFC's grill blockstate {@code grill.json} is a multipart: an axis part for the grill model
 * {@code firepit_grill} and a lit part for the flame, with no smoke level. {@link Firepit#checkMultipart} checks that
 * every state matches a part and that only {@code lit} and {@code axis} are named. Appearances are the firepit's
 * placeholder campfire states (see {@link Firepit}).
 *
 * <p>The output is {@code craftengine/tfp/devices/grill.yml}, outside the CraftEngine {@code configuration/} folder, so
 * CraftEngine does not load it yet. The grill's behaviour (the cooking slots, the block entity, the placed block) is not in
 * this entry.
 */
final class Grill {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/GrillBlock.java";
    static final String CLASS = "GrillBlock";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/grill.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/grill.yml";
    static final String BLOCK_ID = Json.TO_NS + ":grill";

    private Grill() {
    }

    /** Writes the grill entry. Returns the number of server states. Fails when TFC's sources do not match. */
    static int run(TfcCheckout tfc, OutputTree out) {
        List<Firepit.Prop> props = props(tfc);
        List<Map<String, String>> states = Firepit.combinations(props);
        Firepit.checkMultipart(tfc, BLOCKSTATE, states);
        out.put(OUTPUT, Yaml.write(Firepit.HEADER, Firepit.yaml(BLOCK_ID, props, states)));
        return states.size();
    }

    /** The grill's block state properties: the firepit's, in {@code builder.add} order. */
    static List<Firepit.Prop> props(TfcCheckout tfc) {
        return Firepit.inheritedProps(tfc, SOURCE, CLASS);
    }
}
