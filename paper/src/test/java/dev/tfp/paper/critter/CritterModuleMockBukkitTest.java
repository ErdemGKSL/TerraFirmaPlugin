/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.critter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.critter.PestSpecies;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-13 on MockBukkit: the infestation rolls and their draw order, the chest right click and its food count, the feast
 * (one item taken, the 402-tick timer, the five worsening rolls, forgetting the smell), the lazy discard and the behaviours'
 * shape, driven through the module's seams with a scripted random.
 */
class CritterModuleMockBukkitTest extends MockServerTest {
    private World world;
    private CritterModule module;
    private Script random;
    private long now = 1000;
    private int nearby;
    private final List<String> spawned = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();
    private ChunkClimate climate = ChunkClimate.NEUTRAL;
    private Player player;

    /** Scripted draws; a draw past the script returns 1 and 0.99 (a failed gate), and every draw is logged. */
    private static final class Script implements SpawnRandom {
        final List<Integer> ints = new ArrayList<>();
        final List<Float> floats = new ArrayList<>();
        final List<String> log = new ArrayList<>();

        @Override
        public int nextInt(int bound) {
            log.add("i" + bound);
            return ints.isEmpty() ? Math.min(1, bound - 1) : ints.remove(0);
        }

        @Override
        public float nextFloat() {
            log.add("f");
            return floats.isEmpty() ? 0.99f : floats.remove(0);
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("entityDelayed")) {
                        delays.add((Long) args[1]);
                        return true;
                    }
                    return null;
                });
        ModuleContext ctx = new ModuleContext.Of((JavaPlugin) plugin, Logger.getLogger("tfp-test"), scheduler, null, null, null, null,
                dev.tfp.paper.i18n.Messages.fromJar(), null, null, null, null, null, new ModuleServices());
        random = new Script();
        module = new CritterModule();
        Predicate<ItemStack> food = s -> s.getType() == Material.BREAD;
        module.initForTest(ctx, () -> now, food, l -> climate, l -> nearby, random, (s, c, p) -> spawned.add(s.id()));
    }

    private Location chestAt(int x, ItemStack... items) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        for (int i = 0; i < items.length; i++) {
            chest.getBlockInventory().setItem(i, items[i]);
        }
        return block.getLocation();
    }

    @Test
    void levelZeroDrawsNothing() {
        module.infest(chestAt(0), 0, player);
        assertThat(random.log).isEmpty();
        assertThat(spawned).isEmpty();
    }

    @Test
    void aFailedGateStopsAfterOneDraw() {
        random.ints.add(3);
        module.infest(chestAt(0), 5, player);
        assertThat(random.log).containsExactly("i20");
        assertThat(spawned).isEmpty();
    }

    @Test
    void aPassingRollPicksTheRatInATemperateClimateInTfcsOrder() {
        random.ints.addAll(List.of(0, 0));
        random.floats.addAll(List.of(0.0f, 0.9f));
        module.infest(chestAt(0), 5, player);
        // the level gate, the pest-count chance, the 0.7 climate draw, the tag pick
        assertThat(random.log).containsExactly("i20", "f", "f", "i1");
        assertThat(spawned).containsExactly("rat");
    }

    @Test
    void eightNearbyPestsMakeTheChanceZero() {
        nearby = 8;
        random.ints.add(0);
        random.floats.add(0.5f);
        module.infest(chestAt(0), 5, player);
        assertThat(random.log).containsExactly("i20", "f");
        assertThat(spawned).isEmpty();
    }

    @Test
    void theArcticGetsNoPest() {
        climate = new ChunkClimate(-15f, 250f, 0f, 0f);
        random.ints.add(0);
        random.floats.add(0.0f);
        module.infest(chestAt(0), 5, player);
        assertThat(spawned).isEmpty();
    }

    @Test
    void aColdClimateTakesALemming() {
        climate = new ChunkClimate(-5f, 250f, 0f, 0f);
        random.ints.addAll(List.of(0, 0));
        random.floats.add(0.0f);
        module.infest(chestAt(0), 5, player);
        assertThat(spawned).containsExactly("lemming");
    }

    @Test
    void rightClickingAChestRollsWithItsFoodStacksUpToFive() {
        Location chest = chestAt(0, new ItemStack(Material.BREAD, 3), new ItemStack(Material.STONE), new ItemStack(Material.BREAD, 2),
                new ItemStack(Material.BREAD));
        module.onInteract(click(chest));
        // three food stacks: 1 in 120 - 20 * 3
        assertThat(random.log).containsExactly("i60");
    }

    @Test
    void anEmptyChestDrawsNothingAndSixFoodStacksStopAtFive() {
        module.onInteract(click(chestAt(0, new ItemStack(Material.STONE))));
        assertThat(random.log).isEmpty();
        ItemStack[] six = new ItemStack[6];
        java.util.Arrays.fill(six, new ItemStack(Material.BREAD));
        module.onInteract(click(chestAt(2, six)));
        assertThat(random.log).containsExactly("i20");
    }

    @Test
    void aSneakingClickWithAnItemDoesNotOpenTheChest() {
        Location chest = chestAt(0, new ItemStack(Material.BREAD));
        player.setSneaking(true);
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        module.onInteract(click(chest));
        assertThat(random.log).isEmpty();
    }

    private PlayerInteractEvent click(Location chest) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), chest.getBlock(),
                BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test
    void theFeastTakesOneFoodItemHoldsItFor402TicksAndWorsensTheInfestation() {
        Location chest = chestAt(0, new ItemStack(Material.STONE), new ItemStack(Material.BREAD, 3));
        Cow pest = world.spawn(new Location(world, 0.5, 65, 0.5), Cow.class);
        CritterModule.setSmelly(pest, chest);
        module.feast(pest, chest);
        Chest block = (Chest) chest.getBlock().getState();
        assertThat(block.getBlockInventory().getItem(1).getAmount()).isEqualTo(2);
        assertThat(pest.getEquipment().getItemInMainHand().getType()).isEqualTo(Material.BREAD);
        assertThat(pest.getEquipment().getItemInMainHand().getAmount()).isEqualTo(1);
        assertThat(pest.getPersistentDataContainer().get(CritterModule.EAT_END, PersistentDataType.LONG)).isEqualTo(now + 402);
        assertThat(delays).containsExactly(402L);
        assertThat(pest.hasAI()).isTrue();
        assertThat(pest.isAware()).isFalse();
        // levels 1..5, one gate each
        assertThat(random.log).containsExactly("i100", "i80", "i60", "i40", "i20");
        now += 402;
        module.finishEating(pest);
        assertThat(pest.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(pest.isAware()).isTrue();
        assertThat(pest.getPersistentDataContainer().has(CritterModule.EAT_END, PersistentDataType.LONG)).isFalse();
    }

    @Test
    void withNothingToTakeThePestForgetsTheSmell() {
        Location chest = chestAt(0, new ItemStack(Material.STONE));
        Cow pest = world.spawn(new Location(world, 0.5, 65, 0.5), Cow.class);
        CritterModule.setSmelly(pest, chest);
        module.feast(pest, chest);
        assertThat(CritterModule.smelly(pest)).isNull();
        assertThat(pest.getEquipment().getItemInMainHand().isEmpty()).isTrue();
        assertThat(random.log).isEmpty();
    }

    @Test
    void theDiscardRemovesADuePestAndKeepsANamedOrYoungOne() {
        Cow due = world.spawn(new Location(world, 0.5, 65, 0.5), Cow.class);
        due.getPersistentDataContainer().set(CritterModule.DISCARD_AT, PersistentDataType.LONG, now - 1);
        module.discard(due);
        assertThat(due.isValid()).isFalse();

        Cow named = world.spawn(new Location(world, 4.5, 65, 0.5), Cow.class);
        named.getPersistentDataContainer().set(CritterModule.DISCARD_AT, PersistentDataType.LONG, now - 1);
        named.customName(net.kyori.adventure.text.Component.text("Pak"));
        module.discard(named);
        assertThat(named.isValid()).isTrue();

        Cow young = world.spawn(new Location(world, 8.5, 65, 0.5), Cow.class);
        young.getPersistentDataContainer().set(CritterModule.DISCARD_AT, PersistentDataType.LONG, now + 5);
        module.discard(young);
        assertThat(young.isValid()).isTrue();
    }

    @Test
    void pestBehaviourServesTheFourPestsWithOneFeastGoal() {
        var b = module.pestBehaviour();
        assertThat(b.species()).containsExactlyInAnyOrder("tfc:rat", "tfc:lemming", "tfc:mongoose", "tfc:jerboa");
        Mob mob = world.spawn(new Location(world, 0.5, 65, 0.5), Cow.class);
        var goals = b.goals(mob, null);
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).priority()).isEqualTo(3);
        assertThat(goals.get(0).goal().getKey().getNamespacedKey().getKey()).isEqualTo("pest_feast");
    }

    @Test
    void critterBehaviourServesTheFourCrittersAndKeepsTheCarriersVanillaGoals() {
        var b = module.critterBehaviour();
        assertThat(b.species()).containsExactlyInAnyOrder("tfc:isopod", "tfc:lobster", "tfc:crayfish", "tfc:horseshoe_crab");
        assertThat(b.keepVanillaGoals()).isTrue();
        Mob mob = world.spawn(new Location(world, 0.5, 65, 0.5), Cow.class);
        assertThat(b.goals(mob, null)).extracting(g -> g.priority()).containsExactly(1, 2);
    }

    @Test
    void landSpotFindsStandableGroundWithinFifteenBlocks() {
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                world.getBlockAt(x, 64, z).setType(Material.STONE);
            }
        }
        random.ints.addAll(List.of(3, 4, 5));
        Location spot = module.landSpot(new Location(world, 0, 64, 0));
        // dx = 3 - 15, dz = 4 - 15, dy = 5 - 5 = 0: the column at y 64 is solid, so the spot is on top
        assertThat(spot).isNotNull();
        assertThat(spot.getBlockX()).isEqualTo(-12);
        assertThat(spot.getBlockZ()).isEqualTo(-11);
        assertThat(spot.getBlockY()).isEqualTo(65);
        assertThat(PestSpecies.values()).hasSize(4);
    }
}
