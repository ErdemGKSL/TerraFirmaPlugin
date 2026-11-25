/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/ChannelFlow.java @ c3b4d9536 (fromCrucible, damageEntities),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/ChannelBlock.java @ c3b4d9536 (activate),
 *       src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (serverTick: the draw
 *       every second tick, finishFlow, getFluidHandlerIfAppropriate)
 */
package dev.tfp.paper.channel;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.channel.BrokenLinks;
import dev.tfp.core.channel.ChannelData;
import dev.tfp.core.channel.FlowCheck;
import dev.tfp.core.channel.MoldTableData;
import dev.tfp.core.channel.MoldTableRules;
import dev.tfp.core.crucible.ChannelFlow;
import dev.tfp.core.crucible.ChannelFlow.Cell;
import dev.tfp.core.crucible.ChannelFlow.Link;
import dev.tfp.core.crucible.ChannelFlow.Plan;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * The pouring flow of PAR-METAL-13 (TFC {@code ChannelFlow.fromCrucible} and {@code MoldTableBlockEntity.serverTick}).
 *
 * <ul>
 *   <li>Activate (decide async, apply sync): on the channel's region the flood of the channel net is read once into an
 *       immutable cell snapshot; a worker plans the routes ({@link ChannelFlow#plan}: the A* over the net); the plan is applied
 *       by a region task per cell, each re-validating that the channel or mold is still open.</li>
 *   <li>Pour: a mold table with a source draws one mB every second tick (TFC {@code gameTime % 2 == 0}) from the crucible
 *       while the chain of links still reaches it, the crucible is molten with the flow's fluid, the output slot is empty and
 *       the mold takes the fluid. A short region task chain runs only for a table that has a source, so an idle net costs
 *       nothing.</li>
 *   <li>Finish: the table (or a removed channel) walks the links upstream ({@link BrokenLinks}); each channel that loses its
 *       last route is cleared and its {@code with_metal} property drops.</li>
 * </ul>
 * Cells owned by another region (Folia) are treated as solid when planning and "unknown" when checking a flow; a pour keeps
 * going while a check cannot see the cell.
 */
final class PourService {
    /** TFC {@code Entity.lavaHurt}: 4 damage and 15 seconds of fire. */
    static final float LAVA_DAMAGE = 4f;
    static final int LAVA_FIRE_TICKS = 300;

    private final ChannelWorld world;
    private final MoldItems molds;
    private final AlloyRecipes alloys;
    private final DeviceKind<CrucibleData> crucibleKind;
    private final StackCodec stacks;
    private final LongSupplier clock;
    private final Logger logger;
    private final Set<Key> active = ConcurrentHashMap.newKeySet();
    private final Set<Key> settling = ConcurrentHashMap.newKeySet();
    private final Map<Key, Scheduler.TaskHandle> handles = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    PourService(ChannelWorld world, MoldItems molds, AlloyRecipes alloys, DeviceKind<CrucibleData> crucibleKind, StackCodec stacks,
            LongSupplier clock, Logger logger) {
        this.world = Objects.requireNonNull(world, "world");
        this.molds = Objects.requireNonNull(molds, "molds");
        this.alloys = Objects.requireNonNull(alloys, "alloys");
        this.crucibleKind = Objects.requireNonNull(crucibleKind, "crucibleKind");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void stop() {
        running = false;
        settling.clear();
        handles.values().forEach(Scheduler.TaskHandle::cancel);
        handles.clear();
        active.clear();
    }

    int activeTables() {
        return active.size();
    }

    // ---- activate ---------------------------------------------------------------------------------------

    /** What an activation found, for the debug command. */
    enum Outcome {
        NO_CRUCIBLE, NOT_MOLTEN, NO_MOLD, PLANNING
    }

    /**
     * TFC {@code ChannelBlock.activate}: the first horizontal crucible starts a flow into {@code channel}. Region thread of
     * {@code channel}.
     */
    Outcome activate(Block channel) {
        if (!running || !world.is(channel, ChannelWorld.CHANNEL)) {
            return Outcome.NO_CRUCIBLE;
        }
        if (active.isEmpty()) {
            heal(channel); // a flow left over from before a restart: nothing pours, so it is dead
        }
        Block crucible = null;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            Block b = channel.getRelative(face);
            if (world.owns(b) && world.is(b, ChannelWorld.CRUCIBLE)) {
                crucible = b;
                break;
            }
        }
        if (crucible == null) {
            return Outcome.NO_CRUCIBLE;
        }
        Optional<Pourable> pourable = pourable(crucible, Optional.empty());
        if (pourable.isEmpty()) {
            return Outcome.NOT_MOLTEN;
        }
        Pourable pour = pourable.get();
        World w = channel.getWorld();
        BlockPos origin = ChannelWorld.pos(channel);
        BlockPos crucibleAt = ChannelWorld.pos(crucible);

        // snapshot (owner thread): the flood of the net, recorded cell by cell
        Map<BlockPos, Cell> seen = new HashMap<>();
        ChannelFlow.Cells recording = p -> seen.computeIfAbsent(p, q -> world.cell(world.at(w, q)));
        ChannelFlow.plan(recording, m -> false, origin, crucibleAt);
        Set<BlockPos> accepted = new HashSet<>();
        for (Map.Entry<BlockPos, Cell> e : Map.copyOf(seen).entrySet()) {
            if (e.getValue() == Cell.MOLD && accepts(world.at(w, e.getKey()), pour)) {
                accepted.add(e.getKey());
            }
        }
        if (accepted.isEmpty()) {
            return Outcome.NO_MOLD;
        }
        Map<BlockPos, Cell> snapshot = Map.copyOf(seen);

        // decide (worker): the routes
        world.scheduler().cpu(() -> {
            Optional<Plan> plan;
            try {
                plan = ChannelFlow.plan(p -> snapshot.getOrDefault(p, Cell.SOLID), accepted::contains, origin, crucibleAt);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "channel: planning the flow from " + origin + " failed", e);
                return;
            }
            plan.ifPresent(p -> world.scheduler().region(w, origin.chunkX(), origin.chunkZ(),
                () -> apply(w, p, crucibleAt, pour.fluid())));
        });
        return Outcome.PLANNING;
    }

    /**
     * No table is pouring, so a channel that still has a flow keeps it from before a restart or an unload (a table's own
     * stale source is cleared by its catch-up): the connected net of channels is cleared. Owner threads only; a foreign
     * cell ends the walk.
     */
    private void heal(Block start) {
        World w = start.getWorld();
        Set<BlockPos> seen = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(ChannelWorld.pos(start));
        while (!queue.isEmpty() && seen.size() < HEAL_LIMIT) {
            BlockPos p = queue.poll();
            if (!seen.add(p) || !world.owns(w, p)) {
                continue;
            }
            Block b = world.at(w, p);
            Optional<ChannelData> data = world.channel(b);
            if (data.isEmpty()) {
                continue;
            }
            if (data.get().hasFlow()) {
                world.writeChannel(b, new ChannelData(null, data.get().fromChannel(), 0, data.get().fluid()));
            }
            for (Direction d : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.DOWN)) {
                queue.add(p.offset(d));
            }
        }
    }

    static final int HEAL_LIMIT = 256;

    /** The crucible's molten alloy: its fluid, the temperature, and whether a flow with {@code expected} may draw from it. */
    record Pourable(String fluid, float temperature) {
    }

    /** TFC {@code getFluidHandlerIfAppropriate}: the crucible holds a molten alloy (of {@code expected} when given). */
    Optional<Pourable> pourable(Block crucible, Optional<String> expected) {
        heatBelow(crucible);
        Optional<DeviceRecord<CrucibleData>> record = world.devices().edit(crucible, crucibleKind, (r, now) -> r);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        CrucibleData data = record.get().state();
        FluidAlloy alloy = data.alloy();
        if (alloy.isEmpty()) {
            return Optional.empty();
        }
        String result = alloy.result(alloys.get());
        if (!CrucibleRules.canDraw(alloy, result, data.degrees(), expected)) {
            return Optional.empty();
        }
        return Optional.of(new Pourable(result, data.degrees()));
    }

    /** The heat source below catches up first, which hands its runs to the crucible (as {@code CrucibleModule.heatBelow}). */
    private void heatBelow(Block crucible) {
        Block below = crucible.getRelative(BlockFace.DOWN);
        if (world.owns(below) && world.state(below).map(s -> s.id().equals("tfp:firebox") || s.id().equals("tfp:charcoal_forge"))
            .orElse(false)) {
            world.devices().neighbourChanged(below);
        }
    }

    /** TFC {@code couldBeFilled}: an empty output slot and a mold that takes one mB of the fluid. */
    private boolean accepts(Block table, Pourable pour) {
        Optional<DeviceRecord<MoldTableData>> record = world.tableRecord(table);
        if (record.isEmpty() || !record.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty()) {
            return false;
        }
        ItemStack mold = stacks.decode(record.get().slot(MoldTableRules.MOLD_SLOT));
        if (StackCodec.isEmpty(mold)) {
            return false;
        }
        return molds.pourStep(new FluidAmount(pour.fluid(), 1), pour.temperature(), mold.clone()) > 0;
    }

    // ---- apply ------------------------------------------------------------------------------------------

    /** TFC {@code fromCrucible}'s last loops: every cell of the plan gets its link. Region thread of the origin. */
    private void apply(World w, Plan plan, BlockPos crucible, String fluid) {
        if (!running) {
            return;
        }
        Set<BlockPos> outlets = new HashSet<>(plan.outlets());
        for (Map.Entry<BlockPos, Link> e : plan.links().entrySet()) {
            BlockPos at = e.getKey();
            Link link = e.getValue();
            Block block = world.at(w, at);
            if (outlets.contains(at)) {
                world.onOwner(block, () -> linkTable(block, crucible, fluid, link));
            } else {
                ChannelData data = ChannelData.IDLE.linked(link, true, plan.flows().getOrDefault(at, 1), fluid);
                world.onOwner(block, () -> linkChannel(block, data));
            }
        }
        ChannelData origin = ChannelData.IDLE.linked(plan.origin(), false, plan.flows().getOrDefault(plan.channels().getFirst(), 1), fluid);
        Block originBlock = world.at(w, plan.channels().getFirst());
        world.onOwner(originBlock, () -> linkChannel(originBlock, origin));
    }

    private void linkChannel(Block block, ChannelData data) {
        Optional<ChannelData> now = world.channel(block);
        if (now.isEmpty() || (now.get().hasFlow() && !now.get().equals(data) && data.fromChannel())) {
            return; // gone, or taken by another flow since the snapshot
        }
        world.writeChannel(block, data);
    }

    private void linkTable(Block block, BlockPos crucible, String fluid, Link link) {
        Optional<DeviceRecord<MoldTableData>> record = world.tableRecord(block);
        if (record.isEmpty() || record.get().state().hasSource()) {
            return;
        }
        world.devices().edit(block, world.tableKind(), (r, now) -> r.withState(r.state().fed(crucible, fluid, link)));
        start(block);
    }

    // ---- pour -------------------------------------------------------------------------------------------

    /** Starts the draw chain of {@code table} (one chain per table). Owner thread. */
    void start(Block table) {
        if (!running || !active.add(Key.of(table))) {
            return;
        }
        schedule(table);
    }

    private void schedule(Block table) {
        Key key = Key.of(table);
        Scheduler.TaskHandle h = world.scheduler().regionDelayed(table.getWorld(), table.getX() >> 4, table.getZ() >> 4,
            MoldTableRules.DRAW_PERIOD, () -> {
                handles.remove(key);
                if (!running) {
                    return;
                }
                try {
                    if (!tick(table)) {
                        active.remove(key);
                        return;
                    }
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "channel: mold table " + key + " pour failed", e);
                    active.remove(key);
                    return;
                }
                schedule(table);
            });
        handles.put(key, h);
    }

    /** One draw of {@code table} ({@code MoldTableBlockEntity.serverTick}). @return whether the chain goes on */
    boolean tick(Block table) {
        Optional<DeviceRecord<MoldTableData>> record = world.tableRecord(table);
        if (record.isEmpty() || !record.get().state().hasSource()) {
            return false;
        }
        MoldTableData data = record.get().state();
        if (!record.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty()) {
            finish(table, data); // an output is waiting: no draw
            return false;
        }
        World w = table.getWorld();
        BlockPos source = data.source();
        if (!world.owns(w, source)) {
            return true; // the crucible is another region's: wait
        }
        Block crucible = world.at(w, source);
        Optional<Pourable> pourable = world.is(crucible, ChannelWorld.CRUCIBLE) ? pourable(crucible, Optional.of(data.fluid()))
            : Optional.empty();
        FlowCheck.Result flow;
        try {
            flow = FlowCheck.mold(world.facts(w), ChannelWorld.pos(table), data.feed());
        } catch (ChannelWorld.Unowned e) {
            flow = new FlowCheck.Result(true, List.of());
        }
        if (pourable.isEmpty() || !flow.flowing()) {
            finish(table, data);
            return false;
        }
        hurt(w, flow.falls());
        ItemStack mold = stacks.decode(record.get().slot(MoldTableRules.MOLD_SLOT));
        Pourable pour = pourable.get();
        int moved = StackCodec.isEmpty(mold) ? 0 : molds.pourStep(new FluidAmount(pour.fluid(), 1), pour.temperature(), mold);
        if (moved == 0) {
            finish(table, data); // could not fill any longer
            return false;
        }
        HeatPdc.write(mold, new HeatState(pour.temperature(), clock.getAsLong())); // the mold takes the crucible's temperature
        world.devices().edit(table, world.tableKind(), (r, now) -> r.withSlot(MoldTableRules.MOLD_SLOT, stacks.encode(mold)));
        world.devices().edit(crucible, crucibleKind, (r, now) -> {
            CrucibleData d = r.state();
            return r.withState(d.withAlloy(d.alloy().drain(1).alloy()));
        });
        MoldTableDisplays.sync(table, mold, stacks.decode(record.get().slot(MoldTableRules.OUTPUT_SLOT)));
        return true;
    }

    /** TFC {@code damageEntities}: living entities in a falling stream are burnt (lavaHurt). */
    private void hurt(World w, List<FlowCheck.Fall> falls) {
        for (FlowCheck.Fall fall : falls) {
            BlockPos p = fall.pos();
            if (!world.owns(w, p)) {
                continue;
            }
            BoundingBox box = new BoundingBox(p.x() + 6.0 / 16, p.y() + 4.0 / 16, p.z() + 6.0 / 16, p.x() + 10.0 / 16,
                p.y() + (fall.blocks() - 1), p.z() + 10.0 / 16);
            for (var e : w.getNearbyEntities(box, LivingEntity.class::isInstance)) {
                LivingEntity living = (LivingEntity) e;
                living.setFireTicks(Math.max(living.getFireTicks(), LAVA_FIRE_TICKS));
                living.damage(LAVA_DAMAGE, DamageSource.builder(DamageType.LAVA).build());
            }
        }
    }

    // ---- finish and break ----------------------------------------------------------------------------------

    // ---- settle -----------------------------------------------------------------------------------------

    /** A molten mold on a table is watched every second until it solidifies, so its casting appears without an access. */
    static final long SETTLE_PERIOD = 20;

    /**
     * TFC's {@code serverTick} turns a solid mold into its casting every tick; here the table's catch-up does it on the next
     * access, and this short chain of region tasks makes that access while the mold is still molten (one task a second per
     * hot mold; nothing for a cold table). Owner thread of {@code table}.
     */
    void settle(Block table) {
        if (!running || !settling.add(Key.of(table))) {
            return;
        }
        scheduleSettle(table);
    }

    private void scheduleSettle(Block table) {
        Key key = Key.of(table);
        world.scheduler().regionDelayed(table.getWorld(), table.getX() >> 4, table.getZ() >> 4, SETTLE_PERIOD, () -> {
            if (!running) {
                return;
            }
            boolean again = false;
            try {
                Optional<DeviceRecord<MoldTableData>> record = world.tableRecord(table);
                if (record.isPresent()) {
                    ItemStack mold = stacks.decode(record.get().slot(MoldTableRules.MOLD_SLOT));
                    MoldTableDisplays.sync(table, mold, stacks.decode(record.get().slot(MoldTableRules.OUTPUT_SLOT)));
                    again = !StackCodec.isEmpty(mold) && molds.isMold(mold) && !molds.content(mold).isEmpty()
                        && molds.isMolten(mold) && record.get().slot(MoldTableRules.OUTPUT_SLOT).isEmpty();
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "channel: settling mold table " + key + " failed", e);
            }
            if (again) {
                scheduleSettle(table);
            } else {
                settling.remove(key);
            }
        });
    }

    /** Whether the table's mold holds molten metal (so {@link #settle} has something to wait for). */
    boolean hasMoltenMold(DeviceRecord<MoldTableData> record) {
        ItemStack mold = stacks.decode(record.slot(MoldTableRules.MOLD_SLOT));
        return !StackCodec.isEmpty(mold) && molds.isMold(mold) && !molds.content(mold).isEmpty() && molds.isMolten(mold);
    }

    /** TFC {@code finishFlow}: the feeding channel loses one route (upstream too), the table forgets its source. */
    void finish(Block table, MoldTableData data) {
        if (!data.hasSource()) {
            return;
        }
        world.devices().edit(table, world.tableKind(), (r, now) -> r.withState(MoldTableData.IDLE));
        releaseUpstream(table.getWorld(), ChannelWorld.pos(table), data);
        settle(table);
    }

    /** The table is gone (broken, exploded): its flow ends as in TFC's {@code onRemove}'s {@code finishFlow}. */
    void tableRemoved(Block table, MoldTableData data) {
        active.remove(Key.of(table));
        Scheduler.TaskHandle h = handles.remove(Key.of(table));
        if (h != null) {
            h.cancel();
        }
        if (data.hasSource()) {
            releaseUpstream(table.getWorld(), ChannelWorld.pos(table), data);
        }
    }

    private void releaseUpstream(World w, BlockPos table, MoldTableData data) {
        Map<BlockPos, ChannelData> changed;
        try {
            changed = BrokenLinks.finishMold(world.facts(w), table, data.feed());
        } catch (ChannelWorld.Unowned e) {
            return;
        }
        write(w, changed);
    }

    /** TFC {@code onDestroyedByPlayer} / {@code onBlockExploded}: the removed channel notifies upstream with its route count. */
    void channelRemoved(Block channel, ChannelData data) {
        if (!data.hasFlow()) {
            return;
        }
        Link source = data.source();
        BlockPos upstream = ChannelWorld.pos(channel).offset(source.from().dx() * source.distance(),
            source.from().dy() * source.distance(), source.from().dz() * source.distance());
        World w = channel.getWorld();
        Map<BlockPos, ChannelData> changed;
        try {
            changed = BrokenLinks.propagate(world.facts(w), upstream, data.flows());
        } catch (ChannelWorld.Unowned e) {
            return;
        }
        write(w, changed);
    }

    private void write(World w, Map<BlockPos, ChannelData> changed) {
        for (Map.Entry<BlockPos, ChannelData> e : changed.entrySet()) {
            Block b = world.at(w, e.getKey());
            ChannelData data = e.getValue();
            world.onOwner(b, () -> world.writeChannel(b, data));
        }
    }

    /** The crucible at {@code block} as text (debug). */
    Optional<String> crucibleInfo(Block block) {
        if (!world.is(block, ChannelWorld.CRUCIBLE)) {
            return Optional.empty();
        }
        heatBelow(block);
        return world.devices().edit(block, crucibleKind, (r, now) -> r).map(r -> Math.round(r.state().degrees()) + " C, "
            + r.state().alloy().amount() + " mB " + r.state().alloy().contents().keySet());
    }

    /** Direction names for the debug report. */
    static String describe(ChannelData data) {
        if (!data.hasFlow()) {
            return "no flow (last fluid " + (data.fluid().isEmpty() ? "none" : data.fluid()) + ")";
        }
        Direction d = data.source().from();
        return "flow from " + d.name().toLowerCase(java.util.Locale.ROOT) + " x" + data.source().distance() + (data.fromChannel()
            ? " (a channel)" : " (the crucible)") + ", " + data.flows() + " route(s), " + data.fluid();
    }

    List<String> activeKeys() {
        List<String> out = new ArrayList<>();
        active.forEach(k -> out.add(k.x() + "," + k.y() + "," + k.z()));
        return out;
    }
}
