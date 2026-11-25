/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestockai.LivestockRoster;
import dev.tfp.core.livestockai.Temptation;
import dev.tfp.core.prey.FleeState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-08 goal selection on MockBukkit: the livestock goals driven through Paper's goal calls
 * ({@code shouldActivate}, {@code start}, {@code tick}, {@code shouldStayActive}, {@code stop}) with a recording world
 * seam and a hand-set clock.
 */
class LivestockGoalsMockBukkitTest extends MockServerTest {
    /** Long past every species' adulthood (cow: 192 days of 28,800 ticks). */
    private static final long START = 100_000_000L;

    private World world;
    private LivestockAiModule module;
    private Body body;
    private long now;
    private final List<String> removed = new ArrayList<>();

    private static final class Body implements LivestockBody {
        List<LivingEntity> visible = new ArrayList<>();
        List<Player> players = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        double lastSpeed;
        Entity lastFollowed;
        boolean path;

        @Override
        public List<LivingEntity> visible(Mob mob, double range) {
            return visible.stream().filter(e -> e != mob).toList();
        }

        @Override
        public List<Player> players(Mob mob, double range) {
            return players;
        }

        @Override
        public Location standable(Mob mob, int dx, int dy, int dz) {
            return mob.getLocation().getBlock().getLocation().add(dx + 0.5, 0, dz + 0.5);
        }

        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            log.add("move " + speed);
            lastSpeed = speed;
            path = true;
            return true;
        }

        @Override
        public boolean moveTo(Mob mob, Entity to, double speed) {
            log.add("follow " + speed);
            lastSpeed = speed;
            lastFollowed = to;
            path = true;
            return true;
        }

        @Override
        public void stop(Mob mob) {
            log.add("stop");
            path = false;
        }

        @Override
        public boolean hasPath(Mob mob) {
            return path;
        }

        @Override
        public void lookAt(Mob mob, Entity at) {
            log.add("look");
        }

        @Override
        public boolean inWater(Mob mob) {
            return false;
        }

        @Override
        public void swimUp(Mob mob, double speed) {
            log.add("swim");
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new LivestockAiModule();
        body = new Body();
        now = START;
        Map<String, Set<String>> tags = Map.of(
                LivestockSenses.LAND_PREDATORS, Set.of("tfp:wolf"),
                LivestockSenses.RAMMING_ANIMALS, Set.of("tfp:boar"));
        module.initForTest(body, () -> new ClockSnapshot(1L, now, now, 8, true),
                (species, tag) -> tags.getOrDefault(tag, Set.of()).contains(species),
                species -> stack -> stack.getType() == Material.WHEAT, new Random(3L),
                (mob, key) -> removed.add(key.getNamespacedKey().getKey()));
        server.getPluginManager().registerEvents(module, plugin);
    }

    /** An animal with a record: adult (born at 0) or child (born now), ready to mate when {@code ready}. */
    private Mob animal(String species, double x, boolean male, boolean baby, boolean ready) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalState s = AnimalState.spawned(male, baby ? now : 0, now);
        if (ready) {
            s = new AnimalState(male, 0.5f, now, 0, AnimalState.NO_OLD_TICK, now, AnimalState.NEVER, 0, false,
                    AnimalState.NOT_PREGNANT);
        }
        AnimalPdc.write(cow.getPersistentDataContainer(), new AnimalRecord(species, s, 0, "", false, AnimalRecord.NO_GROW_TICK));
        return cow;
    }

    private Player player(double x, GameMode mode, Material held) {
        Player p = server.addPlayer();
        p.teleport(new Location(world, x, 64, 0));
        p.setGameMode(mode);
        p.getInventory().setItemInMainHand(new ItemStack(held));
        return p;
    }

    private static AnimalRecord record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer()).orElseThrow();
    }

    private Map<String, Goal<Mob>> goals(SpeciesBehaviour behaviour, Mob mob) {
        Map<String, Goal<Mob>> byName = new HashMap<>();
        for (SpeciesBehaviour.GoalSpec g : behaviour.goals(mob, record(mob))) {
            byName.put(g.goal().getKey().getNamespacedKey().getKey(), g.goal());
        }
        return byName;
    }

    private static void align(Mob mob, int period) {
        int t = mob.getTicksLived() + 1;
        while ((t + mob.getEntityId()) % period != 0) {
            t++;
        }
        mob.setTicksLived(t);
    }

    @Test
    void brainReplacesVanillaGoalsForTwelveSpeciesInTfcPriorityOrder() {
        SpeciesBehaviour b = module.brainBehaviour();
        assertThat(b.keepVanillaGoals()).isFalse();
        assertThat(b.species()).hasSize(12).contains("tfc:cow", "tfc:quail", "tfc:bactrian_camel")
                .doesNotContain("tfc:horse", "tfc:dog", "tfc:rabbit");
        Mob cow = animal("tfc:cow", 0, false, false, false);
        List<String> order = b.goals(cow, record(cow)).stream()
                .sorted(java.util.Comparator.comparingInt(SpeciesBehaviour.GoalSpec::priority))
                .map(g -> g.goal().getKey().getNamespacedKey().getKey()).toList();
        assertThat(order).containsExactly("livestock_swim", "livestock_panic", "livestock_avoid", "livestock_breed",
                "livestock_tempt", "livestock_follow_adult", "livestock_idle", "livestock_look");
    }

    @Test
    void horsesKeepVanillaGoalsWithTheTemptGoalSwappedForTfcFood() {
        SpeciesBehaviour b = module.horseBehaviour();
        assertThat(b.keepVanillaGoals()).isTrue();
        assertThat(b.species()).containsExactlyInAnyOrder("tfc:horse", "tfc:donkey", "tfc:mule");
        Mob horse = animal("tfc:horse", 0, true, false, false);
        List<SpeciesBehaviour.GoalSpec> g = b.goals(horse, record(horse));
        assertThat(g).hasSize(1);
        assertThat(g.get(0).priority()).isEqualTo(3);
        b.onAdd(horse, record(horse));
        assertThat(removed).containsExactly("tempt");

        Player p = player(5, GameMode.SURVIVAL, Material.WHEAT);
        body.players.add(p);
        Goal<Mob> tempt = g.get(0).goal();
        align(horse, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).isTrue();
        tempt.tick();
        assertThat(body.lastSpeed).isEqualTo(1.25, within(1e-6));
    }

    @Test
    void cowFollowsTheNearestFoodHolderAndCoolsDownAfterwards() {
        Mob cow = animal("tfc:cow", 0, false, false, false);
        Goal<Mob> tempt = goals(module.brainBehaviour(), cow).get("livestock_tempt");
        Player empty = player(2, GameMode.SURVIVAL, Material.STONE);
        Player spectator = player(3, GameMode.SPECTATOR, Material.WHEAT);
        Player far = player(12, GameMode.SURVIVAL, Material.WHEAT);
        body.players.addAll(List.of(empty, spectator, far));
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).as("no food holder within 10").isFalse();

        Player holder = player(6, GameMode.SURVIVAL, Material.WHEAT);
        body.players.add(holder);
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).isTrue();
        assertThat(module.brain(cow).tempting).isSameAs(holder);
        tempt.start();
        align(cow, 10);
        tempt.tick();
        assertThat(body.lastFollowed).isSameAs(holder);
        assertThat(body.lastSpeed).isEqualTo(1.25, within(1e-6));

        // within 2.5 blocks: stand and look
        holder.teleport(new Location(world, 2, 64, 0));
        align(cow, 10);
        tempt.tick();
        assertThat(body.log).endsWith("look", "stop");

        // the player puts the food away: the temptation ends and a 100-tick cooldown starts
        holder.getInventory().setItemInMainHand(new ItemStack(Material.STONE));
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldStayActive()).isFalse();
        tempt.stop();
        assertThat(module.brain(cow).temptCooldownUntil).isEqualTo(now + Temptation.COOLDOWN_TICKS);
        holder.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT));
        now += 50;
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).as("cooling down").isFalse();
        now += 50;
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).isTrue();
    }

    @Test
    void babiesAreTemptedFasterAndFollowAnAdult() {
        Mob calf = animal("tfc:cow", 0, false, true, false);
        Mob mother = animal("tfc:cow", 8, false, false, false);
        Map<String, Goal<Mob>> g = goals(module.brainBehaviour(), calf);
        Player p = player(4, GameMode.SURVIVAL, Material.WHEAT);
        body.players.add(p);
        align(calf, LivestockGoals.Tempt.PERIOD);
        assertThat(g.get("livestock_tempt").shouldActivate()).isTrue();
        align(calf, 10);
        g.get("livestock_tempt").tick();
        assertThat(body.lastSpeed).isEqualTo(1.5, within(1e-6));

        body.visible.add(mother);
        Goal<Mob> follow = g.get("livestock_follow_adult");
        align(calf, 10);
        assertThat(follow.shouldActivate()).as("8 blocks: between 5 and 16").isTrue();
        follow.tick();
        assertThat(body.lastFollowed).isSameAs(mother);
        assertThat(body.lastSpeed).isEqualTo(1.25, within(1e-6));
        mother.teleport(new Location(world, 4, 64, 0));
        align(calf, 10);
        assertThat(follow.shouldStayActive()).as("closer than 5").isFalse();
    }

    @Test
    void hitStartsPanicWhichBlocksTemptation() {
        Mob cow = animal("tfc:cow", 0, false, false, false);
        Map<String, Goal<Mob>> g = goals(module.brainBehaviour(), cow);
        Player p = player(4, GameMode.SURVIVAL, Material.WHEAT);
        body.players.add(p);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(p, cow,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(
                        org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1.0));
        assertThat(module.brain(cow).panicking(now)).isTrue();
        assertThat(module.brain(cow).panicUntil - now).isBetween(100L, 120L);
        Goal<Mob> panic = g.get("livestock_panic");
        assertThat(panic.shouldActivate()).isTrue();
        panic.start();
        panic.tick();
        assertThat(body.lastSpeed).isEqualTo(2.0, within(1e-6));
        align(cow, LivestockGoals.Tempt.PERIOD);
        assertThat(g.get("livestock_tempt").shouldActivate()).as("FollowTemptation: not while panicking").isFalse();
        // a player is no threat to livestock (create(true))
        body.visible.add(p);
        align(cow, LivestockGoals.Avoid.PERIOD);
        assertThat(g.get("livestock_avoid").shouldActivate()).isFalse();
    }

    @Test
    void landPredatorStartsTheAvoidRunAndAHitWarnsTheHerd() {
        Mob cow = animal("tfc:cow", 0, false, false, false);
        Mob herdMate = animal("tfc:cow", 3, true, false, false);
        Mob wolf = animal("tfc:wolf", 6, true, false, false);
        Mob boar = animal("tfc:boar", -4, true, false, false);
        Goal<Mob> avoid = goals(module.brainBehaviour(), cow).get("livestock_avoid");
        goals(module.brainBehaviour(), herdMate);
        body.visible.addAll(List.of(herdMate, wolf));
        align(cow, LivestockGoals.Avoid.PERIOD);
        assertThat(avoid.shouldActivate()).isTrue();
        assertThat(module.brain(cow).flee.threatType()).isEqualTo("tfc:wolf");
        avoid.start();
        assertThat(body.lastSpeed).isEqualTo(1.3, within(1e-6));

        // hit by a ramming animal while fleeing from the wolf: the boar becomes the threat and the herd is warned
        body.visible.add(boar);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(boar, cow,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(
                        org.bukkit.damage.DamageType.MOB_ATTACK).build(), 1.0));
        assertThat(module.brain(cow).flee.threatType()).isEqualTo("tfc:boar");
        FleeState mate = module.brain(herdMate).flee;
        assertThat(mate.threatType()).isEqualTo("tfc:boar");
        assertThat(mate.expiresAtTick() - now).isBetween(140L, 440L);
    }

    @Test
    void readyMaleWalksToTheFemaleAndPairsWithinThreeBlocks() {
        Mob bull = animal("tfc:cow", 0, true, false, true);
        Mob heifer = animal("tfc:cow", 10, false, false, true);
        Mob unready = animal("tfc:cow", 4, false, false, false);
        Goal<Mob> breed = goals(module.brainBehaviour(), bull).get("livestock_breed");
        Goal<Mob> herBreed = goals(module.brainBehaviour(), heifer).get("livestock_breed");
        goals(module.brainBehaviour(), unready);
        body.visible.addAll(List.of(unready, heifer));

        align(heifer, LivestockGoals.Breed.PERIOD);
        assertThat(herBreed.shouldActivate()).as("females never start").isFalse();
        align(bull, LivestockGoals.Breed.PERIOD);
        assertThat(breed.shouldActivate()).isTrue();
        assertThat(module.brain(bull).breedPartner).isSameAs(heifer);
        assertThat(module.brain(heifer).breedPartner).isSameAs(bull);
        long pairAt = module.brain(bull).pairAt;
        assertThat(pairAt - now).isBetween(60L, 109L);
        align(heifer, LivestockGoals.Breed.PERIOD);
        assertThat(herBreed.shouldActivate()).as("the partner walks too").isTrue();
        breed.start();
        assertThat(body.lastFollowed).isSameAs(heifer);
        assertThat(body.lastSpeed).isEqualTo(1.0, within(1e-6));
        // the next attempt waits 200 ticks
        assertThat(module.brain(bull).nextBreedAttempt).isEqualTo(now + 200);

        heifer.teleport(new Location(world, 2, 64, 0));
        breed.tick();
        assertThat(record(heifer).state().fertilized()).as("too early").isFalse();
        now = pairAt;
        breed.tick();
        assertThat(record(heifer).state().fertilized()).isTrue();
        assertThat(record(bull).state().uses()).isGreaterThan(0);
        assertThat(module.brain(bull).breedPartner).isNull();
        assertThat(module.brain(heifer).breedPartner).isNull();
    }

    @Test
    void camelsRunTheSameBrainAtCamelSpeeds() {
        Mob camel = animal("tfc:dromedary_camel", 0, false, false, false);
        Goal<Mob> tempt = goals(module.brainBehaviour(), camel).get("livestock_tempt");
        assertThat(module.brain(camel).profile).isSameAs(LivestockRoster.CAMEL);
        Player p = player(6, GameMode.SURVIVAL, Material.WHEAT);
        body.players.add(p);
        align(camel, LivestockGoals.Tempt.PERIOD);
        assertThat(tempt.shouldActivate()).isTrue();
        align(camel, 10);
        tempt.tick();
        assertThat(body.lastSpeed).isEqualTo(3.5, within(1e-6));
    }

    @Test
    void idleGrazesInPlaceOrStrolls() {
        Mob sheep = animal("tfc:sheep", 0, false, false, false);
        Goal<Mob> idle = goals(module.brainBehaviour(), sheep).get("livestock_idle");
        int strolls = 0;
        int waits = 0;
        for (int i = 0; i < 30; i++) {
            body.path = false;
            align(sheep, 20);
            if (idle.shouldActivate()) {
                if (body.path) {
                    strolls++;
                    assertThat(body.lastSpeed).isEqualTo(1.0, within(1e-6));
                } else {
                    waits++;
                }
                idle.stop();
            }
        }
        assertThat(strolls).isPositive();
        assertThat(waits).isPositive();
    }

    @Test
    void removedAnimalsForgetTheirBrain() {
        Mob cow = animal("tfc:cow", 0, false, false, false);
        goals(module.brainBehaviour(), cow);
        assertThat(module.brain(cow)).isNotNull();
        cow.remove();
        server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityRemoveEvent(cow,
                org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD));
        assertThat(module.brain(cow)).isNull();
    }
}
