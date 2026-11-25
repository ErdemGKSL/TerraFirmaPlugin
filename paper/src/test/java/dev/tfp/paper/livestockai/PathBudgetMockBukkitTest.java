/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.destroystokyo.paper.entity.ai.Goal;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.prey.PathBudget;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-33: the per-tick path budget. A herd whose goals all want a path in the same tick computes at most
 * {@link PathBudget#PER_TICK} paths per tick, every deferred goal starts on a later tick, and the targets and speeds are
 * the ones the goals would have used without the budget.
 */
class PathBudgetMockBukkitTest extends MockServerTest {
    private static final long START = 100_000_000L;
    private static final int HERD = 50;

    private World world;
    private LivestockAiModule module;
    private Body body;
    private long now;

    /** Records every path start with the tick it was computed in. */
    private final class Body implements LivestockBody {
        final List<LivingEntity> visible = new ArrayList<>();
        final Map<Long, Integer> pathsPerTick = new HashMap<>();
        final Map<Mob, Entity> followed = new LinkedHashMap<>();
        final List<Double> speeds = new ArrayList<>();
        final Set<Mob> pathing = new java.util.HashSet<>();

        private void path(Mob mob, double speed) {
            pathsPerTick.merge(now, 1, Integer::sum);
            speeds.add(speed);
            pathing.add(mob);
        }

        @Override
        public List<LivingEntity> nearby(Mob mob, double range, long t) {
            return visible.stream().filter(e -> e != mob).toList();
        }

        @Override
        public boolean sees(Mob mob, LivingEntity e) {
            return true;
        }

        @Override
        public List<Player> players(Mob mob, double range) {
            return List.of();
        }

        @Override
        public Location standable(Mob mob, int dx, int dy, int dz) {
            return mob.getLocation().getBlock().getLocation().add(dx + 0.5, 0, dz + 0.5);
        }

        @Override
        public boolean moveTo(Mob mob, Location to, double speed) {
            path(mob, speed);
            return true;
        }

        @Override
        public boolean moveTo(Mob mob, Entity to, double speed) {
            path(mob, speed);
            followed.put(mob, to);
            return true;
        }

        @Override
        public void stop(Mob mob) {
            pathing.remove(mob);
        }

        @Override
        public boolean hasPath(Mob mob) {
            return pathing.contains(mob);
        }

        @Override
        public void lookAt(Mob mob, Entity at) {
        }

        @Override
        public boolean inWater(Mob mob) {
            return false;
        }

        @Override
        public void swimUp(Mob mob, double speed) {
        }
    }

    @BeforeEach
    void setUp() {
        dev.tfp.paper.prey.Ticks.forTest(() -> now); // the sensing and path-budget tick follows the test clock
        world = server.addSimpleWorld("w");
        module = new LivestockAiModule();
        body = new Body();
        now = START;
        PathBudget.limitForTest(PathBudget.PER_TICK);
        module.initForTest(body, () -> new ClockSnapshot(1L, now, now, 8, true), (species, tag) -> false,
                species -> stack -> stack.getType() == Material.WHEAT, new Random(5L), (mob, key) -> { });
    }

    @AfterEach
    void tearDown() {
        PathBudget.limitForTest(PathBudget.PER_TICK);
        dev.tfp.paper.prey.Ticks.forTest(null);
    }

    private Mob cow(double x, boolean baby) {
        Cow cow = world.spawn(new Location(world, x, 64, 0), Cow.class);
        AnimalState s = AnimalState.spawned(false, baby ? now : 0, now);
        AnimalPdc.write(cow.getPersistentDataContainer(), new AnimalRecord("tfc:cow", s, 0, "", false,
                AnimalRecord.NO_GROW_TICK));
        return cow;
    }

    private Goal<Mob> goal(Mob mob, String name) {
        AnimalRecord r = AnimalPdc.read(mob.getPersistentDataContainer()).orElseThrow();
        for (SpeciesBehaviour.GoalSpec g : module.brainBehaviour().goals(mob, r)) {
            if (g.goal().getKey().getNamespacedKey().getKey().equals(name)) {
                return g.goal();
            }
        }
        throw new AssertionError(name);
    }

    /** Sets the tick count so that {@code (ticksLived + id) % period == 0} at the next call. */
    private static void align(Mob mob, int period) {
        int t = mob.getTicksLived() + 1;
        while ((t + mob.getEntityId()) % period != 0) {
            t++;
        }
        mob.setTicksLived(t);
    }

    @Test
    void aHerdOfFiftyFollowingInOneTickComputesAtMostTheBudgetPerTickAndEveryCalfStarts() {
        Mob mother = cow(0, false);
        body.visible.add(mother);
        List<Mob> calves = new ArrayList<>();
        List<Goal<Mob>> goals = new ArrayList<>();
        for (int i = 0; i < HERD; i++) {
            Mob calf = cow(8 + (i % 5) * 0.5, true); // 8 to 10 blocks: between 5 and 16, so each follows
            calves.add(calf);
            goals.add(goal(calf, "livestock_follow_adult"));
            body.visible.add(calf);
        }
        // every calf decides in the same tick and re-paths in the same tick (the worst case for the budget)
        for (int i = 0; i < HERD; i++) {
            align(calves.get(i), 10);
            assertThat(goals.get(i).shouldActivate()).isTrue();
            goals.get(i).start();
        }
        int ticks = 0;
        while (body.followed.size() < HERD && ticks < 100) {
            for (Goal<Mob> g : goals) {
                g.tick();
            }
            for (Mob calf : calves) {
                calf.setTicksLived(calf.getTicksLived() + 1);
            }
            now++;
            ticks++;
        }
        assertThat(body.followed).as("every calf eventually starts its path").hasSize(HERD);
        assertThat(body.followed.values()).containsOnly(mother);
        assertThat(body.pathsPerTick.values()).as("paths per tick").allMatch(n -> n <= PathBudget.PER_TICK);
        assertThat(body.pathsPerTick.get(START)).isEqualTo(PathBudget.PER_TICK);
        // 3 first starts per tick, and at the 10-tick re-path the refreshes of the calves already walking leave one token
        assertThat(ticks).as("50 starts at %d per tick", PathBudget.PER_TICK)
                .isLessThanOrEqualTo(2 * ((HERD + PathBudget.PER_TICK - 1) / PathBudget.PER_TICK));
        assertThat(body.speeds).allSatisfy(v -> assertThat(v).isEqualTo(1.25, within(1e-6)));
    }

    @Test
    void idleStrollsOverTheBudgetAnswerFalseAndStartOnALaterPeriod() {
        List<Mob> herd = new ArrayList<>();
        List<Goal<Mob>> idles = new ArrayList<>();
        for (int i = 0; i < HERD; i++) {
            Mob cow = cow(i * 2.0, false);
            herd.add(cow);
            idles.add(goal(cow, "livestock_idle"));
        }
        java.util.Set<Integer> started = new java.util.HashSet<>();
        for (int round = 0; round < 60 && started.size() < HERD; round++) {
            for (int i = 0; i < HERD; i++) {
                if (started.contains(i)) {
                    continue;
                }
                align(herd.get(i), 20);
                if (idles.get(i).shouldActivate()) {
                    started.add(i); // a stroll with its path, or a wait in place
                }
            }
            now++;
        }
        assertThat(started).as("every idle goal eventually starts").hasSize(HERD);
        assertThat(body.pathsPerTick.values()).as("paths per tick").allMatch(n -> n <= PathBudget.PER_TICK);
        assertThat(PathBudget.deferred()).isPositive();
    }

    @Test
    void urgentStartsAreNeverRefusedButUseTheTicksTokens() {
        for (int i = 0; i < PathBudget.PER_TICK + 2; i++) {
            PathBudget.urgent(START);
        }
        assertThat(PathBudget.tryStart(START)).isFalse();
        assertThat(PathBudget.mayStart(START)).isFalse();
        assertThat(PathBudget.tryStart(START + 1)).as("a new tick has fresh tokens").isTrue();
    }
}
