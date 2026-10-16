/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The per-target record of what we wrote ({@code <target>/.tfp-manifest.json}, {@code dependency-bootstrap} §5):
 * for each file (path relative to the target, {@code /}-separated) the sha256 it had when we wrote it, or
 * a tombstone when the owner deleted it. Immutable; {@link Builder} makes the next version.
 */
public record InstallManifest(int format, String contentVersion, Map<String, Entry> files) {
    public static final String FILE_NAME = ".tfp-manifest.json";
    public static final int FORMAT = 1;
    public static final InstallManifest EMPTY = new InstallManifest(FORMAT, "", Map.of());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * @param sha256  hash of the bytes we last wrote (or adopted) at this path
     * @param deleted the owner deleted the file; it is not recreated unless {@code restore-deleted}
     */
    public record Entry(String sha256, boolean deleted) {
        public Entry {
            Objects.requireNonNull(sha256, "sha256");
        }

        public static Entry written(String sha256) {
            return new Entry(sha256, false);
        }

        public static Entry tombstone(String sha256) {
            return new Entry(sha256, true);
        }
    }

    public InstallManifest {
        Objects.requireNonNull(contentVersion, "contentVersion");
        files = java.util.Collections.unmodifiableMap(new TreeMap<>(files));
    }

    public Entry get(String path) {
        return files.get(path);
    }

    public Builder toBuilder(String newContentVersion) {
        return new Builder(newContentVersion, files);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", format);
        root.addProperty("content_version", contentVersion);
        JsonObject f = new JsonObject();
        for (Map.Entry<String, Entry> e : files.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("sha256", e.getValue().sha256());
            if (e.getValue().deleted()) {
                o.addProperty("deleted", true);
            }
            f.add(e.getKey(), o);
        }
        root.add("files", f);
        return GSON.toJson(root) + "\n";
    }

    /** @throws IllegalArgumentException if the text is not a manifest we understand */
    public static InstallManifest fromJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int format = root.has("format") ? root.get("format").getAsInt() : 0;
            if (format != FORMAT) {
                throw new IllegalArgumentException("unsupported manifest format " + format);
            }
            String version = root.has("content_version") ? root.get("content_version").getAsString() : "";
            Map<String, Entry> files = new TreeMap<>();
            JsonObject f = root.getAsJsonObject("files");
            if (f != null) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : f.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    boolean deleted = o.has("deleted") && o.get("deleted").getAsBoolean();
                    files.put(e.getKey(), new Entry(o.get("sha256").getAsString(), deleted));
                }
            }
            return new InstallManifest(format, version, files);
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            throw new IllegalArgumentException("not a valid install manifest: " + e.getMessage(), e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mutable, single-threaded. */
    public static final class Builder {
        private final String contentVersion;
        private final Map<String, Entry> files;

        Builder(String contentVersion, Map<String, Entry> files) {
            this.contentVersion = contentVersion;
            this.files = new TreeMap<>(files);
        }

        public Builder put(String path, Entry entry) {
            files.put(path, entry);
            return this;
        }

        public Builder remove(String path) {
            files.remove(path);
            return this;
        }

        public InstallManifest build() {
            return new InstallManifest(FORMAT, contentVersion, files);
        }
    }
}
