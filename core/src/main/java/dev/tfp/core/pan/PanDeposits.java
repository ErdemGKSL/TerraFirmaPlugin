/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Deposit.java @ c3b4d9536 (get: ingredient to loot table),
 *       src/main/java/net/dries007/tfc/common/items/PanItem.java @ c3b4d9536 (onUseTick)
 */
package dev.tfp.core.pan;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.RegistrySnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** PAR-PROC-12: the {@code deposit} data as a lookup, deposit item id to loot table id. Immutable, any thread. */
public final class PanDeposits {
    /** Deposit item ids start with this; the rest names the filled-pan model. */
    public static final String PREFIX = "tfp:deposit/";

    private final Map<String, String> tables;

    private PanDeposits(Map<String, String> tables) {
        this.tables = Map.copyOf(tables);
    }

    public static PanDeposits of(Map<String, String> tables) {
        return new PanDeposits(tables);
    }

    /** Every {@code deposit} definition of {@code snapshot} whose ingredient is a single item. */
    public static PanDeposits fromRegistry(RegistrySnapshot snapshot) {
        Map<String, String> out = new LinkedHashMap<>();
        for (SchemaRecord r : snapshot.get(DefinitionKinds.key("deposit")).asMap().values()) {
            r.get("ingredient", Ingredient.class).ifPresent(i -> {
                if (i instanceof Ingredient.Item item) {
                    out.putIfAbsent(item.item(), r.require("loot_table", String.class));
                }
            });
        }
        return new PanDeposits(out);
    }

    public boolean isDeposit(String itemId) {
        return itemId != null && tables.containsKey(itemId);
    }

    public Optional<String> lootTable(String itemId) {
        return Optional.ofNullable(itemId == null ? null : tables.get(itemId));
    }

    public int size() {
        return tables.size();
    }

    /** TFC {@code onUseTick(countLeft)}: the sound plays after {@code elapsed} ticks when the ticks left are a multiple of 16. */
    public static boolean soundDue(int elapsed) {
        int left = PanRules.USE_TICKS - elapsed;
        return left > 0 && left % PanRules.SOUND_INTERVAL == 0;
    }

    /** {@code tfp:deposit/native_copper/granite} gives {@code native_copper/granite}. */
    public static String modelKey(String depositItemId) {
        return depositItemId.startsWith(PREFIX) ? depositItemId.substring(PREFIX.length()) : depositItemId;
    }
}
