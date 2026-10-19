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

class GrillTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

    /** The TFC class at the pinned commit: it adds no state of its own and extends the firepit. */
    static final String CLASS_SOURCE = """
        public class GrillBlock extends FirepitBlock implements IHighlightHandler
        {
            private static final VoxelShape GRILL_SHAPE = Shapes.or(BASE_SHAPE, box(2, 0, 2, 14, 11, 14));

            public GrillBlock(ExtendedProperties properties)
            {
                super(properties, GRILL_SHAPE);
            }
        }
        """;

    /** The six parts of TFC's grill.json at the pinned commit: an axis part and a lit part for each of x and z. */
    static final List<String> PARTS = List.of(
        "{\"when\": {\"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_grill\"}}",
        "{\"when\": {\"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_grill\", \"y\": 90}}",
        "{\"when\": {\"lit\": true, \"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_lit_low\"}}",
        "{\"when\": {\"lit\": true, \"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_lit_low\", \"y\": 90}}",
        "{\"when\": {\"lit\": false, \"axis\": \"x\"}, \"apply\": {\"model\": \"tfc:block/firepit_unlit\"}}",
        "{\"when\": {\"lit\": false, \"axis\": \"z\"}, \"apply\": {\"model\": \"tfc:block/firepit_unlit\", \"y\": 90}}");

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Ptfc.checkout); skipped when absent. */
    @Test
    void realCheckoutEmitsTheTwentyGrillStates() {
        TfcCheckout tfc = FirepitTest.realCheckout();

        assertThat(Grill.props(tfc).stream().map(Firepit.Prop::name).toList()).containsExactly("lit", "smoke_level", "axis");

        OutputTree out = new OutputTree();
        assertThat(Grill.run(tfc, out)).isEqualTo(2 * 5 * 2);
        FirepitTest.assertEntry(new String(out.get(Grill.OUTPUT), StandardCharsets.UTF_8), 4, Grill.BLOCK_ID, Grill.OUTPUT);
    }

    @Test
    void fixtureStatesFollowTheInheritedDefinitionAndTheSmokeRangeOfTheSource() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 2, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeGrill(root, CLASS_SOURCE, blockstate(PARTS));
        OutputTree out = new OutputTree();

        assertThat(Grill.run(TfcCheckout.open(root, PIN), out)).isEqualTo(2 * 3 * 2);
        FirepitTest.assertEntry(new String(out.get(Grill.OUTPUT), StandardCharsets.UTF_8), 2, Grill.BLOCK_ID, Grill.OUTPUT);
    }

    @Test
    void missingGrillSourceIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeBlockstate(root, PARTS);
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Grill.SOURCE);
    }

    @Test
    void grillNotExtendingTheFirepitIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeGrill(root, CLASS_SOURCE.replace("extends FirepitBlock", "extends Block"), blockstate(PARTS));
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("does not extend FirepitBlock");
    }

    @Test
    void grillDeclaringItsOwnStateIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        String own = CLASS_SOURCE.replace("public GrillBlock(ExtendedProperties properties)",
            "@Override\n    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)\n    {\n"
                + "        builder.add(LIT, AXIS);\n    }\n\n    public GrillBlock(ExtendedProperties properties)");
        writeGrill(root, own, blockstate(PARTS));
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("declares its own block state definition");
    }

    @Test
    void missingBlockstateIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeGrill(root, CLASS_SOURCE, blockstate(PARTS));
        Files.delete(root.resolve(Grill.BLOCKSTATE));
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Grill.BLOCKSTATE);
    }

    @Test
    void stateWithoutAPartIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        writeGrill(root, CLASS_SOURCE, blockstate(List.of(PARTS.get(0), PARTS.get(2), PARTS.get(3), PARTS.get(4))));
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("lit=false,smoke_level=0,axis=z");
    }

    @Test
    void partNamingAnotherPropertyIsRefused() throws IOException {
        Path root = checkout();
        FirepitTest.writeFirepit(root, 4, FirepitTest.DEFINITION, FirepitTest.allVisualVariants());
        List<String> parts = new java.util.ArrayList<>(PARTS);
        parts.add("{\"when\": {\"facing\": \"north\"}, \"apply\": {\"model\": \"tfc:block/firepit_grill\"}}");
        writeGrill(root, CLASS_SOURCE, blockstate(parts));
        assertThatThrownBy(() -> Grill.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("part names facing");
    }

    /** The default part, added to {@link Fixture#create}: the TFC grill class and its multipart blockstate. */
    static void addDefault(Path root) throws IOException {
        writeGrill(root, CLASS_SOURCE, blockstate(PARTS));
    }

    static String blockstate(List<String> parts) {
        return "{\"__comment__\": \"x\", \"multipart\": [" + String.join(", ", parts) + "]}";
    }

    static void writeGrill(Path root, String classSource, String blockstate) throws IOException {
        Fixture.write(root, Grill.SOURCE, classSource);
        Fixture.write(root, Grill.BLOCKSTATE, blockstate);
    }

    static void writeBlockstate(Path root, List<String> parts) throws IOException {
        Fixture.write(root, Grill.BLOCKSTATE, blockstate(parts));
    }

    /** A checkout at the pinned commit with no firepit or grill sources yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }
}
