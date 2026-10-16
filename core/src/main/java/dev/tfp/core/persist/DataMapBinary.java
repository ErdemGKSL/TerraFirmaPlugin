/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.persist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compact, strict binary form of a {@link DataMap}, for stores that hold bytes (player files, chunk PDC).
 *
 * <pre>
 * blob  = "TFPD" format:u8(=1) map
 * map   = count:i32 { type:u8 key:str value }
 * str   = len:i32 utf8-bytes
 * value = i32 | i64 | f32 | f64 | u8(0/1) | str | len:i32 bytes | map | count:i32 { map }
 * </pre>
 * Decoding is strict: a bad magic/format byte, unknown type tag, negative or oversized length, nesting
 * deeper than {@value #MAX_DEPTH}, or trailing bytes fail with {@link PersistException}. Pure and
 * thread-safe (no state).
 */
public final class DataMapBinary {
    static final byte[] MAGIC = {'T', 'F', 'P', 'D'};
    static final int FORMAT = 1;
    static final int MAX_DEPTH = 64;
    /** Upper bound for any length/count in one blob: larger values mean corruption. */
    static final int MAX_LENGTH = 64 * 1024 * 1024;

    private static final int T_INT = 1;
    private static final int T_LONG = 2;
    private static final int T_FLOAT = 3;
    private static final int T_DOUBLE = 4;
    private static final int T_BOOLEAN = 5;
    private static final int T_STRING = 6;
    private static final int T_BYTES = 7;
    private static final int T_MAP = 8;
    private static final int T_LIST = 9;

    private DataMapBinary() {
    }

    public static byte[] encode(DataMap map) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write(MAGIC);
            out.writeByte(FORMAT);
            writeMap(out, map);
        } catch (IOException e) {
            throw new UncheckedIOException(e); // ByteArrayOutputStream does not throw
        }
        return bytes.toByteArray();
    }

    public static DataMap decode(byte[] blob) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new PersistException("not a TFPD blob (bad magic)");
            }
            int format = in.readUnsignedByte();
            if (format != FORMAT) {
                throw new PersistException("unknown TFPD format " + format);
            }
            DataMap map = readMap(in, 0);
            if (in.read() != -1) {
                throw new PersistException("trailing bytes after TFPD map");
            }
            return map;
        } catch (EOFException e) {
            throw new PersistException("truncated TFPD blob", e);
        } catch (IOException e) {
            throw new PersistException("unreadable TFPD blob: " + e.getMessage(), e);
        }
    }

    private static void writeMap(DataOutputStream out, DataMap map) throws IOException {
        Map<String, Object> entries = map.entries();
        out.writeInt(entries.size());
        for (Map.Entry<String, Object> e : entries.entrySet()) {
            Object v = e.getValue();
            switch (v) {
                case Integer i -> {
                    header(out, T_INT, e.getKey());
                    out.writeInt(i);
                }
                case Long l -> {
                    header(out, T_LONG, e.getKey());
                    out.writeLong(l);
                }
                case Float f -> {
                    header(out, T_FLOAT, e.getKey());
                    out.writeFloat(f);
                }
                case Double d -> {
                    header(out, T_DOUBLE, e.getKey());
                    out.writeDouble(d);
                }
                case Boolean b -> {
                    header(out, T_BOOLEAN, e.getKey());
                    out.writeByte(b ? 1 : 0);
                }
                case String s -> {
                    header(out, T_STRING, e.getKey());
                    writeString(out, s);
                }
                case byte[] b -> {
                    header(out, T_BYTES, e.getKey());
                    out.writeInt(b.length);
                    out.write(b);
                }
                case DataMap m -> {
                    header(out, T_MAP, e.getKey());
                    writeMap(out, m);
                }
                case List<?> l -> {
                    header(out, T_LIST, e.getKey());
                    out.writeInt(l.size());
                    for (Object o : l) {
                        writeMap(out, (DataMap) o);
                    }
                }
                default -> throw new PersistException("unsupported value type " + v.getClass().getName());
            }
        }
    }

    private static void header(DataOutputStream out, int type, String key) throws IOException {
        out.writeByte(type);
        writeString(out, key);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static DataMap readMap(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new PersistException("TFPD nesting deeper than " + MAX_DEPTH);
        }
        int count = length(in);
        DataMap map = new DataMap();
        for (int i = 0; i < count; i++) {
            int type = in.readUnsignedByte();
            String key = readString(in);
            if (map.has(key)) {
                throw new PersistException("duplicate key '" + key + "'");
            }
            switch (type) {
                case T_INT -> map.putInt(key, in.readInt());
                case T_LONG -> map.putLong(key, in.readLong());
                case T_FLOAT -> map.putFloat(key, in.readFloat());
                case T_DOUBLE -> map.putDouble(key, in.readDouble());
                case T_BOOLEAN -> {
                    int b = in.readUnsignedByte();
                    if (b > 1) {
                        throw new PersistException("key '" + key + "': bad boolean " + b);
                    }
                    map.putBoolean(key, b == 1);
                }
                case T_STRING -> map.putString(key, readString(in));
                case T_BYTES -> {
                    byte[] b = new byte[length(in)];
                    in.readFully(b);
                    map.putBytes(key, b);
                }
                case T_MAP -> map.putMap(key, readMap(in, depth + 1));
                case T_LIST -> {
                    int n = length(in);
                    List<DataMap> list = new ArrayList<>(Math.min(n, 1024));
                    for (int j = 0; j < n; j++) {
                        list.add(readMap(in, depth + 1));
                    }
                    map.putList(key, list);
                }
                default -> throw new PersistException("key '" + key + "': unknown type tag " + type);
            }
        }
        return map;
    }

    private static String readString(DataInputStream in) throws IOException {
        byte[] b = new byte[length(in)];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        // Every counted element takes at least one byte, so a length beyond the remaining bytes is corrupt
        // (checked before allocating; the stream is a ByteArrayInputStream, so available() is exact).
        if (n < 0 || n > MAX_LENGTH || n > in.available()) {
            throw new PersistException("bad TFPD length " + n);
        }
        return n;
    }
}
