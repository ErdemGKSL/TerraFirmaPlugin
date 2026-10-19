/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java @ c3b4d9536 (the class declaration; its state is inherited from FirepitBlock),
 *       src/main/resources/assets/tfc/blockstates/pot.json @ c3b4d9536 (the multipart parts of the lit x axis states)
 */
package dev.tfp.datagen;

import java.util.List;
import java.util.Map;

/**
 * M3-HEAT-03 (block data slice): the TFC pot as one CraftEngine block entry, {@code tfp:pot}. {@code PotBlock} extends
 * {@code FirepitBlock} and adds no state of its own, so its states are the firepit's: {@code lit}, {@code smoke_level} (0 to 4)
 * and {@code axis}, 2 x 5 x 2 = 20 server states. The properties are read through {@link Firepit#inheritedProps}, which
 * refuses a pot that stops extending the firepit or declares its own state.
 *
 * <p>TFC's pot blockstate {@code pot.json} is a multipart: an axis part for the pot model
 * {@code firepit_pot} and a lit part for the flame, with no smoke level. {@link Firepit#checkMultipart} checks that
 * every state matches a part and that only {@code lit} and {@code axis} are named. Appearances are the firepit's
 * placeholder campfire states (see {@link Firepit}).
 *
 * <p>The output is {@code craftengine/tfp/devices/pot.yml}, outside the CraftEngine {@code configuration/} folder, so
 * CraftEngine does not load it yet. The pot's behaviour (the boiling, the soups and jams, the fluid transfer, the block
 * entity, the placed block) is not in this entry.
 */
final class Pot {
    static final String SOURCE = "src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java";
    static final String CLASS = "PotBlock";
    static final String BLOCKSTATE = TfcCheckout.ASSETS + "blockstates/pot.json";
    static final String OUTPUT = AssetPipeline.PACK + "devices/pot.yml";
    static final String BLOCK_ID = Json.TO_NS + ":pot";

    private Pot() {
    }

    /** Writes the pot entry. Returns the number of server states. Fails when TFC's sources do not match. */
    static int run(TfcCheckout tfc, OutputTree out) {
        List<Firepit.Prop> props = props(tfc);
        List<Map<String, String>> states = Firepit.combinations(props);
        Firepit.checkMultipart(tfc, BLOCKSTATE, states);
        out.put(OUTPUT, Yaml.write(Firepit.HEADER, Firepit.yaml(BLOCK_ID, props, states)));
        return states.size();
    }

    /** The pot's block state properties: the firepit's, in {@code builder.add} order. */
    static List<Firepit.Prop> props(TfcCheckout tfc) {
        return Firepit.inheritedProps(tfc, SOURCE, CLASS);
    }
}
