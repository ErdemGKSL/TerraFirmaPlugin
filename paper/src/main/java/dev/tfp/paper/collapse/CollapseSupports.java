/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/Support.java @ c3b4d9536 (the support definitions and their order)
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * The support blocks that keep a collapse from starting (TFC {@code Support}, {@code data/tfc/tfc/support/}). Built from
 * the loaded {@code support} definitions, sorted by id, and rebuilt when the registries reload. Until PAR-PHYS-04 adds the
 * beam blocks no world block carries those ids, so nothing is supported yet; {@link #override} lets that item (or a test)
 * replace the table. Any thread.
 *
 * <p>A definition whose ingredient is a {@code #tag} is skipped: TFC's data lists every beam, and a tag here would need its
 * members at decision time.
 */
public final class CollapseSupports implements Supplier<SupportTable> {
    private static final SupportTable EMPTY = new SupportTable(List.of());

    private record Entry(long version, SupportTable table) {}

    private final Supplier<Registries> registries;
    private volatile Entry entry = new Entry(-1, EMPTY);
    private volatile SupportTable override;

    public CollapseSupports(Supplier<Registries> registries) {
        this.registries = registries;
    }

    /** Replaces the loaded table ({@code null} goes back to the definitions). */
    public void override(SupportTable table) {
        this.override = table;
    }

    @Override
    public SupportTable get() {
        SupportTable o = override;
        if (o != null) {
            return o;
        }
        Registries r = registries.get();
        if (r == null || r.version() == 0) {
            return EMPTY;
        }
        Entry e = entry;
        if (e.version() == r.version()) {
            return e.table();
        }
        SupportTable table = fromRegistry(r.get());
        entry = new Entry(r.version(), table);
        return table;
    }

    static SupportTable fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> source = snapshot.get(DefinitionKinds.key("support"));
        Map<String, SupportTable.Support> sorted = new TreeMap<>();
        source.asMap().forEach((id, r) -> r.get("ingredient", BlockIngredient.class)
                .filter(i -> i.tag().isEmpty())
                .ifPresent(i -> sorted.put(id, new SupportTable.Support(Set.copyOf(i.blocks()),
                        r.get("support_up", Integer.class).orElse(0), r.get("support_down", Integer.class).orElse(0),
                        r.get("support_horizontal", Integer.class).orElse(0)))));
        return new SupportTable(new ArrayList<>(sorted.values()));
    }
}
