/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Lists the TFC paths that changed upstream since the pinned commit in {@link #PIN_FILE}, so a developer can see
 * whether a port needs review before the pin is bumped. Read-only and offline: it compares two commits of a local
 * TFC clone that the developer has already fetched.
 *
 * <p>Entry point: {@code ./gradlew :tools:datagen:tfcPinDrift -Ptfc.checkout=<path to a TFC clone>}, or
 * {@code java dev.tfp.datagen.TfcPinDrift <tfc-checkout> [pin-file]}. It prints the changed paths under
 * {@link #WATCHED} (the Java sources and the generated data) and exits 0 in both the up-to-date and the drifted case.
 * It exits 1 when the checkout has no git history, or the pinned commit is not in it.
 */
public final class TfcPinDrift {
    /** The pin, relative to the repository root: one commit hash and nothing else. */
    public static final String PIN_FILE = "docs/porting/tfc-pin.txt";
    /** TFC paths the port reads: the Java sources and the generated data and assets. */
    public static final List<String> WATCHED = List.of(
        "src/main/java/net/dries007/tfc",
        "src/generated/resources/data/tfc");

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    private TfcPinDrift() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("usage: TfcPinDrift <tfc-checkout> [pin-file]");
            System.exit(2);
        }
        Path pinFile = Path.of(args.length == 2 ? args[1] : PIN_FILE);
        try {
            String pin = readPin(pinFile);
            List<String> changed = changedSincePin(Path.of(args[0]).toAbsolutePath().normalize(), pinFile);
            if (changed.isEmpty()) {
                System.out.println("TFC is at the pin " + pin + ": no watched paths changed.");
            } else {
                System.out.println(changed.size() + " watched TFC paths changed since the pin " + pin + ":");
                changed.forEach(p -> System.out.println("  " + p));
            }
        } catch (DatagenException e) {
            System.err.println("tfc pin drift: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Reads the pinned commit. Fails when the file is missing or does not hold exactly one 40-digit hash. */
    public static String readPin(Path pinFile) {
        if (!Files.isRegularFile(pinFile)) {
            throw new DatagenException("pin file not found: " + pinFile + " (it holds the TFC commit hash, see "
                + "docs/porting/ledger.md)");
        }
        String pin;
        try {
            pin = Files.readString(pinFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read pin file " + pinFile, e);
        }
        if (!COMMIT.matcher(pin).matches()) {
            throw new DatagenException("pin file " + pinFile + " must hold one lowercase 40-character commit hash, "
                + "found: '" + pin + "'");
        }
        return pin;
    }

    /**
     * Paths under {@link #WATCHED} that differ between the pinned commit and the checkout's HEAD, as checkout-relative
     * forward-slash paths, sorted by git.
     *
     * @throws DatagenException if the checkout is missing, has no {@code .git}, does not contain the pinned commit,
     *                          or git cannot run
     */
    public static List<String> changedSincePin(Path checkout, Path pinFile) {
        String pin = readPin(pinFile);
        if (!Files.isDirectory(checkout)) {
            throw new DatagenException("TFC checkout not found: " + checkout);
        }
        if (!Files.exists(checkout.resolve(".git"))) {
            throw new DatagenException(checkout + " is not a git checkout (no .git), so the pin " + pin
                + " cannot be compared. Clone TerraFirmaCraft there with its history.");
        }
        Result known = git(checkout, "cat-file", "-e", pin + "^{commit}");
        if (known.exit() != 0) {
            throw new DatagenException("commit " + pin + " is not in the history of " + checkout
                + ". Fetch upstream TerraFirmaCraft into that clone and try again.");
        }
        List<String> args = new ArrayList<>(List.of("diff", "--name-only", pin, "HEAD", "--"));
        args.addAll(WATCHED);
        Result diff = git(checkout, args.toArray(String[]::new));
        if (diff.exit() != 0) {
            throw new DatagenException("git diff failed in " + checkout + ": " + diff.stderr().strip());
        }
        return diff.stdout().lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private record Result(int exit, String stdout, String stderr) {
    }

    private static Result git(Path checkout, String... args) {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", checkout.toString()));
        cmd.addAll(List.of(args));
        Process p;
        try {
            p = new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new DatagenException("cannot run git, which the TFC pin check needs: " + e.getMessage());
        }
        try {
            p.getOutputStream().close();
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(p.getErrorStream()));
            String stdout = read(p.getInputStream());
            int exit = p.waitFor();
            return new Result(exit, stdout, stderr.join());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatagenException("interrupted while running git in " + checkout);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read git output in " + checkout, e);
        }
    }

    private static String read(InputStream in) {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
