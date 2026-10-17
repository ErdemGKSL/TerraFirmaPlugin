/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * M2-GEN-12: TFC's tree structure templates ({@code src/main/resources/data/tfc/structure/<tree>/<n>.nbt}, gzipped NBT
 * written by Minecraft's structure block) → {@code data/tfp/worldgen/tree_structure/<tree>/<n>.json}, with the size, the
 * palette (block id renamed to our namespace and its properties) and the blocks in file order as
 * {@code [x, y, z, paletteIndex]}. The runtime orders the blocks as vanilla's template loader does. The output is compact
 * JSON (one line): the 539 templates hold about 135,000 blocks.
 *
 * <p>Every structure of TFC at the pinned commit is a tree. A template with entities, several palettes or block entity data
 * is refused (TFC's trees have none).
 */
final class TreeStructures {
    static final String SOURCE = TfcCheckout.MAIN + "data/" + Json.FROM_NS + "/structure/";
    static final String TARGET = "data/" + Json.TO_NS + "/worldgen/tree_structure/";
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private TreeStructures() {
    }

    /** Exports every template below {@link #SOURCE}; returns the number written. */
    static int run(TfcCheckout tfc, OutputTree out) {
        int n = 0;
        for (String file : tfc.list(SOURCE)) {
            if (!file.endsWith(".nbt")) {
                throw new DatagenException("unexpected non-NBT structure file " + file);
            }
            final String rest = file.substring(SOURCE.length(), file.length() - ".nbt".length());
            out.put(TARGET + rest + ".json", COMPACT.toJson(convert(tfc.read(file), file)) + "\n");
            n++;
        }
        return n;
    }

    /** One template as JSON. */
    static JsonObject convert(byte[] gzipped, String source) {
        final Map<String, Object> root = read(gzipped, source);
        if (root.containsKey("palettes")) {
            throw new DatagenException("structure with several palettes is not supported: " + source);
        }
        final List<?> entities = (List<?>) root.get("entities");
        if (entities != null && !entities.isEmpty()) {
            throw new DatagenException("structure with entities is not supported: " + source);
        }
        final JsonObject out = new JsonObject();
        final JsonArray size = new JsonArray();
        for (Object v : (List<?>) root.get("size")) {
            size.add((Integer) v);
        }
        out.add("size", size);
        final JsonArray palette = new JsonArray();
        for (Object entry : (List<?>) root.get("palette")) {
            final Map<?, ?> state = (Map<?, ?>) entry;
            final JsonObject s = new JsonObject();
            s.addProperty("Name", Json.renameId((String) state.get("Name")));
            final Map<?, ?> props = (Map<?, ?>) state.get("Properties");
            if (props != null) {
                final JsonObject p = new JsonObject();
                new TreeMap<>(props).forEach((k, v) -> p.addProperty((String) k, (String) v));
                s.add("Properties", p);
            }
            palette.add(s);
        }
        out.add("palette", palette);
        final JsonArray blocks = new JsonArray();
        for (Object entry : (List<?>) root.get("blocks")) {
            final Map<?, ?> block = (Map<?, ?>) entry;
            if (block.containsKey("nbt")) {
                throw new DatagenException("structure block with block entity data is not supported: " + source);
            }
            final JsonArray b = new JsonArray();
            for (Object v : (List<?>) block.get("pos")) {
                b.add((Integer) v);
            }
            b.add((Integer) block.get("state"));
            blocks.add(b);
        }
        out.add("blocks", blocks);
        return out;
    }

    /** Reads a gzipped NBT file: compounds become ordered maps, lists become lists, int arrays {@code int[]}. */
    static Map<String, Object> read(byte[] gzipped, String source) {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzipped)))) {
            final int type = in.readByte();
            if (type != 10) {
                throw new DatagenException("NBT root is not a compound in " + source);
            }
            in.readUTF();
            @SuppressWarnings("unchecked")
            final Map<String, Object> root = (Map<String, Object>) payload(in, type, source);
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read NBT " + source, e);
        }
    }

    private static Object payload(DataInputStream in, int type, String source) throws IOException {
        return switch (type) {
            case 1 -> (int) in.readByte();
            case 2 -> (int) in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> {
                final byte[] b = new byte[in.readInt()];
                in.readFully(b);
                yield b;
            }
            case 8 -> in.readUTF();
            case 9 -> {
                final int element = in.readByte();
                final int n = in.readInt();
                final List<Object> list = new ArrayList<>(Math.max(n, 0));
                for (int i = 0; i < n; i++) {
                    list.add(payload(in, element, source));
                }
                yield list;
            }
            case 10 -> {
                final Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    final int t = in.readByte();
                    if (t == 0) {
                        break;
                    }
                    final String key = in.readUTF();
                    map.put(key, payload(in, t, source));
                }
                yield map;
            }
            case 11 -> {
                final int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readInt();
                }
                yield a;
            }
            case 12 -> {
                final long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = in.readLong();
                }
                yield a;
            }
            default -> throw new DatagenException("unknown NBT tag type " + type + " in " + source);
        };
    }
}
