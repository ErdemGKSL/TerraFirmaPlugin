/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import java.util.List;

/**
 * Minecraft version families we ship one allocation for. A vanilla state is usable for a family only if it is an
 * unused opaque full cube in every researched version of the family (see {@link VanillaSupply}).
 */
public enum VersionFamily {
    /** 1.21.4 – 1.21.11 (researched: 1.21.4, 1.21.11). */
    V1_21("1.21", 1289, List.of("1.21.4", "1.21.11")),
    /** 26.x (researched: 26.1.2, 26.3; 26.2 assumed to lie between them). */
    V26("26", 1489, List.of("26.1.2", "26.3"));

    private final String id;
    private final int solidPool;
    private final List<String> researchedVersions;

    VersionFamily(String id, int solidPool, List<String> researchedVersions) {
        this.id = id;
        this.solidPool = solidPool;
        this.researchedVersions = researchedVersions;
    }

    /** Config/directory name: {@code 1.21} or {@code 26}. */
    public String id() {
        return id;
    }

    /** Candidates of CE's {@code auto_state: solid} pool with CE's default mappings.yml (verified in M0-VIS-01). */
    public int solidPool() {
        return solidPool;
    }

    public List<String> researchedVersions() {
        return researchedVersions;
    }

    public static VersionFamily byId(String id) {
        for (VersionFamily f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        throw new IllegalArgumentException("unknown MC version family " + id);
    }
}
