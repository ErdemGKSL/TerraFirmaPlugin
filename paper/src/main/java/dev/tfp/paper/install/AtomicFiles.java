/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * Temp file + move writes, so a crash or an exception never leaves a half-written file where another
 * plugin will read it. The temp file sits in the target's folder (same file system) and ends in
 * {@value #TEMP_SUFFIX}, which neither CraftEngine nor BetterHud loads.
 */
public final class AtomicFiles {
    public static final String TEMP_SUFFIX = ".tfp-tmp";

    private AtomicFiles() {
    }

    /** Writes whatever {@code body} writes. If it throws, the target is untouched and the temp file removed. */
    public static void write(Path target, IOConsumer<OutputStream> body) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "." + target.getFileName() + ".", TEMP_SUFFIX);
        boolean moved = false;
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                    OutputStream out = Channels.newOutputStream(ch)) {
                body.accept(out);
                out.flush();
                ch.force(true);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tmp);
            }
        }
    }

    public static void write(Path target, byte[] bytes) throws IOException {
        write(target, out -> out.write(bytes));
    }

    /** Deletes temp files left by a crash below {@code root}. Returns how many. */
    public static int deleteStaleTemps(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int n = 0;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(TEMP_SUFFIX)) {
                    Files.deleteIfExists(p);
                    n++;
                }
            }
        }
        return n;
    }

    @FunctionalInterface
    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }
}
