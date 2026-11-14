/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/mixin/ServerLevelMixin.java @ c3b4d9536 (tickPrecipitation cancelled)
 * Also: src/main/java/net/dries007/tfc/mixin/LevelMixin.java @ c3b4d9536 (isRainingAt asks the climate)
 */
package dev.tfp.paper.weather.cycle;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.concurrent.CompletableFuture;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.weather.LightningStrikeEvent;

/**
 * PAR-CLIM-12: the vanilla weather effects TFC switches off or routes through the climate, in TFC overworlds only.
 * <ul>
 *   <li><b>Precipitation ticking off</b> (TFC cancels {@code ServerLevel.tickPrecipitation}): snow layers and ice that
 *       vanilla's weather forms ({@link BlockFormEvent} to snow or ice, never an entity's) and cauldrons that rain or snow
 *       fills ({@link CauldronLevelChangeEvent} {@code NATURAL_FILL} to a water or powder snow cauldron at the top of its
 *       column; a dripstone drip has the dripstone above it) are cancelled. Our own snow and ice
 *       ({@code SnowService}) are direct writes and never fire these events.</li>
 *   <li><b>{@code Level.isRainingAt} consumers</b> (TFC asks the climate there): a fire that vanilla puts out with rain
 *       and a weather lightning strike are held back, decided by the climate on a worker, and applied on the block's
 *       region only where the climate rains there. Consumers without a Paper decision point are Divergences rows.</li>
 * </ul>
 * Handlers only read the event, snapshot plain values and enqueue (decide async, apply sync).
 */
public final class VanillaWeatherGates implements Listener {
    private final ClimateWeatherCycle cycle;
    private final GlobalClockHolder clock;
    private final Scheduler scheduler;

    public VanillaWeatherGates(ClimateWeatherCycle cycle, GlobalClockHolder clock, Scheduler scheduler) {
        this.cycle = cycle;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    // ---- precipitation ticking -----------------------------------------------------------------------

    /** Vanilla weather snow layers and ice. Entity forms (snow golems, frost walker) are not weather. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (event instanceof EntityBlockFormEvent) {
            return;
        }
        if (isWeatherForm(event.getNewState().getType()) && cycle.controls(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** What vanilla's {@code tickPrecipitation} forms: snow layers and ice. */
    static boolean isWeatherForm(Material to) {
        return to == Material.SNOW || to == Material.ICE;
    }

    /** Vanilla rain or snow filling a cauldron. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        if (event.getReason() != CauldronLevelChangeEvent.ChangeReason.NATURAL_FILL) {
            return;
        }
        Block block = event.getBlock();
        if (!isPrecipitationFill(event.getNewState().getType())) {
            return;
        }
        World world = block.getWorld();
        if (cycle.controls(world) && atColumnTop(world, block)) {
            event.setCancelled(true);
        }
    }

    /** A water or powder snow cauldron: what rain and snow fill (dripstone also fills lava). */
    static boolean isPrecipitationFill(Material to) {
        return to == Material.WATER_CAULDRON || to == Material.POWDER_SNOW_CAULDRON;
    }

    /** Rain reaches the cauldron only when nothing motion-blocking is above it; a dripstone drip needs a tip above. */
    static boolean atColumnTop(World world, Block block) {
        return block.getY() >= world.getHighestBlockYAt(block.getX(), block.getZ(), HeightMap.MOTION_BLOCKING);
    }

    // ---- isRainingAt consumers -----------------------------------------------------------------------

    /**
     * A fire put out by rain. Vanilla's {@code FireBlock.tick} removes a fire (firing this event) for four reasons; only
     * the rain one is held back here, the others pass: it cannot survive, it has no flammable neighbour, or it is old
     * over a block that does not burn (that last one is ambiguous with rain and passes too).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Block fire = event.getBlock();
        if (fire.getType() != Material.FIRE || !cycle.controls(fire.getWorld()) || !onlyRainExtinguishes(fire)) {
            return;
        }
        event.setCancelled(true);
        World world = fire.getWorld();
        // Vanilla isNearRain: the fire's position or one of its four horizontal neighbours
        BlockFace[] faces = {BlockFace.SELF, BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH};
        BlockPos[] at = new BlockPos[faces.length];
        boolean[] reaches = new boolean[faces.length];
        for (int i = 0; i < faces.length; i++) {
            Block b = fire.getRelative(faces[i]);
            at[i] = new BlockPos(b.getX(), b.getY(), b.getZ());
            reaches[i] = PrecipitationService.rainReaches(world, b.getX(), b.getY(), b.getZ());
        }
        long seed = world.getSeed();
        boolean enabled = cycle.enabled(world);
        ClockSnapshot now = clock.get();
        int x = fire.getX();
        int y = fire.getY();
        int z = fire.getZ();
        CompletableFuture.supplyAsync(() -> anyRain(seed, at, reaches, now, enabled), scheduler.cpuExecutor())
                .thenAccept(rain -> {
                    if (rain) {
                        scheduler.region(world, x >> 4, z >> 4, () -> {
                            Block live = world.getBlockAt(x, y, z);
                            if (live.getType() == Material.FIRE) {
                                live.setType(Material.AIR);
                            }
                        });
                    }
                });
    }

    /** Whether vanilla removed this fire only because of rain (see {@link #onFade}). Region thread. */
    static boolean onlyRainExtinguishes(Block fire) {
        Block below = fire.getRelative(BlockFace.DOWN);
        if (Tag.INFINIBURN_OVERWORLD.isTagged(below.getType())) {
            return false; // never put out by rain
        }
        BlockData data = fire.getBlockData();
        if (!data.isSupported(fire)) {
            return false; // canSurvive false
        }
        boolean flammableNeighbour = false;
        for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            if (fire.getRelative(face).getType().isBurnable()) {
                flammableNeighbour = true;
                break;
            }
        }
        if (!flammableNeighbour) {
            return false; // isValidFireLocation false: burns out
        }
        int age = data instanceof Ageable a ? a.getAge() : 0;
        return !(age == 15 && !below.getType().isBurnable());
    }

    /** Worker: TFC {@code isRainingAt} (RAIN, not snow) at any of the positions. */
    static boolean anyRain(long seed, BlockPos[] at, boolean[] reaches, ClockSnapshot now, boolean enabled) {
        for (int i = 0; i < at.length; i++) {
            if (LocalRain.at(seed, at[i], now, enabled, reaches[i]) == PrecipitationType.RAIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * A weather lightning strike: vanilla only strikes where {@code isRainingAt} holds, which TFC answers from the climate.
     * The strike is held back and struck again (as a plugin strike) where the climate rains at that position.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        if (event.getCause() != LightningStrikeEvent.Cause.WEATHER || !cycle.controls(event.getWorld())) {
            return;
        }
        event.setCancelled(true);
        World world = event.getWorld();
        Location loc = event.getLightning().getLocation();
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        long seed = world.getSeed();
        boolean enabled = cycle.enabled(world);
        ClockSnapshot now = clock.get();
        // Vanilla already checked the sky gate before striking
        CompletableFuture.supplyAsync(() -> LocalRain.at(seed, pos, now, enabled, true) == PrecipitationType.RAIN,
                scheduler.cpuExecutor()).thenAccept(rain -> {
                    if (rain) {
                        scheduler.region(world, pos.x() >> 4, pos.z() >> 4, () -> world.strikeLightning(loc));
                    }
                });
    }
}
