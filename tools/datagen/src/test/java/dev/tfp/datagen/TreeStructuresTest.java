/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M2-GEN-12: the tree features and the tree structure templates go through datagen like the other worldgen data. */
class TreeStructuresTest {
    @TempDir
    Path tmp;

    /** A structure-block NBT file: size 3 x 2 x 1, a log and a leaves palette entry, three blocks, no entities. */
    static byte[] template(boolean withEntity) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            intList(out, "size", 3, 2, 1);
            out.writeByte(9);
            out.writeUTF("palette");
            out.writeByte(10);
            out.writeInt(2);
            out.writeByte(8);
            out.writeUTF("Name");
            out.writeUTF("tfc:wood/log/oak");
            out.writeByte(10);
            out.writeUTF("Properties");
            string(out, "branch_direction", "down");
            string(out, "axis", "y");
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(8);
            out.writeUTF("Name");
            out.writeUTF("tfc:wood/leaves/oak");
            out.writeByte(0);
            out.writeByte(9);
            out.writeUTF("blocks");
            out.writeByte(10);
            out.writeInt(3);
            block(out, 2, 1, 0, 1);
            block(out, 1, 0, 0, 0);
            block(out, 0, 1, 0, 1);
            out.writeByte(9);
            out.writeUTF("entities");
            if (withEntity) {
                out.writeByte(10);
                out.writeInt(1);
                intList(out, "blockPos", 0, 0, 0);
                out.writeByte(0);
            } else {
                out.writeByte(0);
                out.writeInt(0);
            }
            out.writeByte(3);
            out.writeUTF("DataVersion");
            out.writeInt(3955);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void intList(DataOutputStream out, String name, int... values) throws IOException {
        out.writeByte(9);
        out.writeUTF(name);
        out.writeByte(3);
        out.writeInt(values.length);
        for (int v : values) {
            out.writeInt(v);
        }
    }

    private static void string(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8);
        out.writeUTF(name);
        out.writeUTF(value);
    }

    private static void block(DataOutputStream out, int x, int y, int z, int state) throws IOException {
        intList(out, "pos", x, y, z);
        out.writeByte(3);
        out.writeUTF("state");
        out.writeInt(state);
        out.writeByte(0);
    }

    @Test
    void exportsTreeFeaturesAndTemplatesWithRenamedIds() throws IOException {
        final Path tfc = Fixture.create(tmp.resolve("tfc"), TfcCheckout.PINNED_COMMIT);
        final Path nbt = tfc.resolve(TreeStructures.SOURCE + "oak/1.nbt");
        Files.createDirectories(nbt.getParent());
        Files.write(nbt, template(false));
        final Path feature = tfc.resolve(TfcCheckout.MAIN + "data/tfc/" + DataExport.TREE_DIR + "/oak.json");
        Files.createDirectories(feature.getParent());
        Files.writeString(feature, "{\"__comment__\": \"x\", \"type\": \"tfc:random_tree\", \"config\": {\"structures\": [\"tfc:oak/1\"], "
            + "\"placement\": {\"width\": 1, \"height\": 5}}}");

        final Datagen.Result result = Datagen.generate(tfc, TfcCheckout.PINNED_COMMIT);
        final SortedMap<String, byte[]> files = result.tree().files();
        assertThat(new String(files.get("data/tfp/worldgen/configured_feature/tree/oak.json"), StandardCharsets.UTF_8))
            .contains("\"type\": \"tfp:random_tree\"", "\"tfp:oak/1\"").doesNotContain("tfc:", "__comment__");
        assertThat(new String(files.get(TreeStructures.TARGET + "oak/1.json"), StandardCharsets.UTF_8)).isEqualTo(
            "{\"size\":[3,2,1],\"palette\":[{\"Name\":\"tfp:wood/log/oak\",\"Properties\":{\"axis\":\"y\",\"branch_direction\":\"down\"}},"
                + "{\"Name\":\"tfp:wood/leaves/oak\"}],\"blocks\":[[2,1,0,1],[1,0,0,0],[0,1,0,1]]}\n");
    }

    @Test
    void refusesTemplatesWithEntities() {
        assertThatThrownBy(() -> TreeStructures.convert(template(true), "x.nbt")).isInstanceOf(DatagenException.class)
            .hasMessageContaining("entities");
    }
}
