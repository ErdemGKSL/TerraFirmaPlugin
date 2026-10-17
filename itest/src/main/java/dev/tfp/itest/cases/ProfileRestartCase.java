/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.visuals.ProfileCheck;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.install.PackRebuildTrigger;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code inst.profile_restart} (M1-INST-03): a restart on the same run directory reproduces the CraftEngine
 * solid-pool figures of the visual profile check, and the verdict agrees with the figures.
 *
 * <p>Source: the server's own record, {@code plugins/TerraFirmaPlugin/install/}{@value PackRebuildTrigger#POOL_CHECK_FILE}
 * (written after every pool check), checked against the in-memory last check. The matrix starts each target
 * twice on one run directory: the first start copies the record to {@value #MARKER} in the self-test data
 * folder and is skipped with the reason; the second start (marker present) compares and deletes the marker.
 * The case never switches profiles (that needs a config edit; manual run checks2-1.21.11).
 */
public final class ProfileRestartCase implements SelfTestCase {
    static final String MARKER = "profile-restart.marker";
    private static final Duration SETTLE = Duration.ofSeconds(3);

    /** One pool check's figures, as persisted by {@link PackRebuildTrigger#poolCheckRecord}. */
    record Figures(String selection, String verdict, int capacity, int needed, int others, int free, int stale,
            int reserve, String line) {
        static Figures parse(String text) {
            Properties p = new Properties();
            try {
                p.load(new StringReader(text));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new Figures(req(p, "selection"), req(p, "verdict"), num(p, "capacity"), num(p, "needed"),
                    num(p, "others"), num(p, "free"), num(p, "stale"), num(p, "reserve"), p.getProperty("line", ""));
        }

        private static String req(Properties p, String key) {
            String v = p.getProperty(key);
            if (v == null) {
                throw new IllegalArgumentException("pool check record has no '" + key + "'");
            }
            return v;
        }

        private static int num(Properties p, String key) {
            try {
                return Integer.parseInt(req(p, key).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("pool check record: '" + key + "' is not a number", e);
            }
        }

        /** Whether this record describes {@code r} (the in-memory last check). */
        boolean describes(ProfileCheck.Result r) {
            return verdict.equals(r.verdict().name()) && needed == r.needed() && others == r.othersUsed() && free == r.free();
        }

        String summary() {
            return selection + " " + verdict + ": " + free + " free, " + others + " used by other packs, " + needed
                    + " needed (pool " + capacity + ")";
        }
    }

    /** Differences between the previous start's figures and this start's; empty if the restart reproduced them. */
    static List<String> compare(Figures before, Figures now) {
        List<String> out = new ArrayList<>();
        diff(out, "selection", before.selection(), now.selection());
        diff(out, "verdict", before.verdict(), now.verdict());
        diff(out, "pool capacity", before.capacity(), now.capacity());
        diff(out, "needed", before.needed(), now.needed());
        diff(out, "used by other packs", before.others(), now.others());
        diff(out, "free", before.free(), now.free());
        return out;
    }

    private static void diff(List<String> out, String what, Object before, Object now) {
        if (!before.equals(now)) {
            out.add(what + " was " + before + ", now " + now);
        }
    }

    /**
     * The verdict {@link ProfileCheck} must give for these figures: FAIL iff free &lt; 0; otherwise OK iff
     * free &ge; reserve, else WARN (with stale claims of an earlier profile WARN is allowed too).
     *
     * @return the problem, empty if consistent
     */
    static Optional<String> inconsistency(Figures f) {
        String expected = f.free() < 0 ? "FAIL" : f.free() >= f.reserve() ? "OK" : "WARN";
        boolean ok = f.verdict().equals(expected)
                || (f.stale() > 0 && f.free() >= 0 && f.verdict().equals("WARN"));
        if (ok && !f.line().isEmpty()) {
            boolean lineOk = switch (f.verdict()) {
                case "OK" -> f.line().contains(" fits: " + f.free() + " ");
                case "WARN" -> f.stale() > 0 || f.line().startsWith("Only " + f.free() + " ");
                default -> f.line().contains("does not fit: " + (-f.free()) + " ");
            };
            if (!lineOk) {
                return Optional.of("verdict " + f.verdict() + " but the capacity line reads: " + f.line());
            }
        }
        return ok ? Optional.empty() : Optional.of("verdict " + f.verdict() + " with " + f.free() + " free and reserve "
                + f.reserve() + " (expected " + expected + ")");
    }

    @Override
    public String name() {
        return "inst.profile_restart: a restart reproduces the visual profile pool figures and verdict (M1-INST-03)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds() + 60);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        Path dataDir = ctx.dataDir();
        if (dataDir == null) {
            throw new Skip("no self-test data folder for the restart marker");
        }
        PackRebuildTrigger trigger = tfp.packRebuild();
        if (trigger == null) {
            throw new Skip("no visual pool check in this run (the visuals were not enabled)");
        }
        Path record = tfp.getDataPath().resolve("install").resolve(PackRebuildTrigger.POOL_CHECK_FILE);
        return OnThread.script("profile-restart", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            if (trigger.lastCheck() == null) {
                throw new Skip("no pool check ran this start (CraftEngine missing, or no visual index/allocation)");
            }
            String text = settledRecord(trigger, record);
            Figures now = Figures.parse(text);
            inconsistency(now).ifPresent(p -> {
                throw new AssertionError(p);
            });
            Path marker = dataDir.resolve(MARKER);
            if (!Files.exists(marker)) {
                Files.createDirectories(dataDir);
                Files.writeString(marker, text, StandardCharsets.UTF_8);
                throw new Skip("first start: recorded \"" + now.line() + "\"; the second start on this run directory "
                        + "compares against it");
            }
            Figures before;
            try {
                before = Figures.parse(Files.readString(marker, StandardCharsets.UTF_8));
            } finally {
                Files.deleteIfExists(marker); // the next first start records afresh
            }
            List<String> diffs = compare(before, now);
            if (!diffs.isEmpty()) {
                throw new AssertionError("the restart changed the pool figures: " + String.join("; ", diffs)
                        + " (before: " + before.line() + " | now: " + now.line() + ")");
            }
            return "restart reproduced " + now.summary() + ": \"" + now.line() + "\"";
        });
    }

    /**
     * The persisted record once it describes the in-memory last check and neither changed for {@link #SETTLE}
     * (the record is written on the io executor, and the rebuild's own CraftEngine reload checks once more).
     */
    private static String settledRecord(PackRebuildTrigger trigger, Path record) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<ProfileCheck.Result> check = new AtomicReference<>();
        long[] stableSince = {0};
        boolean ok = OnThread.poll(Duration.ofSeconds(30), () -> {
            String text = read(record);
            ProfileCheck.Result r = trigger.lastCheck();
            if (text == null || !Figures.parse(text).describes(r)) {
                stableSince[0] = 0;
                return false;
            }
            if (!text.equals(seen.get()) || r != check.get()) {
                seen.set(text);
                check.set(r);
                stableSince[0] = System.nanoTime();
                return false;
            }
            return System.nanoTime() - stableSince[0] >= SETTLE.toNanos();
        });
        if (!ok) {
            String text = read(record);
            throw new AssertionError(record + " does not match the last pool check within 30 s: "
                    + (text == null ? "missing" : Figures.parse(text).summary()) + " vs " + trigger.lastCheck().lines());
        }
        return seen.get();
    }

    private static String read(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }
}
