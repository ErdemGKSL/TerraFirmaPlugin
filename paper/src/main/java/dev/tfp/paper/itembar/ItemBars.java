/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import dev.tfp.core.itembar.ItemBar;
import dev.tfp.core.itembar.ItemBarRegistry;
import java.util.List;

/**
 * The shared item-bar layer (PAR-HEAT-14), published as a module service: the bars in TFC's registry order. The
 * {@code item_bar} module registers the heat bar; later modules add theirs (the egg bar, PAR-FAUNA-23) with
 * {@link #register} from their {@code enable}. The packet handler reads {@link #registry()} on the Netty thread.
 */
public final class ItemBars {
    private volatile ItemBarRegistry<BarStack> registry = ItemBarRegistry.of(List.of());

    /** Adds {@code bar} after the registered ones. Startup thread. */
    public synchronized void register(ItemBar<BarStack> bar) {
        registry = registry.with(bar);
    }

    /** The current registry (immutable). Any thread. */
    public ItemBarRegistry<BarStack> registry() {
        return registry;
    }
}
