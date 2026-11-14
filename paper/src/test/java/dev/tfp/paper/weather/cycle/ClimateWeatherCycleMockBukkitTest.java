/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.weather.cycle.WeatherCycle;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-CLIM-12 on MockBukkit with an inline scheduler: in a TFC world the vanilla weather rule goes off and the storm and
 * thunder flags follow the climate, a {@code /weather} change is undone by the next sync, the weather flag clears the
 * sky and persists in the world PDC, a player's own weather follows the climate at their position and is released in a
 * vanilla world, a vanilla world keeps its weather, and weather snow and ice never form in a TFC world.
 */
class ClimateWeatherCycleMockBukkitTest extends MockServerTest {
    private WorldMock tfc;
    private WorldMock vanilla;
    private GlobalClockHolder clock;
    private ClimateWeatherCycle cycle;
    private long rainTick;
    private long clearTick;
    private long thunderTick;

    /** Everything runs inline on the calling thread. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void region(Location location, Runnable task) {
            task.run();
        }

        @Override
        public void region(World world, int chunkX, int chunkZ, Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle regionRepeating(World world, int chunkX, int chunkZ, long initialDelayTicks, long periodTicks,
                Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean entity(Entity entity, Runnable task, Runnable retired) {
            task.run();
            return true;
        }

        @Override
        public boolean entityDelayed(Entity entity, long delayTicks, Runnable task, Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskHandle entityRepeating(Entity entity, long initialDelayTicks, long periodTicks, Runnable task,
                Runnable retired) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void global(Runnable task) {
            task.run();
        }

        @Override
        public TaskHandle globalRepeating(long initialDelayTicks, long periodTicks, Runnable task) {
            return null;
        }

        @Override
        public void cpu(Runnable task) {
            task.run();
        }

        @Override
        public Executor cpuExecutor() {
            return Runnable::run;
        }

        @Override
        public CompletableFuture<Void> io(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Executor ioExecutor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOwner(World world, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void shutdown() {
        }
    }

    @BeforeEach
    void setUp() {
        tfc = server.addSimpleWorld("tfc");
        vanilla = server.addSimpleWorld("vanilla");
        clock = new GlobalClockHolder();
        cycle = new ClimateWeatherCycle(clock, new InlineScheduler(), w -> w.getName().equals("tfc"),
                () -> server.getWorlds(), () -> server.getOnlinePlayers());
        server.getPluginManager().registerEvents(cycle, plugin);
        long seed = tfc.getSeed();
        rainTick = -1;
        clearTick = -1;
        thunderTick = -1;
        for (long t = 1_000; rainTick < 0 || clearTick < 0 || thunderTick < 0; t += 1_000) {
            WeatherCycle.Target target = ClimateWeatherCycle.target(seed, at(t), true);
            if (target.raining() && !target.thunder() && rainTick < 0) {
                rainTick = t;
            }
            if (!target.raining() && !target.thunder() && clearTick < 0) {
                clearTick = t;
            }
            if (target.raining() && target.thunder() && thunderTick < 0) {
                thunderTick = t;
            }
        }
    }

    private ClockSnapshot at(long calendarTicks) {
        return new ClockSnapshot(1, calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
    }

    private void syncAt(long calendarTicks) {
        clock.publish(calendarTicks, calendarTicks, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
        cycle.sync();
    }

    @Test
    void vanillaWeatherRuleGoesOffInTfcWorldsOnly() {
        GameRule<Boolean> rule = ClimateWeatherCycle.weatherRule();
        assertThat(rule).as("the vanilla weather cycle rule").isNotNull();
        tfc.setGameRule(rule, true);
        vanilla.setGameRule(rule, true);
        syncAt(clearTick);
        assertThat(tfc.getGameRuleValue(rule)).isFalse();
        assertThat(vanilla.getGameRuleValue(rule)).isTrue();
    }

    @Test
    void stormAndThunderFollowTheClimate() {
        syncAt(rainTick);
        assertThat(tfc.hasStorm()).isTrue();
        assertThat(tfc.isThundering()).isFalse();
        syncAt(thunderTick);
        assertThat(tfc.hasStorm()).isTrue();
        assertThat(tfc.isThundering()).isTrue();
        syncAt(clearTick);
        assertThat(tfc.hasStorm()).isFalse();
        assertThat(tfc.isThundering()).isFalse();
    }

    @Test
    void weatherCommandIsUndoneOnTheNextSync() {
        syncAt(rainTick);
        tfc.setStorm(false); // /weather clear
        syncAt(rainTick + 20);
        assertThat(tfc.hasStorm()).isTrue();
        syncAt(clearTick);
        tfc.setStorm(true); // /weather rain
        tfc.setThundering(true); // /weather thunder
        syncAt(clearTick + 20);
        assertThat(tfc.hasStorm()).isFalse();
        assertThat(tfc.isThundering()).isFalse();
    }

    @Test
    void vanillaWorldKeepsItsWeather() {
        vanilla.setStorm(true);
        syncAt(clearTick);
        assertThat(vanilla.hasStorm()).isTrue();
        vanilla.setStorm(false);
        syncAt(rainTick);
        assertThat(vanilla.hasStorm()).isFalse();
    }

    @Test
    void disabledWeatherClearsTheSkyAndPersists() {
        assertThat(cycle.enabled(tfc)).isTrue();
        cycle.setEnabled(tfc, false);
        syncAt(thunderTick);
        assertThat(tfc.hasStorm()).isFalse();
        assertThat(tfc.isThundering()).isFalse();
        assertThat(tfc.getPersistentDataContainer().get(ClimateWeatherCycle.ENABLED_KEY, PersistentDataType.BOOLEAN))
                .isFalse();
        ClimateWeatherCycle reloaded = new ClimateWeatherCycle(clock, new InlineScheduler(), w -> true,
                () -> server.getWorlds(), () -> server.getOnlinePlayers());
        assertThat(reloaded.enabled(tfc)).as("read back from the world PDC").isFalse();
        cycle.setEnabled(tfc, true);
        syncAt(thunderTick);
        assertThat(tfc.hasStorm()).isTrue();
    }

    @Test
    void playerWeatherFollowsTheClimateAtTheirPosition() {
        BlockPos wet = findSite(true);
        BlockPos dry = findSite(false);
        PlayerMock inWet = server.addPlayer("wet");
        PlayerMock inDry = server.addPlayer("dry");
        inWet.teleport(new Location(tfc, wet.x() + 0.5, wet.y(), wet.z() + 0.5));
        inDry.teleport(new Location(tfc, dry.x() + 0.5, dry.y(), dry.z() + 0.5));

        syncAt(rainTick);
        assertThat(inWet.getPlayerWeather()).isEqualTo(WeatherType.DOWNFALL);
        assertThat(inDry.getPlayerWeather()).as("no rain drawn at a dry site while it rains elsewhere")
                .isEqualTo(WeatherType.CLEAR);

        syncAt(clearTick);
        assertThat(inWet.getPlayerWeather()).isEqualTo(WeatherType.CLEAR);

        // Into a vanilla world: the player's own weather is released at once
        inWet.teleport(new Location(vanilla, 0.5, 80, 0.5));
        cycle.refresh(inWet);
        assertThat(inWet.getPlayerWeather()).isNull();
        assertThat(cycle.manages(inWet.getUniqueId())).isFalse();
    }

    @Test
    void localWeatherIsClearWithWeatherDisabled() {
        BlockPos wet = findSite(true);
        ClimateWeatherCycle.PlayerCapture on = new ClimateWeatherCycle.PlayerCapture(tfc.getUID(), tfc.getSeed(), wet, true);
        ClimateWeatherCycle.PlayerCapture off = new ClimateWeatherCycle.PlayerCapture(tfc.getUID(), tfc.getSeed(), wet, false);
        assertThat(ClimateWeatherCycle.localWeather(on, at(rainTick))).isEqualTo(WeatherType.DOWNFALL);
        assertThat(ClimateWeatherCycle.localWeather(off, at(rainTick))).isEqualTo(WeatherType.CLEAR);
        assertThat(ClimateWeatherCycle.localWeather(on, ClockSnapshot.UNSET)).isEqualTo(WeatherType.CLEAR);
    }

    @Test
    void weatherSnowAndIceNeverFormInTfcWorlds() {
        VanillaWeatherGates gates = new VanillaWeatherGates(cycle, clock, new InlineScheduler());
        assertThat(formCancelled(gates, tfc, Material.SNOW, false)).isTrue();
        assertThat(formCancelled(gates, tfc, Material.ICE, false)).isTrue();
        assertThat(formCancelled(gates, tfc, Material.OBSIDIAN, false)).as("not weather").isFalse();
        assertThat(formCancelled(gates, tfc, Material.SNOW, true)).as("a snow golem's trail").isFalse();
        assertThat(formCancelled(gates, vanilla, Material.SNOW, false)).as("vanilla world").isFalse();
    }

    @Test
    void precipitationFillsAreRainAndPowderSnowCauldronsOnly() {
        assertThat(VanillaWeatherGates.isPrecipitationFill(Material.WATER_CAULDRON)).isTrue();
        assertThat(VanillaWeatherGates.isPrecipitationFill(Material.POWDER_SNOW_CAULDRON)).isTrue();
        assertThat(VanillaWeatherGates.isPrecipitationFill(Material.LAVA_CAULDRON)).as("dripstone lava").isFalse();
        assertThat(VanillaWeatherGates.isWeatherForm(Material.STONE)).isFalse();
    }

    @Test
    void lightningAndFireAskTheClimateForRainNotSnow() {
        BlockPos wet = findSite(true);
        BlockPos dry = findSite(false);
        ClockSnapshot rain = at(rainTick);
        boolean wetIsRain = LocalRain.at(tfc.getSeed(), wet, rain, true, true) == PrecipitationType.RAIN;
        assertThat(VanillaWeatherGates.anyRain(tfc.getSeed(), new BlockPos[] {dry, wet}, new boolean[] {true, true}, rain,
                true)).isEqualTo(wetIsRain);
        assertThat(VanillaWeatherGates.anyRain(tfc.getSeed(), new BlockPos[] {wet}, new boolean[] {false}, rain, true))
                .as("rain does not reach the position").isFalse();
        assertThat(VanillaWeatherGates.anyRain(tfc.getSeed(), new BlockPos[] {wet}, new boolean[] {true}, rain, false))
                .as("weather disabled").isFalse();
        assertThat(VanillaWeatherGates.anyRain(tfc.getSeed(), new BlockPos[] {dry}, new boolean[] {true}, rain, true))
                .isFalse();
    }

    private boolean formCancelled(VanillaWeatherGates gates, WorldMock world, Material to, boolean entity) {
        Block block = world.getBlockAt(3, 70, 3);
        block.setType(Material.AIR);
        BlockState state = block.getState();
        state.setType(to);
        BlockFormEvent event = entity ? new EntityBlockFormEvent(server.addPlayer(), block, state)
                : new BlockFormEvent(block, state);
        gates.onForm(event);
        return event.isCancelled();
    }

    /** A sea-level column where the climate precipitates ({@code wet}) or not at {@link #rainTick}. */
    private BlockPos findSite(boolean wet) {
        ClockSnapshot rain = at(rainTick);
        for (int i = 0; i < 400; i++) {
            BlockPos pos = new BlockPos((i % 20) * 1_000 - 10_000, 64, (i / 20) * 1_000 - 10_000);
            boolean precipitates = LocalRain.at(tfc.getSeed(), pos, rain, true, true) != PrecipitationType.NONE;
            if (precipitates == wet) {
                return pos;
            }
        }
        throw new AssertionError("no " + (wet ? "wet" : "dry") + " site found for seed " + tfc.getSeed() + " at "
                + List.of(rainTick));
    }
}
