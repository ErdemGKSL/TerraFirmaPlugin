/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.predator;

import static org.assertj.core.api.Assertions.assertThat;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.predator.PredatorActivity;
import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
 * PAR-FAUNA-10 goal transitions on MockBukkit: hunt, fight, give up, retreat, rest and sleep, the pack alpha and alert,
 * driven through Paper's goal calls with a recording world seam, a hand-set clock and a hand-set day time.
 */
class PredatorGoalsMockBukkitTest extends MockServerTest {
    private World world;
    private PredatorModule module;
    private Body body;
    private long now;

    /** Records every world write and answers the reads from fields the test sets. */
    private static final class Body implements PredatorBody {
        List<LivingEntity> visible = new ArrayList<>();
        List<LivingEntity> touching = new ArrayList<>();
        List<Player> players = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        long dayTime = 18000;
        boolean peaceful;
        boolean killOnHit;
        double lastSpeed;
        boolean path;

        @Override
        public List<LivingEntity> visible(Mob mob, double range) {
            return visible.stream().filter(e -> e != mob).toList();
        }

        @Override
        public List<LivingEntity> nearby(Mob mob, double range) {
            return visible(mob, range);
        }

        @Override
        public List<Player> players(Mob mob, double range) {
            return players;
        }

        @Override
        public List<LivingEntity> touching(Mob mob) {
            return touching;
        }

        @Override
        public boolean sees(Mob mob, LivingEntity target, double range) {
            return visible.contains(target);
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
        public boolean moveTo(Mob mob, LivingEntity to, double speed) {
            log.add("follow " + speed);
            lastSpeed = speed;
            path = true;
            return true;
        }

        @Override
        public void stop(Mob mob) {
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
        public boolean inWater(Mob mob) {
            return false;
        }

        @Override
        public void jump(Mob mob, double vx, double vy, double vz) {
            log.add("jump");
        }

        @Override
        public PredatorMelee.Box box(Entity e) {
            Location l = e.getLocation();
            return new PredatorMelee.Box(l.getX() - 0.325, l.getY(), l.getZ() - 0.325, l.getX() + 0.325, l.getY() + 0.9,
                l.getZ() + 0.325);
        }

        @Override
        public long dayTime(Mob mob) {
            return dayTime;
        }

        @Override
        public boolean peaceful(Mob mob) {
            return peaceful;
        }

        @Override
        public void attack(Mob mob, LivingEntity target, String sound) {
            log.add("attack " + sound);
            if (killOnHit) {
                target.setHealth(0);
            }
        }

        @Override
        public void sleepPose(Mob mob, boolean asleep) {
            log.add("sleep " + asleep);
        }

        @Override
        public void vanillaSound(Mob mob, String key, float volume, float pitch) {
            log.add("sound " + key);
        }

        @Override
        public void aggressive(Mob mob, boolean aggressive) {
        }
    }

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        module = new PredatorModule();
        body = new Body();
        now = 1_000;
        module.initForTest(body, () -> now, new Random(3L));
        server.getPluginManager().registerEvents(module, plugin);
    }

    private Mob animal(String species, double x, boolean baby) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalRecord record = new AnimalRecord(species, AnimalState.spawned(true, 0, 0), 0, "", baby,
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

    private Map<String, Goal<Mob>> goals(Mob mob) {
        Map<String, Goal<Mob>> byName = new HashMap<>();
        AnimalRecord record = AnimalPdc.read(mob.getPersistentDataContainer()).orElseThrow();
        for (SpeciesBehaviour.GoalSpec g : module.behaviour().goals(mob, record)) {
            byName.put(g.goal().getKey().getNamespacedKey().getKey(), g.goal());
        }
        return byName;
    }

    /** Moves the tick count so the goals ask their question and think now. */
    private static void align(Mob mob) {
        int t = mob.getTicksLived() + 1;
        while ((t + mob.getEntityId()) % PredatorGoals.PERIOD != 0) {
            t++;
        }
        mob.setTicksLived(t);
    }

    private void hit(Entity by, Entity victim) {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(by, victim,
            EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 1.0));
    }

    @Test
    void aWolfHuntsADeerAtNightBitesItAndRestsFromHuntingAfterTheKill() {
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob deer = animal("tfc:deer", 6, false);
        body.visible.add(deer);
        Map<String, Goal<Mob>> goals = goals(wolf);
        PredatorBrain brain = module.brain(wolf);

        align(wolf);
        Goal<Mob> hunt = goals.get("predator_hunt");
        assertThat(hunt.shouldActivate()).isTrue();
        hunt.start();
        hunt.tick();
        assertThat(brain.target).isSameAs(deer);
        assertThat(brain.activity(now)).isEqualTo(PredatorActivity.FIGHT);
        assertThat(hunt.shouldStayActive()).isFalse();
        hunt.stop();

        Goal<Mob> fight = goals.get("predator_fight");
        assertThat(fight.shouldActivate()).isTrue();
        fight.start();
        // out of reach: chase at 1.15
        assertThat(body.lastSpeed).isEqualTo(PredatorMelee.CHASE_SPEED, org.assertj.core.api.Assertions.within(1e-6));
        fight.tick();
        assertThat(body.log).doesNotContain("attack tfc_wolf");

        // in reach (gap 0.35 < 0.828): one bite, then the 40-tick cooldown
        deer.teleport(new Location(world, 1.0, 64, 0));
        body.killOnHit = true;
        fight.tick();
        assertThat(body.log).containsOnlyOnce("attack tfc_wolf");
        fight.tick();
        assertThat(body.log).containsOnlyOnce("attack tfc_wolf");

        // the kill: the target is dropped with HUNTED_RECENTLY for 12000 ticks, back to the hunt, no new prey
        align(wolf);
        fight.tick();
        assertThat(brain.target).isNull();
        assertThat(brain.memory.huntedRecently(now + 11_999)).isTrue();
        assertThat(brain.activity(now)).isEqualTo(PredatorActivity.HUNT);
    }

    @Test
    void creativeAndPeacefulPlayersAreNotHunted() {
        Mob wolf = animal("tfc:wolf", 0, false);
        Player creative = player(3, GameMode.CREATIVE);
        body.visible.add(creative);
        Map<String, Goal<Mob>> goals = goals(wolf);
        Goal<Mob> hunt = goals.get("predator_hunt");
        align(wolf);
        hunt.start();
        hunt.tick();
        assertThat(module.brain(wolf).target).isNull();

        Player survivor = player(4, GameMode.SURVIVAL);
        body.visible.add(survivor);
        body.peaceful = true;
        now += PredatorGoals.SENSE_TICKS;
        align(wolf);
        hunt.tick();
        assertThat(module.brain(wolf).target).isNull();

        body.peaceful = false;
        now += PredatorGoals.SENSE_TICKS;
        align(wolf);
        hunt.tick();
        assertThat(module.brain(wolf).target).isSameAs(survivor);
    }

    @Test
    void itGivesUpBeyondEightyBlocksFromHomeAndRetreatsBelowFiveHealth() {
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob deer = animal("tfc:deer", 82, false);
        body.visible.add(deer);
        Map<String, Goal<Mob>> goals = goals(wolf);
        PredatorBrain brain = module.brain(wolf);
        brain.setTarget(deer);
        wolf.teleport(new Location(world, 81, 64, 0));
        Goal<Mob> fight = goals.get("predator_fight");
        align(wolf);
        fight.start();
        assertThat(brain.target).isNull();
        // not a kill: no HUNTED_RECENTLY
        assertThat(brain.memory.huntedRecently(now)).isFalse();

        // back home, fighting again, hurt to 4.5 health: PACIFIED for 200 ticks and the retreat
        wolf.teleport(new Location(world, 0, 64, 0));
        brain.setTarget(deer);
        wolf.setHealth(4.5);
        align(wolf);
        fight.tick();
        assertThat(brain.activity(now)).isEqualTo(PredatorActivity.AVOID);
        assertThat(goals.get("predator_retreat").shouldActivate()).isTrue();
        now += 200;
        assertThat(brain.activity(now)).isEqualTo(PredatorActivity.HUNT);
    }

    @Test
    void byDayItSleepsAtHomeAndATouchWakesAPackPredatorForGood() {
        body.dayTime = 6000;
        Mob wolf = animal("tfc:wolf", 0, false);
        Map<String, Goal<Mob>> goals = goals(wolf);
        PredatorBrain brain = module.brain(wolf);
        Goal<Mob> rest = goals.get("predator_rest");
        align(wolf);
        assertThat(rest.shouldActivate()).isTrue();
        rest.start();
        assertThat(brain.memory.sleeping()).isTrue();
        assertThat(body.log).contains("sleep true");
        assertThat(wolf.getPersistentDataContainer().has(PredatorBrain.SLEEPING)).isTrue();

        body.touching.add(player(0.2, GameMode.SURVIVAL));
        align(wolf);
        rest.tick();
        assertThat(brain.memory.sleeping()).isFalse();
        assertThat(body.log).contains("sleep false");
        // PackPredatorAi has no WAKEUP_TICKS countdown: the wolf stays awake
        body.touching.clear();
        now += 1_000_000;
        align(wolf);
        rest.tick();
        assertThat(brain.memory.sleeping()).isFalse();
    }

    @Test
    void aBearSleepsAtNightAndWakesWhenItsScheduleTurnsToHunt() {
        Mob bear = animal("tfc:black_bear", 0, false);
        Map<String, Goal<Mob>> goals = goals(bear);
        PredatorBrain brain = module.brain(bear);
        Goal<Mob> rest = goals.get("predator_rest");
        align(bear);
        assertThat(rest.shouldActivate()).isTrue();
        rest.start();
        assertThat(brain.memory.sleeping()).isTrue();
        // morning: the schedule is HUNT; the hunt goal's schedule tick wakes it
        body.dayTime = 24000 + 1000;
        assertThat(rest.shouldStayActive()).isFalse();
        Goal<Mob> hunt = goals.get("predator_hunt");
        align(bear);
        assertThat(hunt.shouldActivate()).isTrue();
        hunt.start();
        hunt.tick();
        assertThat(brain.memory.sleeping()).isFalse();
    }

    @Test
    void theAlphaIsChosenByRespectAndTheMemberTakesItsTarget() {
        Mob alpha = animal("tfc:wolf", 0, false);
        Mob member = animal("tfc:wolf", 3, false);
        Mob deer = animal("tfc:deer", 10, false);
        body.visible.add(alpha);
        body.visible.add(member);
        goals(alpha);
        Map<String, Goal<Mob>> memberGoals = goals(member);
        PredatorBrain a = module.brain(alpha);
        PredatorBrain m = module.brain(member);
        a.setRespect(8);
        m.setRespect(2);
        module.scanPack(m, now);
        assertThat(m.alpha).isSameAs(a);
        module.scanPack(a, now);
        assertThat(a.alpha).isNull();

        a.setTarget(deer);
        Goal<Mob> hunt = memberGoals.get("predator_hunt");
        align(member);
        hunt.start();
        hunt.tick();
        // the member does not see the deer itself
        assertThat(m.target).isSameAs(deer);
    }

    @Test
    void aHitWolfTargetsThePlayerAndAlertsThePack() {
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob mate = animal("tfc:wolf", 4, false);
        body.visible.add(mate);
        body.visible.add(wolf);
        goals(wolf);
        goals(mate);
        PredatorBrain mateBrain = module.brain(mate);
        mateBrain.update(PredatorBrain.with(mateBrain.memory, false, true));
        Player p = player(2, GameMode.SURVIVAL);
        hit(p, wolf);
        assertThat(module.brain(wolf).target).isSameAs(p);
        assertThat(mateBrain.target).isSameAs(p);
        assertThat(mateBrain.memory.sleeping()).isFalse();
        assertThat(mateBrain.activity(now)).isEqualTo(PredatorActivity.FIGHT);
    }

    @Test
    void wolvesHowlOnceANightAndHyenasNever() {
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob hyena = animal("tfc:hyena", 5, false);
        goals(wolf);
        goals(hyena);
        body.dayTime = 18500;
        for (int i = 0; i < 200; i++) {
            module.howl(module.brain(wolf));
            module.howl(module.brain(hyena));
        }
        assertThat(body.log.stream().filter(s -> s.startsWith("sound")).count()).isEqualTo(1);
        body.dayTime = 19500;
        module.howl(module.brain(wolf));
        assertThat(module.brain(wolf).howled).isFalse();
    }

    @Test
    void respectIsDrawnOnceAndKeptInThePdc() {
        Mob wolf = animal("tfc:wolf", 0, false);
        goals(wolf);
        int respect = module.brain(wolf).respect();
        assertThat(respect).isBetween(0, 9);
        goals(wolf);
        assertThat(module.brain(wolf).respect()).isEqualTo(respect);
        assertThat(module.brain(wolf).home()).containsExactly(0, 64, 0);
    }

    /** PAR-FAUNA-29: the dog-food test (a bone stands for the {@code tfc:dog_food} tag) and a recording conversion. */
    private final List<String> conversions = new ArrayList<>();

    private void taming() {
        module.initTamingForTest(stack -> stack.getType() == org.bukkit.Material.BONE,
            (wolf, male, baby) -> conversions.add("dog male=" + male + " baby=" + baby));
    }

    private void feed(Player player, Mob wolf) {
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEntityEvent(player, wolf,
            org.bukkit.inventory.EquipmentSlot.HAND));
    }

    @Test
    void aWolfTemptedByDogFoodIsPassiveFor400TicksAndFollowsTheHolder() {
        taming();
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob deer = animal("tfc:deer", 6, false);
        Player player = player(5, GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BONE));
        body.visible.add(deer);
        body.players.add(player);
        Map<String, Goal<Mob>> goals = goals(wolf);
        PredatorBrain brain = module.brain(wolf);
        Goal<Mob> hunt = goals.get("predator_hunt");

        align(wolf);
        hunt.start();
        hunt.tick();
        // becomePassiveIf(TEMPTING_PLAYER, 400): no attack on the deer in sight, a walk to the player at 1.2
        assertThat(brain.target).isNull();
        assertThat(brain.memory.pacified(now + 399)).isTrue();
        assertThat(brain.memory.pacified(now + 400)).isFalse();
        assertThat(body.lastSpeed).isEqualTo(1.2, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(body.log).contains("follow 1.2000000476837158");

        // the player puts the food away: after the 400 ticks the wolf hunts again
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        now += 400;
        body.players.clear();
        align(wolf);
        hunt.tick();
        assertThat(brain.target).isSameAs(deer);
    }

    @Test
    void nothingTemptsAHyenaOrAWolfHoldingTheWrongFood() {
        taming();
        Mob hyena = animal("tfc:hyena", 0, false);
        Mob wolf = animal("tfc:wolf", 10, false);
        Player player = player(4, GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BONE));
        body.players.add(player);
        Goal<Mob> hyenaHunt = goals(hyena).get("predator_hunt");
        align(hyena);
        hyenaHunt.start();
        hyenaHunt.tick();
        assertThat(module.brain(hyena).memory.pacified(now)).isFalse(); // not tamable: no temptation sensor use

        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.STICK));
        Goal<Mob> wolfHunt = goals(wolf).get("predator_hunt");
        align(wolf);
        wolfHunt.start();
        wolfHunt.tick();
        assertThat(module.brain(wolf).memory.pacified(now)).isFalse();
    }

    @Test
    void tenDailyFeedsMakeAWolfADogOfTheSameGenderAndOneADayIsAllItEats() {
        taming();
        Mob wolf = animal("tfc:wolf", 0, false);
        goals(wolf);
        PredatorBrain brain = module.brain(wolf);
        Player player = player(1, GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BONE, 20));

        feed(player, wolf);
        assertThat(brain.taming().familiarity()).isEqualTo(0.1f);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(19);
        assertThat(body.log).contains("sound minecraft:entity.generic.eat");
        // the same day: the click is taken but nothing is eaten
        feed(player, wolf);
        assertThat(brain.taming().familiarity()).isEqualTo(0.1f);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(19);

        for (int day = 2; day <= 9; day++) {
            now += 24_001;
            feed(player, wolf);
        }
        assertThat(conversions).isEmpty();
        assertThat(wolf.getPersistentDataContainer().get(PredatorBrain.FAMILIARITY,
            org.bukkit.persistence.PersistentDataType.FLOAT)).isGreaterThan(0.89f);
        now += 24_001;
        feed(player, wolf);
        // the animal's record is male (see animal()): the dog is male and, as an adult wolf, an adult
        assertThat(conversions).containsExactly("dog male=true baby=false");
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(10);
    }

    @Test
    void creativeFeedsKeepTheItemAndAHyenaIsNotFedAtAll() {
        taming();
        Mob wolf = animal("tfc:wolf", 0, false);
        Mob hyena = animal("tfc:hyena", 8, false);
        goals(wolf);
        goals(hyena);
        Player player = player(1, GameMode.CREATIVE);
        player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.BONE, 3));
        feed(player, wolf);
        assertThat(player.getInventory().getItemInMainHand().getAmount()).isEqualTo(3);
        assertThat(module.brain(wolf).taming().familiarity()).isEqualTo(0.1f);
        feed(player, hyena);
        assertThat(module.brain(hyena).taming().familiarity()).isEqualTo(0f);
    }
}
