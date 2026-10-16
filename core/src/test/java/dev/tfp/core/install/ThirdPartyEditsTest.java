/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.install.ConfigEdit.Outcome;
import dev.tfp.core.install.ConfigEdit.Plan;
import dev.tfp.core.install.ConfigEdit.Record;
import dev.tfp.core.install.ConfigEdit.Target;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * M1-INST-02: every edit of the {@code dependency-bootstrap} §6 table on the real config files (fixtures in
 * {@code src/test/resources/install/}, copied from a spike server; each names its origin). Each edit must
 * change exactly the lines it owns, keep every comment, and produce YAML with the expected value.
 */
class ThirdPartyEditsTest {
    static final int STATES = 1353;
    static final List<ConfigEdit> EDITS = ThirdPartyEdits.all(STATES);

    static String fixture(String name) throws IOException {
        try (InputStream in = ThirdPartyEditsTest.class.getResourceAsStream("/install/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static ConfigEdit edit(Target target, String key) {
        return EDITS.stream().filter(e -> e.target() == target && e.key().equals(key)).findFirst().orElseThrow();
    }

    /** Applies one edit to {@code text} (no record) and checks the diff touches only {@code expectedChanged} lines. */
    static Plan applyOne(ConfigEdit e, String text, int expectedChanged) {
        Plan p = e.plan(text, null);
        assertThat(p.outcome()).as(e.id()).isEqualTo(Outcome.APPLIED);
        assertThat(changedLines(text, p.text())).as(e.id() + " diff").isEqualTo(expectedChanged);
        assertThat(comments(p.text())).as(e.id() + " comments").containsExactlyElementsOf(comments(text));
        // Idempotent: a second run changes nothing.
        assertThat(e.plan(p.text(), p.record()).outcome()).isEqualTo(Outcome.UNCHANGED);
        return p;
    }

    @Test
    void delayConfigurationLoad() throws IOException {
        Plan p = applyOne(edit(Target.CRAFTENGINE_CONFIG, "misc.delay-configuration-load"), fixture("craftengine-config.yml"), 1);
        assertThat(get(p.text(), "misc", "delay-configuration-load")).isEqualTo(false);
        assertThat(p.before()).isEqualTo("true");
        assertThat(p.record()).isEqualTo(new Record("true", "false"));
    }

    @Test
    void cacheSystemIsForcedOn() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "chunk-system.cache-system");
        String ce = fixture("craftengine-config.yml");
        assertThat(e.plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED); // CE's default is already true
        Plan p = applyOne(e, ce.replace("cache-system: true", "cache-system: false"), 1);
        assertThat(get(p.text(), "chunk-system", "cache-system")).isEqualTo(true);
    }

    @Test
    void storageTypeNone() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "chunk-system.storage-type");
        String ce = fixture("craftengine-config.yml");
        assertThat(e.plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(e.plan(ce.replace("storage-type: linear", "storage-type: mca"), null).outcome()).isEqualTo(Outcome.UNCHANGED);
        Plan p = applyOne(e, ce.replace("storage-type: linear", "storage-type: none"), 1);
        assertThat(get(p.text(), "chunk-system", "storage-type")).isEqualTo("linear");
    }

    @Test
    void serversideBlocksSizing() throws IOException {
        assertThat(ThirdPartyEdits.serversideBlocks(0)).isEqualTo(2000);
        assertThat(ThirdPartyEdits.serversideBlocks(1353)).isEqualTo(2000);
        assertThat(ThirdPartyEdits.serversideBlocks(1601)).isEqualTo(3000);
        assertThat(ThirdPartyEdits.serversideBlocks(4000)).isEqualTo(5000);
        String ce = fixture("craftengine-config.yml");
        assertThat(edit(Target.CRAFTENGINE_CONFIG, "block.serverside-blocks").plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED);
        ConfigEdit big = ThirdPartyEdits.all(5000).stream().filter(e -> e.key().equals("block.serverside-blocks")).findFirst().orElseThrow();
        Plan p = applyOne(big, ce, 1);
        assertThat(get(p.text(), "block", "serverside-blocks")).isEqualTo(7000);
        // An owner who already raised it higher keeps their value.
        assertThat(big.plan(ce.replace("serverside-blocks: 2000", "serverside-blocks: 9000"), null).outcome()).isEqualTo(Outcome.UNCHANGED);
    }

    @Test
    void supportedVersionMin() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "resource-pack.supported-version.min");
        String ce = fixture("craftengine-config.yml");
        assertThat(e.plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED); // "server" >= 1.21.4 on supported servers
        assertThat(e.plan(ce.replace("min: server", "min: 1.21.8"), null).outcome()).isEqualTo(Outcome.UNCHANGED);
        assertThat(e.plan(ce.replace("min: server", "min: \"26.1\""), null).outcome()).isEqualTo(Outcome.UNCHANGED);
        Plan p = applyOne(e, ce.replace("min: server", "min: 1.20.1"), 1);
        assertThat(get(p.text(), "resource-pack", "supported-version", "min")).isEqualTo("1.21.4");
        assertThat(get(p.text(), "resource-pack", "supported-version", "max")).isEqualTo("latest");
    }

    @Test
    void excludeCoreShaders() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "resource-pack.exclude-core-shaders");
        String ce = fixture("craftengine-config.yml");
        assertThat(e.plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED);
        Plan p = applyOne(e, ce.replace("exclude-core-shaders: false", "exclude-core-shaders: true"), 1);
        assertThat(get(p.text(), "resource-pack", "exclude-core-shaders")).isEqualTo(false);
    }

    @Test
    void mergeExternalZipKeepsBetterModel() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "resource-pack.merge-external-zip-files");
        String ce = fixture("craftengine-config.yml");
        assertThat(e.plan(ce, null).outcome()).isEqualTo(Outcome.UNCHANGED);
        String without = ce.replace("    - \"BetterModel/build.zip\"\n", "");
        Plan p = applyOne(e, without, 1);
        assertThat(get(p.text(), "resource-pack", "merge-external-zip-files"))
                .isEqualTo(List.of("CustomNameplates/resourcepack.zip", "BetterModel/build.zip"));
    }

    @Test
    void shulkerHeadPackDisabled() throws IOException {
        Plan p = applyOne(edit(Target.CRAFTENGINE_SHULKER_PACK, "enable"), fixture("remove-shulker-head-pack.yml"), 1);
        assertThat(get(p.text(), "enable")).isEqualTo(false);
        assertThat(get(p.text(), "namespace")).isEqualTo("minecraft");
    }

    @Test
    void betterHudTestHudRemoved() throws IOException {
        Plan p = applyOne(edit(Target.BETTERHUD_CONFIG, "default-hud"), fixture("betterhud-config.yml"), 2);
        assertThat(get(p.text(), "default-hud")).isEqualTo(List.of());
        assertThat(p.text()).contains("default-hud: []\ndefault-popup: []");
    }

    @Test
    void betterHudMergeSettings() throws IOException {
        String bh = fixture("betterhud-config.yml");
        for (String key : List.of("merge-with-external-resources", "pack-type", "enable-self-host")) {
            assertThat(edit(Target.BETTERHUD_CONFIG, key).plan(bh, null).outcome()).as(key).isEqualTo(Outcome.UNCHANGED);
        }
        String broken = bh.replace("merge-with-external-resources: true", "merge-with-external-resources: false")
                .replace("pack-type: zip", "pack-type: none").replace("enable-self-host: false", "enable-self-host: true");
        String text = broken;
        for (String key : List.of("merge-with-external-resources", "pack-type", "enable-self-host")) {
            text = applyOne(edit(Target.BETTERHUD_CONFIG, key), text, 1).text();
        }
        assertThat(get(text, "merge-with-external-resources")).isEqualTo(true);
        assertThat(get(text, "pack-type")).isEqualTo("zip");
        assertThat(get(text, "enable-self-host")).isEqualTo(false);
    }

    @Test
    void betterModelPackType() throws IOException {
        ConfigEdit e = edit(Target.BETTERMODEL_CONFIG, "pack-type");
        String bm = fixture("bettermodel-config.yml");
        assertThat(e.plan(bm, null).outcome()).isEqualTo(Outcome.UNCHANGED);
        Plan p = applyOne(e, bm.replace("pack-type: zip", "pack-type: folder"), 1);
        assertThat(get(p.text(), "pack-type")).isEqualTo("zip");
    }

    @Test
    void allEditsOnFreshFilesTogether() throws IOException {
        // First start on a fresh server: only delay-configuration-load and the demo HUD need changing.
        Map<Target, String> files = Map.of(Target.CRAFTENGINE_CONFIG, fixture("craftengine-config.yml"),
                Target.CRAFTENGINE_SHULKER_PACK, fixture("remove-shulker-head-pack.yml"),
                Target.BETTERHUD_CONFIG, fixture("betterhud-config.yml"), Target.BETTERMODEL_CONFIG, fixture("bettermodel-config.yml"));
        List<String> applied = new ArrayList<>();
        for (ConfigEdit e : EDITS) {
            Plan p = e.plan(files.get(e.target()), null);
            if (p.outcome() == Outcome.APPLIED) {
                applied.add(e.id());
            } else {
                assertThat(p.outcome()).as(e.id()).isEqualTo(Outcome.UNCHANGED);
            }
        }
        assertThat(applied).containsExactly("CraftEngine/config.yml#misc.delay-configuration-load",
                "CraftEngine/resources/remove_shulker_head/pack.yml#enable", "BetterHud/config.yml#default-hud");
    }

    @Test
    void ownerWhoChoseAThirdValueWins() throws IOException {
        ConfigEdit e = edit(Target.CRAFTENGINE_CONFIG, "resource-pack.supported-version.min");
        String ce = fixture("craftengine-config.yml");
        Record rec = new Record("1.20.1", "1.21.4");
        // Owner later set 1.21 (below our minimum, but neither the original nor ours): left alone.
        Plan kept = e.plan(ce.replace("min: server", "min: 1.21"), rec);
        assertThat(kept.outcome()).isEqualTo(Outcome.OWNER_KEPT);
        assertThat(kept.text()).isEqualTo(ce.replace("min: server", "min: 1.21"));
        // Plugin regenerated its config with the original value: re-applied, the original stays recorded.
        Plan again = e.plan(ce.replace("min: server", "min: 1.20.1"), rec);
        assertThat(again.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(again.record()).isEqualTo(rec);
    }

    @Test
    void malformedFileFailsInsteadOfGuessing() {
        ConfigEdit e = edit(Target.BETTERHUD_CONFIG, "default-hud");
        Plan p = e.plan("default-hud:\n  key: value\n", null);
        assertThat(p.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(p.text()).isEqualTo("default-hud:\n  key: value\n");
    }

    static Object get(String text, String... path) {
        Object node = new Yaml().load(text);
        for (String p : path) {
            node = ((Map<?, ?>) node).get(p);
        }
        return node;
    }

    /** Size of the changed region: lines differing between the common prefix and the common suffix. */
    static int changedLines(String a, String b) {
        List<String> x = a.lines().toList();
        List<String> y = b.lines().toList();
        int i = 0;
        while (i < Math.min(x.size(), y.size()) && x.get(i).equals(y.get(i))) {
            i++;
        }
        int j = 0;
        while (j < Math.min(x.size(), y.size()) - i && x.get(x.size() - 1 - j).equals(y.get(y.size() - 1 - j))) {
            j++;
        }
        return Math.max(x.size() - i - j, y.size() - i - j);
    }

    static List<String> comments(String text) {
        List<String> out = new ArrayList<>();
        for (String l : text.lines().toList()) {
            int h = l.indexOf(" #");
            if (l.strip().startsWith("#")) {
                out.add(l.strip());
            } else if (h >= 0 && !l.contains("'") && !l.contains("\"")) {
                out.add(l.substring(h).strip());
            }
        }
        return out;
    }
}
