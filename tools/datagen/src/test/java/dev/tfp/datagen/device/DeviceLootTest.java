/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-08: reading TFC's device loot tables and writing them as CraftEngine loot. */
class DeviceLootTest {
    private static final String POOL = """
        {"name": "loot_pool", "rolls": 1, "entries": [{"type": "minecraft:item", "name": "%s"%s}],
         "conditions": [{"condition": "minecraft:survives_explosion"}]}""";

    private static DeviceLoot parse(String self, String... pools) {
        String json = "{\"__comment__\": \"x\", \"type\": \"minecraft:block\", \"pools\": [" + String.join(",", pools) + "]}";
        return DeviceLoot.fromTfc(Json.rename(Json.parse(json, "test")), self, "test");
    }

    private static String pool(String item) {
        return POOL.formatted(item, "");
    }

    @Test
    void readsTfcDeviceTables() {
        assertThat(parse("tfp:firepit", pool("tfc:powder/wood_ash"))).isEqualTo(DeviceLoot.items("tfp:powder/wood_ash"));
        assertThat(parse("tfp:pot", pool("tfc:powder/wood_ash"), pool("tfc:ceramic/pot")))
            .isEqualTo(DeviceLoot.items("tfp:powder/wood_ash", "tfp:ceramic/pot"));
        assertThat(parse("tfp:bellows", pool("tfc:bellows"))).isEqualTo(DeviceLoot.self());
        assertThat(parse("tfp:charcoal_forge", POOL.formatted("minecraft:charcoal",
            ", \"functions\": [{\"function\": \"minecraft:set_count\", \"count\": 7}]")))
            .isEqualTo(DeviceLoot.item("minecraft:charcoal", 7));
    }

    @Test
    void rejectsOtherShapes() {
        assertThatThrownBy(() -> parse("tfp:crucible", POOL.formatted("tfc:crucible",
            ", \"functions\": [{\"function\": \"minecraft:copy_components\", \"source\": \"block_entity\"}]")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("set_count");
        assertThatThrownBy(() -> parse("tfp:x", POOL.replace("1,", "2,").formatted("tfc:x", "")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("roll exactly once");
        assertThatThrownBy(() -> parse("tfp:x", POOL.replace("survives_explosion", "random_chance").formatted("tfc:x", "")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("survives_explosion");
        assertThatThrownBy(() -> new DeviceLoot(List.of())).isInstanceOf(DatagenException.class);
    }

    @Test
    void writesTheLootTemplatesWhereOneFits() {
        assertThat(DeviceLoot.self().yaml("tfp:x")).isEqualTo(Map.of("template", "tfp:loot_table/self"));
        assertThat(DeviceLoot.item("tfp:powder/wood_ash").yaml("tfp:x")).isEqualTo(Map.of("template", "tfp:loot_table/item",
            "arguments", Map.of("loot_item", "tfp:powder/wood_ash")));
        assertThat(DeviceLoot.item("minecraft:charcoal", 7).yaml("tfp:x")).isEqualTo(Map.of(
            "template", "tfp:loot_table/item_count",
            "arguments", Map.of("loot_item", "minecraft:charcoal", "loot_count_min", 7, "loot_count_max", 7)));
        assertThat(DeviceLoot.items("tfp:a", "tfp:b").yaml("tfp:x")).containsOnlyKeys("pools");
    }
}
