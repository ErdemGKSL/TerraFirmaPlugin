/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Read-only view of a TFC checkout at the pinned commit. Every file read through it is hashed, so the
 * manifest records exactly the inputs that shaped the output.
 */
public final class TfcCheckout {
    /** Must equal the commit pinned in docs/porting/ledger.md (a unit test checks this). */
    public static final String PINNED_COMMIT = "c3b4d9536b82e5a9746dd793c586720a74108057";

    public static final String GENERATED = "src/generated/resources/";
    public static final String MAIN = "src/main/resources/";
    public static final String ASSETS = MAIN + "assets/tfc/";

    private final Path root;
    private final String head;
    /** Relative path (forward slashes) → sha256 of every file read. */
    private final Map<String, String> inputs = new TreeMap<>();

    private TfcCheckout(Path root, String head) {
        this.root = root;
        this.head = head;
    }

    /** Opens a checkout and fails with a clear message unless HEAD is the pinned commit. */
    public static TfcCheckout open(Path root, String expectedCommit) {
        if (!Files.isDirectory(root)) {
            throw new DatagenException("TFC checkout not found: " + root);
        }
        String head = resolveHead(root);
        if (!head.equals(expectedCommit)) {
            throw new DatagenException("TFC checkout " + root + " is at " + head + ", but datagen is pinned to "
                + expectedCommit + " (docs/porting/ledger.md). Run `git -C " + root + " checkout " + expectedCommit
                + "` or update the pin together with a port review.");
        }
        return new TfcCheckout(root, head);
    }

    public String head() {
        return head;
    }

    public Path root() {
        return root;
    }

    public boolean exists(String rel) {
        return Files.isRegularFile(root.resolve(rel));
    }

    public boolean isDirectory(String rel) {
        return Files.isDirectory(root.resolve(rel));
    }

    /** Reads a file and records its hash as an input. */
    public byte[] read(String rel) {
        try {
            byte[] bytes = Files.readAllBytes(root.resolve(rel));
            synchronized (inputs) {
                inputs.put(rel, sha256(bytes));
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + rel + " in TFC checkout", e);
        }
    }

    public String readString(String rel) {
        return new String(read(rel), StandardCharsets.UTF_8);
    }

    /** All regular files below {@code dir} (relative to the checkout), sorted, as checkout-relative paths. */
    public List<String> list(String dir) {
        Path base = root.resolve(dir);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(base)) {
            List<String> out = new ArrayList<>();
            s.filter(Files::isRegularFile).forEach(p -> out.add(rel(p)));
            out.sort(null);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Direct child directory names of {@code dir}, sorted. */
    public List<String> childDirs(String dir) {
        Path base = root.resolve(dir);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(base)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, String> inputs() {
        synchronized (inputs) {
            return new TreeMap<>(inputs);
        }
    }

    private String rel(Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    /** Resolves HEAD without a git binary: detached sha, loose ref, or packed-refs; supports `.git` files (worktrees). */
    static String resolveHead(Path root) {
        try {
            Path git = root.resolve(".git");
            if (Files.isRegularFile(git)) {
                String line = Files.readString(git).trim();
                if (!line.startsWith("gitdir:")) {
                    throw new DatagenException("unrecognised .git file in " + root);
                }
                git = root.resolve(line.substring("gitdir:".length()).trim()).normalize();
            }
            if (!Files.isDirectory(git)) {
                throw new DatagenException(root + " is not a git checkout (no .git); datagen needs the pinned TFC commit "
                    + PINNED_COMMIT);
            }
            String head = Files.readString(git.resolve("HEAD")).trim();
            if (!head.startsWith("ref:")) {
                return head;
            }
            String ref = head.substring(4).trim();
            Path common = git;
            Path commonDirFile = git.resolve("commondir");
            if (Files.isRegularFile(commonDirFile)) {
                common = git.resolve(Files.readString(commonDirFile).trim()).normalize();
            }
            for (Path base : List.of(git, common)) {
                Path loose = base.resolve(ref);
                if (Files.isRegularFile(loose)) {
                    return Files.readString(loose).trim();
                }
            }
            Path packed = common.resolve("packed-refs");
            if (Files.isRegularFile(packed)) {
                Optional<String> sha = Files.readAllLines(packed).stream()
                    .filter(l -> !l.startsWith("#") && !l.startsWith("^") && l.endsWith(" " + ref))
                    .map(l -> l.substring(0, l.indexOf(' ')))
                    .findFirst();
                if (sha.isPresent()) {
                    return sha.get();
                }
            }
            throw new DatagenException("cannot resolve HEAD (" + ref + ") in " + root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
