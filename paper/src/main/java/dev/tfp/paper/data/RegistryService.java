/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.data;

import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Paper glue of the registries (M1-REG-01, ARCHITECTURE.md §6): loads the datagen data shipped in the jar
 * ({@code /content/data}) off-thread and swaps it in atomically. Used at enable (first load signals the
 * ready gate) and by {@code /tfp reload data}.
 */
public final class RegistryService {
    /** Jar path of the data root (the {@code :content} module ships {@code content/generated/content} as {@code /content}). */
    public static final String JAR_DATA_ROOT = "content/data";

    private final Registries registries = new Registries();
    private final Supplier<Path> dataRoot;
    private final Executor executor;

    /**
     * @param dataRoot resolves the data root; called on {@code executor} for every load
     * @param executor where parsing runs (our CPU pool; file reads are from the jar, cheap)
     */
    public RegistryService(Supplier<Path> dataRoot, Executor executor) {
        this.dataRoot = dataRoot;
        this.executor = executor;
    }

    public Registries registries() {
        return registries;
    }

    /** Loads and publishes; the future fails with the loader's error and the current snapshot stays. */
    public CompletableFuture<Registries.ReloadResult> reload() {
        return registries.reload(executor, () -> {
            Path root = dataRoot.get();
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + root, e);
            }
        });
    }

    /** Total entries over all registries of a snapshot (for messages). */
    public static long entries(RegistrySnapshot snapshot) {
        long n = 0;
        for (Registry<?> r : snapshot.registries()) {
            n += r.size();
        }
        return n;
    }

    /**
     * The data root inside the jar that holds {@code loader}'s classes (or a directory on the class path in
     * development). Opens the jar as a zip file system once and keeps it open.
     */
    public static Path jarDataRoot(ClassLoader loader) {
        URL marker = loader.getResource(JAR_DATA_ROOT + "/" + KnownIds.ID_MAP_FILE);
        if (marker == null) {
            throw new IllegalStateException(JAR_DATA_ROOT + "/" + KnownIds.ID_MAP_FILE + " is missing from the plugin jar");
        }
        try {
            URI uri = marker.toURI();
            if (!"jar".equals(uri.getScheme())) {
                return Path.of(uri).getParent();
            }
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(uri);
            }
            return fs.getPath("/" + JAR_DATA_ROOT);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("bad resource URL " + marker, e);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open the plugin jar to read " + JAR_DATA_ROOT, e);
        }
    }
}
