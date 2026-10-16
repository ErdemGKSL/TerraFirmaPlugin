/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import dev.tfp.core.install.FileDecision;
import dev.tfp.core.install.InstallManifest;
import dev.tfp.core.install.InstallManifest.Entry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Copies one tree of our generated content (a folder of the jar's {@code /content}) into another plugin's
 * folder ({@code dependency-bootstrap} §5, M1-INST-01). Per file, {@link FileDecision} decides from the
 * shipped hash, the hash on disk and the hash recorded in {@code <target>/.tfp-manifest.json}:
 * untouched files are updated, owner-edited files are kept (ours goes to {@code <name>.tfp-new}), files the
 * owner deleted stay deleted. Only changed files are written, each atomically; the manifest is written last.
 *
 * <p>Blocking file I/O: {@code onLoad} or the io executor only.
 */
public final class ContentInstaller {
    private ContentInstaller() {
    }

    /**
     * @param written        files written with our content (new or updated)
     * @param unchanged      files that already had our content
     * @param sideFiles      {@code .tfp-new} files written next to owner-edited files (target-relative paths)
     * @param keptEdited     owner-edited files with no new version from us
     * @param deleted        files the owner deleted that we did not recreate
     * @param removed        files we wrote earlier, no longer shipped, untouched: deleted
     * @param orphanedEdited files we wrote earlier, no longer shipped, edited by the owner: left in place
     */
    public record Report(Path target, int written, int unchanged, List<String> sideFiles, int keptEdited,
            int deleted, int removed, List<String> orphanedEdited) {
        public Report {
            sideFiles = List.copyOf(sideFiles);
            orphanedEdited = List.copyOf(orphanedEdited);
        }

        /** True if the files the target plugin loads changed (so its content must be reloaded). */
        public boolean changed() {
            return written > 0 || removed > 0;
        }

        public String summary() {
            return target + ": " + written + " written, " + unchanged + " unchanged, " + removed + " removed"
                    + (sideFiles.isEmpty() ? "" : ", " + sideFiles.size() + " edited by you (ours saved as *" + FileDecision.SIDE_SUFFIX + ")")
                    + (keptEdited == 0 ? "" : ", " + keptEdited + " edited by you kept")
                    + (deleted == 0 ? "" : ", " + deleted + " deleted by you not restored")
                    + (orphanedEdited.isEmpty() ? "" : ", " + orphanedEdited.size() + " no longer shipped but edited by you, left in place");
        }
    }

    /**
     * @param source         root of the shipped tree (a jar file-system path or a directory)
     * @param target         the folder to fill (created if needed)
     * @param contentVersion recorded in the manifest
     * @param restoreDeleted {@code auto-install.restore-deleted}
     */
    public static Report install(Path source, Path target, String contentVersion, boolean restoreDeleted) throws IOException {
        Files.createDirectories(target);
        AtomicFiles.deleteStaleTemps(target);
        Path manifestFile = target.resolve(InstallManifest.FILE_NAME);
        InstallManifest old = readManifest(manifestFile);
        InstallManifest.Builder next = old.toBuilder(contentVersion);

        Map<String, Path> shipped = list(source);
        int written = 0;
        int unchanged = 0;
        int keptEdited = 0;
        int deleted = 0;
        List<String> sideFiles = new ArrayList<>();
        for (Map.Entry<String, Path> e : shipped.entrySet()) {
            String rel = e.getKey();
            byte[] bytes = Files.readAllBytes(e.getValue());
            String shippedHash = InstallManifest.sha256(bytes);
            Path dest = resolve(target, rel);
            String current = hashOrNull(dest);
            Entry recorded = old.get(rel);
            FileDecision d = FileDecision.decide(shippedHash, current, recorded, restoreDeleted);
            switch (d) {
                case WRITE -> {
                    AtomicFiles.write(dest, bytes);
                    written++;
                }
                case UNCHANGED -> unchanged++;
                case WRITE_SIDE_FILE -> {
                    Path side = dest.resolveSibling(dest.getFileName() + FileDecision.SIDE_SUFFIX);
                    if (!shippedHash.equals(hashOrNull(side))) {
                        AtomicFiles.write(side, bytes);
                    }
                    sideFiles.add(rel + FileDecision.SIDE_SUFFIX);
                }
                case KEEP_EDITED -> keptEdited++;
                case RESPECT_DELETED -> deleted++;
            }
            next.put(rel, d.entryAfter(shippedHash, recorded));
        }

        int removed = 0;
        List<String> orphanedEdited = new ArrayList<>();
        for (Map.Entry<String, Entry> e : old.files().entrySet()) {
            String rel = e.getKey();
            if (shipped.containsKey(rel)) {
                continue;
            }
            next.remove(rel);
            if (e.getValue().deleted()) {
                continue;
            }
            Path dest = resolve(target, rel);
            String current = hashOrNull(dest);
            if (current == null) {
                continue;
            }
            if (current.equals(e.getValue().sha256())) {
                Files.delete(dest);
                removed++;
            } else {
                orphanedEdited.add(rel);
            }
        }

        InstallManifest result = next.build();
        if (!result.equals(old) || Files.notExists(manifestFile)) {
            AtomicFiles.write(manifestFile, result.toJson().getBytes(StandardCharsets.UTF_8));
        }
        return new Report(target, written, unchanged, sideFiles, keptEdited, deleted, removed, orphanedEdited);
    }

    /** The manifest at {@code file}; an unreadable one is treated as absent (every differing file then counts as edited). */
    static InstallManifest readManifest(Path file) throws IOException {
        try {
            return InstallManifest.fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return InstallManifest.EMPTY;
        } catch (IllegalArgumentException e) {
            return InstallManifest.EMPTY;
        }
    }

    /** Every regular file below {@code root}, keyed by its {@code /}-separated relative path, sorted. */
    static Map<String, Path> list(Path root) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (rel.equals(InstallManifest.FILE_NAME) || rel.endsWith(FileDecision.SIDE_SUFFIX)
                        || rel.endsWith(AtomicFiles.TEMP_SUFFIX)) {
                    continue;
                }
                out.put(rel, p);
            }
        }
        return out;
    }

    private static Path resolve(Path target, String rel) {
        Path p = target;
        for (String part : rel.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("bad content path: " + rel);
            }
            p = p.resolve(part);
        }
        return p;
    }

    private static String hashOrNull(Path p) throws IOException {
        if (!Files.isRegularFile(p)) {
            return null;
        }
        return InstallManifest.sha256(Files.readAllBytes(p));
    }
}
