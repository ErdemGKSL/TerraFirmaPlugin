/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.install.InstallManifest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M1-INST-01: the hash-manifest installer on a temp directory (untouched / edited / deleted, atomic writes). */
class ContentInstallerTest {
    @TempDir
    Path tmp;
    Path source;
    Path target;

    @BeforeEach
    void setUp() throws IOException {
        source = tmp.resolve("jar/content/craftengine/tfp");
        target = tmp.resolve("plugins/CraftEngine/resources/tfp");
        ship("pack.yml", "namespace: tfp\n");
        ship("configuration/rock.yml", "items: v1\n");
        ship("resourcepack/assets/tfp/textures/block/granite.png", "PNG-v1");
    }

    void ship(String rel, String text) throws IOException {
        Path p = source.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    String read(String rel) throws IOException {
        return Files.readString(target.resolve(rel));
    }

    ContentInstaller.Report install(String version) throws IOException {
        return ContentInstaller.install(source, target, version, false);
    }

    @Test
    void firstInstallWritesEverythingAndTheManifest() throws IOException {
        ContentInstaller.Report r = install("1");
        assertThat(r.written()).isEqualTo(3);
        assertThat(r.changed()).isTrue();
        assertThat(read("configuration/rock.yml")).isEqualTo("items: v1\n");
        InstallManifest m = InstallManifest.fromJson(read(InstallManifest.FILE_NAME));
        assertThat(m.contentVersion()).isEqualTo("1");
        assertThat(m.files()).containsOnlyKeys("pack.yml", "configuration/rock.yml",
                "resourcepack/assets/tfp/textures/block/granite.png");
        assertThat(m.get("pack.yml").sha256()).isEqualTo(InstallManifest.sha256("namespace: tfp\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void secondRunWithoutChangesWritesNothing() throws IOException {
        install("1");
        Path rock = target.resolve("configuration/rock.yml");
        FileTime before = FileTime.fromMillis(1_000_000L);
        Files.setLastModifiedTime(rock, before);
        Files.setLastModifiedTime(target.resolve(InstallManifest.FILE_NAME), before);
        ContentInstaller.Report r = install("1");
        assertThat(r.written()).isZero();
        assertThat(r.unchanged()).isEqualTo(3);
        assertThat(r.changed()).isFalse();
        // CraftEngine caches configs by mtime + size: untouched files must keep their mtime.
        assertThat(Files.getLastModifiedTime(rock)).isEqualTo(before);
        assertThat(Files.getLastModifiedTime(target.resolve(InstallManifest.FILE_NAME))).isEqualTo(before);
    }

    @Test
    void untouchedFileIsOverwrittenOnUpdate() throws IOException {
        install("1");
        ship("configuration/rock.yml", "items: v2\n");
        ContentInstaller.Report r = install("2");
        assertThat(r.written()).isEqualTo(1);
        assertThat(read("configuration/rock.yml")).isEqualTo("items: v2\n");
        assertThat(Files.exists(target.resolve("configuration/rock.yml.tfp-new"))).isFalse();
    }

    @Test
    void editedFileIsKeptAndOursGoesToTfpNew() throws IOException {
        install("1");
        Files.writeString(target.resolve("configuration/rock.yml"), "items: mine\n");
        ship("configuration/rock.yml", "items: v2\n");
        ContentInstaller.Report r = install("2");
        assertThat(read("configuration/rock.yml")).isEqualTo("items: mine\n");
        assertThat(read("configuration/rock.yml.tfp-new")).isEqualTo("items: v2\n");
        assertThat(r.sideFiles()).containsExactly("configuration/rock.yml.tfp-new");
        assertThat(r.written()).isZero();

        // Next start, nothing new shipped: theirs is kept quietly, no new side file churn.
        Files.delete(target.resolve("configuration/rock.yml.tfp-new"));
        ContentInstaller.Report again = install("2");
        assertThat(again.keptEdited()).isEqualTo(1);
        assertThat(again.sideFiles()).isEmpty();
        assertThat(Files.exists(target.resolve("configuration/rock.yml.tfp-new"))).isFalse();

        // The owner adopts our version: tracked as ours again, so the next update overwrites it.
        Files.writeString(target.resolve("configuration/rock.yml"), "items: v2\n");
        ship("configuration/rock.yml", "items: v3\n");
        install("3");
        assertThat(read("configuration/rock.yml")).isEqualTo("items: v3\n");
    }

    @Test
    void deletedFileIsRespectedAndRecorded() throws IOException {
        install("1");
        Files.delete(target.resolve("configuration/rock.yml"));
        ship("configuration/rock.yml", "items: v2\n");
        ContentInstaller.Report r = install("2");
        assertThat(r.deleted()).isEqualTo(1);
        assertThat(Files.exists(target.resolve("configuration/rock.yml"))).isFalse();
        InstallManifest m = InstallManifest.fromJson(read(InstallManifest.FILE_NAME));
        assertThat(m.get("configuration/rock.yml").deleted()).isTrue();
        // Still respected on later runs.
        assertThat(install("2").deleted()).isEqualTo(1);
        assertThat(Files.exists(target.resolve("configuration/rock.yml"))).isFalse();
        // restore-deleted brings it back.
        ContentInstaller.install(source, target, "2", true);
        assertThat(read("configuration/rock.yml")).isEqualTo("items: v2\n");
        assertThat(InstallManifest.fromJson(read(InstallManifest.FILE_NAME)).get("configuration/rock.yml").deleted()).isFalse();
    }

    @Test
    void filesNoLongerShippedAreRemovedOnlyIfUntouched() throws IOException {
        ship("configuration/old.yml", "old: 1\n");
        ship("configuration/old-edited.yml", "old: 1\n");
        install("1");
        Files.delete(source.resolve("configuration/old.yml"));
        Files.delete(source.resolve("configuration/old-edited.yml"));
        Files.writeString(target.resolve("configuration/old-edited.yml"), "old: mine\n");
        ContentInstaller.Report r = install("2");
        assertThat(r.removed()).isEqualTo(1);
        assertThat(r.orphanedEdited()).containsExactly("configuration/old-edited.yml");
        assertThat(Files.exists(target.resolve("configuration/old.yml"))).isFalse();
        assertThat(read("configuration/old-edited.yml")).isEqualTo("old: mine\n");
        assertThat(InstallManifest.fromJson(read(InstallManifest.FILE_NAME)).files())
                .doesNotContainKeys("configuration/old.yml", "configuration/old-edited.yml");
    }

    @Test
    void foreignFileAtOurPathIsNotOverwritten() throws IOException {
        Files.createDirectories(target);
        Files.writeString(target.resolve("pack.yml"), "namespace: someone-else\n");
        ContentInstaller.Report r = install("1");
        assertThat(read("pack.yml")).isEqualTo("namespace: someone-else\n");
        assertThat(read("pack.yml.tfp-new")).isEqualTo("namespace: tfp\n");
        assertThat(r.written()).isEqualTo(2);
    }

    @Test
    void corruptManifestIsTreatedAsFirstInstallWithoutLosingEdits() throws IOException {
        install("1");
        Files.writeString(target.resolve("configuration/rock.yml"), "items: mine\n");
        Files.writeString(target.resolve(InstallManifest.FILE_NAME), "{ broken");
        install("1");
        assertThat(read("configuration/rock.yml")).isEqualTo("items: mine\n");
        assertThat(read("configuration/rock.yml.tfp-new")).isEqualTo("items: v1\n");
    }

    @Test
    void atomicWriteLeavesTheOldFileWhenWritingFails() throws IOException {
        Path f = tmp.resolve("dir/file.yml");
        AtomicFiles.write(f, "old\n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> AtomicFiles.write(f, out -> {
            out.write("new partial".getBytes(StandardCharsets.UTF_8));
            throw new IOException("disk full");
        })).isInstanceOf(IOException.class).hasMessage("disk full");
        assertThat(Files.readString(f)).isEqualTo("old\n");
        try (Stream<Path> s = Files.list(f.getParent())) {
            assertThat(s.map(p -> p.getFileName().toString()).toList()).containsExactly("file.yml");
        }
        AtomicFiles.write(f, "new\n".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.readString(f)).isEqualTo("new\n");
    }

    @Test
    void staleTempFilesFromACrashAreCleanedAndNeverInstalled() throws IOException {
        install("1");
        Path stale = target.resolve("configuration/.rock.yml.123" + AtomicFiles.TEMP_SUFFIX);
        Files.writeString(stale, "half");
        ship("configuration/.x" + AtomicFiles.TEMP_SUFFIX, "never shipped");
        install("1");
        assertThat(Files.exists(stale)).isFalse();
        assertThat(InstallManifest.fromJson(read(InstallManifest.FILE_NAME)).files().keySet())
                .noneMatch(k -> k.endsWith(AtomicFiles.TEMP_SUFFIX));
    }

    @Test
    void installsFromAJarFileSystem() throws IOException {
        Path jar = tmp.resolve("plugin.jar");
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of("create", "true"))) {
            Path root = zip.getPath("/content/craftengine/tfp");
            Files.createDirectories(root.resolve("configuration"));
            Files.writeString(root.resolve("pack.yml"), "namespace: tfp\n");
            Files.writeString(root.resolve("configuration/rock.yml"), "items: jar\n");
        }
        try (FileSystem zip = FileSystems.newFileSystem(jar)) {
            ContentInstaller.Report r = ContentInstaller.install(zip.getPath("/content/craftengine/tfp"), target, "1", false);
            assertThat(r.written()).isEqualTo(2);
        }
        assertThat(read("configuration/rock.yml")).isEqualTo("items: jar\n");
        assertThat(InstallManifest.fromJson(read(InstallManifest.FILE_NAME)).files().keySet())
                .isEqualTo(java.util.Set.of("pack.yml", "configuration/rock.yml"));
    }

    @Test
    void listSkipsOurBookkeepingFiles() throws IOException {
        ship(InstallManifest.FILE_NAME, "{}");
        ship("pack.yml.tfp-new", "x");
        assertThat(ContentInstaller.list(source).keySet()).isEqualTo(java.util.Set.copyOf(List.of("pack.yml",
                "configuration/rock.yml", "resourcepack/assets/tfp/textures/block/granite.png")));
    }
}
