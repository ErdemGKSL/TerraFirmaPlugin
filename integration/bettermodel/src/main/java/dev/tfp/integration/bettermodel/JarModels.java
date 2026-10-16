/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.bettermodel;

import dev.tfp.paper.integration.ModelSource;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code .bbmodel} files shipped in our jar. A jar folder cannot be listed portably, so the folder
 * holds an {@code index.txt} (one model id per line, {@code #} comments); each id is {@code <id>.bbmodel}
 * in the same folder. Streams are opened lazily, on BetterModel's threads.
 */
final class JarModels {
    static final String DEFAULT_FOLDER = "bettermodel/models/";

    private JarModels() {
    }

    static List<ModelSource> load(ClassLoader loader, String folder) {
        List<ModelSource> out = new ArrayList<>();
        try (InputStream in = loader.getResourceAsStream(folder + "index.txt")) {
            if (in == null) {
                return List.of();
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                String id = line.strip();
                if (id.isEmpty() || id.startsWith("#")) {
                    continue;
                }
                String path = folder + id + ".bbmodel";
                if (loader.getResource(path) == null) {
                    throw new IllegalStateException("model index lists " + id + " but " + path + " is missing");
                }
                out.add(new ModelSource(id, () -> {
                    InputStream s = loader.getResourceAsStream(path);
                    if (s == null) {
                        throw new FileNotFoundException(path);
                    }
                    return s;
                }));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + folder + "index.txt", e);
        }
        return List.copyOf(out);
    }
}
