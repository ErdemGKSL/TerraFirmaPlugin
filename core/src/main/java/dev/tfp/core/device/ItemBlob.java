/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.device;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * One inventory slot of a device as the platform serialised it (Paper: {@code ItemStack.serializeAsBytes}). Core never
 * reads the bytes; it only stores, compares and moves them. An empty array is an empty slot. Immutable: the bytes are
 * copied in and out.
 */
public final class ItemBlob {
    /** The empty slot. */
    public static final ItemBlob EMPTY = new ItemBlob(new byte[0]);

    private final byte[] bytes;

    private ItemBlob(byte[] bytes) {
        this.bytes = bytes;
    }

    /** A blob of a copy of {@code bytes}; {@code null} or empty is {@link #EMPTY}. */
    public static ItemBlob of(byte[] bytes) {
        return bytes == null || bytes.length == 0 ? EMPTY : new ItemBlob(bytes.clone());
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    /** A copy of the bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }

    public int size() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemBlob b && Arrays.equals(bytes, b.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "ItemBlob[empty]";
        }
        String hex = HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, 8));
        return "ItemBlob[" + bytes.length + " bytes, " + hex + (bytes.length > 8 ? "..." : "") + "]";
    }
}
