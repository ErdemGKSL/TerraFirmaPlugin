/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

/**
 * A spawn attempt that passed: the species and the group size. For a vanilla entry the placement is still decided by
 * Minecraft, so {@link SpawnEntry#isTfc()} says whether the TFC gate ran.
 *
 * @param entry     the chosen entry
 * @param groupSize how many of the species to place, between the entry's min and max count
 */
public record SpawnPick(SpawnEntry entry, int groupSize) {
}
