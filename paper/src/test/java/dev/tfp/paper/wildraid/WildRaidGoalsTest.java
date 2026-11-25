/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.wildraid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.wildraid.ArmadilloScutes;
import dev.tfp.core.wildraid.FoxBerry;
import dev.tfp.core.wildraid.RabbitRaid;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.prey.Ticks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-24 on MockBukkit: the fox, rabbit and armadillo goals over a fake world seam, the scute check and the drop cancel. */
class WildRaidGoalsTest extends MockServerTest {
    private World world;
    private final Set<String> fruiting = new HashSet<>();
    private final Set<String> farmland = new HashSet<>();
    private final Set<String> crops = new HashSet<>();
    private final List<String> destroyed = new ArrayList<>();
    private final List<String> picked = new ArrayList<>();
    private boolean griefing = true;

    private final RaidSites sites = new RaidSites() {
        @Override
        public boolean farmland(Block block) {
            return farmland.contains(key(block));
        }

        @Override
        public boolean rabbitRaidable(Block block) {
            return crops.contains(key(block));
        }

        @Override
        public boolean foxFruiting(Block block) {
            return fruiting.contains(key(block));
        }

        @Override
        public boolean mayGrief(Mob mob) {
            return griefing;
        }

        @Override
        public Optional<ItemStack> pick(Block bush) {
            picked.add(key(bush));
            fruiting.remove(key(bush));
            return Optional.of(new ItemStack(Material.SWEET_BERRIES));
        }

        @Override
        public void destroyCrop(Block crop) {
            destroyed.add(key(crop));
            crops.remove(key(crop));
        }
    };

    /** Records the walks; the animal stands wherever the test puts it. */
    private final List<Double> speeds = new ArrayList<>();
    private final List<Location> targets = new ArrayList<>();
    private final RaidSites.Walker walker = new RaidSites.Walker() {
        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            speeds.add(speed);
            targets.add(to);
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

    @AfterEach
    void restoreTicks() {
        Ticks.forTest(null);
    }

    @BeforeEach
    void setUp() {
        // every path start of a test gets a fresh tick of its own: the per-thread path budget (3 a tick) is not under test here
        AtomicLong tick = new AtomicLong();
        Ticks.forTest(tick::incrementAndGet);
        world = server.addSimpleWorld("w");
        for (int cx = -2; cx <= 2; cx++) { // the mock world loads no chunk by itself: the search skips unloaded ones
            for (int cz = -2; cz <= 2; cz++) {
                world.loadChunk(cx, cz);
            }
        }
    }

    private <T extends Mob> T spawn(Class<T> type, double x, double y, double z) {
        T mob = world.spawn(new Location(world, x, y, z), type);
        // the goals ask on a staggered period of 10 ticks: live the animal to a tick it is asked on
        int t = (10 - mob.getEntityId() % 10) % 10;
        mob.setTicksLived(t == 0 ? 10 : t);
        return mob;
    }

    // ---- fox ----------------------------------------------------------------------------------------

    @Test
    void aFoxWithAnEmptyMouthWalksToAFruitingBushWaitsAndTakesOneProduct() {
        Fox fox = spawn(Fox.class, 0.5, 64, 0.5);
        fruiting.add("5,64,3");
        FoxBerryGoal goal = new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current());
        assertThat(goal.shouldActivate()).isTrue();
        goal.start();
        goal.tick();
        assertThat(speeds).containsExactly(FoxBerry.SPEED);
        assertThat(targets.getFirst().getBlockX()).as("the bush's block").isEqualTo(5);
        assertThat(targets.getFirst().getBlockZ()).isEqualTo(3);
        assertThat(fox.getEquipment().getItemInMainHand().getType()).as("not there yet").isEqualTo(Material.AIR);
        fox.teleport(new Location(world, 5.5, 64, 3.5)); // within 2 blocks
        for (int i = 0; i < FoxBerry.WAIT_TICKS; i++) {
            goal.tick();
        }
        assertThat(picked).as("40 ticks of waiting first").isEmpty();
        goal.tick();
        assertThat(picked).containsExactly("5,64,3");
        assertThat(fox.getEquipment().getItemInMainHand().getType()).isEqualTo(Material.SWEET_BERRIES);
        assertThat(fox.getEquipment().getItemInMainHand().getAmount()).isEqualTo(1);
    }

    @Test
    void aFoxWithAFullMouthOrNoBushDoesNotStartAndTheSearchWaitsTwoHundredTicks() {
        Fox fox = spawn(Fox.class, 0.5, 64, 0.5);
        fox.getEquipment().setItemInMainHand(new ItemStack(Material.BONE));
        fruiting.add("4,64,0");
        FoxBerryGoal full = new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current());
        assertThat(full.shouldActivate()).as("a full mouth").isFalse();
        fox.getEquipment().setItemInMainHand(null);
        assertThat(full.shouldActivate()).as("the search ran and is now 200+ ticks away").isFalse();

        FoxBerryGoal none = new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current());
        fruiting.clear();
        assertThat(none.shouldActivate()).as("no bush in range").isFalse();
    }

    @Test
    void aBushOutOfRangeOrWithMobGriefingOffGivesNothing() {
        Fox fox = spawn(Fox.class, 0.5, 64, 0.5);
        fruiting.add("13,64,0"); // 13 blocks away: the search covers 11 rings
        assertThat(new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current()).shouldActivate()).isFalse();
        fruiting.clear();
        fruiting.add("2,64,0");
        griefing = false;
        FoxBerryGoal goal = new FoxBerryGoal(fox, sites, walker, ThreadLocalRandom.current());
        assertThat(goal.shouldActivate()).isTrue();
        goal.start();
        fox.teleport(new Location(world, 2.5, 64, 0.5));
        for (int i = 0; i <= FoxBerry.WAIT_TICKS; i++) {
            goal.tick();
        }
        assertThat(picked).as("mob griefing off").isEmpty();
        assertThat(fox.getEquipment().getItemInMainHand().getType()).isEqualTo(Material.AIR);
    }

    // ---- rabbit -------------------------------------------------------------------------------------

    @Test
    void aHungryRabbitWalksToFarmlandWithACropAndDestroysItThenIsFull() {
        Rabbit rabbit = spawn(Rabbit.class, 0.5, 65, 0.5);
        farmland.add("4,64,2");
        crops.add("4,65,2");
        RabbitRaidGoal goal = new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current());
        assertThat(goal.shouldActivate()).as("never raided: hungry").isTrue();
        goal.start();
        goal.tick();
        assertThat(speeds).containsExactly(RabbitRaid.SPEED);
        assertThat(destroyed).isEmpty();
        rabbit.teleport(new Location(world, 4.5, 65, 2.5));
        goal.tick();
        assertThat(destroyed).containsExactly("4,65,2");
        assertThat(RaidPdc.rabbitRaid(rabbit)).as("the raid tick is stored").isEqualTo(world.getGameTime());
        assertThat(RabbitRaid.wantsMoreFood(RaidPdc.rabbitRaid(rabbit), world.getGameTime())).as("moreCarrotTicks = 40").isFalse();
        assertThat(RabbitRaid.wantsMoreFood(RaidPdc.rabbitRaid(rabbit), world.getGameTime() + 200)).isTrue();

        crops.add("4,65,2");
        RabbitRaidGoal again = new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current());
        assertThat(again.shouldActivate()).as("not hungry again yet").isFalse();
    }

    @Test
    void aRabbitNeedsFarmlandUnderARaidableCropAndMobGriefing() {
        Rabbit rabbit = spawn(Rabbit.class, 0.5, 65, 0.5);
        crops.add("3,65,0"); // a crop on no farmland
        assertThat(new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current()).shouldActivate()).isFalse();
        farmland.add("3,64,0");
        griefing = false;
        assertThat(new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current()).shouldActivate()).as("griefing off").isFalse();
        griefing = true;
        assertThat(new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current()).shouldActivate()).isTrue();
        farmland.clear();
        farmland.add("30,64,0");
        crops.clear();
        crops.add("30,65,0");
        assertThat(new RabbitRaidGoal(rabbit, sites, walker, ThreadLocalRandom.current()).shouldActivate()).as("out of range").isFalse();
    }

    // ---- armadillo ----------------------------------------------------------------------------------

    private static AnimalRecord armadilloRecord() {
        return new AnimalRecord("tfc:armadillo", new AnimalState(false, 0f, 0, 0, AnimalState.NO_OLD_TICK, 0, AnimalState.NEVER, 0,
                false, AnimalState.NOT_PREGNANT), 0, "", false, AnimalRecord.NO_GROW_TICK);
    }

    private long calendar = 1_000_000;

    private ArmadilloScuteBehaviour behaviour() {
        return new ArmadilloScuteBehaviour(() -> new ClockSnapshot(1, 0, calendar, 8, true), ThreadLocalRandom.current());
    }

    private List<Item> items() {
        return new ArrayList<>(world.getEntitiesByClass(Item.class));
    }

    @Test
    void anArmadilloGetsTheSpawnDrawAndDropsOneScuteWhenTheCooldownRunsOut() {
        Armadillo armadillo = spawn(Armadillo.class, 0.5, 64, 0.5);
        ArmadilloScuteBehaviour behaviour = behaviour();
        behaviour.onAdd(armadillo, armadilloRecord());
        long produced = RaidPdc.armadilloProduced(armadillo.getPersistentDataContainer()).orElseThrow();
        assertThat(produced).isBetween(calendar - ArmadilloScutes.SPAWN_DRAW_BOUND + 1, calendar);
        behaviour.onAdd(armadillo, armadilloRecord());
        assertThat(RaidPdc.armadilloProduced(armadillo.getPersistentDataContainer())).as("drawn once").hasValue(produced);

        check(behaviour, armadillo, LivestockAge.ADULT);
        assertThat(items()).as("the cooldown has 16001..36000 ticks to run").isEmpty();

        calendar = produced + ArmadilloScutes.PRODUCE_TICKS - 1;
        check(behaviour, armadillo, LivestockAge.ADULT);
        assertThat(items()).isEmpty();
        calendar = produced + ArmadilloScutes.PRODUCE_TICKS;
        check(behaviour, armadillo, LivestockAge.CHILD);
        assertThat(items()).as("a baby gives none").isEmpty();
        check(behaviour, armadillo, LivestockAge.ADULT);
        assertThat(items()).hasSize(1);
        assertThat(items().getFirst().getItemStack().getType()).isEqualTo(Material.ARMADILLO_SCUTE);
        assertThat(RaidPdc.armadilloProduced(armadillo.getPersistentDataContainer())).hasValue(calendar);
        check(behaviour, armadillo, LivestockAge.ADULT);
        assertThat(items()).as("one scute per cooldown").hasSize(1);
        calendar += 10L * ArmadilloScutes.PRODUCE_TICKS; // long unloaded: still one scute, not ten
        check(behaviour, armadillo, LivestockAge.ADULT);
        assertThat(items()).hasSize(2);
    }

    private static void check(ArmadilloScuteBehaviour behaviour, Armadillo armadillo, LivestockAge age) {
        behaviour.onCheck(new SpeciesBehaviour.Check(armadillo, armadilloRecord(), age, 0));
    }

    @Test
    void theVanillaScuteDropOfATfcArmadilloIsCancelledAndNothingElseIs() {
        WildRaidModule module = new WildRaidModule();
        Armadillo tfc = spawn(Armadillo.class, 0.5, 64, 0.5);
        AnimalPdc.write(tfc.getPersistentDataContainer(), armadilloRecord());
        Armadillo plain = spawn(Armadillo.class, 5.5, 64, 0.5);
        Item scute = world.dropItem(new Location(world, 0.5, 64, 0.5), new ItemStack(Material.ARMADILLO_SCUTE));
        Item stick = world.dropItem(new Location(world, 0.5, 64, 0.5), new ItemStack(Material.STICK));
        EntityDropItemEvent vanilla = new EntityDropItemEvent(tfc, scute);
        module.onDrop(vanilla);
        assertThat(vanilla.isCancelled()).isTrue();
        EntityDropItemEvent other = new EntityDropItemEvent(tfc, stick);
        module.onDrop(other);
        assertThat(other.isCancelled()).isFalse();
        EntityDropItemEvent notOurs = new EntityDropItemEvent(plain, scute);
        module.onDrop(notOurs);
        assertThat(notOurs.isCancelled()).as("an armadillo that is no TFC animal").isFalse();
    }

    @Test
    void onlyAFruitingStationaryBushCountsAsBerries() {
        assertThat(BerryPicker.fruiting(BlockStateRef.of("tfp:berry_bush/snowberry").with("lifecycle", "fruiting").with("stage", "2")))
            .isTrue();
        assertThat(BerryPicker.fruiting(BlockStateRef.of("tfp:berry_bush/snowberry").with("lifecycle", "flowering"))).isFalse();
        assertThat(BerryPicker.fruiting(BlockStateRef.of("tfp:berry_bush/dead").with("stage", "1"))).isFalse();
        assertThat(BerryPicker.fruiting(BlockStateRef.of("tfp:crop/carrot").with("lifecycle", "fruiting"))).isFalse();
    }
}
