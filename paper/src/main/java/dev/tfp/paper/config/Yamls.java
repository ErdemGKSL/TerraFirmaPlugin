/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.config;

import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Plain SnakeYAML (shipped by Paper) instead of Bukkit's {@code YamlConfiguration}: Bukkit splits keys
 * on '.', which breaks keys like {@code "1.21"} in {@code visuals.profile}, and drops comments anyway.
 */
public final class Yamls {
    private Yamls() {
    }

    /**
     * @return the root mapping; empty for an empty document
     * @throws IllegalArgumentException if the text is not YAML or its root is not a mapping
     */
    public static Map<?, ?> load(String text) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not valid YAML: " + e.getMessage(), e);
        }
        if (root == null) {
            return Map.of();
        }
        if (root instanceof Map<?, ?> m) {
            return m;
        }
        throw new IllegalArgumentException("the top level must be a mapping (key: value), got " + root.getClass().getSimpleName());
    }

    /** Block-style YAML without comments (tests and diagnostics; never used to rewrite an owner's file). */
    public static String dump(Map<?, ?> tree) {
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(o), o).dump(tree);
    }
}
