/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start lights a log pile),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (isValidCoverBlock),
 *       src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (LOG_PILE_LOGS, CHARCOAL_PIT_INSULATION)
 */
package dev.tfp.paper.charcoal;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.charcoal.CharcoalPit;
import dev.tfp.core.charcoal.CharcoalSpread;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

/**
 * PAR-PROC-10: the log pile and the charcoal pit. Registers the device kinds {@value CharcoalPitDevice#LOG_PILE} and
 * {@value CharcoalPitDevice#BURNING} ({@link CharcoalPitDevice}), the {@code FireLightable} (a strong start lights a log
 * pile), the {@link CharcoalListener} (log pile and charcoal placement, layer breaks), the {@link PitClock} task and the
 * chunk hooks that list loaded burning piles. Needs the tags, device, catch-up and fire modules before it in the service
 * file. Without the CraftEngine blocks (PAR-PROC-37) nothing can be placed.
 */
public final class CharcoalModule implements FeatureModule {
    public static final String ID = "charcoal";
    public static final String LOG_PILE_LOGS = "tfp:log_pile_logs";
    public static final String INSULATION = "tfp:charcoal_pit_insulation";

    private volatile Scheduler.TaskHandle task;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Scheduler scheduler = context.scheduler();
        PitClock clock = new PitClock();
        CharcoalEffects effects = new PaperCharcoalEffects(scheduler, blocks, () -> context.integrations().blockVisuals());
        CharcoalPitDevice.Services services = new CharcoalPitDevice.Services(
            stack -> tags.is(stack, LOG_PILE_LOGS),
            blocks::stateAt,
            blocks::exists,
            CharcoalModule::sturdyTop,
            block -> Tag.FIRE.isTagged(block.getType()),
            (side, face) -> cover(side, face, blocks, tags),
            block -> devices.inspect(block).flatMap(DeviceService.Inspection::record),
            ThreadLocalRandom::current,
            CharcoalPit.DEFAULT_CHARCOAL_TICKS,
            effects);
        CharcoalPitDevice device = new CharcoalPitDevice(services, devices.stacks(), clock, devices::neighbourChanged);
        devices.register(device.logPileKind());
        devices.register(device.burningKind());
        context.services().get(FireStarting.class).ifPresent(fire -> fire.register(ctx -> ctx.strong()
            && blocks.stateAt(ctx.block()).map(s -> s.id().equals(CharcoalPitDevice.LOG_PILE)).orElse(false)
            && device.light(ctx.block())));
        context.listen(new CharcoalListener(device, services,
            block -> scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)));
        Optional<CatchUpService> catchUp = context.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devices, scheduler)));
        blocks.onChunkUnload(chunk -> clock.untrackChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()));
        task = scheduler.globalRepeating(PitClock.PERIOD_TICKS, PitClock.PERIOD_TICKS,
            () -> period(clock, device, blocks, scheduler, context.clock().get().playerTicks()));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = task;
        task = null;
        if (t != null) {
            t.cancel();
        }
    }

    /** One {@link PitClock} period: each due pile gets its callback on its region. Global thread. */
    static void period(PitClock clock, CharcoalPitDevice device, CustomBlocks blocks, Scheduler scheduler, long now) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (PitClock.Due due : clock.due(now, CharcoalModule::randomTickSpeed, r::nextInt)) {
            Block block = due.block();
            scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> {
                if (!blocks.stateAt(block).map(s -> s.id().equals(CharcoalPitDevice.BURNING)).orElse(false)) {
                    clock.untrack(block);
                    return;
                }
                device.randomTicks(block, due.randomTicks());
            });
        }
    }

    /** The burning piles of a loaded chunk get a callback: it converts a pile whose time is up and lists the others. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices,
            Scheduler scheduler) {
        Optional<LazyBlockIndex> index = catchUp.index(chunk);
        if (index.isEmpty()) {
            return;
        }
        World world = chunk.getWorld();
        for (Map.Entry<BlockPos, String> e : index.get().entries().entrySet()) {
            if (!CatchUpModule.DEVICE_KIND.equals(e.getValue())) {
                continue;
            }
            BlockPos p = e.getKey();
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (blocks.stateAt(block).map(s -> s.id().equals(CharcoalPitDevice.BURNING)).orElse(false)) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }

    static int randomTickSpeed(World world) {
        Integer speed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
        return speed == null ? 3 : speed;
    }

    /** TFC {@code isFaceSturdy(level, pos, UP)} with the full support type. */
    static boolean sturdyTop(Block block) {
        return block.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    /**
     * TFC {@code isValidCoverBlock} as a cover kind: a log pile; another {@code #tfp:charcoal_pit_insulation} block; air;
     * a block that is not flammable and has a sturdy face towards the pile; or anything else. Flammability is vanilla's
     * {@code Material.isFlammable} of the server block.
     */
    static CharcoalSpread.Cover cover(Block side, BlockFace face, CustomBlocks blocks, Tags tags) {
        Optional<BlockStateRef> state = blocks.stateAt(side);
        if (state.isPresent()) {
            if (state.get().id().equals(CharcoalPitDevice.LOG_PILE)) {
                return CharcoalSpread.Cover.LOG_PILE;
            }
            if (tags.is(state.get(), INSULATION)) {
                return CharcoalSpread.Cover.PIT_INSULATION;
            }
        } else if (side.getType().isAir()) {
            return CharcoalSpread.Cover.AIR;
        }
        boolean sturdy = side.getBlockData().isFaceSturdy(face, BlockSupport.FULL);
        return sturdy && !side.getType().isFlammable() ? CharcoalSpread.Cover.STURDY_NON_FLAMMABLE
            : CharcoalSpread.Cover.OTHER;
    }
}
