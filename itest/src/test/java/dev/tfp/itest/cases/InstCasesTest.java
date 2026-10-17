/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.visuals.ProfileCheck;
import dev.tfp.core.visuals.VisualSelection;
import dev.tfp.itest.LogFindings;
import dev.tfp.paper.install.PackRebuildTrigger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The pure parts of inst.profile_restart (M1-INST-03) and inst.suppression (M1-INST-04). */
class InstCasesTest {
    private static final VisualSelection L3A = new VisualSelection("1.21", Profile.LEVEL_3A, false);
    private static final VisualSelection L0 = new VisualSelection("1.21", Profile.LEVEL_0, false);

    /** checks2-1.21.11: level-3a 915 free, 35 used by other packs, 339 needed; level-0 474 free, 780 needed. */
    private static String record(VisualSelection sel, int used, int ours, int stale, int needed, int reserve) {
        ProfileCheck.PoolUsage usage = new ProfileCheck.PoolUsage(1289, used, ours, stale);
        ProfileCheck.Result r = ProfileCheck.check(sel, Map.of(sel.profile(), needed), usage, reserve);
        return PackRebuildTrigger.poolCheckRecord(sel, usage, reserve, r);
    }

    // ---- inst.profile_restart ---------------------------------------------------------------------------

    @Test
    void recordRoundTripsTheCapacityLineAndFigures() {
        ProfileRestartCase.Figures f = ProfileRestartCase.Figures.parse(record(L3A, 374, 339, 0, 339, 500));
        assertThat(f.free()).isEqualTo(915);
        assertThat(f.others()).isEqualTo(35);
        assertThat(f.needed()).isEqualTo(339);
        assertThat(f.verdict()).isEqualTo("OK");
        assertThat(f.selection()).isEqualTo(L3A.id());
        assertThat(f.line()).contains("fits: 915 solid state(s) free").contains("35 used by other packs, 339 needed");
        assertThat(ProfileRestartCase.inconsistency(f)).isEmpty();
    }

    @Test
    void sameFiguresAfterRestartCompareEqual() {
        ProfileRestartCase.Figures a = ProfileRestartCase.Figures.parse(record(L3A, 374, 339, 0, 339, 500));
        ProfileRestartCase.Figures b = ProfileRestartCase.Figures.parse(record(L3A, 374, 339, 0, 339, 500));
        assertThat(ProfileRestartCase.compare(a, b)).isEmpty();
    }

    @Test
    void changedFiguresAreNamed() {
        ProfileRestartCase.Figures l3a = ProfileRestartCase.Figures.parse(record(L3A, 374, 339, 0, 339, 500));
        ProfileRestartCase.Figures l0 = ProfileRestartCase.Figures.parse(record(L0, 815, 780, 0, 780, 500));
        assertThat(l0.free()).isEqualTo(474);
        assertThat(l0.verdict()).isEqualTo("WARN");
        assertThat(l0.line()).startsWith("Only 474 ");
        assertThat(ProfileRestartCase.inconsistency(l0)).isEmpty();
        assertThat(ProfileRestartCase.compare(l3a, l0)).anyMatch(d -> d.startsWith("selection"))
                .anyMatch(d -> d.equals("free was 915, now 474")).anyMatch(d -> d.equals("needed was 339, now 780"))
                .anyMatch(d -> d.equals("verdict was OK, now WARN"));
        ProfileRestartCase.Figures others = ProfileRestartCase.Figures.parse(record(L3A, 384, 339, 0, 339, 500));
        assertThat(ProfileRestartCase.compare(l3a, others)).containsExactly("used by other packs was 35, now 45",
                "free was 915, now 905");
    }

    @Test
    void verdictMustAgreeWithTheFigures() {
        String ok = record(L3A, 374, 339, 0, 339, 500);
        assertThat(ProfileRestartCase.inconsistency(ProfileRestartCase.Figures.parse(ok.replace("verdict=OK", "verdict=WARN"))))
                .hasValueSatisfying(p -> assertThat(p).contains("expected OK"));
        ProfileRestartCase.Figures fail = ProfileRestartCase.Figures.parse(record(L0, 1289, 300, 0, 1300, 500));
        assertThat(fail.verdict()).isEqualTo("FAIL");
        assertThat(ProfileRestartCase.inconsistency(fail)).isEmpty();
        // a line that does not carry the free figure
        assertThat(ProfileRestartCase.inconsistency(ProfileRestartCase.Figures.parse(ok.replace("fits\\: 915", "fits\\: 916"))))
                .isPresent();
        // stale claims of the earlier profile after a switch: WARN is consistent
        ProfileRestartCase.Figures released = ProfileRestartCase.Figures.parse(record(L3A, 815, 339, 441, 339, 500));
        assertThat(released.stale()).isEqualTo(441);
        assertThat(ProfileRestartCase.inconsistency(released)).isEmpty();
    }

    @Test
    void incompleteRecordIsRejected() {
        assertThatThrownBy(() -> ProfileRestartCase.Figures.parse("verdict=OK\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("selection");
        assertThatThrownBy(() -> ProfileRestartCase.Figures.parse(record(L3A, 374, 339, 0, 339, 500)
                .replace("free=915", "free=x"))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- inst.suppression ---------------------------------------------------------------------------------

    @Test
    void formLineMatchesOnlyTheExactPosition() {
        String line = "suppress: BlockFormEvent at world 708,72,12: minecraft:lava -> minecraft:stone";
        assertThat(SuppressionCase.formLineFor(line, "world", 708, 72, 12)).isTrue();
        assertThat(SuppressionCase.formLineFor("[TerraFirmaPlugin] " + line, "world", 708, 72, 12)).isTrue();
        assertThat(SuppressionCase.formLineFor(line, "world", 708, 72, 1)).isFalse();
        assertThat(SuppressionCase.formLineFor(line, "world", 70, 72, 12)).isFalse();
        assertThat(SuppressionCase.formLineFor(line, "world_nether", 708, 72, 12)).isFalse();
        assertThat(SuppressionCase.formLineFor(line.replace("BlockFormEvent", "BlockSpreadEvent"), "world", 708, 72, 12))
                .isFalse();
    }

    @Test
    void startupLineSaysWhetherSuppressionLoaded() {
        assertThat(SuppressionCase.startupLine(List.of("Vanilla-block suppression: 441 repurposed block(s) of 1.21/level-3a")))
                .isPresent();
        assertThat(SuppressionCase.startupLine(List.of("No visual allocation: vanilla-block suppression is off"))).isEmpty();
        assertThat(SuppressionCase.startupLine(List.of())).isEmpty();
    }

    @Test
    void logFindingsKeepWatchedLinesWithoutTheLoggerPrefix() {
        LogFindings f = M1CasesTest.findings();
        f.keepLinesContaining(SuppressionCase.STARTUP_LINE, SuppressionCase.FORM_LINE);
        f.accept("INFO", "[TerraFirmaPlugin] Vanilla-block suppression: 441 repurposed block(s) of x", (Throwable) null);
        f.accept("INFO", "[TerraFirmaPlugin] suppress: BlockFormEvent at world 1,2,3: minecraft:lava -> minecraft:stone",
                (Throwable) null);
        f.accept("INFO", "unrelated", (Throwable) null);
        assertThat(f.linesContaining(SuppressionCase.STARTUP_LINE)).hasSize(1);
        assertThat(f.linesContaining(SuppressionCase.FORM_LINE)).singleElement()
                .satisfies(l -> assertThat(SuppressionCase.formLineFor(l, "world", 1, 2, 3)).isTrue());
        assertThat(f.linesContaining("not watched")).isEmpty();
        assertThat(f.threadViolations()).isZero();
        assertThat(f.craftEngineIssues()).isZero();
    }
}
