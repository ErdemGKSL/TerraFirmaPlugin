/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import dev.tfp.paper.install.ContentInstaller;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The self-test's own CraftEngine pack ({@code /craftengine/tfpitest} in this jar, namespace
 * {@value #NAMESPACE}): one marker block for {@code ce.block_roundtrip} (M1-INT-02). Installed with the main
 * plugin's {@link ContentInstaller} (hash manifest, atomic writes) into
 * {@code plugins/CraftEngine/resources/tfpitest} from the self-test's {@code onLoad}, before CraftEngine
 * enables and reads its packs. Like the main installer it never creates {@code CraftEngine/resources/}
 * itself: CraftEngine extracts its default packs only while that folder does not exist (M0-VIS-01).
 */
public final class SelfTestPack {
    public static final String NAMESPACE = "tfpitest";
    /** The marker block the pack defines. */
    public static final String MARKER = NAMESPACE + ":marker";
    static final String JAR_DIR = "craftengine/" + NAMESPACE;

    private SelfTestPack() {
    }

    /**
     * @param pluginsDir the server's {@code plugins} folder
     * @return one line for the log
     */
    static String install(Path pluginsDir, ClassLoader loader, String version) throws IOException {
        Path resources = pluginsDir.resolve("CraftEngine").resolve("resources");
        if (!Files.isDirectory(resources)) {
            return "not installed: " + resources + " does not exist yet (CraftEngine creates it on its first start)";
        }
        ContentInstaller.Report r = ContentInstaller.install(source(loader), resources.resolve(NAMESPACE), version, true);
        return r.summary();
    }

    /** The pack folder inside this plugin's jar (or the resources directory in tests). */
    static Path source(ClassLoader loader) throws IOException {
        URL url = loader.getResource(JAR_DIR + "/pack.yml");
        if (url == null) {
            throw new IOException(JAR_DIR + "/pack.yml is missing from the self-test jar");
        }
        try {
            URI uri = url.toURI();
            if (!"jar".equals(uri.getScheme())) {
                return Path.of(uri).getParent();
            }
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
            } catch (FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(uri);
            }
            return fs.getPath("/" + JAR_DIR);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
