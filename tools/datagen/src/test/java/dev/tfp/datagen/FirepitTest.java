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
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FirepitTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

    /** The TFC state definition, as {@code FirepitBlock} writes it at the pinned commit. */
    static final String DEFINITION = """
        public class FirepitBlock extends BottomSupportedDeviceBlock
        {
            public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
            public static final BooleanProperty LIT = BlockStateProperties.LIT;
            public static final IntegerProperty SMOKE_LEVEL = TFCBlockStateProperties.SMOKE_LEVEL;

            public FirepitBlock(ExtendedProperties properties)
            {
                super(properties, InventoryRemoveBehavior.DROP, BASE_SHAPE);
                registerDefaultState(getStateDefinition().any().setValue(LIT, false).setValue(SMOKE_LEVEL, 0).setValue(AXIS, Direction.Axis.X));
            }

            @Override
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
            {
                builder.add(LIT, SMOKE_LEVEL, AXIS);
            }
        }
        """;

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout, from -Ptfc.checkout or $TFC_CHECKOUT); skipped when absent. */
    @Test
    void realCheckoutEmitsTheTwentyFirepitStates() {
        TfcCheckout tfc = realCheckout();

        assertThat(Firepit.props(tfc).stream().map(Firepit.Prop::name).toList()).containsExactly("lit", "smoke_level", "axis");

        OutputTree out = new OutputTree();
        assertThat(Firepit.run(tfc, out)).isEqualTo(2 * 5 * 2);
        assertEntry(new String(out.get(Firepit.OUTPUT), StandardCharsets.UTF_8), 4);
    }

    @Test
    void fixtureStatesFollowTheDefinitionAndTheSmokeRangeOfTheSource() throws IOException {
        Path root = checkout();
        writeFirepit(root, 2, DEFINITION, allVisualVariants());
        OutputTree out = new OutputTree();

        assertThat(Firepit.run(TfcCheckout.open(root, PIN), out)).isEqualTo(2 * 3 * 2);
        assertEntry(new String(out.get(Firepit.OUTPUT), StandardCharsets.UTF_8), 2);
    }

    @Test
    void missingFirepitSourceIsRefused() throws IOException {
        Path root = checkout();
        Fixture.write(root, Firepit.PROPERTIES, "public final class TFCBlockStateProperties\n{\n"
            + "    public static final IntegerProperty SMOKE_LEVEL = IntegerProperty.create(\"smoke_level\", 0, 4);\n}\n");
        writeBlockstate(root, allVisualVariants());
        assertThatThrownBy(() -> Firepit.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Firepit.SOURCE);
    }

    @Test
    void missingBlockstateIsRefused() throws IOException {
        Path root = checkout();
        writeFirepit(root, 4, DEFINITION, allVisualVariants());
        Files.delete(root.resolve(Firepit.BLOCKSTATE));
        assertThatThrownBy(() -> Firepit.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining(Firepit.BLOCKSTATE);
    }

    @Test
    void blockstateWithoutAVariantOfTheDefinitionIsRefused() throws IOException {
        Path root = checkout();
        writeFirepit(root, 4, DEFINITION, List.of("lit=true,axis=x", "lit=true,axis=z", "lit=false,axis=x"));
        assertThatThrownBy(() -> Firepit.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("axis=z,lit=false");
    }

    @Test
    void unknownPropertyInTheDefinitionIsRefused() throws IOException {
        Path root = checkout();
        String facing = DEFINITION
            .replace("builder.add(LIT, SMOKE_LEVEL, AXIS);", "builder.add(LIT, SMOKE_LEVEL, AXIS, FACING);")
            .replace("setValue(AXIS, Direction.Axis.X));", "setValue(AXIS, Direction.Axis.X).setValue(FACING, Direction.NORTH));")
            .replace("public static final BooleanProperty LIT",
                "public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;\n"
                    + "    public static final BooleanProperty LIT");
        writeFirepit(root, 4, facing, allVisualVariants());
        assertThatThrownBy(() -> Firepit.run(TfcCheckout.open(root, PIN), new OutputTree()))
            .isInstanceOf(DatagenException.class).hasMessageContaining("BlockStateProperties.FACING");
    }

    /**
     * The carriers the hitbox selector picks (PAR-INFRA-13): firepit 16x6x16 against TFC's 2 px plate + 12x4 core (IoU 0.71),
     * grill 5 snow layers (0.62 / 0.56), pot a cactus 14x16x14 (0.65 / 0.69). The same for lit and unlit (one TFC shape).
     */
    static final Map<String, String> SELECTED = Map.of(
        Firepit.BLOCK_ID, "minecraft:daylight_detector[inverted=false,power=0]",
        Grill.BLOCK_ID, "minecraft:snow[layers=5]",
        Pot.BLOCK_ID, "minecraft:cactus[age=0]");

    /** The firepit entry: see {@link #assertEntry(String, int, String, String)}. */
    static void assertEntry(String yaml, int smokeMax) {
        assertEntry(yaml, smokeMax, Firepit.BLOCK_ID, Firepit.OUTPUT);
    }

    /**
     * The entry of {@code blockId} has the three properties with TFC's defaults, the lit and unlit states and one variant per
     * state. The grill and the pot share the firepit's state definition, so they use this check too.
     */
    static void assertEntry(String yaml, int smokeMax, String blockId, String output) {
        assertThat(yaml).startsWith("# Generated by tools/datagen from TerraFirmaCraft @ " + PIN.substring(0, 9));
        Map<?, ?> root = (Map<?, ?>) CeYamlCheck.parse(yaml, output);
        Map<?, ?> states = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) root.get("blocks")).get(blockId)).get("states");

        Map<?, ?> props = (Map<?, ?>) states.get("properties");
        assertThat(props.keySet().stream().map(String::valueOf).toList()).containsExactly("lit", "smoke_level", "axis");
        assertThat(props.get("lit")).isEqualTo(Map.of("type", "boolean", "default", false));
        assertThat(props.get("smoke_level")).isEqualTo(Map.of("type", "int", "range", "0~" + smokeMax, "default", 0));
        assertThat(props.get("axis")).isEqualTo(Map.of("type", "string", "values", List.of("x", "z"), "default", "x"));

        Map<?, ?> appearances = (Map<?, ?>) states.get("appearances");
        assertThat(appearances.keySet().stream().map(String::valueOf).toList()).containsExactly(Firepit.LIT_APPEARANCE, Firepit.UNLIT_APPEARANCE);
        // PAR-INFRA-13: both appearances sit on the hitbox selector's carrier for TFC's shape, in its vanilla look.
        String carrier = SELECTED.get(blockId);
        assertThat(appearances.get(Firepit.LIT_APPEARANCE)).isEqualTo(Map.of("state", carrier));
        assertThat(appearances.get(Firepit.UNLIT_APPEARANCE)).isEqualTo(Map.of("state", carrier));
        assertThat(Firepit.carrier(blockId, Firepit.LIT_APPEARANCE)).isEqualTo(carrier);
        assertThat(yaml).as("no placeholder campfire: lit campfires make client smoke").doesNotContain("campfire")
            .as("a vanilla look needs no hide mapping").doesNotContain("block_state_mappings");

        Map<?, ?> variants = (Map<?, ?>) states.get("variants");
        assertThat(variants).hasSize(2 * (smokeMax + 1) * 2);
        assertThat(variants.get("lit=true,smoke_level=0,axis=x")).isEqualTo(Map.of("appearance", Firepit.LIT_APPEARANCE));
        assertThat(variants.get("lit=false," + "smoke_level=" + smokeMax + ",axis=z")).isEqualTo(Map.of("appearance", Firepit.UNLIT_APPEARANCE));
    }

    /** The four lit x axis variants that TFC's blockstate has. */
    static List<String> allVisualVariants() {
        return List.of("lit=true,axis=x", "lit=true,axis=z", "lit=false,axis=x", "lit=false,axis=z");
    }

    /** The default part, added to {@link Fixture#create}: the TFC definition, the smoke range and the blockstate. */
    static void addDefault(Path root) throws IOException {
        writeFirepit(root, 4, DEFINITION, allVisualVariants());
    }

    /** A checkout at the pinned commit with no firepit sources yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    /** Writes the firepit definition, the smoke-level property with {@code smokeMax} and the blockstate with {@code variants}. */
    static void writeFirepit(Path root, int smokeMax, String definition, List<String> variants) throws IOException {
        Fixture.write(root, Firepit.SOURCE, definition);
        Fixture.write(root, Firepit.PROPERTIES, "public final class TFCBlockStateProperties\n{\n"
            + "    public static final IntegerProperty SMOKE_LEVEL = IntegerProperty.create(\"smoke_level\", 0, " + smokeMax + ");\n}\n");
        writeBlockstate(root, variants);
    }

    private static void writeBlockstate(Path root, List<String> variants) throws IOException {
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        for (int i = 0; i < variants.size(); i++) {
            sb.append("    \"").append(variants.get(i)).append("\": { \"model\": \"tfc:block/firepit\" }")
                .append(i == variants.size() - 1 ? "\n" : ",\n");
        }
        sb.append("  }\n}\n");
        Fixture.write(root, Firepit.BLOCKSTATE, sb.toString());
    }

    /** The TFC checkout at the pinned commit, or a skipped test when there is none. */
    static TfcCheckout realCheckout() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }
}
