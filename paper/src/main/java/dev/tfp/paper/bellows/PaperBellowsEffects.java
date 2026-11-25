/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (afterPush: the blow sound,
 *       the poof particle)
 */
package dev.tfp.paper.bellows;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

/** PAR-METAL-11: {@link BellowsEffects} on Paper; PAR-METAL-26: the pack's blow sound and the pressed morphs. */
public final class PaperBellowsEffects implements BellowsEffects {
    /** TFC's {@code tfc:block.bellows.blow}, copied by datagen into the pack as {@code tfp_bellows:block.bellows.blow}. */
    static final String SOUND = "tfp_bellows:block.bellows.blow";

    private final Scheduler scheduler;
    private final BellowsVisuals visuals;

    public PaperBellowsEffects(Scheduler scheduler) {
        this(scheduler, null);
    }

    /** With the pressed look: every blow also runs the morphs of {@link BellowsVisuals}. */
    public PaperBellowsEffects(Scheduler scheduler, BellowsVisuals visuals) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.visuals = visuals;
    }

    @Override
    public boolean owns(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    @Override
    public void onRegion(Block block, Runnable task) {
        if (owns(block)) {
            task.run();
        } else {
            scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task);
        }
    }

    @Override
    public void blow(Block bellows, String facing) {
        World world = bellows.getWorld();
        if (visuals != null) {
            visuals.press(bellows);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float pitch = 1f + (r.nextFloat() - r.nextFloat()) / 16f;
        world.playSound(bellows.getLocation().add(0.5, 0.5, 0.5), SOUND, SoundCategory.BLOCKS, 1f, pitch);
        int sx = dev.tfp.core.bellows.BellowsMotion.stepX(facing);
        int sz = dev.tfp.core.bellows.BellowsMotion.stepZ(facing);
        // TFC: facingPos centre minus 0.3 along the facing, y centre, speed 0.005 upward
        Location at = bellows.getLocation().add(0.5 + sx - 0.3 * sx, 0.5, 0.5 + sz - 0.3 * sz);
        world.spawnParticle(Particle.POOF, at, 0, 0, 0.005, 0, 1);
    }
}
