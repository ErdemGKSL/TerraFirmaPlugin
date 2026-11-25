/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.amphibian;

import static org.assertj.core.api.Assertions.assertThat;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.amphibian.AmphibianActivity;
import dev.tfp.core.amphibian.AmphibianIdle;
import dev.tfp.core.amphibian.AmphibiousEscape;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.aquatic.PlayDeadTimer;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-12 on MockBukkit: the hit turtle plays dead on land (and not in water), the penguin without effects, the seal
 * retreats instead, small-fish hunting, healing by food, TFC attributes and frog feeding, driven through the goal and the
 * listeners with a recording world seam.
 */
class AmphibianModuleMockBukkitTest extends MockServerTest {
    private World world;
    private AmphibianModule module;
    private Body body;
    private long now;

    /** Records every world write and answers the reads from fields the test sets. */
    private static final class Body implements AmphibianBody {
        final List<String> log = new ArrayList<>();
        List<LivingEntity> visible = new ArrayList<>();
        boolean water;
        boolean path;
        AmphibiousEscape.Candidate away;

        @Override
        public boolean inWater(Mob mob) {
            return water;
        }

        @Override
        public boolean waterAt(Location at) {
            return water;
        }

        @Override
        public List<LivingEntity> visible(Mob mob, double range) {
            return visible.stream().filter(e -> e != mob).toList();
        }

        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            log.add("move " + (float) speed);
            path = true;
            return true;
        }

        @Override
        public boolean moveTo(Mob mob, LivingEntity to, double speed) {
            log.add("follow " + (float) speed);
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
        }

        @Override
        public Optional<Location> randomSwim(Mob mob) {
            return Optional.empty();
        }

        @Override
        public Optional<Location> randomStroll(Mob mob) {
            return Optional.empty();
        }

        @Override
        public AmphibiousEscape.Candidate awayFrom(Mob mob, Location from) {
            return away;
        }

        @Override
        public AmphibianIdle.WaterProbe waterProbe(Mob mob) {
            return new AmphibianIdle.WaterProbe() {
                @Override
                public boolean water(int dx, int dy, int dz) {
                    return false;
                }

                @Override
                public boolean airAbove(int dx, int dy, int dz) {
                    return false;
                }

                @Override
                public boolean close(int dx, int dy, int dz) {
                    return false;
                }
            };
        }

        @Override
        public PredatorMelee.Box box(Entity e) {
            Location l = e.getLocation();
            return new PredatorMelee.Box(l.getX() - 0.4, l.getY(), l.getZ() - 0.4, l.getX() + 0.4, l.getY() + 0.4, l.getZ() + 0.4);
        }

        @Override
        public void attack(Mob mob, LivingEntity target) {
            log.add("attack");
            target.setHealth(0);
        }

        @Override
        public void effects(Mob mob, List<PlayDeadTimer.StatusEffect> effects) {
            log.add("effects " + effects.size());
        }

        @Override
        public void playDeadPose(Mob mob, boolean dead) {
            log.add("pose " + dead);
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new AmphibianModule();
        body = new Body();
        now = 1_000;
        module.initForTest(null, body, (id, tag) -> tag.equals("tfp:small_fish") && id.equals("tfp:cod")
                        || tag.equals("tfp:turtle_friends") && id.equals("minecraft:player"),
                tag -> stack -> stack != null && stack.getType() == Material.KELP,
                stack -> stack != null && stack.getType() == Material.COD, stack -> false, () -> now, l -> 25.0, new Random(5L));
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Mob animal(String species, double x) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalPdc.write(cow.getPersistentDataContainer(),
                new AnimalRecord(species, AnimalState.spawned(true, 0, 0), 0, "", false, AnimalRecord.NO_GROW_TICK));
        return cow;
    }

    private Goal<Mob> goal(Mob mob) {
        AnimalRecord record = AnimalPdc.read(mob.getPersistentDataContainer()).orElseThrow();
        List<SpeciesBehaviour.GoalSpec> goals = module.behaviour().goals(mob, record);
        assertThat(goals).hasSize(1);
        module.behaviour().onAdd(mob, record);
        return goals.get(0).goal();
    }

    /** One server tick of the goal. */
    private static void tick(Mob mob, Goal<Mob> goal) {
        mob.setTicksLived(mob.getTicksLived() + 1);
        assertThat(goal.shouldActivate()).isTrue();
        goal.tick();
    }

    private void hit(Entity by, Entity victim, double amount) {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(by, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), amount));
    }

    private Player player(double x) {
        Player p = server.addPlayer();
        p.teleport(new Location(world, x, 64, 0));
        p.setGameMode(GameMode.SURVIVAL);
        return p;
    }

    @Test
    void aHitTurtlePlaysDeadOnLandFor200TicksWithRegenerationAndResistance() {
        Mob turtle = animal("tfc:turtle", 0);
        Goal<Mob> goal = goal(turtle);
        AmphibianBrain brain = module.brain(turtle);
        tick(turtle, goal);
        body.path = true;
        hit(player(3), turtle, 2.0);
        tick(turtle, goal);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        assertThat(brain.memory.playingDead()).isTrue();
        assertThat(body.log).contains("pose true");
        assertThat(body.path).isFalse();
        // the behaviour starts on the tick after the activity switch: walk stopped, the turtle's effects given once
        tick(turtle, goal);
        assertThat(body.log).containsOnlyOnce("effects 2");
        for (int i = 0; i < 198; i++) {
            tick(turtle, goal);
        }
        assertThat(brain.memory.playingDead()).isFalse();
        assertThat(body.log).contains("pose false");
        tick(turtle, goal);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.IDLE);
        assertThat(brain.memory.hurtBy()).isFalse();
    }

    @Test
    void inWaterTheTurtleDoesNotLieStillButStillStopsItsIdleBehaviours() {
        Mob turtle = animal("tfc:turtle", 0);
        Goal<Mob> goal = goal(turtle);
        body.water = true;
        hit(player(3), turtle, 2.0);
        tick(turtle, goal);
        tick(turtle, goal);
        AmphibianBrain brain = module.brain(turtle);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        assertThat(brain.memory.playingDead()).isFalse();
        assertThat(body.log).doesNotContain("pose true");
    }

    @Test
    void aPenguinPlaysDeadWithoutEffectsAndAWeakHitIsIgnored() {
        Mob penguin = animal("tfc:penguin", 0);
        Goal<Mob> goal = goal(penguin);
        Player p = player(3);
        hit(p, penguin, 0.5);
        tick(penguin, goal);
        assertThat(module.brain(penguin).memory.playingDead()).isFalse();
        hit(p, penguin, 1.0);
        tick(penguin, goal);
        tick(penguin, goal);
        assertThat(module.brain(penguin).memory.playingDead()).isTrue();
        assertThat(body.log).noneMatch(s -> s.startsWith("effects"));
    }

    @Test
    void aHitSealRetreatsToWaterAndNeverPlaysDead() {
        Mob seal = animal("tfc:leopard_seal", 0);
        Goal<Mob> goal = goal(seal);
        Player p = player(-4);
        body.away = new AmphibiousEscape.Candidate(new AmphibiousEscape.Vec(6, 63, 0), true, false);
        hit(p, seal, 3.0);
        tick(seal, goal);
        AmphibianBrain brain = module.brain(seal);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.AVOID);
        assertThat(brain.memory.playingDead()).isFalse();
        body.log.clear();
        tick(seal, goal);
        // flee on land at getFleeingSpeedModifier 0.2; in the same tick AVOID's own home stroll (its 80-tick wait is over)
        // overwrites the walk target, as in TFC
        assertThat(body.log).containsSubsequence("move 0.2", "move 0.15");
        tick(seal, goal);
        assertThat(brain.walkTo.getX()).isEqualTo(6.0);
        assertThat(brain.walkSpeed).isEqualTo(0.2f);
    }

    @Test
    void aTurtleHuntsAVisibleSmallFishAndRestsFromHuntingAfterTheKill() {
        Mob turtle = animal("tfc:turtle", 0);
        Mob cod = animal("tfc:cod", 0.5);
        body.visible.add(cod);
        body.water = true;
        Goal<Mob> goal = goal(turtle);
        AmphibianBrain brain = module.brain(turtle);
        tick(turtle, goal);
        assertThat(brain.target).isSameAs(cod);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.FIGHT);
        tick(turtle, goal);
        assertThat(body.log).containsOnlyOnce("attack");
        assertThat(brain.memory.huntedRecently(turtle.getTicksLived() + 999)).isTrue();
        tick(turtle, goal);
        assertThat(brain.memory.activity()).isEqualTo(AmphibianActivity.IDLE);
        assertThat(brain.target).isNull();
    }

    @Test
    void tfcAttributesAndFeedingHealsOnePoint() {
        Mob turtle = animal("tfc:turtle", 0);
        goal(turtle);
        assertThat(turtle.getAttribute(Attribute.MAX_HEALTH).getBaseValue()).isEqualTo(14.0);
        assertThat(turtle.getHealth()).isEqualTo(14.0);
        assertThat(turtle.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue()).isEqualTo(1.0);
        assertThat(AmphibianModule.readHome(turtle)).isNotNull();
        turtle.setHealth(10.0);
        Player p = player(1);
        p.getInventory().setItemInMainHand(new ItemStack(Material.KELP, 3));
        PlayerInteractEntityEvent e = new PlayerInteractEntityEvent(p, turtle, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(e);
        assertThat(e.isCancelled()).isTrue();
        assertThat(turtle.getHealth()).isEqualTo(11.0);
        assertThat(p.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
        // at full health nothing happens
        turtle.setHealth(14.0);
        PlayerInteractEntityEvent full = new PlayerInteractEntityEvent(p, turtle, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(full);
        assertThat(full.isCancelled()).isFalse();
        assertThat(p.getInventory().getItemInMainHand().getAmount()).isEqualTo(2);
    }

    @Test
    void aFrogGainsFamiliarityOnceADayAndIgnoresSlimeBalls() {
        Mob frog = animal("tfc:frog", 0);
        assertThat(module.frogBehaviour().keepVanillaGoals()).isTrue();
        assertThat(module.frogBehaviour().species()).isEqualTo(Set.of("tfc:frog"));
        Player p = player(1);
        p.getInventory().setItemInMainHand(new ItemStack(Material.COD, 2));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(p, frog, EquipmentSlot.HAND));
        assertThat(AmphibianModule.frogState(frog.getPersistentDataContainer()).familiarity()).isEqualTo(0.1f);
        assertThat(AmphibianModule.frogState(frog.getPersistentDataContainer()).nextFeed()).isEqualTo(25_000);
        assertThat(p.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        // the same day: the click is used up, nothing eaten
        PlayerInteractEntityEvent again = new PlayerInteractEntityEvent(p, frog, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(again);
        assertThat(again.isCancelled()).isTrue();
        assertThat(p.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
        p.getInventory().setItemInMainHand(new ItemStack(Material.SLIME_BALL, 1));
        PlayerInteractEntityEvent slime = new PlayerInteractEntityEvent(p, frog, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(slime);
        assertThat(slime.isCancelled()).isTrue();
        assertThat(p.getInventory().getItemInMainHand().getAmount()).isEqualTo(1);
    }
}
