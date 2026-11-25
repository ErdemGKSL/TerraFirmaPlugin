/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/GetHookedGoal.java @ c3b4d9536
 */
package dev.tfp.core.fishing;

import dev.tfp.core.aquatic.AquaticSpecies;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The animals that run TFC's {@code GetHookedGoal} (priority 1): every freshwater fish ({@code FreshwaterFish}), cod, tropical
 * fish, pufferfish, dolphins and orcas (both are {@code TFCDolphin}). Squid, jellyfish, manatees and the critters never go for a hook.
 */
public final class HookedSpecies {
    /** Goal priority in TFC's {@code registerGoals}. */
    public static final int PRIORITY = 1;
    /** Speed modifier of the goal's {@code MoveToBlockGoal}. */
    public static final double SPEED = 0.9;
    /** Search range of the goal's {@code MoveToBlockGoal}, in blocks. */
    public static final int SEARCH_RANGE = 16;
    /** Radius, in blocks, of the box around the animal in which it looks for a hook. */
    public static final int HOOK_RADIUS = 12;

    private static final Set<AquaticSpecies> SPECIES = EnumSet.of(AquaticSpecies.BLUEGILL, AquaticSpecies.CRAPPIE,
            AquaticSpecies.LAKE_TROUT, AquaticSpecies.LARGEMOUTH_BASS, AquaticSpecies.RAINBOW_TROUT, AquaticSpecies.SALMON,
            AquaticSpecies.SMALLMOUTH_BASS, AquaticSpecies.NORTHERN_PIKE, AquaticSpecies.BURBOT, AquaticSpecies.ARCTIC_CHAR,
            AquaticSpecies.MUKSUN, AquaticSpecies.TILAPIA, AquaticSpecies.SPOTTED_GUDGEON, AquaticSpecies.PEACOCK_BASS,
            AquaticSpecies.PACU, AquaticSpecies.RED_PIRANHA, AquaticSpecies.COD, AquaticSpecies.TROPICAL_FISH,
            AquaticSpecies.PUFFERFISH, AquaticSpecies.DOLPHIN, AquaticSpecies.ORCA);

    private static final Set<String> IDS = SPECIES.stream().map(AquaticSpecies::entityType).collect(Collectors.toUnmodifiableSet());

    private HookedSpecies() {
    }

    /** The entity types ({@code tfc:cod}, ...) that go for a hook. */
    public static Set<String> ids() {
        return IDS;
    }

    /** Whether {@code entityType} runs the goal. */
    public static boolean goesForHook(String entityType) {
        return IDS.contains(entityType);
    }
}
