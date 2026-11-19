/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/particle/TFCParticles.java @ c3b4d9536 (particle names)
 */
package dev.tfp.paper.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * PAR-DATA-09: TFC's particle types as vanilla equivalents, played only for explicit actions (locked policy, NP-PART-01).
 * Ambient TFC particles (butterflies, moths, wind, falling leaves, leaf, snowflakes, flying snowflakes, feathers, sleep
 * and the smoke_N family) are not listed: they have no vanilla client form and would be a constant packet stream.
 * Every entry names the action that may play it; see {@code docs/particles.md}.
 *
 * @param vanilla the vanilla particle sent to clients
 * @param count   particles per play
 * @param spread  random offset on each axis, blocks
 * @param speed   particle speed / extra
 */
public enum TfcParticle {
    /** {@code tfc:bubble}: a bubbling fluid in a device (pot, barrel brewing). */
    BUBBLE(Particle.BUBBLE_POP, 3, 0.2, 0.0),
    /** {@code tfc:steam}: water on hot metal or a hot device, quenching. */
    STEAM(Particle.CLOUD, 6, 0.15, 0.02),
    /** {@code tfc:spark}: hammering, forge welding, flint strikes. */
    SPARK(Particle.CRIT, 8, 0.2, 0.15),
    /** {@code tfc:nitrogen}: fertiliser applied (bonemeal-style). */
    NITROGEN(Particle.HAPPY_VILLAGER, 5, 0.3, 0.0),
    /** {@code tfc:phosphorus}. */
    PHOSPHORUS(Particle.HAPPY_VILLAGER, 5, 0.3, 0.0),
    /** {@code tfc:potassium}. */
    POTASSIUM(Particle.HAPPY_VILLAGER, 5, 0.3, 0.0),
    /** {@code tfc:compost_ready}: a compost heap that became ready, on interaction. */
    COMPOST_READY(Particle.HAPPY_VILLAGER, 6, 0.3, 0.0),
    /** {@code tfc:compost_rotten}: a rotten compost heap, on interaction. */
    COMPOST_ROTTEN(Particle.SMOKE, 6, 0.3, 0.01),
    /** {@code tfc:fluid_drip}/{@code fluid_fall}/{@code fluid_land}: pouring a fluid. */
    FLUID_POUR(Particle.FALLING_WATER, 8, 0.1, 0.0),
    /** {@code tfc:barrel_spill}/{@code barrel_drip}: a barrel emptied or spilling. */
    BARREL_SPILL(Particle.FALLING_WATER, 12, 0.25, 0.0),
    /** {@code tfc:water_flow}/{@code bubble_column_*}: water directed by a device. */
    WATER_FLOW(Particle.SPLASH, 6, 0.2, 0.0);

    private final Particle vanilla;
    private final int count;
    private final double spread;
    private final double speed;

    TfcParticle(Particle vanilla, int count, double spread, double speed) {
        this.vanilla = vanilla;
        this.count = count;
        this.spread = spread;
        this.speed = speed;
    }

    public Particle vanilla() {
        return vanilla;
    }

    public int count() {
        return count;
    }

    public double spread() {
        return spread;
    }

    public double speed() {
        return speed;
    }

    /** Region/entity thread of {@code at}: plays the particle for an explicit action. */
    public void play(Location at) {
        World world = at.getWorld();
        if (world != null) {
            world.spawnParticle(vanilla, at, count, spread, spread, spread, speed);
        }
    }
}
