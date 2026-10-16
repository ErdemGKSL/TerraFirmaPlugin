/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.json;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Where in which file a codec is decoding, and the sink for the ids/tags it refers to.
 * Immutable; {@link #field}/{@link #index} create child contexts.
 */
public final class DecodeContext {
    private final String source;
    private final DecodeContext parent;
    private final String segment;
    private final Consumer<Reference> references;

    private DecodeContext(String source, DecodeContext parent, String segment, Consumer<Reference> references) {
        this.source = source;
        this.parent = parent;
        this.segment = segment;
        this.references = references;
    }

    /** Root context of a file; {@code source} is shown in errors (e.g. {@code tfp:recipe/quern/flux}). */
    public static DecodeContext root(String source, Consumer<Reference> references) {
        return new DecodeContext(Objects.requireNonNull(source), null, "$", Objects.requireNonNull(references));
    }

    /** Root context that discards references (tests, ad-hoc decoding). */
    public static DecodeContext root(String source) {
        return root(source, r -> { });
    }

    public DecodeContext field(String key) {
        return new DecodeContext(source, this, "." + key, references);
    }

    public DecodeContext index(int i) {
        return new DecodeContext(source, this, "[" + i + "]", references);
    }

    public String source() {
        return source;
    }

    /** JSON path inside the file, e.g. {@code $.result.modifiers[1].type}. */
    public String path() {
        StringBuilder sb = new StringBuilder();
        build(sb);
        return sb.toString();
    }

    private void build(StringBuilder sb) {
        if (parent != null) {
            parent.build(sb);
        }
        sb.append(segment);
    }

    public String location() {
        return source + " " + path();
    }

    public JsonDataException error(String problem) {
        return new JsonDataException(location(), problem);
    }

    /** Records a reference to {@code id} in {@code registry}; resolved after loading. */
    public void reference(String registry, String id, boolean tag) {
        references.accept(new Reference(registry, id, tag, location()));
    }
}
