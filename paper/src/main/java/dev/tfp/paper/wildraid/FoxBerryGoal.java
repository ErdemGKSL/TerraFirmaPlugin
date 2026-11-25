/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/prey/TFCFox.java @ c3b4d9536 (TFCFoxEatBerriesGoal)
 */
package dev.tfp.paper.wildraid;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.wildraid.FoxBerry;
import dev.tfp.paper.animal.AnimalGoal;
import dev.tfp.paper.prey.PathBudget;
import dev.tfp.paper.prey.Ticks;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * {@code TFCFoxEatBerriesGoal} (speed 1.2, range 12, vertical 1): a fox with an empty mouth finds a fruiting bush of
 * {@code tfc:fox_raidable} (vanilla's search order, one search 200 to 399 ticks after the last), walks to it, waits 40 ticks
 * within 2 blocks and takes one product, setting the bush to its picked state through the berry module. The product goes
 * into the mouth (main hand).
 *
 * <p>PAR-FAUNA-42: the search ({@link RaidScan}, vanilla's order from where the fox stood) runs in time slices of
 * {@code sliceNanos}, one per ask; the goal answers false until it is done. The mouth and sleep check is TFC's, at the start.
 */
final class FoxBerryGoal extends AnimalGoal {
    private final RaidSites sites;
    private final RaidSites.Walker walker;
    private final RandomGenerator random;
    private Block target;
    private long nextSearch = Long.MIN_VALUE;
    private int waited;
    private int tryTicks;
    private final LongSupplier clock;
    private final long sliceNanos;
    private RaidScan scan;

    /** One search per ask, whole (tests). */
    FoxBerryGoal(Mob fox, RaidSites sites, RaidSites.Walker walker, RandomGenerator random) {
        this(fox, sites, walker, random, System::nanoTime, RaidScan.UNLIMITED);
    }

    FoxBerryGoal(Mob fox, RaidSites sites, RaidSites.Walker walker, RandomGenerator random, LongSupplier clock, long sliceNanos) {
        super(fox, "fox_eat_berries", 10, GoalType.MOVE, GoalType.LOOK);
        this.sites = sites;
        this.walker = walker;
        this.random = random;
        this.clock = clock;
        this.sliceNanos = sliceNanos;
    }

    /** Whether a search is under way (between slices). */
    boolean searching() {
        return scan != null;
    }

    private boolean mouthEmpty() {
        EntityEquipment eq = mob.getEquipment();
        return eq == null || eq.getItemInMainHand().getType().isAir();
    }

    private boolean sleeping() {
        return mob instanceof org.bukkit.entity.Fox f && f.isSleeping();
    }

    @Override
    protected boolean wants() {
        if (scan != null && scan.world() == mob.getWorld()) {
            return continueSearch();
        }
        scan = null;
        long ticks = mob.getTicksLived();
        if (ticks < nextSearch) {
            return false;
        }
        nextSearch = ticks + FoxBerry.searchDelay(random.nextInt(FoxBerry.SEARCH_DELAY_RANDOM));
        if (!FoxBerry.canStart(mouthEmpty(), sleeping())) {
            return false;
        }
        scan = RaidScan.around(mob.getLocation(), FoxBerry.RANGE, FoxBerry.VERTICAL, sites::foxFruiting);
        return continueSearch();
    }

    private boolean continueSearch() {
        if (!scan.advance(clock, sliceNanos)) {
            return false;
        }
        target = scan.found();
        scan = null;
        return target != null;
    }

    @Override
    protected boolean keepsGoing() {
        return target != null && tryTicks <= 1200 && mouthEmpty() && Bukkit.isOwnedByCurrentRegion(target.getLocation())
                && sites.foxFruiting(target);
    }

    @Override
    protected void begin() {
        waited = 0;
        tryTicks = 0;
    }

    @Override
    protected void end() {
        target = null;
    }

    private boolean reached() {
        Location center = new Location(target.getWorld(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        double d = FoxBerry.ACCEPTED_DISTANCE;
        return mob.getLocation().distanceSquared(center) < d * d;
    }

    @Override
    protected void step() {
        if (target == null) {
            return;
        }
        if (reached()) {
            if (waited >= FoxBerry.WAIT_TICKS) {
                take();
            } else {
                waited++;
            }
            return;
        }
        if (tryTicks++ % 20 == 0 && !walker.hasPath(mob) && PathBudget.tryStart(Ticks.now())) {
            walker.moveTo(mob, new Location(target.getWorld(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5), FoxBerry.SPEED);
        }
    }

    /** {@code onReachedTarget}. */
    private void take() {
        Block bush = target;
        target = null;
        if (!FoxBerry.mayPick(sites.mayGrief(mob), sites.foxFruiting(bush), mouthEmpty())) {
            return;
        }
        Optional<ItemStack> product = sites.pick(bush);
        if (product.isPresent()) {
            ItemStack one = product.get().clone();
            one.setAmount(1);
            mob.getEquipment().setItemInMainHand(one);
            mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 1f);
        }
    }
}
