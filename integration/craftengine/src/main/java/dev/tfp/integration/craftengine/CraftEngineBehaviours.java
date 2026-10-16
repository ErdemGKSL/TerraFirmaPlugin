/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import java.util.LinkedHashMap;
import java.util.Map;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviorFactory;
import net.momirealms.craftengine.core.block.behavior.BlockBehaviors;
import net.momirealms.craftengine.core.util.Key;

/**
 * Our CraftEngine block behaviours ({@code type: tfp:<path>} in block YAML). Collected while the adapter
 * is built and registered in one go from our {@code onLoad()}, the only moment that works: CraftEngine's
 * registries exist by then and it parses configs in its own {@code onEnable()}
 * ({@code craftengine-java-api} skill, "Startup"). Registration after that is refused.
 */
public final class CraftEngineBehaviours {
    private final Map<String, BlockBehaviorFactory<?>> blocks = new LinkedHashMap<>();
    private boolean registered;

    /** Adds {@code tfp:<path>}. Must be called before our {@code onLoad()} finished. */
    public synchronized CraftEngineBehaviours block(String path, BlockBehaviorFactory<?> factory) {
        if (registered) {
            throw new IllegalStateException("CraftEngine behaviour tfp:" + path
                    + " added after onLoad; CraftEngine has already parsed its configuration");
        }
        if (blocks.putIfAbsent(path, factory) != null) {
            throw new IllegalArgumentException("duplicate CraftEngine behaviour tfp:" + path);
        }
        return this;
    }

    /** Registers everything with CraftEngine. Called once, from our {@code onLoad()}. @return the count */
    synchronized int registerAll() {
        if (registered) {
            throw new IllegalStateException("CraftEngine behaviours already registered");
        }
        registered = true;
        for (Map.Entry<String, BlockBehaviorFactory<?>> e : blocks.entrySet()) {
            BlockBehaviors.register(Key.of(CraftEngineIntegration.NAMESPACE, e.getKey()), e.getValue());
        }
        return blocks.size();
    }
}
