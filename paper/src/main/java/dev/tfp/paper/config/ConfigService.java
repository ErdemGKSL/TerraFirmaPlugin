/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.config;

import dev.tfp.core.config.ConfigCodec;
import dev.tfp.core.config.TfpConfig;
import dev.tfp.core.config.YamlDefaultsMerge;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Supplier;

/**
 * Our {@code config.yml} on disk (M1-BOOT-03). Writes the shipped defaults only when the file is absent;
 * otherwise appends missing top-level sections textually ({@link YamlDefaultsMerge}) so the owner's
 * comments survive. Parsing is lenient ({@link ConfigCodec}). The current config is an immutable record
 * behind a volatile field, swapped on reload.
 *
 * <p>Does blocking file I/O: call it from {@code onLoad} or the {@code io} executor, never a region thread.
 */
public final class ConfigService {
    private final Path file;
    private final Supplier<String> defaults;
    private volatile TfpConfig current = TfpConfig.DEFAULTS;

    /**
     * @param created  the file did not exist and was written from the defaults
     * @param added    top-level sections appended to the owner's file
     * @param warnings unknown keys, bad values, or why the file could not be used
     * @param applied  false if the file was unreadable/invalid and the previous config was kept
     */
    public record Outcome(TfpConfig config, boolean created, List<String> added, List<String> warnings, boolean applied) {
        public Outcome {
            added = List.copyOf(added);
            warnings = List.copyOf(warnings);
        }
    }

    public ConfigService(Path file, Supplier<String> defaults) {
        this.file = file;
        this.defaults = defaults;
    }

    public TfpConfig current() {
        return current;
    }

    public Path file() {
        return file;
    }

    /** Loads (or reloads) the file, creating or completing it first. */
    public synchronized Outcome load() {
        String defaultText = defaults.get();
        boolean created = false;
        List<String> added = List.of();
        String text;
        try {
            if (Files.notExists(file)) {
                writeAtomically(defaultText);
                created = true;
                text = defaultText;
            } else {
                text = Files.readString(file, StandardCharsets.UTF_8);
                YamlDefaultsMerge.Result merged = YamlDefaultsMerge.merge(text, defaultText);
                if (merged.changed()) {
                    // Only append when the owner's file is valid YAML: never pile our text onto a broken file.
                    Yamls.load(text);
                    writeAtomically(merged.text());
                    text = merged.text();
                    added = merged.added();
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return new Outcome(current, false, List.of(),
                    List.of(file.getFileName() + ": " + e.getMessage() + "; keeping the previous settings"), false);
        }
        ConfigCodec.Parsed parsed;
        try {
            parsed = ConfigCodec.parse(Yamls.load(text));
        } catch (IllegalArgumentException e) {
            return new Outcome(current, created, added,
                    List.of(file.getFileName() + ": " + e.getMessage() + "; keeping the previous settings"), false);
        }
        current = parsed.config();
        return new Outcome(parsed.config(), created, added, parsed.warnings(), true);
    }

    private void writeAtomically(String text) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
