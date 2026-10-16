/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.registry.Registries;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The registry glue behind {@code /tfp reload data}: loads the real datagen output; finds the data root in a jar. */
class RegistryServiceTest {
    @Test
    void reloadLoadsTheContentDataAndBumpsTheVersion() throws Exception {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        RegistryService service = new RegistryService(() -> Path.of(configured), Runnable::run);
        Registries.ReloadResult first = service.reload().get(120, TimeUnit.SECONDS);
        assertThat(first.published()).isTrue();
        assertThat(RegistryService.entries(first.snapshot())).isPositive();
        Registries.ReloadResult second = service.reload().get(120, TimeUnit.SECONDS);
        assertThat(second.version()).isGreaterThan(first.version());
        assertThat(service.registries().get()).isSameAs(second.snapshot());
    }

    @Test
    void failedReloadKeepsTheCurrentSnapshot(@TempDir Path empty) {
        RegistryService service = new RegistryService(() -> empty, Runnable::run);
        assertThat(service.reload()).isCompletedExceptionally();
        assertThat(service.registries().version()).isZero();
    }

    @Test
    void findsTheDataRootInsideAJar(@TempDir Path dir) throws IOException {
        Path jar = dir.resolve("plugin.jar");
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("content/data/id_map.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            Path root = RegistryService.jarDataRoot(loader);
            assertThat(Files.readString(root.resolve("id_map.json"))).isEqualTo("{}");
            // a second call reuses the open zip file system
            assertThat(RegistryService.jarDataRoot(loader)).isEqualTo(root);
            root.getFileSystem().close();
        }
    }
}
