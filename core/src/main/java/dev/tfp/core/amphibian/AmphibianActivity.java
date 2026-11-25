/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianAi.java @ c3b4d9536 (makeBrain),
 * ai/amphibian/PinnipedAI.java (makeBrain), same commit
 */
package dev.tfp.core.amphibian;

/** The non-core activities of the two amphibian brains. {@code IDLE} is the default of both. */
public enum AmphibianActivity {
    /** Look around, follow, find water, stroll, swim, hunt small fish, go home. */
    IDLE,
    /** {@code AmphibianAi} only: lie still while {@code PLAY_DEAD_TICKS} is present. */
    PLAY_DEAD,
    /** Chase and bite the attack target (a small fish). */
    FIGHT,
    /** {@code PinnipedAI} only: flee the attacker, else stroll home, else idle movement. */
    AVOID
}
