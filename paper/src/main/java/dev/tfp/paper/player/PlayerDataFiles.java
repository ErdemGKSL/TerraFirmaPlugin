/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.core.persist.PersistException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * One file per player, {@code <dir>/<uuid>.dat}, written crash-safely:
 * <ol>
 *   <li>the new bytes go to a temp file in the same directory, which is flushed to disk ({@code force});</li>
 *   <li>the current file, if it verifies, is copied to {@code <uuid>.dat.bak};</li>
 *   <li>the temp file atomically replaces {@code <uuid>.dat}.</li>
 * </ol>
 * A crash at any point leaves either the old or the new complete file. Each file carries a CRC32, so a
 * torn file (a file system without atomic rename) is detected; reading then falls back to the backup.
 * Leftover temp files from a crash are ignored and deleted by {@link #deleteStaleTempFiles()}.
 *
 * <pre>
 * file = "TFPP" length:i32 crc32:i32 payload   (payload = PlayerDataCodec bytes, a TFPD blob)
 * </pre>
 * Blocking I/O: call it from the I/O executor or the async pre-login thread, never a tick thread.
 */
public final class PlayerDataFiles {
    private static final byte[] MAGIC = {'T', 'F', 'P', 'P'};
    private static final int HEADER = 12;
    static final String SUFFIX = ".dat";
    static final String BACKUP_SUFFIX = ".dat.bak";
    static final String TEMP_SUFFIX = ".tmp";

    /** Writes the whole buffer to the channel. A seam for crash tests. */
    @FunctionalInterface
    interface ChannelWriter {
        void write(FileChannel channel, ByteBuffer bytes) throws IOException;
    }

    /** What {@link #read} found. */
    public record Loaded(PlayerData data, boolean fromBackup, String problem) {
    }

    private final Path dir;
    private final ChannelWriter writer;

    public PlayerDataFiles(Path dir) {
        this(dir, (ch, buf) -> {
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        });
    }

    PlayerDataFiles(Path dir, ChannelWriter writer) {
        this.dir = dir;
        this.writer = writer;
    }

    public Path dir() {
        return dir;
    }

    Path file(UUID id) {
        return dir.resolve(id + SUFFIX);
    }

    Path backup(UUID id) {
        return dir.resolve(id + BACKUP_SUFFIX);
    }

    /**
     * Reads the player's file, or its backup if the file is missing or damaged.
     *
     * @return empty for a player without any file (first join)
     * @throws PersistException if neither file is readable, or the data is from a newer plugin version
     *                          ({@link PersistException.NewerVersion}); the files are left untouched
     */
    public Optional<Loaded> read(UUID id) throws IOException {
        PersistException mainProblem = null;
        try {
            byte[] raw = readIfExists(file(id));
            if (raw != null) {
                return Optional.of(new Loaded(PlayerDataCodec.CODEC.fromBytes(unframe(raw)), false, null));
            }
        } catch (PersistException.NewerVersion e) {
            throw e; // a backup would be older still: never fall back to it and lose the newer data later
        } catch (PersistException e) {
            mainProblem = e;
        }
        byte[] bak = readIfExists(backup(id));
        if (bak == null) {
            if (mainProblem != null) {
                throw new PersistException(file(id).getFileName() + ": " + mainProblem.getMessage() + "; no backup", mainProblem);
            }
            return Optional.empty();
        }
        try {
            PlayerData data = PlayerDataCodec.CODEC.fromBytes(unframe(bak));
            String problem = mainProblem != null ? mainProblem.getMessage() : file(id).getFileName() + " missing";
            return Optional.of(new Loaded(data, true, problem));
        } catch (PersistException e) {
            throw new PersistException(file(id).getFileName() + " and its backup are unreadable: "
                    + (mainProblem != null ? mainProblem.getMessage() : "missing") + "; " + e.getMessage(), e);
        }
    }

    /** Writes {@code data} crash-safely (see the class comment). */
    public void write(UUID id, PlayerData data) throws IOException {
        byte[] framed = frame(PlayerDataCodec.CODEC.toBytes(data));
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, id + ".", TEMP_SUFFIX);
        try {
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(ch, ByteBuffer.wrap(framed));
                ch.force(true);
            }
            Path main = file(id);
            if (verifies(main)) {
                Files.copy(main, backup(id), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmp, main, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, main, StandardCopyOption.REPLACE_EXISTING);
            }
            syncDirectory();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Deletes temp files left by a crash mid-write. Call once at startup. @return how many */
    public int deleteStaleTempFiles() throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int n = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + TEMP_SUFFIX)) {
            for (Path p : files) {
                Files.deleteIfExists(p);
                n++;
            }
        }
        return n;
    }

    private boolean verifies(Path p) throws IOException {
        byte[] raw = readIfExists(p);
        if (raw == null) {
            return false;
        }
        try {
            unframe(raw);
            return true;
        } catch (PersistException e) {
            return false;
        }
    }

    private void syncDirectory() {
        // Makes the rename durable on Linux; not supported everywhere (Windows), so best effort.
        try (FileChannel d = FileChannel.open(dir, StandardOpenOption.READ)) {
            d.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            // ignore
        }
    }

    private static byte[] readIfExists(Path p) throws IOException {
        try {
            return Files.readAllBytes(p);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    static byte[] frame(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        ByteBuffer b = ByteBuffer.allocate(HEADER + payload.length);
        b.put(MAGIC).putInt(payload.length).putInt((int) crc.getValue()).put(payload);
        return b.array();
    }

    static byte[] unframe(byte[] raw) {
        if (raw.length < HEADER) {
            throw new PersistException("player file truncated (" + raw.length + " bytes)");
        }
        ByteBuffer b = ByteBuffer.wrap(raw);
        byte[] magic = new byte[MAGIC.length];
        b.get(magic);
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new PersistException("not a player data file (bad magic)");
        }
        int length = b.getInt();
        int crc = b.getInt();
        if (length != raw.length - HEADER) {
            throw new PersistException("player file truncated or padded: header says " + length + " bytes, found "
                    + (raw.length - HEADER));
        }
        byte[] payload = new byte[length];
        b.get(payload);
        CRC32 c = new CRC32();
        c.update(payload);
        if ((int) c.getValue() != crc) {
            throw new PersistException("player file checksum mismatch");
        }
        return payload;
    }
}
