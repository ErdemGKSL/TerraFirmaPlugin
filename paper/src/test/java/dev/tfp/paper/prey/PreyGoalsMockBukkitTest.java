/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import static org.assertj.core.api.Assertions.assertThat;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.RamCharge;
import dev.tfp.core.prey.RamRules;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-09 goal transitions on MockBukkit: the flee, panic, ram, idle, small-prey avoid and armadillo scare goals
 * driven through Paper's goal calls ({@code shouldActivate}, {@code start}, {@code tick}, {@code shouldStayActive},
 * {@code stop}) with a recording world seam and a hand-set clock.
 */
class PreyGoalsMockBukkitTest extends MockServerTest {
    private World world;
    private PreyModule module;
    private Body body;
    private long now;
    private final Map<LivingEntity, Boolean> danger = new HashMap<>();
    private final List<String> removed = new ArrayList<>();

    /** Records every world write and answers the reads from lists the test sets. */
    private static final class Body implements PreyBody {
        List<LivingEntity> visible = new ArrayList<>();
        List<LivingEntity> touching = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        Location lastTarget;
        double lastSpeed;
        boolean path;
        boolean water;

        @Override
        public List<LivingEntity> visible(Mob mob, double range) {
            return visible.stream().filter(e -> e != mob).toList();
        }

        @Override
        public List<LivingEntity> touching(Mob mob, double reach) {
            return touching;
        }

        @Override
        public Location standable(Mob mob, int dx, int dy, int dz) {
            Location l = mob.getLocation().getBlock().getLocation().add(dx + 0.5, 0, dz + 0.5);
            return l;
        }

        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            log.add("move " + speed);
            lastTarget = to;
            lastSpeed = speed;
            path = true;
            return true;
        }

        @Override
        public boolean moveTo(Mob mob, Entity to, double speed) {
            log.add("follow " + speed);
            lastSpeed = speed;
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
            return water;
        }

        @Override
        public void swimUp(Mob mob, double speed) {
            log.add("swim " + speed);
        }

        @Override
        public void ram(Mob mob, LivingEntity target, double damage, double strength, double x, double z) {
            log.add("ram " + damage);
        }

        @Override
        public void sound(Mob mob, String event) {
            log.add("sound " + event);
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new PreyModule();
        body = new Body();
        now = 1_000;
        // the tag files: wolves are land predators, boars ramming animals, rats not rammed
        Map<String, Set<String>> tags = Map.of(
            PreySenses.LAND_PREDATORS, Set.of("tfp:wolf"),
            PreySenses.RAMMING_ANIMALS, Set.of("tfp:boar"),
            PreySenses.NOT_RAMMED, Set.of("tfp:rat"));
        module.initForTest(body, () -> now, (species, tag) -> tags.getOrDefault(tag, Set.of()).contains(species),
            new Random(1L), danger::put, (mob, key) -> removed.add(key.getNamespacedKey().getKey()));
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Mob animal(String species, double x, boolean male, boolean baby) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalRecord record = new AnimalRecord(species, AnimalState.spawned(male, 0, 0), 0, "", baby,
            baby ? Long.MAX_VALUE : AnimalRecord.NO_GROW_TICK);
        AnimalPdc.write(cow.getPersistentDataContainer(), record);
        return cow;
    }

    private Player player(double x, GameMode mode) {
        Player p = server.addPlayer();
        p.teleport(new Location(world, x, 64, 0));
        p.setGameMode(mode);
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

    /** Moves the animal's tick count so the goal with this period asks its question now. */
    private static void align(Mob mob, int period) {
        int t = mob.getTicksLived() + 1;
        while ((t + mob.getEntityId()) % period != 0) {
            t++;
        }
        mob.setTicksLived(t);
    }

    @Test
    void deerFleesFromASurvivalPlayerAndStopsBeyondTwentyBlocks() {
        Mob deer = animal("tfc:deer", 0, true, false);
        Player p = player(5, GameMode.SURVIVAL);
        body.visible.add(p);
        Goal<Mob> flee = goals(module.wildBehaviour(), deer).get("prey_flee");

        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isTrue();
        assertThat(module.flee(deer).threatType()).isEqualTo("minecraft:player");
        // a sighting memory has no expiry
        assertThat(module.flee(deer).expiresAtTick()).isEqualTo(Long.MAX_VALUE);
        flee.start();
        assertThat(body.lastSpeed).isEqualTo(PreyFlee.FLEE_SPEED, org.assertj.core.api.Assertions.within(1e-6));
        // the run target is on the far side of the deer from the player
        assertThat(body.lastTarget.getX()).isLessThan(5.0);

        // 20 blocks keeps fleeing, beyond 20 ends the flee and erases the memory
        p.teleport(new Location(world, 20, 64, 0));
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldStayActive()).isTrue();
        p.teleport(new Location(world, 20.6, 64, 0));
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldStayActive()).isFalse();
        flee.stop();
        assertThat(module.flee(deer)).isEqualTo(FleeState.IDLE);
    }

    @Test
    void creativePlayersAreIgnoredAndOffPeriodAsksNothing() {
        Mob deer = animal("tfc:deer", 0, true, false);
        body.visible.add(player(4, GameMode.CREATIVE));
        Goal<Mob> flee = goals(module.wildBehaviour(), deer).get("prey_flee");
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isFalse();
        body.visible.add(player(4, GameMode.SURVIVAL));
        // staggered: one tick later is not this animal's turn
        deer.setTicksLived(deer.getTicksLived() + 1);
        assertThat(flee.shouldActivate()).isFalse();
        // the sensor reading is reused until 20 ticks have passed
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isFalse();
        now += PreyFleeGoal.SENSE_TICKS;
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isTrue();
    }

    @Test
    void boarIgnoresPlayersButFleesFromAWolf() {
        Mob boar = animal("tfc:boar", 0, true, false);
        body.visible.add(player(3, GameMode.SURVIVAL));
        Goal<Mob> flee = goals(module.wildBehaviour(), boar).get("prey_flee");
        align(boar, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isFalse();
        Mob wolf = animal("tfc:wolf", 6, true, false);
        body.visible.add(wolf);
        now += PreyFleeGoal.SENSE_TICKS;
        align(boar, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isTrue();
        assertThat(module.flee(boar).threatType()).isEqualTo("tfc:wolf");
    }

    @Test
    void plainHitPanicsAndHitWhileFleeingSwitchesTheThreatAndWarnsFriends() {
        Mob deer = animal("tfc:deer", 0, true, false);
        Mob friend = animal("tfc:deer", 3, false, false);
        Map<String, Goal<Mob>> goals = goals(module.wildBehaviour(), deer);
        goals(module.wildBehaviour(), friend);
        Player p = player(14, GameMode.SURVIVAL);
        Goal<Mob> panic = goals.get("prey_panic");
        assertThat(panic.shouldActivate()).isFalse();

        // not fleeing: the hit only starts AnimalPanic (100..120 ticks); wasHurtBy does nothing
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(p, deer, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1.0));
        assertThat(panic.shouldActivate()).isTrue();
        assertThat(module.flee(deer)).isEqualTo(FleeState.IDLE);
        now += 121;
        assertThat(panic.shouldStayActive()).isFalse();

        // fleeing from a wolf, then hit by the player: the threat switches with a 7..22 s retreat, the friend is warned
        Mob wolf = animal("tfc:wolf", -10, true, false);
        body.visible.add(wolf);
        body.visible.add(friend);
        Goal<Mob> flee = goals.get("prey_flee");
        align(deer, PreyFleeGoal.PERIOD);
        assertThat(flee.shouldActivate()).isTrue();
        assertThat(module.flee(deer).threatType()).isEqualTo("tfc:wolf");
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(p, deer, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1.0));
        FleeState switched = module.flee(deer);
        assertThat(switched.threatType()).isEqualTo("minecraft:player");
        assertThat(switched.expiresAtTick() - now).isBetween(140L, 440L);
        // the friend at x = 3 had no threat, so it takes the attacker
        assertThat(module.flee(friend).threatType()).isEqualTo("minecraft:player");
    }

    @Test
    void boarRamsAPlayerAfterStandingTwentyTicksThenCoolsDown() {
        Mob boar = animal("tfc:boar", 0, true, false);
        Player p = player(3, GameMode.SURVIVAL);
        body.visible.add(p);
        Map<String, Goal<Mob>> goals = goals(module.wildBehaviour(), boar);
        Goal<Mob> ram = goals.get("prey_ram");
        PreyBrain brain = module.brain(boar);
        // initMemories: a male cooldown of 600..1000 ticks
        assertThat(brain.ram.cooldownUntil() - now).isBetween(600L, 1000L);
        align(boar, RamGoal.PERIOD);
        assertThat(ram.shouldActivate()).isFalse();

        // a hit from a nearby attacker erases the cooldown (PredatorAi.hasNearbyAttacker)
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(p, boar, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1.0));
        align(boar, RamGoal.PERIOD);
        assertThat(ram.shouldActivate()).isTrue();
        assertThat(brain.ram.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        ram.start();
        for (int i = 0; i < RamRules.RAM_PREPARE_TIME; i++) {
            ram.tick();
            now++;
        }
        assertThat(brain.ram.phase()).isEqualTo(RamCharge.Phase.PREPARING);
        ram.tick();
        assertThat(brain.ram.phase()).isEqualTo(RamCharge.Phase.CHARGING);
        assertThat(body.log).contains("sound entity.boar.attack", "move 3.0");
        // the player is in the hit box: 6 * 1.2 damage (3 blocks: multiplier 1.2), then the cooldown
        body.touching = List.of(p);
        now++;
        ram.tick();
        assertThat(body.log).anyMatch(s -> s.startsWith("ram 7.19") || s.startsWith("ram 7.2"));
        assertThat(body.log).contains("sound entity.ramming.impact");
        assertThat(brain.ram.phase()).isEqualTo(RamCharge.Phase.COOLDOWN);
        assertThat(brain.ram.cooldownUntil() - now).isBetween(600L, 1000L);
        align(boar, RamGoal.PERIOD);
        assertThat(ram.shouldStayActive()).isFalse();
    }

    @Test
    void ramWithNobodyToRamFailsWithTheTenTickCooldown() {
        Mob boar = animal("tfc:boar", 0, false, false);
        Map<String, Goal<Mob>> goals = goals(module.wildBehaviour(), boar);
        PreyBrain brain = module.brain(boar);
        brain.ram = RamCharge.cooling(now);
        // a rat is in not_rammed_by_rammers: a female does not ram it
        body.visible.add(animal("tfc:rat", 2, true, false));
        align(boar, RamGoal.PERIOD);
        assertThat(goals.get("prey_ram").shouldActivate()).isFalse();
        assertThat(brain.ram).isEqualTo(RamCharge.cooling(now + RamRules.COOLDOWN_ON_FAIL));
    }

    @Test
    void idlePicksAMovementAndWaitsOrWalks() {
        Mob deer = animal("tfc:deer", 0, true, false);
        Goal<Mob> idle = goals(module.wildBehaviour(), deer).get("prey_idle");
        int strolls = 0;
        int waits = 0;
        for (int i = 0; i < 30; i++) {
            body.path = false;
            align(deer, 20);
            if (idle.shouldActivate()) {
                if (body.path) {
                    strolls++;
                    assertThat(body.lastSpeed).isEqualTo(1.0);
                } else {
                    waits++;
                    // DoNothing(30, 60): still waiting 29 ticks later
                    now += 29;
                    align(deer, 20);
                    assertThat(idle.shouldStayActive()).isTrue();
                    now += 32;
                    align(deer, 20);
                    assertThat(idle.shouldStayActive()).isFalse();
                }
                idle.stop();
            }
        }
        assertThat(strolls).isPositive();
        assertThat(waits).isPositive();
    }

    @Test
    void swimGoalSwimsUpInWater() {
        Mob deer = animal("tfc:deer", 0, true, false);
        Goal<Mob> swim = goals(module.wildBehaviour(), deer).get("prey_swim");
        assertThat(swim.shouldActivate()).isFalse();
        body.water = true;
        assertThat(swim.shouldActivate()).isTrue();
        for (int i = 0; i < 100; i++) {
            swim.tick();
        }
        long swims = body.log.stream().filter(s -> s.startsWith("swim")).count();
        assertThat(swims).isBetween(55L, 85L);
    }

    @Test
    void rammersHaveNoPanicAndPreyHaveNoRam() {
        assertThat(goals(module.wildBehaviour(), animal("tfc:boar", 0, true, false))).containsKey("prey_ram")
            .doesNotContainKey("prey_panic");
        assertThat(goals(module.wildBehaviour(), animal("tfc:grouse", 0, true, false))).containsKey("prey_panic")
            .doesNotContainKey("prey_ram");
        assertThat(module.wildBehaviour().keepVanillaGoals()).isFalse();
        assertThat(module.smallBehaviour().keepVanillaGoals()).isTrue();
    }

    @Test
    void foxAvoidsAMovingWolfButNotAStillOne() {
        Mob fox = animal("tfc:fox", 0, true, false);
        Mob wolf = animal("tfc:wolf", 5, true, false);
        body.visible.add(wolf);
        Goal<Mob> avoid = goals(module.smallBehaviour(), fox).get("prey_avoid_predator");
        align(fox, SmallPreyGoals.AvoidPredator.PERIOD);
        assertThat(avoid.shouldActivate()).isFalse();
        wolf.setVelocity(new org.bukkit.util.Vector(0.2, 0, 0));
        align(fox, SmallPreyGoals.AvoidPredator.PERIOD);
        assertThat(avoid.shouldActivate()).isTrue();
        // within 7 blocks: the near speed 1.4
        assertThat(body.lastSpeed).isEqualTo(1.4);
    }

    @Test
    void smallPreyLoseTheVanillaGoalsTfcRemoves() {
        assertThat(PreyModule.removedVanillaGoals("tfc:fox")).containsExactly(VanillaGoal.FOX_BREED, VanillaGoal.FOX_EAT_BERRIES);
        assertThat(PreyModule.removedVanillaGoals("tfc:rabbit"))
            .containsExactly(VanillaGoal.TEMPT, VanillaGoal.RABBIT_AVOID_ENTITY, VanillaGoal.RABBIT_RAID_GARDEN);
        assertThat(PreyModule.removedVanillaGoals("tfc:panda")).containsExactly(VanillaGoal.PANDA_BREED);
        Mob fox = animal("tfc:fox", 0, true, false);
        module.smallBehaviour().onAdd(fox, record(fox));
        assertThat(removed).containsExactly(((GoalKey<?>) VanillaGoal.FOX_BREED).getNamespacedKey().getKey(),
            ((GoalKey<?>) VanillaGoal.FOX_EAT_BERRIES).getNamespacedKey().getKey());
    }

    @Test
    void armadilloIsScaredByANearWolfForEightyTicks() {
        Mob armadillo = animal("tfc:armadillo", 0, true, false);
        Map<String, Goal<Mob>> goals = goals(module.armadilloBehaviour(), armadillo);
        Goal<Mob> scare = goals.get("prey_armadillo_scare");
        Mob wolf = animal("tfc:wolf", 6, true, false);
        body.visible.add(wolf);
        align(armadillo, 5);
        assertThat(scare.shouldActivate()).isFalse();
        assertThat(danger).containsEntry(armadillo, true);
        // the wolf leaves the 7-block box; the memory runs out 80 ticks after the last sighting
        wolf.teleport(new Location(world, 12, 64, 0));
        now += 79;
        module.brain(armadillo).seenAt = Long.MIN_VALUE;
        align(armadillo, 5);
        scare.shouldActivate();
        assertThat(danger).containsEntry(armadillo, true);
        now += 1;
        align(armadillo, 5);
        scare.shouldActivate();
        assertThat(danger).containsEntry(armadillo, false);
        // the armadillo's retreat: predators and rammers at speed 2, not players
        assertThat(module.brain(armadillo).fleeSpeed).isEqualTo(PreyFlee.ARMADILLO_FLEE_SPEED);
    }

    @Test
    void interactionsTfcPasses() {
        assertThat(PreyModule.blocksInteraction("tfc:armadillo", Material.BRUSH, false)).isTrue();
        assertThat(PreyModule.blocksInteraction("tfc:armadillo", Material.SPIDER_EYE, false)).isTrue();
        assertThat(PreyModule.blocksInteraction("tfc:fox", Material.SWEET_BERRIES, false)).isTrue();
        assertThat(PreyModule.blocksInteraction("tfc:panda", Material.BAMBOO, true)).isTrue();
        assertThat(PreyModule.blocksInteraction("tfc:panda", Material.BAMBOO, false)).isFalse();
        assertThat(PreyModule.blocksInteraction("tfc:deer", Material.WHEAT, false)).isFalse();
    }

    @Test
    void attributesAreTfcsAndHealthIsFilledOnce() {
        Mob moose = animal("tfc:moose", 0, true, false);
        module.wildBehaviour().goals(moose, record(moose));
        PreyModule.applyAttributes(moose, dev.tfp.core.prey.WildPrey.RAMMING_LARGE);
        assertThat(moose.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(40.0);
        assertThat(moose.getHealth()).isEqualTo(40.0);
        moose.setHealth(12);
        PreyModule.applyAttributes(moose, dev.tfp.core.prey.WildPrey.RAMMING_LARGE);
        assertThat(moose.getHealth()).isEqualTo(12.0);
    }
}
