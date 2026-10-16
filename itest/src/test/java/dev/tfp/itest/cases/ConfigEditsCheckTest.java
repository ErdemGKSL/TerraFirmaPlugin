/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.install.ConfigEdit;
import dev.tfp.core.install.ThirdPartyEdits;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The read-only check behind the config case (M1-INST-02 result). */
class ConfigEditsCheckTest {
    static final ConfigEdit DELAY = new ConfigEdit(ConfigEdit.Target.CRAFTENGINE_CONFIG, "misc.delay-configuration-load",
            new ConfigEdit.Rule.Equals("false"), ConfigEdit.Effect.RESTART, true, "load early");
    static final ConfigEdit SHULKER = new ConfigEdit(ConfigEdit.Target.CRAFTENGINE_SHULKER_PACK, "enable",
            new ConfigEdit.Rule.Equals("false"), ConfigEdit.Effect.PACK_REBUILD, false, "old shader");
    static final ConfigEdit BH = new ConfigEdit(ConfigEdit.Target.BETTERHUD_CONFIG, "merge-other-folders",
            new ConfigEdit.Rule.Equals("true"), ConfigEdit.Effect.RELOAD, false, "merge");

    @Test
    void satisfiedEditsPassAndOnlyInstalledPluginsAreChecked() {
        Map<String, String> files = Map.of("CraftEngine/config.yml", "misc:\n  # keep\n  delay-configuration-load: false\n",
                "CraftEngine/resources/remove_shulker_head/pack.yml", "enable: false\n");
        ConfigEditsCheck.Result r = ConfigEditsCheck.check(List.of(DELAY, SHULKER, BH), Set.of("CraftEngine"),
                p -> Optional.ofNullable(files.get(p)));
        assertThat(r.problems()).isEmpty();
        assertThat(r.checked()).isEqualTo(2);
    }

    @Test
    void wrongValuesAndMissingMandatoryFilesAreProblemsOptionalOnesNotes() {
        ConfigEditsCheck.Result wrong = ConfigEditsCheck.check(List.of(DELAY), Set.of("CraftEngine"),
                p -> Optional.of("misc:\n  delay-configuration-load: true\n"));
        assertThat(wrong.problems()).singleElement().asString()
                .contains("CraftEngine/config.yml#misc.delay-configuration-load is true");

        ConfigEditsCheck.Result missing = ConfigEditsCheck.check(List.of(DELAY, SHULKER), Set.of("CraftEngine"),
                p -> Optional.empty());
        assertThat(missing.problems()).singleElement().asString().contains("CraftEngine/config.yml is missing");
        assertThat(missing.notes()).singleElement().asString().contains("remove_shulker_head/pack.yml is missing");
    }

    @Test
    void theRealEditTableRunsOnAFreshlyEditedCraftEngineConfig() {
        // every edit unsatisfied on an empty file: one problem per CraftEngine-config edit, nothing thrown
        List<ConfigEdit> all = ThirdPartyEdits.all(1353);
        ConfigEditsCheck.Result r = ConfigEditsCheck.check(all, Set.of("CraftEngine"), p -> Optional.of("{}\n"));
        long ceEdits = all.stream().filter(e -> e.target().plugin().equals("CraftEngine")).count();
        assertThat(r.checked()).isEqualTo((int) ceEdits);
        assertThat(r.problems()).isNotEmpty();
    }
}
