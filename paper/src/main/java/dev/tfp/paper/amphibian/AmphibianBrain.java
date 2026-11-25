/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/amphibian/AmphibianAi.java @ c3b4d9536 (initCoreActivity,
 * initIdleActivity, initFightActivity, getAttackTarget), ai/amphibian/PinnipedAI.java (initRetreatActivity,
 * createIdleMovementBehaviors), ai/amphibian/AmphibianPlayDeadBehavior.java (start), aquatic/AmphibiousAnimal.java
 * (customServerAiStep, doHurtTarget, finalizeSpawn), same commit
 */
package dev.tfp.paper.amphibian;

import dev.tfp.core.amphibian.AmphibianActivity;
import dev.tfp.core.amphibian.AmphibianIdle;
import dev.tfp.core.amphibian.AmphibianMemory;
import dev.tfp.core.amphibian.AmphibianSpecies;
import dev.tfp.core.amphibian.AmphibiousEscape;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.aquatic.PlayDeadTimer;
import dev.tfp.core.predator.PredatorMelee;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The brain of one turtle, penguin or leopard seal: the {@link AmphibianMemory} plus the entities and waits the Paper glue
 * keeps. {@link #think} is TFC's {@code customServerAiStep}: the behaviours of the current activity, then the countdown,
 * the activity choice and the playing-dead flag. Owning thread only.
 */
final class AmphibianBrain {
    /** {@code NEAREST_LIVING_ENTITIES}: 16 blocks, refreshed every 20 ticks as vanilla sensors are. */
    static final double SENSE_RANGE = 16.0;
    static final int SENSOR_TICKS = 20;
    /** {@code LookAtTargetSink(45, 90)}. */
    static final int LOOK_MIN = 45;
    static final int LOOK_MAX = 90;
    /** {@code StopAttackingIfTargetInvalid}: give up a target unreachable for longer than this. */
    static final int UNREACHABLE_TICKS = 200;
    /** The chase re-paths at most this often while a path exists. */
    static final int CHASE_REPATH_TICKS = 10;

    /** The world, the tags, the records and the draws. */
    record Deps(AmphibianBody body, BiPredicate<String, String> speciesInTag, Function<Entity, Optional<AnimalRecord>> records,
            Predicate<ItemStack> food, RandomGenerator random) {
    }

    /** One {@code GateBehavior} instance: when it can restart and its own home-stroll wait. */
    static final class GateState {
        long restartAt;
        long homeWaitAt;
    }

    final Mob mob;
    final AmphibianSpecies species;
    final Deps d;
    private final AmphibianIdle.Draws draws;

    AmphibianMemory memory = AmphibianMemory.FRESH;
    LivingEntity attacker;
    LivingEntity target;
    Entity lookTarget;
    long lookUntil;
    int lookTicker;
    Player tempting;
    boolean tempted;
    long temptCooldownUntil = Long.MIN_VALUE;
    final GateState idleGate = new GateState();
    final GateState avoidGate = new GateState();
    long avoidHomeWaitAt;
    long findWaterWaitAt;
    long attackCoolingUntil = Long.MIN_VALUE;
    long unreachableSince = Long.MIN_VALUE;
    Location home;
    Location walkTo;
    double walkSpeed = Double.NaN;
    boolean playDeadStarted;
    boolean pose;
    boolean baby;
    long lastTick = Long.MIN_VALUE;
    long sensedAt = Long.MIN_VALUE;
    List<LivingEntity> seen = List.of();

    AmphibianBrain(Mob mob, AmphibianSpecies species, Deps d) {
        this.mob = mob;
        this.species = species;
        this.d = d;
        this.draws = new AmphibianIdle.Draws() {
            @Override
            public int nextInt(int bound) {
                return d.random().nextInt(bound);
            }

            @Override
            public float nextFloat() {
                return d.random().nextFloat();
            }
        };
    }

    // ---- the tick -------------------------------------------------------------------------------------------------

    /** One server tick at game time {@code now}; missed ticks run the countdown only (catch-up, at most 200). */
    void think(long now) {
        if (now == lastTick) {
            return;
        }
        long dt = lastTick == Long.MIN_VALUE || now < lastTick ? 1 : Math.min(now - lastTick, PlayDeadTimer.PLAY_DEAD_TIME + 1);
        lastTick = now;
        boolean inWater = d.body().inWater(mob);
        sense(now);
        behave(now, inWater);
        for (long i = 0; i < dt; i++) {
            memory = memory.tick(species, inWater);
        }
        if (memory.activity() != AmphibianActivity.PLAY_DEAD) {
            playDeadStarted = false;
        }
        if (memory.activity() != AmphibianActivity.FIGHT) {
            target = null;
        }
        if (!memory.hurtBy()) {
            attacker = null;
        }
        boolean dead = memory.playingDead();
        if (dead != pose) {
            pose = dead;
            d.body().playDeadPose(mob, dead);
        }
        if (dead && d.body().hasPath(mob)) {
            // AmphibianMoveControl does not tick and travel keeps no input while playing dead
            d.body().stop(mob);
        }
    }

    /** {@code AmphibiousAnimal.hurt} and the {@code HURT_BY} sensor. */
    void hurt(float amount, boolean alive, LivingEntity by) {
        if (by != null) {
            attacker = by;
        }
        memory = memory.hurt(species, amount, alive, by != null);
    }

    private void sense(long now) {
        if (sensedAt != Long.MIN_VALUE && now - sensedAt < SENSOR_TICKS && now >= sensedAt) {
            return;
        }
        sensedAt = now;
        seen = d.body().visible(mob, SENSE_RANGE);
        baby = d.records().apply(mob).map(AnimalRecord::baby).orElse(false);
        // DelegatingTemptingSensor: the nearest non-spectator player within 10 holding the food in either hand
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (LivingEntity e : seen) {
            if (e instanceof Player p && p.getGameMode() != GameMode.SPECTATOR && holdsFood(p)) {
                double sq = p.getLocation().distanceSquared(mob.getLocation());
                if (sq < AmphibianIdle.TEMPTATION_RANGE * AmphibianIdle.TEMPTATION_RANGE && sq < bestSq) {
                    best = p;
                    bestSq = sq;
                }
            }
        }
        tempting = best;
        // HurtBySensor: forget an attacker that is dead or in another level
        if (attacker != null && (attacker.isDead() || !attacker.isValid() || !attacker.getWorld().equals(mob.getWorld()))) {
            attacker = null;
            memory = memory.attackerGone();
        }
    }

    private boolean holdsFood(Player p) {
        return d.food().test(p.getInventory().getItemInMainHand()) || d.food().test(p.getInventory().getItemInOffHand());
    }

    private void behave(long now, boolean inWater) {
        // CORE: LookAtTargetSink
        if (lookTarget != null) {
            if (now > lookUntil || !lookTarget.isValid() || !lookTarget.getWorld().equals(mob.getWorld())) {
                lookTarget = null;
            } else {
                d.body().lookAt(mob, lookTarget);
            }
        }
        switch (memory.activity()) {
            case PLAY_DEAD -> playDead();
            case FIGHT -> fight(now, inWater);
            case AVOID -> avoid(now, inWater);
            case IDLE -> idle(now, inWater);
        }
    }

    // ---- PLAY_DEAD ------------------------------------------------------------------------------------------------

    /** {@code AmphibianPlayDeadBehavior.start}: needs the countdown and the attacker; stops walking and looking, gives the effects. */
    private void playDead() {
        if (playDeadStarted || !memory.hurtBy()) {
            return;
        }
        playDeadStarted = true;
        d.body().stop(mob);
        walkTo = null;
        lookTarget = null;
        List<PlayDeadTimer.StatusEffect> effects = PlayDeadTimer.startEffects(species.playingDeadEffective());
        if (!effects.isEmpty()) {
            d.body().effects(mob, effects);
        }
    }

    // ---- FIGHT ----------------------------------------------------------------------------------------------------

    private void fight(long now, boolean inWater) {
        LivingEntity t = target;
        boolean invalid = t == null || t.isDead() || !t.isValid() || !t.getWorld().equals(mob.getWorld())
                || (unreachableSince != Long.MIN_VALUE && now - unreachableSince > UNREACHABLE_TICKS);
        if (invalid) {
            stopAttacking();
            return;
        }
        boolean visible = seen.contains(t);
        boolean inRange = PredatorMelee.withinMeleeRange(d.body().box(mob), d.body().box(t));
        if (PredatorMelee.chases(visible, inRange)) {
            if (!d.body().hasPath(mob) || (now + mob.getEntityId()) % CHASE_REPATH_TICKS == 0) {
                if (d.body().moveTo(mob, t, AmphibianSpecies.chaseSpeed(inWater))) {
                    unreachableSince = Long.MIN_VALUE;
                } else if (unreachableSince == Long.MIN_VALUE) {
                    unreachableSince = now;
                }
            }
        } else {
            d.body().stop(mob);
        }
        if (PredatorMelee.attacks(now, attackCoolingUntil, visible, inRange)) {
            d.body().attack(mob, t);
            attackCoolingUntil = now + AmphibianIdle.MELEE_COOLDOWN;
            if (t.isDead() || t.getHealth() <= 0) {
                memory = memory.killed(now);
            }
        }
        // EraseMemoryIf(isTempted, ATTACK_TARGET)
        if (tempting != null) {
            stopAttacking();
        }
    }

    private void stopAttacking() {
        target = null;
        unreachableSince = Long.MIN_VALUE;
        memory = memory.stopAttacking();
    }

    // ---- AVOID (leopard seal) -------------------------------------------------------------------------------------

    private void avoid(long now, boolean inWater) {
        // triggerIf(hasNearbyAttacker, AmphibiousSetWalkTargetAwayFrom.entity(HURT_BY_ENTITY, flee speed, 16, true))
        LivingEntity a = attacker;
        if (a != null && memory.hurtBy() && a.getWorld().equals(mob.getWorld())
                && AmphibiousEscape.attackerNearby(a.getLocation().distanceSquared(mob.getLocation()))) {
            float speed = AmphibianSpecies.fleeSpeed(inWater);
            AmphibiousEscape.Vec here = vec(mob.getLocation());
            AmphibiousEscape.Vec threat = vec(a.getLocation());
            AmphibiousEscape.Vec current = walkTo != null && d.body().hasPath(mob) ? vec(walkTo) : null;
            if (AmphibiousEscape.shouldPick(here, threat, current, walkSpeed == speed)) {
                boolean ocean = d.records().apply(a).map(r -> d.speciesInTag().test(ours(r.species()), "tfp:ocean_predators"))
                        .orElse(false);
                Location from = a.getLocation();
                AmphibiousEscape.pick(here, threat, ocean, () -> d.body().awayFrom(mob, from))
                        .ifPresent(v -> walk(new Location(mob.getWorld(), v.x(), v.y(), v.z()), speed));
            }
        }
        // AmphibiousStrollToPoi(HOME, speed, 5, MAX_WANDER_DISTANCE)
        AmphibianIdle.HomeStroll<Location> h = AmphibianIdle.strollHome(now, avoidHomeWaitAt,
                AmphibianIdle.PINNIPED_RETREAT_HOME_MAX, inWater, world(inWater));
        avoidHomeWaitAt = h.waitAt();
        if (h.walk() != null) {
            walk(h.walk().target(), h.walk().speed());
        }
        idleMovement(now, inWater, avoidGate);
    }

    // ---- IDLE -----------------------------------------------------------------------------------------------------

    private void idle(long now, boolean inWater) {
        // 0: SetLookTarget(TURTLE_FRIENDS, 6, 30..60)
        if (lookTarget == null) {
            LivingEntity friend = nearest(e -> friend(e), AmphibianIdle.LOOK_RANGE);
            if (friend != null) {
                AmphibianIdle.Ticked t = AmphibianIdle.lookTicker(lookTicker, draws);
                lookTicker = t.ticksUntilNextStart();
                if (t.fire()) {
                    lookTarget = friend;
                    lookUntil = now + AmphibianIdle.between(draws, LOOK_MIN, LOOK_MAX);
                }
            }
        }
        // 2: RunOne(BabyFollowAdult, FollowTemptation)
        if (tempted) {
            followTemptation(now, inWater);
        } else {
            boolean temptFirst = AmphibianIdle.temptationFirst(draws);
            if (temptFirst) {
                if (!startTemptation(now, inWater)) {
                    followAdult(inWater);
                }
            } else if (!followAdult(inWater)) {
                startTemptation(now, inWater);
            }
        }
        // 3: TryFindWater(6, 0.15)
        if (!memory.attackTarget() && !d.body().hasPath(mob) && !d.body().waterAt(mob.getLocation())) {
            AmphibianIdle.FoundWater f = AmphibianIdle.findWater(now, findWaterWaitAt, d.body().waterProbe(mob));
            findWaterWaitAt = f.waitAt();
            f.offset().ifPresent(o -> {
                Location base = mob.getLocation();
                walk(new Location(mob.getWorld(), base.getBlockX() + o[0] + 0.5, base.getBlockY() + o[1],
                        base.getBlockZ() + o[2] + 0.5), AmphibianIdle.FIND_WATER_SPEED);
            });
        }
        // 4: the idle-movement gate
        idleMovement(now, inWater, idleGate);
    }

    private boolean followAdult(boolean inWater) {
        LivingEntity adult = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : seen) {
            Optional<AnimalRecord> r = d.records().apply(e);
            if (r.isPresent() && r.get().species().equals(species.id()) && !r.get().baby()) {
                double dist = e.getLocation().distance(mob.getLocation());
                if (dist < best) {
                    best = dist;
                    adult = e;
                }
            }
        }
        if (adult == null || !AmphibianIdle.followsAdult(baby, best)) {
            return false;
        }
        d.body().moveTo(mob, adult, AmphibianSpecies.chaseSpeed(inWater));
        walkTo = adult.getLocation();
        walkSpeed = AmphibianSpecies.chaseSpeed(inWater);
        return true;
    }

    private boolean startTemptation(long now, boolean inWater) {
        if (tempting == null || now < temptCooldownUntil) {
            return false;
        }
        tempted = true;
        followTemptation(now, inWater);
        return true;
    }

    /** Vanilla {@code FollowTemptation}: walk to the player until 2.5 blocks; 100 ticks of cooldown once it stops. */
    private void followTemptation(long now, boolean inWater) {
        Player p = tempting;
        if (p == null || !p.isValid() || !p.getWorld().equals(mob.getWorld())) {
            tempted = false;
            temptCooldownUntil = now + AmphibianIdle.TEMPTATION_COOLDOWN;
            d.body().stop(mob);
            walkTo = null;
            lookTarget = null;
            return;
        }
        d.body().lookAt(mob, p);
        if (p.getLocation().distanceSquared(mob.getLocation()) < AmphibianIdle.TEMPT_CLOSE_ENOUGH * AmphibianIdle.TEMPT_CLOSE_ENOUGH) {
            d.body().stop(mob);
            walkTo = null;
        } else {
            d.body().moveTo(mob, p, AmphibianSpecies.walkSpeed(inWater));
            walkTo = p.getLocation();
            walkSpeed = AmphibianSpecies.walkSpeed(inWater);
        }
    }

    private void idleMovement(long now, boolean inWater, GateState gate) {
        if (now < gate.restartAt || d.body().hasPath(mob)) {
            return;
        }
        AmphibianIdle.Gate<Location> g = AmphibianIdle.gate(inWater, memory.huntedRecently(now), now, gate.homeWaitAt,
                AmphibianIdle.HOME_MAX_DISTANCE, world(inWater), draws);
        if (g.attack()) {
            LivingEntity fish = nearest(this::smallFish, SENSE_RANGE);
            if (fish != null && !memory.attackTarget()) {
                target = fish;
                memory = memory.startAttacking();
            }
        }
        if (g.walk() != null) {
            walk(g.walk().target(), g.walk().speed());
        }
        gate.homeWaitAt = g.homeWaitAt();
        gate.restartAt = AmphibianIdle.gateRestartAt(now, g.busyTicks());
    }

    private AmphibianIdle.GateWorld<Location> world(boolean inWater) {
        return new AmphibianIdle.GateWorld<>() {
            @Override
            public boolean smallFishVisible() {
                return nearest(AmphibianBrain.this::smallFish, SENSE_RANGE) != null;
            }

            @Override
            public Optional<Location> swimTarget() {
                return d.body().randomSwim(mob);
            }

            @Override
            public Optional<Location> strollTarget() {
                return d.body().randomStroll(mob);
            }

            @Override
            public Optional<Location> lookTargetInSameMedium() {
                Entity look = lookTarget;
                if (look == null) {
                    return Optional.empty();
                }
                Location at = look.getLocation();
                return d.body().waterAt(at) == inWater ? Optional.of(at.getBlock().getLocation().add(0.5, 0, 0.5))
                        : Optional.empty();
            }

            @Override
            public Optional<Location> home(int maxDistance) {
                Location h = home;
                if (h == null || !h.getWorld().equals(mob.getWorld())) {
                    return Optional.empty();
                }
                Location centre = h.getBlock().getLocation().add(0.5, 0.5, 0.5);
                return centre.distanceSquared(mob.getLocation()) < (double) maxDistance * maxDistance
                        ? Optional.of(centre) : Optional.empty();
            }
        };
    }

    private void walk(Location to, double speed) {
        d.body().moveTo(mob, to, speed);
        walkTo = to;
        walkSpeed = speed;
    }

    // ---- senses ---------------------------------------------------------------------------------------------------

    private LivingEntity nearest(Predicate<LivingEntity> filter, double range) {
        LivingEntity best = null;
        double bestSq = range * range;
        for (LivingEntity e : seen) {
            if (!e.isDead() && filter.test(e)) {
                double sq = e.getLocation().distanceSquared(mob.getLocation());
                if (sq <= bestSq) {
                    best = e;
                    bestSq = sq;
                }
            }
        }
        return best;
    }

    /** {@code #tfc:turtle_friends}: players and dolphins. */
    private boolean friend(LivingEntity e) {
        if (e instanceof Player) {
            return d.speciesInTag().test("minecraft:player", "tfp:turtle_friends");
        }
        return d.records().apply(e).map(r -> d.speciesInTag().test(ours(r.species()), "tfp:turtle_friends")).orElse(false);
    }

    /** {@code #tfc:small_fish}, alive. */
    boolean smallFish(LivingEntity e) {
        return d.records().apply(e).map(r -> d.speciesInTag().test(ours(r.species()), "tfp:small_fish")).orElse(false);
    }

    /** TFC's {@code tfc:} ids are {@code tfp:} in our tags. */
    static String ours(String id) {
        return "tfp:" + id.substring(id.indexOf(':') + 1);
    }

    private static AmphibiousEscape.Vec vec(Location l) {
        return new AmphibiousEscape.Vec(l.getX(), l.getY(), l.getZ());
    }
}
