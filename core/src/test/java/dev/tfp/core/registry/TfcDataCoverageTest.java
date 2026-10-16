/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.tag.TagType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Loads TFC's complete generated data tree with our strict loader, proving every recipe type,
 * definition kind, ingredient type and output modifier in upstream TFC parses. Ids are accepted
 * as-is (no datagen id table for upstream data); tags must resolve, except vanilla/common ones.
 *
 * <p>Needs a TFC checkout at the pinned commit: set {@code TFC_ROOT} (e.g. {@code /opt/tfc});
 * skipped otherwise. The fixed sample in {@code SampleDataTest} runs everywhere.
 */
class TfcDataCoverageTest {
    @Test
    void allUpstreamTfcDataParses() {
        String tfcRoot = System.getenv("TFC_ROOT");
        assumeTrue(tfcRoot != null && !tfcRoot.isBlank(), "TFC_ROOT not set");
        Path data = Path.of(tfcRoot, "src/generated/resources/data");
        assumeTrue(Files.isDirectory(data), "no TFC generated data at " + data);

        RegistrySnapshot snapshot = DataLoader.standard(KnownIds.permissive(Set.of("minecraft", "c"))).load(List.of(data));

        // Counts from tfc-source-map.md (≈6200 recipes) and the data folders at c3b4d9536.
        assertThat(snapshot.get(DefinitionKinds.RECIPE).size()).isGreaterThan(6000);
        assertThat(snapshot.get(DefinitionKinds.key("item_heat")).size()).isEqualTo(343);
        assertThat(snapshot.get(DefinitionKinds.key("food")).size()).isEqualTo(195);
        assertThat(snapshot.get(DefinitionKinds.key("worldgen/rock_settings")).size()).isEqualTo(21);
        assertThat(snapshot.disabledRecipes()).hasSize(195);
        assertThat(snapshot.tags().size(TagType.ITEM)).isGreaterThan(400);
        SchemaRecord quern = snapshot.get(DefinitionKinds.RECIPE).getOrThrow("tfc:quern/bone_meal");
        assertThat(quern.schema()).isEqualTo("tfc:quern");
    }
}
