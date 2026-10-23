/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PotTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

    /** The TFC class at the pinned commit: it adds no state of its own and extends the firepit. */
    static final String CLASS_SOURCE = """
        public class PotBlock extends FirepitBlock
        {
            private static final VoxelShape POT_SHAPE = Shapes.or(BASE_SHAPE, box(2, 0, 2, 14, 14, 14));

            public PotBlock(ExtendedProperties properties)
            {
                super(properties, POT_SHAPE);
            }
        }
        """;

    /** The six parts of TFC's pot.json at the pinned commit: an axis part and a lit part for each of x and z. */
    static final List<String> PARTS = List.of(
        "{\"when\": {\"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_pot\"}}",
        "{\"when\": {\"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_pot\", \"y\": 90}}",
        "{\"when\": {\"lit\": true, \"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_lit_low\"}}",
        "{\"when\": {\"lit\": true, \"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_lit_low\", \"y\": 90}}",
        "{\"when\": {\"lit\": false, \"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_unlit\"}}",
        "{\"when\": {\"lit\": false, \"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_unlit\", \"y\": 90}}");

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Ptfc.checkout); skipped when absent. */
    @Test
    void realCheckoutEmitsTheTwentyPotStates() {
        TfcCheckout tfc = FirepitTest.realCheckout();

        assertThat(Pot.props(tfc).stream().map(dev.tfp.datagen.device.DeviceProperty::name).toList()).containsExactly("lit", "smoke_level", "axis");

        OutputTree out = new OutputTree();
        assertThat(Pot.run(tfc, out)).isEqualTo(2 * 5 * 2);
        FirepitTest.assertEntry(new String(out.get(Pot.OUTPUT), StandardCharsets.UTF_8), 4, Pot.BLOCK_ID, Pot.OUTPUT);
    }

    @Test
    void fixtureStatesFollowTheInheritedDefinitionAndTheSmokeRangeOfTheSource() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 2, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writePot(root, CLASS_SOURCE, blockstate(PARTS));
        OutputTree out = new OutputTree();

        assertThat(Pot.run(TfcCheckout.open(root, PIN), out)).isEqualTo(2 * 3 * 2);
        FirepitTest.assertEntry(new String(out.get(Pot.OUTPUT), StandardCharsets.UTF_8), 2, Pot.BLOCK_ID, Pot.OUTPUT);
    }

    @Test
    void missingPotSourceIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeBlockstate(root, PARTS);
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Pot.SOURCE);
    }

    @Test
    void potNotExtendingTheFirepitIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writePot(root, CLASS_SOURCE.replace("extends FirepitBlock", "extends Block"), blockstate(PARTS));
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("does not extend FirepitBlock");
    }

    @Test
    void potDeclaringItsOwnStateIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        String own = CLASS_SOURCE.replace("public PotBlock(ExtendedProperties properties)",
            "@Override\n    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)\n    {\n"
                + "        builder.add(LIT, AXIS);\n    }\n\n    public PotBlock(ExtendedProperties properties)");
        writePot(root, own, blockstate(PARTS));
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("declares its own block state definition");
    }

    @Test
    void missingBlockstateIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writePot(root, CLASS_SOURCE, blockstate(PARTS));
        Files.delete(root.resolve(Pot.BLOCKSTATE));
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Pot.BLOCKSTATE);
    }

    @Test
    void stateWithoutAPartIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writePot(root, CLASS_SOURCE, blockstate(List.of(PARTS.get(0), PARTS.get(2), PARTS.get(3), PARTS.get(4))));
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("lit=false,smoke_level=0,axis=z");
    }

    @Test
    void partNamingAnotherPropertyIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        List<String> parts = new java.util.ArrayList<>(PARTS);
        parts.add("{\"when\": {\"facing\": \"north\"}, \"apply\": {\"model\": \"tfc:block/firepit_pot\"}}");
        writePot(root, CLASS_SOURCE, blockstate(parts));
        assertThatThrownBy(() -> Pot.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("part names facing");
    }

    /** The default part, added to {@link Fixture#create}: the TFC pot class and its multipart blockstate. */
    static void addDefault(Path root) throws IOException {
        writePot(root, CLASS_SOURCE, blockstate(PARTS));
    }

    static String blockstate(List<String> parts) {
        return "{\"__comment__\": \"x\", \"multipart\": [" + String.join(", ", parts) + "]}";
    }

    static void writePot(Path root, String classSource, String blockstate) throws IOException {
        Fixture.write(root, Pot.SOURCE, classSource);
        Fixture.write(root, Pot.BLOCKSTATE, blockstate);
        FirepitTest.writeDeviceData(root, FirepitTest.registration("POT", "pot", "PotBlock"), "pot", "pickaxe",
            List.of("tfc:powder/wood_ash", "tfc:ceramic/pot"));
    }

    static void writeBlockstate(Path root, List<String> parts) throws IOException {
        Fixture.write(root, Pot.BLOCKSTATE, blockstate(parts));
    }

    /** A checkout at the pinned commit with no firepit or pot sources yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }
}
