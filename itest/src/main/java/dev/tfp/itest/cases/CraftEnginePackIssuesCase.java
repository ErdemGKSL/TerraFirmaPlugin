/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.LogFindings;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.install.Installer;
import dev.tfp.paper.integration.CustomBlocks;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * {@code ce.pack_issues} (M1-DGN-03): after CraftEngine loaded (the ready gate passed), the live server log
 * (the {@code Log4jTap}) has no {@code Found N issue(s) in file '...'} line for our packs ({@code tfp}, and the
 * self-test's {@code tfpitest}) and no warning naming one of our ids, and one block of each datagen v1 family
 * (rock, soil, ore) is loaded. The end-of-run log case repeats the issue check over the whole log.
 */
public final class CraftEnginePackIssuesCase implements SelfTestCase {
    /** One generated block per family of M1-DGN-03. */
    static final List<String> FAMILY_SAMPLES = List.of("tfp:rock/raw/granite", "tfp:dirt/alfisol",
            "tfp:ore/rich_hematite/granite");
    static final Duration REBUILD = CraftEngineBlockRoundTripCase.REBUILD;

    @Override
    public String name() {
        return "ce.pack_issues: CraftEngine loaded our pack with zero 'Found N issue(s)' lines (M1-DGN-03)";
    }

    @Override
    public Duration timeout() {
        return REBUILD.plusSeconds(10);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        Installer.Result install = tfp.install();
        if (install == null || install.craftEngine() == null) {
            throw new Skip("our CraftEngine pack was not installed this run (" + (install == null ? "no install step"
                    : String.join("; ", install.skipped())) + "): start the server again");
        }
        if (!ctx.liveLog() || ctx.logFindings() == null) {
            throw new Skip("no live Log4j tap; the end-of-run log scan checks the CraftEngine issues instead");
        }
        CustomBlocks blocks = tfp.integrations().customBlocks();
        // A first install triggers our pack rebuild, a full CraftEngine reload that empties its block registry
        // until it ends (start 1 of the M1 matrix ran this case inside that window).
        return OnThread.script("ce-pack-issues", () -> {
            OnThread.awaitPackRebuild(tfp, REBUILD);
            if (!tfp.integrations().blockVisuals().isLoaded()) {
                throw new AssertionError("CraftEngine has not loaded yet (no CraftEngineReloadEvent seen by our adapter)");
            }
            return check(ctx.logFindings(), blocks::exists);
        });
    }

    /** The case body (unit-tested). */
    static String check(LogFindings findings, Predicate<String> loaded) {
        int issues = findings.craftEngineIssues();
        if (issues > 0) {
            throw new AssertionError(issues + " CraftEngine issue(s) for " + findings.namespaces() + ":\n"
                    + String.join("\n", findings.craftEngineLines()));
        }
        List<String> missing = FAMILY_SAMPLES.stream().filter(loaded.negate()).toList();
        if (!missing.isEmpty()) {
            throw new AssertionError("CraftEngine did not load " + missing + " although our pack is installed");
        }
        return "0 issues for " + findings.namespaces() + "; loaded " + FAMILY_SAMPLES + " (log source: "
                + findings.source() + ")";
    }
}
