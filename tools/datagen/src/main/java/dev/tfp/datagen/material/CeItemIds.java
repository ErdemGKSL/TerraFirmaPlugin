/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.material;

import dev.tfp.datagen.OutputTree;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CraftEngine item ids an output tree already defines: the keys of every top-level {@code items:} section of the
 * {@code craftengine/tfp/configuration/**.yml} files (as {@link dev.tfp.datagen.Yaml} writes them: unquoted keys, two-space
 * indent), mapped to the file that defines them.
 */
final class CeItemIds {
    static final String CONFIG = "craftengine/tfp/configuration/";
    private static final Pattern ITEM_KEY = Pattern.compile("^ {2}([a-z0-9_]+:[a-z0-9_/.\\-]+):\\s*$");

    private CeItemIds() {
    }

    /** CE item id ({@code tfp:glue}) → config file (relative to the configuration folder), skipping {@code skipPrefix}. */
    static Map<String, String> of(OutputTree out, String skipPrefix) {
        Map<String, String> ids = new TreeMap<>();
        for (Map.Entry<String, byte[]> f : out.files().entrySet()) {
            String path = f.getKey();
            if (!path.startsWith(CONFIG) || !path.endsWith(".yml") || (skipPrefix != null && path.startsWith(skipPrefix))) {
                continue;
            }
            boolean inItems = false;
            for (String line : new String(f.getValue(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (!line.startsWith(" ")) {
                    inItems = line.equals("items:");
                    continue;
                }
                Matcher m = ITEM_KEY.matcher(line);
                if (inItems && m.matches()) {
                    ids.putIfAbsent(m.group(1), path.substring(CONFIG.length()));
                }
            }
        }
        return ids;
    }
}
