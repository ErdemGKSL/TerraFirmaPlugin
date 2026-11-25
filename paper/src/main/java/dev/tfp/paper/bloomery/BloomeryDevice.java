/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (serverTick, light,
 *       addItemsFromWorld, completeRecipe, dumpItems, popItemsOffOverCapacity, calculateCapacity),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BloomeryBlock.java @ c3b4d9536 (useItemOn, updateShape,
 *       getStateForPlacement, onRemove)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomeryData;
import dev.tfp.core.bloomery.BloomeryItem;
import dev.tfp.core.bloomery.BloomeryRecipe;
import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.bloomery.BloomeryStructure;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * PAR-METAL-14: the bloomery as a device kind ({@code tfp:bloomery}): TFC's {@code BloomeryBlock} + {@code BloomeryBlockEntity}
 * on the device framework. The contents are one unit per slot in TFC's {@code inputStacks} order; the lit time, the finish and
 * the bloom are computed from the calendar when the device is touched, so nothing ticks.
 *
 * <ul>
 *   <li><b>Run</b> ({@link #run}), at every callback, neighbour change, click and lighting: a lit bloomery whose recipe time is
 *       over makes its bloom (TFC {@code completeRecipe}: the molten units, the catalysts, the bloom of the least batch count
 *       placed on the inside block, the contents gone, the bloomery unlit); then the structure is scanned ({@link
 *       BloomeryStructure}: capacity = chimney levels times the capacity per level) and units above the capacity are
 *       popped off the end, which puts a lit bloomery out; a gate that cannot stay breaks; an unlit bloomery takes the
 *       items lying in its chimney (alternating catalyst and input, up to its capacity); the molten tower shows the contents.</li>
 *   <li><b>Light</b> ({@link #lightAt}), a strong fire start: takes the items in the chimney, picks the recipe of the contents
 *       and starts its burn, door closed. A lit bloomery re-runs when the burn ends or at least every
 *       {@link #STRUCTURE_PERIOD} ticks, so a broken chimney shows within a minute (TFC checks every second).</li>
 *   <li><b>Right click</b> on an unlit bloomery opens or closes the door; a lit one does nothing.</li>
 * </ul>
 */
public final class BloomeryDevice {
    public static final String ID = "tfp:bloomery";
    public static final String FACING = "facing";
    public static final String LIT = "lit";
    public static final String OPEN = "open";
    /** The most units a bloomery holds (the slots; the configured capacity is capped to it). */
    public static final int SLOTS = 128;
    /** How often a lit bloomery re-checks its structure at most, in ticks. */
    public static final long STRUCTURE_PERIOD = 1200;
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");

    /** What one item melts into: its fluid id and the millibuckets of one item. */
    public record Melt(String fluid, int amount) {
        public static final Melt NONE = new Melt(null, 0);
    }

    /** The item side: what the plugin knows about stacks. */
    public interface Outputs {
        /** The item id of a stack. */
        Optional<String> itemId(ItemStack stack);

        /** TFC {@code HeatingRecipe.getRecipe(stack)} and {@code assembleFluid}: one item's melt. */
        Melt melt(ItemStack stack);

        /** A new stack of {@code id}, one item. */
        Optional<ItemStack> create(String id);

        /** The melting temperature of the stack's heating recipe, empty when it has none. */
        Optional<Float> meltTemperature(ItemStack stack);

        /** TFC {@code HeatCapability.setTemperature}: the stack at {@code temperature} as of {@code now}. */
        void heat(ItemStack stack, float temperature, long now);
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param outputs the items
     * @param recipes the bloomery recipes
     * @param config  the capacity and the chimney height
     * @param probe   the block tests of the multiblock
     * @param effects the world effects
     * @param state   the custom block state at a block
     */
    public record Services(Outputs outputs, Supplier<List<BloomeryRecipe>> recipes, Supplier<BloomeryRules.Config> config,
            BloomeryProbe probe, BloomeryEffects effects, Function<Block, Optional<BlockStateRef>> state) {
        public Services {
            Objects.requireNonNull(outputs, "outputs");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(probe, "probe");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(state, "state");
        }
    }

    private final Services services;
    private final StackCodec stacks;
    private final DeviceKind<BloomeryData> kind;

    public BloomeryDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, BloomeryData.CODEC, BloomeryData.FRESH)
            .slots(SLOTS)
            .slotLimit(1)
            .filter((slot, stack) -> false) // the contents come from the chimney only
            .removal(DeviceKind.RemoveBehaviour.NOOP) // removed() ejects them at the front, as TFC
            .handler(new Handler())
            .build();
    }

    public DeviceKind<BloomeryData> kind() {
        return kind;
    }

    /** The block state of a bloomery. */
    public static BlockStateRef state(String facing, boolean lit, boolean open) {
        return BlockStateRef.of(ID).with(FACING, facing).with(LIT, Boolean.toString(lit)).with(OPEN, Boolean.toString(open));
    }

    public static String facing(BlockStateRef state) {
        return state.properties().getOrDefault(FACING, "north");
    }

    public static boolean open(BlockStateRef state) {
        return Boolean.parseBoolean(state.properties().getOrDefault(OPEN, "false"));
    }

    public static boolean lit(BlockStateRef state) {
        return Boolean.parseBoolean(state.properties().getOrDefault(LIT, "false"));
    }

    /** The block of an offset {@code {dx, dz}} from {@code block}. */
    private static Block relative(Block block, String direction) {
        int[] d = BloomeryStructure.delta(direction);
        return block.getRelative(d[0], 0, d[1]);
    }

    /** TFC {@code getInternalBlockPos}: where the bloom forms. */
    static Block inside(Block gate, String facing) {
        return relative(gate, BloomeryStructure.opposite(facing));
    }

    /** TFC {@code getExternalBlock}: where items are ejected. */
    static Block outside(Block gate, String facing) {
        return relative(gate, facing);
    }

    // ---- the contents ----------------------------------------------------------------------------------------

    public List<ItemStack> contents(DeviceRecord<BloomeryData> record) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemBlob blob = record.slot(i);
            if (!blob.isEmpty()) {
                out.add(stacks.decode(blob));
            }
        }
        return out;
    }

    DeviceRecord<BloomeryData> withContents(DeviceRecord<BloomeryData> record, List<ItemStack> contents) {
        DeviceRecord<BloomeryData> next = record;
        for (int i = 0; i < SLOTS; i++) {
            ItemBlob want = i < contents.size() ? stacks.encode(contents.get(i)) : ItemBlob.EMPTY;
            if (!want.equals(next.slot(i))) {
                next = next.withSlot(i, want);
            }
        }
        return next;
    }

    private BloomeryItem unit(ItemStack stack, int count) {
        String id = services.outputs().itemId(stack).orElse(stack.getType().getKey().toString());
        Melt melt = services.outputs().melt(stack);
        return new BloomeryItem(id, count, melt.fluid(), melt.amount());
    }

    private List<BloomeryItem> units(List<ItemStack> contents) {
        List<BloomeryItem> out = new ArrayList<>(contents.size());
        for (ItemStack s : contents) {
            out.add(unit(s, 1));
        }
        return out;
    }

    // ---- the run -----------------------------------------------------------------------------------------------

    /**
     * TFC {@code serverTick} (the 20 tick branch) over the time since the last touch. Region thread of {@code gate}. Returns
     * the new record; the block shows the result and the next callback is queued.
     *
     * @param lit the block's lit property now
     */
    DeviceRecord<BloomeryData> run(Block gate, String facing, boolean open, boolean lit, DeviceRecord<BloomeryData> record,
            long now) {
        BloomeryEffects effects = services.effects();
        BloomeryRules.Config cfg = services.config().get();
        Block inside = inside(gate, facing);
        if (!effects.owns(inside) || !effects.owns(gate)) {
            return record; // another region's blocks: not read from here
        }
        BloomeryData data = record.state();
        List<ItemStack> contents = contents(record);
        List<BloomeryRecipe> recipes = services.recipes().get();

        // 1. a finished burn completes first, whatever the structure now is (TFC checks the remaining ticks first)
        if (data.lit() && now >= data.finishTick()) {
            complete(gate, inside, cfg, recipes, contents, now);
            contents = new ArrayList<>();
            data = BloomeryData.FRESH;
        }

        // 2. the structure: capacity, and units above it popped off the end
        BloomeryWorld base = new BloomeryWorld(inside, services.probe());
        boolean formed = BloomeryStructure.isFormed(base, facing);
        int levels = formed ? BloomeryStructure.chimneyLevels(base, cfg.maxChimneyHeight()) : 0;
        int capacity = Math.min(BloomeryRules.capacity(formed, levels, cfg.capacityPerLevel()), SLOTS);
        int excess = BloomeryRules.excess(contents.size(), capacity);
        if (excess > 0) {
            while (contents.size() > capacity) {
                effects.spawn(gate, contents.removeLast());
            }
            data = BloomeryData.FRESH; // the structure was compromised: the recipe halts, the inputs stay
        }
        BloomeryWorld gateWorld = new BloomeryWorld(gate, services.probe());
        if (!BloomeryStructure.canGateStayInPlace(gateWorld, facing)) {
            effects.breakGate(gate);
            return withContents(record, contents).withState(data);
        }

        // 3. an unlit bloomery takes the items lying in its chimney
        if (!data.lit()) {
            contents = intake(gate, inside, facing, levels, capacity, recipes, contents);
        }

        // 4. the molten tower shows the contents
        effects.molten(inside, BloomeryRules.moltenTower(contents.size(), cfg.capacityPerLevel(), cfg.maxChimneyHeight()),
            data.lit());
        return finish(gate, facing, open, lit, withContents(record, contents).withState(data), now);
    }

    /** The block follows the record, and a lit bloomery queues its next callback. */
    private DeviceRecord<BloomeryData> finish(Block gate, String facing, boolean open, boolean shownLit,
            DeviceRecord<BloomeryData> record, long now) {
        BloomeryData data = record.state();
        if (data.lit() != shownLit) {
            services.effects().show(gate, facing, data.lit(), data.lit() ? false : open);
        }
        if (data.lit()) {
            services.effects().callbackIn(gate, Math.max(1, Math.min(data.finishTick() - now, STRUCTURE_PERIOD)));
        }
        return record;
    }

    /** TFC {@code completeRecipe}: the bloom, the contents consumed, the molten tower removed. */
    private void complete(Block gate, Block inside, BloomeryRules.Config cfg, List<BloomeryRecipe> recipes,
            List<ItemStack> contents, long now) {
        List<BloomeryItem> units = units(contents);
        BloomeryRecipe recipe = BloomeryRules.selectRecipe(recipes, units);
        if (recipe != null) {
            Optional<ItemStack> made = services.outputs().create(recipe.result());
            Float melt = made.flatMap(services.outputs()::meltTemperature).orElse(null);
            BloomeryRules.Bloom bloom = BloomeryRules.produce(recipe, units, melt);
            if (bloom != null && made.isPresent()) {
                ItemStack item = made.get();
                if (bloom.temperature() != null) {
                    services.outputs().heat(item, bloom.temperature(), now);
                }
                services.effects().placeBloom(inside, item, bloom.count());
            }
        }
        services.effects().removeMolten(gate, cfg.maxChimneyHeight());
    }

    /** TFC {@code addItemsFromWorld}. */
    private List<ItemStack> intake(Block gate, Block inside, String facing, int levels, int capacity,
            List<BloomeryRecipe> recipes, List<ItemStack> contents) {
        List<ItemStack> held = new ArrayList<>(contents);
        List<BloomeryItem> units = units(held);
        if (BloomeryRules.mustDump(recipes, units)) {
            Block outside = outside(gate, facing);
            for (ItemStack s : held) {
                services.effects().spawn(outside, s);
            }
            held.clear();
            units = List.of();
        }
        if (held.size() >= capacity) {
            return held;
        }
        List<BloomeryEffects.Drop> drops = services.effects().dropped(inside, levels);
        if (drops.isEmpty()) {
            return held;
        }
        List<BloomeryItem> world = new ArrayList<>(drops.size());
        for (BloomeryEffects.Drop d : drops) {
            world.add(unit(d.stack(), d.count()));
        }
        BloomeryRecipe recipe = BloomeryRules.intakeRecipe(recipes, units, world);
        for (int index : BloomeryRules.intakeSequence(recipe, world, held.size(), capacity)) {
            BloomeryEffects.Drop drop = drops.get(index);
            ItemStack one = drop.stack().clone();
            one.setAmount(1);
            drop.take(1);
            held.add(one);
        }
        return held;
    }

    // ---- lighting -------------------------------------------------------------------------------------------

    /**
     * TFC {@code light(state)} on a strong fire start: takes the items in the chimney, then lights when a recipe matches the
     * contents. Region thread of {@code gate}. Returns whether the bloomery is lit after.
     */
    public boolean lightAt(DeviceService devices, Block gate) {
        boolean[] lit = {false};
        devices.edit(gate, kind, (record, now) -> {
            Optional<BlockStateRef> st = services.state().apply(gate);
            if (st.isEmpty() || !st.get().id().equals(ID)) {
                return record;
            }
            String facing = facing(st.get());
            boolean open = open(st.get());
            DeviceRecord<BloomeryData> next = run(gate, facing, open, lit(st.get()), record, now);
            if (next.state().lit()) {
                lit[0] = true; // already lit
                return next;
            }
            BloomeryRecipe recipe = BloomeryRules.selectRecipe(services.recipes().get(), units(contents(next)));
            if (recipe == null) {
                return next;
            }
            lit[0] = true;
            next = next.withState(next.state().lit(now, recipe.duration()));
            services.effects().show(gate, facing, true, false);
            services.effects().callbackIn(gate, Math.max(1, Math.min(recipe.duration(), STRUCTURE_PERIOD)));
            BloomeryRules.Config cfg = services.config().get();
            services.effects().molten(inside(gate, facing),
                BloomeryRules.moltenTower(contents(next).size(), cfg.capacityPerLevel(), cfg.maxChimneyHeight()), true);
            return next;
        });
        return lit[0];
    }

    // ---- reactions --------------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<BloomeryData> {
        /** TFC {@code useItemOn}: an unlit bloomery opens or closes its door. */
        @Override
        public DeviceResult<BloomeryData> useWithItem(DeviceContext<BloomeryData> ctx) {
            if (ctx.player() == null || ctx.deviceState().lit()) {
                return DeviceResult.pass();
            }
            BlockStateRef st = ctx.state();
            boolean open = !open(st);
            services.effects().show(ctx.block(), facing(st), false, open);
            services.effects().doorSound(ctx.block(), open);
            return DeviceResult.consume();
        }

        /** The burn end, the structure re-check, intake after a drop, and neighbour changes. */
        @Override
        public DeviceResult<BloomeryData> neighbourChanged(DeviceContext<BloomeryData> ctx) {
            BlockStateRef st = ctx.state();
            DeviceRecord<BloomeryData> next = run(ctx.block(), facing(st), open(st), lit(st), ctx.record(), ctx.now());
            return next.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(next);
        }

        /** TFC {@code getStateForPlacement}: the facing opposite the nearest looked-at direction whose gate can stay. */
        @Override
        public DeviceResult<BloomeryData> placed(DeviceContext<BloomeryData> ctx) {
            Player player = ctx.player();
            Block gate = ctx.block();
            String chosen = null;
            for (String look : lookingOrder(player)) {
                String facing = BloomeryStructure.opposite(look);
                if (BloomeryStructure.canGateStayInPlace(new BloomeryWorld(gate, services.probe()), facing)) {
                    chosen = facing;
                    break;
                }
            }
            if (chosen == null) {
                services.effects().breakGate(gate); // TFC: no state for placement, nothing is placed
                return DeviceResult.pass();
            }
            services.effects().show(gate, chosen, false, false);
            return DeviceResult.pass();
        }

        /** TFC {@code onRemove}: the contents are ejected in front of the gate and the molten tower goes. */
        @Override
        public void removed(DeviceContext<BloomeryData> ctx) {
            String facing = facing(ctx.state());
            Block outside = outside(ctx.block(), facing);
            for (ItemStack s : contents(ctx.record())) {
                services.effects().spawn(outside, s);
            }
            services.effects().removeMolten(ctx.block(), services.config().get().maxChimneyHeight());
        }
    }

    /** TFC {@code getNearestLookingDirections}, horizontal ones: the four directions by how close the look is to each. */
    static List<String> lookingOrder(Player player) {
        Vector look = player == null ? new Vector(0, 0, -1) : player.getEyeLocation().getDirection();
        List<String> order = new ArrayList<>(FACINGS);
        order.sort(Comparator.comparingDouble((String d) -> {
            int[] v = BloomeryStructure.delta(d);
            return -(look.getX() * v[0] + look.getZ() * v[1]);
        }));
        return order;
    }
}
