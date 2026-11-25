/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCRabbit.java @ c3b4d9536 (RaidGardenGoal)
 */
package dev.tfp.paper.wildraid;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.wildraid.FoxBerry;
import dev.tfp.core.wildraid.RabbitRaid;
import dev.tfp.paper.animal.AnimalGoal;
import dev.tfp.paper.prey.PathBudget;
import dev.tfp.paper.prey.Ticks;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

/**
 * {@code TFCRabbit.RaidGardenGoal} (speed 0.7, range 16, vertical 1): a hungry rabbit ({@link RabbitRaid#wantsMoreFood}, from
 * the stored raid tick) finds farmland with a {@code tfc:rabbit_raidable} crop above, walks to the crop's cell and destroys the
 * crop with its drops, which sets the raid tick (TFC's {@code moreCarrotTicks = 40}). One search 200 to 399 ticks after the
 * last, 10 ticks after a raid attempt ends; none while mob griefing is off.
 */
final class RabbitRaidGoal extends AnimalGoal {
    private final RaidSites sites;
    private final RaidSites.Walker walker;
    private final RandomGenerator random;
    private Block farmland;
    private long nextSearch = Long.MIN_VALUE;
    private int tryTicks;

    RabbitRaidGoal(Mob rabbit, RaidSites sites, RaidSites.Walker walker, RandomGenerator random) {
        super(rabbit, "rabbit_raid_garden", 10, GoalType.MOVE, GoalType.JUMP);
        this.sites = sites;
        this.walker = walker;
        this.random = random;
    }

    private boolean validTarget(Block block, boolean wantsToRaid) {
        return RabbitRaid.validTarget(sites.farmland(block), wantsToRaid, false,
                sites.farmland(block) && sites.rabbitRaidable(block.getRelative(0, 1, 0)));
    }

    @Override
    protected boolean wants() {
        long ticks = mob.getTicksLived();
        if (ticks < nextSearch) {
            return false;
        }
        nextSearch = ticks + FoxBerry.SEARCH_DELAY_BASE + random.nextInt(FoxBerry.SEARCH_DELAY_RANDOM);
        if (!sites.mayGrief(mob)) {
            return false;
        }
        boolean hungry = RabbitRaid.wantsMoreFood(RaidPdc.rabbitRaid(mob), mob.getWorld().getGameTime());
        farmland = RaidFinder.nearest(mob, RabbitRaid.RANGE, RabbitRaid.VERTICAL, b -> validTarget(b, hungry));
        return farmland != null;
    }

    @Override
    protected boolean keepsGoing() {
        return farmland != null && tryTicks <= 1200 && Bukkit.isOwnedByCurrentRegion(farmland.getLocation())
                && sites.rabbitRaidable(farmland.getRelative(0, 1, 0));
    }

    @Override
    protected void begin() {
        tryTicks = 0;
    }

    @Override
    protected void end() {
        farmland = null;
    }

    @Override
    protected void step() {
        if (farmland == null) {
            return;
        }
        Block crop = farmland.getRelative(0, 1, 0);
        Location center = new Location(crop.getWorld(), crop.getX() + 0.5, crop.getY() + 0.5, crop.getZ() + 0.5);
        if (mob.getLocation().distanceSquared(center) < 1.0) { // vanilla acceptedDistance 1.0 from the cell above the farmland
            if (sites.rabbitRaidable(crop)) {
                sites.destroyCrop(crop);
                RaidPdc.rabbitRaid(mob, mob.getWorld().getGameTime());
            }
            farmland = null;
            nextSearch = mob.getTicksLived() + RabbitRaid.RESTART_DELAY;
            return;
        }
        if (tryTicks++ % 20 == 0 && !walker.hasPath(mob) && PathBudget.tryStart(Ticks.now())) {
            walker.moveTo(mob, new Location(crop.getWorld(), crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5), RabbitRaid.SPEED);
        }
    }
}
