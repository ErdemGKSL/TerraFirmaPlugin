/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.wildraid.FoxBerry;
import dev.tfp.core.wildraid.RabbitRaid;
import dev.tfp.core.wildraid.RaidSearch;
import dev.tfp.paper.prey.Ticks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Rabbit;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-42: the raid searches over time slices find what one full scan in TFC's order finds, and read no more. */
class RaidScanTest extends MockServerTest {
    private World world;
    private final Set<String> farmland = new HashSet<>();
    private final Set<String> crops = new HashSet<>();
    private final Set<String> fruiting = new HashSet<>();
    private final AtomicInteger reads = new AtomicInteger();
    /** A clock that advances one unit per read: a slice of 2 is two clock reads, at most 2 x CHECK_EVERY cells. */
    private final AtomicLong clock = new AtomicLong();

    private final RaidSites sites = new RaidSites() {
        @Override
        public boolean farmland(Block block) {
            reads.incrementAndGet();
            return farmland.contains(key(block));
        }

        @Override
        public boolean rabbitRaidable(Block block) {
            return crops.contains(key(block));
        }

        @Override
        public boolean foxFruiting(Block block) {
            reads.incrementAndGet();
            return fruiting.contains(key(block));
        }

        @Override
        public boolean mayGrief(Mob mob) {
            return true;
        }

        @Override
        public Optional<ItemStack> pick(Block bush) {
            return Optional.empty();
        }

        @Override
        public void destroyCrop(Block crop) {
        }
    };

    private final RaidSites.Walker walker = new RaidSites.Walker() {
        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            return true;
        }

        @Override
        public boolean hasPath(Mob mob) {
            return false;
        }
    };

    private static String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    @BeforeEach
    void setUp() {
        AtomicLong tick = new AtomicLong();
        Ticks.forTest(tick::incrementAndGet);
        world = server.addSimpleWorld("w");
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
    }

    @AfterEach
    void restoreTicks() {
        Ticks.forTest(null);
    }

    private <T extends Mob> T spawn(Class<T> type, double x, double y, double z) {
        T mob = world.spawn(new Location(world, x, y, z), type);
        int t = (10 - mob.getEntityId() % 10) % 10;
        mob.setTicksLived(t == 0 ? 10 : t);
        return mob;
    }

    /** TFC's order, one cell at a time, no shortcuts: the reference. */
    private Block reference(Location at, int range, int vertical, Predicate<Block> valid) {
        for (RaidSearch.Offset o : RaidSearch.offsets(range, vertical)) {
            Block b = world.getBlockAt(at.getBlockX() + o.dx(), at.getBlockY() + o.dy(), at.getBlockZ() + o.dz());
            if (valid.test(b)) {
                return b;
            }
        }
        return null;
    }

    @Test
    void aSlicedSearchFindsTheFullScansBlockOnRandomGardens() {
        Random r = new Random(42);
        Location at = new Location(world, 0.5, 65, 0.5);
        Predicate<Block> valid = b -> farmland.contains(key(b)) && crops.contains(key(b.getRelative(0, 1, 0)));
        int foundSome = 0;
        for (int garden = 0; garden < 200; garden++) {
            farmland.clear();
            crops.clear();
            int n = r.nextInt(6);
            for (int i = 0; i < n; i++) {
                int x = r.nextInt(40) - 20;
                int y = 63 + r.nextInt(3);
                int z = r.nextInt(40) - 20;
                farmland.add(x + "," + y + "," + z);
                if (r.nextInt(3) > 0) {
                    crops.add(x + "," + (y + 1) + "," + z);
                }
            }
            Block want = reference(at, RabbitRaid.RANGE, RabbitRaid.VERTICAL, valid);
            assertThat(RaidFinder.nearest(spawn(Rabbit.class, 0.5, 65, 0.5), RabbitRaid.RANGE, RabbitRaid.VERTICAL, valid))
                .as("garden " + garden).isEqualTo(want);
            RaidScan scan = RaidScan.around(at, RabbitRaid.RANGE, RabbitRaid.VERTICAL, valid);
            int calls = 0;
            int before = 0;
            while (!scan.advance(clock::incrementAndGet, 2)) {
                calls++;
                assertThat(scan.reads() - before).as("cells per slice").isLessThanOrEqualTo(2 * RaidScan.CHECK_EVERY);
                before = scan.reads();
            }
            assertThat(scan.found()).as("garden " + garden + " after " + calls + " slices").isEqualTo(want);
            foundSome += want != null ? 1 : 0;
        }
        assertThat(foundSome).as("the gardens cover both outcomes").isBetween(20, 180);
    }

    @Test
    void aRabbitThatIsNotHungryReadsNoBlock() {
        Rabbit rabbit = spawn(Rabbit.class, 0.5, 65, 0.5);
        RaidPdc.rabbitRaid(rabbit, world.getGameTime()); // just raided: TFC's wantsToRaid is false
        RabbitRaidGoal goal = new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current());
        assertThat(goal.shouldActivate()).isFalse();
        assertThat(reads.get()).as("no farmland lookup").isZero();
    }

    @Test
    void aHungryRabbitFarFromFarmlandSearchesInSlicesAndThenFindsTheFarCrop() {
        Rabbit rabbit = spawn(Rabbit.class, 0.5, 65, 0.5);
        farmland.add("15,64,-15"); // the last ring of the first level
        crops.add("15,65,-15");
        RabbitRaidGoal goal = new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current(), clock::incrementAndGet, 2);
        int asks = 0;
        while (true) {
            int before = reads.get();
            boolean active = goal.shouldActivate();
            asks++;
            assertThat(reads.get() - before).as("cells per ask").isLessThanOrEqualTo(2 * RaidScan.CHECK_EVERY);
            if (active) {
                break;
            }
            assertThat(goal.searching()).as("ask " + asks).isTrue();
            rabbit.setTicksLived(rabbit.getTicksLived() + 10); // the next ask of the staggered period
        }
        assertThat(asks).as("the 31 x 31 inner rings of the first level in slices of 32 cells").isGreaterThanOrEqualTo(30);
        assertThat(goal.searching()).isFalse();
        goal.start();
        goal.tick();
    }

    @Test
    void aFoxSearchInSlicesFindsTheSameBushAsTheWholeSearch() {
        Fox fox = spawn(Fox.class, 0.5, 64, 0.5);
        fruiting.add("5,64,3");
        fruiting.add("-9,63,2");
        Block want = reference(fox.getLocation(), FoxBerry.RANGE, FoxBerry.VERTICAL, b -> fruiting.contains(key(b)));
        assertThat(key(want)).as("the lower level comes first in vanilla's order").isEqualTo("-9,63,2");
        FoxBerryGoal goal = new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current(), clock::incrementAndGet, 2);
        int asks = 0;
        while (!goal.shouldActivate()) {
            asks++;
            assertThat(goal.searching()).isTrue();
            fox.setTicksLived(fox.getTicksLived() + 10);
        }
        assertThat(asks).isPositive();
        assertThat(new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current()).shouldActivate()).as("whole search").isTrue();
    }
}
