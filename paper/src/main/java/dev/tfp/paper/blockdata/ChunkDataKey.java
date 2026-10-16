/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blockdata;

import dev.tfp.core.persist.VersionedCodec;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One kind of chunk-scale data (climate cache override, collapse support map, soil nutrients) and its
 * versioned codec. Stored under {@code tfp:<id>} in the chunk's persistent data container.
 *
 * @param id    path part of the key: {@code [a-z0-9_./-]+}
 * @param codec how the value is (de)serialised; old versions are migrated on read
 */
public record ChunkDataKey<T>(String id, VersionedCodec<T> codec) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_./-]+");

    public ChunkDataKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("bad chunk data id '" + id + "': must match " + ID.pattern());
        }
    }
}
