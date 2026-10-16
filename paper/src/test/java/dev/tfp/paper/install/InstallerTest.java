/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.config.TfpConfig.AutoInstall;
import dev.tfp.core.install.ConfigEdit.Outcome;
import dev.tfp.core.config.TfpConfig.Profile;
import dev.tfp.core.install.InstallManifest;
import dev.tfp.core.visuals.VisualSelection;
import dev.tfp.paper.integration.FakePackRebuilder;
import dev.tfp.paper.integration.FakeVisualPools;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * M1-INST-01/02 orchestration on a temp server folder: opt-outs, the CraftEngine resources/ guard, the
 * config edits and the first start → restart → normal sequence of {@code InstallStatus}.
 */
class InstallerTest {
    static final Set<String> ALL = Set.of("CraftEngine", "BetterModel", "BetterHud");
    static final String CE_CONFIG = """
            # CraftEngine (shape of the real file, see core's install/craftengine-config.yml fixture)
            resource-pack:
              supported-version:
                min: server
              merge-external-zip-files:
                - "BetterModel/build.zip"
              exclude-core-shaders: false
            block:
              serverside-blocks: 2000
            chunk-system:
              storage-type: linear
              cache-system: true
            misc:
              # Defers CraftEngine configuration loading
              delay-configuration-load: true
            """;
    static final String BH_CONFIG = """
            pack-type: zip
            enable-self-host: false
            default-hud:
            - test_hud
            merge-with-external-resources: true
            """;
    static final Logger LOG = Logger.getLogger("InstallerTest");

    @TempDir
    Path tmp;
    Path plugins;
    Path content;

    @BeforeEach
    void setUp() throws IOException {
        plugins = tmp.resolve("plugins");
        content = tmp.resolve("content");
        write(content.resolve("craftengine/tfp/pack.yml"), "namespace: tfp\n");
        write(content.resolve("betterhud/tfp/huds/tfp.yml"), "tfp_hud: {}\n");
        write(plugins.resolve("CraftEngine/config.yml"), CE_CONFIG);
        write(plugins.resolve("CraftEngine/resources/remove_shulker_head/pack.yml"), "namespace: minecraft\nenable: true\n");
        write(plugins.resolve("BetterHud/config.yml"), BH_CONFIG);
        write(plugins.resolve("BetterModel/config.yml"), "# BM\npack-type: zip\n");
    }

    static void write(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    Installer.Result run(AutoInstall settings, Set<String> installed) {
        return Installer.run(new Installer.Inputs(settings, plugins, plugins.resolve("TerraFirmaPlugin/install"), content,
                "1.0", installed, 1353), LOG);
    }

    @Test
    void firstStartInstallsEditsAndAsksForARestartThenRunsNormally() throws IOException {
        Installer.Result first = run(AutoInstall.DEFAULTS, ALL);
        assertThat(first.craftEngine().written()).isEqualTo(1);
        assertThat(first.betterHud().written()).isEqualTo(1);
        assertThat(Files.readString(plugins.resolve("CraftEngine/resources/tfp/pack.yml"))).isEqualTo("namespace: tfp\n");
        assertThat(Files.readString(plugins.resolve("BetterHud/packs/tfp/huds/tfp.yml"))).isEqualTo("tfp_hud: {}\n");
        assertThat(Files.readString(plugins.resolve("CraftEngine/config.yml")))
                .isEqualTo(CE_CONFIG.replace("delay-configuration-load: true", "delay-configuration-load: false"));
        assertThat(Files.readString(plugins.resolve("BetterHud/config.yml"))).contains("default-hud: []\nmerge-with");
        assertThat(Files.readString(plugins.resolve("CraftEngine/resources/remove_shulker_head/pack.yml"))).contains("enable: false");
        assertThat(first.status().restartRequired()).isTrue();
        assertThat(first.status().degraded()).isTrue();
        assertThat(first.status().packRebuild()).isTrue();
        assertThat(Files.exists(plugins.resolve("TerraFirmaPlugin/install/" + ThirdPartyConfigEditor.RECORD_FILE))).isTrue();

        // The rebuild requested by the first start succeeded (PackRebuildTrigger records it).
        Installer.recordPackBuilt(plugins.resolve("TerraFirmaPlugin/install"), first.packFingerprint());
        Installer.Result second = run(AutoInstall.DEFAULTS, ALL);
        assertThat(second.status().restartRequired()).isFalse();
        assertThat(second.status().degraded()).isFalse();
        assertThat(second.status().packRebuild()).isFalse();
        assertThat(second.status().banner()).isEmpty();
        assertThat(second.edits()).allMatch(e -> e.outcome() == Outcome.UNCHANGED);
    }

    @Test
    void ownerRevertingToTheOriginalGetsItReappliedButAThirdValueWins() throws IOException {
        run(AutoInstall.DEFAULTS, ALL);
        Path ce = plugins.resolve("CraftEngine/config.yml");
        Files.writeString(ce, Files.readString(ce).replace("serverside-blocks: 2000", "serverside-blocks: 500"));
        Installer.Result r = run(AutoInstall.DEFAULTS, ALL);
        // 500 is neither the original (2000) nor ours: never recorded for this key, so this is a first edit.
        assertThat(Files.readString(ce)).contains("serverside-blocks: 2000");
        assertThat(r.status().restartRequired()).isTrue();

        Files.writeString(ce, Files.readString(ce).replace("serverside-blocks: 2000", "serverside-blocks: 700"));
        Installer.Result kept = run(AutoInstall.DEFAULTS, ALL);
        assertThat(Files.readString(ce)).contains("serverside-blocks: 700");
        assertThat(kept.edits()).anyMatch(e -> e.edit().key().equals("block.serverside-blocks") && e.outcome() == Outcome.OWNER_KEPT);
        assertThat(kept.status().degraded()).isTrue();
        assertThat(kept.status().restartRequired()).isFalse();
    }

    @Test
    void optOutsPerTarget() throws IOException {
        Installer.Result r = run(new AutoInstall(false, false, true, false, false), ALL);
        assertThat(r.craftEngine()).isNull();
        assertThat(r.betterHud()).isNull();
        assertThat(Files.exists(plugins.resolve("CraftEngine/resources/tfp"))).isFalse();
        assertThat(Files.exists(plugins.resolve("BetterHud/packs/tfp"))).isFalse();
        assertThat(r.skipped()).anyMatch(s -> s.contains("auto-install.craftengine is false"))
                .anyMatch(s -> s.contains("auto-install.betterhud is false"));
    }

    @Test
    void renamedBetterHudPackIsNotRecreated() throws IOException {
        Files.createDirectories(plugins.resolve("BetterHud/packs/-tfp"));
        Installer.Result r = run(AutoInstall.DEFAULTS, ALL);
        assertThat(r.betterHud()).isNull();
        assertThat(Files.exists(plugins.resolve("BetterHud/packs/tfp"))).isFalse();
        assertThat(r.craftEngine()).isNotNull();
    }

    @Test
    void neverCreatesCraftEngineResourcesBeforeCraftEngineDid() throws IOException {
        Path resources = plugins.resolve("CraftEngine/resources");
        try (var s = Files.walk(resources)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        Installer.Result r = run(AutoInstall.DEFAULTS, ALL);
        assertThat(Files.exists(resources)).isFalse();
        assertThat(r.craftEngine()).isNull();
        assertThat(r.status().degraded()).isTrue();
    }

    @Test
    void configEditsOffOnlyChecks() throws IOException {
        Installer.Result r = run(new AutoInstall(true, true, false, false, false), ALL);
        assertThat(Files.readString(plugins.resolve("CraftEngine/config.yml"))).isEqualTo(CE_CONFIG);
        assertThat(Files.readString(plugins.resolve("BetterHud/config.yml"))).isEqualTo(BH_CONFIG);
        assertThat(r.status().degraded()).isTrue(); // delay-configuration-load is still true
        assertThat(r.status().restartRequired()).isFalse();
        assertThat(r.edits()).anyMatch(e -> e.outcome() == Outcome.OWNER_KEPT && e.detail().contains("config-edits is off"));
    }

    @Test
    void missingPluginsAreSkipped() throws IOException {
        Installer.Result r = run(AutoInstall.DEFAULTS, Set.of());
        assertThat(r.craftEngine()).isNull();
        assertThat(r.betterHud()).isNull();
        assertThat(r.edits()).isEmpty();
        assertThat(r.status().degraded()).isFalse();
        assertThat(Files.readString(plugins.resolve("CraftEngine/config.yml"))).isEqualTo(CE_CONFIG);
    }

    @Test
    void unparseableResultIsNeverWritten() throws IOException {
        Path bh = plugins.resolve("BetterHud/config.yml");
        String broken = BH_CONFIG + "bad: [unclosed\n";
        Files.writeString(bh, broken);
        Installer.Result r = run(AutoInstall.DEFAULTS, ALL);
        assertThat(Files.readString(bh)).isEqualTo(broken);
        assertThat(r.edits()).anyMatch(e -> e.edit().key().equals("default-hud") && e.outcome() == Outcome.FAILED);
    }

    @Test
    void ceStatesFromTheDatagenReport() throws IOException {
        assertThat(Installer.ceStates(content)).isZero();
        write(content.resolve("reports/datagen-summary.json"), "{\"counts\": {\"ce_blocks\": 1353}}");
        assertThat(Installer.ceStates(content)).isEqualTo(1353);
        write(content.resolve("reports/datagen-summary.json"), "{\"counts\": {\"ce_blocks\": 1353, \"ce_states\": 4000}}");
        assertThat(Installer.ceStates(content)).isEqualTo(4000);
        assertThat(Installer.ceStates(null)).isZero();
    }

    @Test
    void manifestLivesInTheTarget() {
        run(AutoInstall.DEFAULTS, ALL);
        assertThat(Files.exists(Installer.craftEngineTarget(plugins).resolve(InstallManifest.FILE_NAME))).isTrue();
        assertThat(Files.exists(Installer.betterHudTarget(plugins).resolve(InstallManifest.FILE_NAME))).isTrue();
    }

    // ---- M1-INST-03: visual profile selection and the pack rebuild -----------------------------------

    static final VisualSelection L3A = new VisualSelection("1.21", Profile.LEVEL_3A, false);
    static final VisualSelection L2 = new VisualSelection("1.21", Profile.LEVEL_2, false);

    Path stateDir() {
        return plugins.resolve("TerraFirmaPlugin/install");
    }

    void shipAllocation(VisualSelection v) throws IOException {
        write(content.resolve(v.dir()).resolve("visual_templates.yml"), "templates: " + v.profile().id() + "\n");
        write(content.resolve(v.dir()).resolve("visual_mappings.yml"), "block_state_mappings: {} # " + v.profile().id() + "\n");
        write(content.resolve(v.dir()).resolve("suppressed_blocks.json"), "{\"suppress\": []}");
    }

    Installer.Result run(VisualSelection v) {
        return Installer.run(new Installer.Inputs(AutoInstall.DEFAULTS, plugins, stateDir(), content, "1.0", ALL, 1353, v), LOG);
    }

    /** One server run after CraftEngine loaded: two CraftEngine reloads (startup + the rebuild's own). */
    int startServer(Installer.Result r, FakePackRebuilder rebuilder) {
        int before = rebuilder.requests();
        PackRebuildTrigger t = new PackRebuildTrigger(r, stateDir(), rebuilder, new FakeVisualPools(), null, null, 500,
                Runnable::run, LOG);
        t.onCraftEngineReload();
        t.onCraftEngineReload();
        return rebuilder.requests() - before;
    }

    @Test
    void selectedAllocationIsInstalledIntoTheCraftEnginePack() throws IOException {
        shipAllocation(L3A);
        Installer.Result r = run(L3A);
        Path visuals = Installer.craftEngineTarget(plugins).resolve("configuration/visuals");
        assertThat(Files.readString(visuals.resolve("visual_templates.yml"))).isEqualTo("templates: level-3a\n");
        assertThat(visuals.resolve("visual_mappings.yml")).isRegularFile();
        assertThat(visuals.resolve("suppressed_blocks.json")).doesNotExist();
        assertThat(r.packFingerprint().selection()).isEqualTo(L3A);
    }

    @Test
    void profileChangedBetweenRestartsRequestsOneRebuildAndChangesNoWorld() throws IOException {
        shipAllocation(L3A);
        shipAllocation(L2);
        Path world = plugins.getParent().resolve("world/region/r.0.0.mca");
        write(world, "chunks");
        FakePackRebuilder ce = new FakePackRebuilder();

        Installer.Result first = run(L3A);
        assertThat(first.status().packRebuild()).isTrue();
        assertThat(startServer(first, ce)).isEqualTo(1);
        assertThat(Installer.readPackRecord(stateDir())).contains(first.packFingerprint());

        Installer.Result same = run(L3A);
        assertThat(same.packPlan().rebuild()).isFalse();
        assertThat(startServer(same, ce)).isZero();

        Installer.Result switched = run(L2);
        assertThat(switched.status().packRebuild()).isTrue();
        assertThat(switched.packPlan().migrateWorldData()).isFalse();
        assertThat(switched.packPlan().reason()).contains("1.21/level-3a/nether-end-off -> 1.21/level-2/nether-end-off");
        assertThat(Files.readString(Installer.craftEngineTarget(plugins).resolve("configuration/visuals/visual_templates.yml")))
                .isEqualTo("templates: level-2\n");
        assertThat(startServer(switched, ce)).isEqualTo(1);

        Installer.Result again = run(L2);
        assertThat(again.packPlan().rebuild()).isFalse();
        assertThat(startServer(again, ce)).isZero();
        assertThat(ce.requests()).isEqualTo(2);
        assertThat(Files.readString(world)).isEqualTo("chunks");
    }

    @Test
    void failedRebuildIsRetriedAtTheNextStart() throws IOException {
        shipAllocation(L3A);
        FakePackRebuilder ce = new FakePackRebuilder().succeeding(false);
        assertThat(startServer(run(L3A), ce)).isEqualTo(1);
        assertThat(Installer.readPackRecord(stateDir())).isEmpty();
        ce.failing(new IllegalStateException("busy"));
        assertThat(startServer(run(L3A), ce)).isEqualTo(1);
        ce.succeeding(true);
        assertThat(startServer(run(L3A), ce)).isEqualTo(1);
        assertThat(startServer(run(L3A), ce)).isZero();
    }

    @Test
    void missingAllocationIsReportedAndDegrades() throws IOException {
        Installer.Result r = run(L2);
        assertThat(r.craftEngine()).isNotNull();
        assertThat(r.status().degraded()).isTrue();
        assertThat(String.join("\n", r.status().banner())).contains("visual allocation 1.21/level-2/nether-end-off is not shipped");
    }

    @Test
    void capacityCheckRunsOnEveryReload() throws IOException {
        shipAllocation(L2);
        dev.tfp.core.visuals.VisualIndex index = dev.tfp.core.visuals.VisualIndex.parse("""
                {"format": 1, "families": {"1.21": {"solid_pool": 1289}}, "allocations": [
                  {"family": "1.21", "profile": "level-2", "nether_end": false, "dir": "d", "solid_used": 591, "solid_free": 698},
                  {"family": "1.21", "profile": "level-3a", "nether_end": false, "dir": "d", "solid_used": 339, "solid_free": 950}]}""");
        FakeVisualPools pools = new FakeVisualPools().solid(1289, 1289, 389);
        PackRebuildTrigger t = new PackRebuildTrigger(run(L2), stateDir(), new FakePackRebuilder(), pools, index, L2, 500,
                Runnable::run, LOG);
        t.onCraftEngineReload();
        assertThat(t.lastCheck().verdict()).isEqualTo(dev.tfp.core.visuals.ProfileCheck.Verdict.FAIL);
        assertThat(t.lastCheck().suggestion()).contains(Profile.LEVEL_3A);
        assertThat(t.rebuildRequested()).isTrue();
        pools.solid(1289, 691, 591);
        t.onCraftEngineReload();
        assertThat(t.lastCheck().verdict()).isEqualTo(dev.tfp.core.visuals.ProfileCheck.Verdict.OK);
    }
}
