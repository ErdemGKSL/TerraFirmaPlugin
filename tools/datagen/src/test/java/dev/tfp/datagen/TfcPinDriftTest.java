/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TfcPinDriftTest {
    private static final String OTHER = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path tmp;

    @Test
    void readsThePinFromTheCheckedInFile() {
        Path pin = Path.of(System.getProperty("tfp.repoRoot", "."), TfcPinDrift.PIN_FILE);
        Assumptions.assumeTrue(Files.isRegularFile(pin), "no pin file at " + pin);
        assertThat(TfcPinDrift.readPin(pin)).isEqualTo(TfcCheckout.PINNED_COMMIT);
    }

    @Test
    void readsOneHashAndIgnoresTrailingNewline() throws IOException {
        Path pin = Files.writeString(tmp.resolve("pin.txt"), OTHER + "\n", StandardCharsets.UTF_8);
        assertThat(TfcPinDrift.readPin(pin)).isEqualTo(OTHER);
    }

    @Test
    void refusesAMissingPin() {
        assertThatThrownBy(() -> TfcPinDrift.readPin(tmp.resolve("missing.txt")))
            .isInstanceOf(DatagenException.class)
            .hasMessageContaining("pin file not found");
    }

    @Test
    void refusesAPinThatIsNotOneCommitHash() throws IOException {
        Path pin = Files.writeString(tmp.resolve("pin.txt"), "c3b4d95\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TfcPinDrift.readPin(pin))
            .isInstanceOf(DatagenException.class)
            .hasMessageContaining("40-character");
    }

    @Test
    void refusesACheckoutWithoutGit() throws IOException {
        Path checkout = Files.createDirectory(tmp.resolve("tfc"));
        Path pin = Files.writeString(tmp.resolve("pin.txt"), OTHER + "\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TfcPinDrift.changedSincePin(checkout, pin))
            .isInstanceOf(DatagenException.class)
            .hasMessageContaining("is not a git checkout");
    }

    @Test
    void refusesACheckoutThatDoesNotHaveThePinnedCommit() throws Exception {
        Path repo = newRepo("tfc-no-pin");
        Path pin = Files.writeString(tmp.resolve("pin.txt"), OTHER + "\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> TfcPinDrift.changedSincePin(repo, pin))
            .isInstanceOf(DatagenException.class)
            .hasMessageContaining("is not in the history");
    }

    @Test
    void listsWatchedPathsChangedSincePinAndIgnoresOthers() throws Exception {
        Path repo = newRepo("tfc-drift");
        Path java = repo.resolve("src/main/java/net/dries007/tfc/Port.java");
        Path data = repo.resolve("src/generated/resources/data/tfc/recipes/x.json");
        Path other = repo.resolve("README.md");
        Files.createDirectories(java.getParent());
        Files.createDirectories(data.getParent());
        Files.writeString(java, "a\n");
        Files.writeString(data, "{}\n");
        Files.writeString(other, "a\n");
        String pin = commit(repo, "pinned");

        Files.writeString(java, "b\n");
        Files.writeString(other, "b\n");
        Files.writeString(data, "{ }\n");
        commit(repo, "upstream");
        Files.writeString(tmp.resolve("pin.txt"), pin + "\n", StandardCharsets.UTF_8);

        List<String> changed = TfcPinDrift.changedSincePin(repo, tmp.resolve("pin.txt"));
        assertThat(changed).containsExactly(
            "src/generated/resources/data/tfc/recipes/x.json",
            "src/main/java/net/dries007/tfc/Port.java");
    }

    /** The real pinned TFC clone (-Ptfc.checkout). Its HEAD is the pin, so no watched path may differ. */
    @Test
    void pinnedScratchCloneHasNoDrift() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout), "no TFC checkout at " + checkout + "; test skipped");
        Path pin = Path.of(System.getProperty("tfp.repoRoot", "."), TfcPinDrift.PIN_FILE);
        assertThat(TfcPinDrift.changedSincePin(checkout, pin)).isEmpty();
    }

    /** A throwaway git repository with a user identity, so commits work on a bare CI runner. */
    private Path newRepo(String name) throws Exception {
        Path repo = Files.createDirectory(tmp.resolve(name));
        git(repo, "init", "-q");
        return repo;
    }

    private static String commit(Path repo, String message) throws Exception {
        git(repo, "add", "-A");
        git(repo, "-c", "user.name=tfp-test", "-c", "user.email=tfp-test@example.invalid", "commit", "-q", "-m", message);
        return git(repo, "rev-parse", "HEAD").strip();
    }

    private static String git(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", dir.toString()));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException("git " + List.of(args) + " failed: " + out);
        }
        return out;
    }
}
