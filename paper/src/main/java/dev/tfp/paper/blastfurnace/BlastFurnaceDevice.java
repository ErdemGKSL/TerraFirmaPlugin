/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BlastFurnaceBlockEntity.java @ c3b4d9536 (serverTick, light,
 *       intakeAir, addItemsFromWorld, dumpItems, popItemsOffOverCapacity, calculateCapacity, isItemValid),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java @ c3b4d9536 (useItemOn, canAcceptAir,
 *       intakeAir, getChimneyLevels)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceData;
import dev.tfp.core.blastfurnace.BlastFurnaceRecipe;
import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Charge;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.WorldItem;
import dev.tfp.core.blastfurnace.BlastFurnaceSim;
import dev.tfp.core.blastfurnace.BlastFurnaceState;
import dev.tfp.core.blastfurnace.BlastFurnaceStructure;
import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-METAL-15: the blast furnace as a device kind ({@code tfp:blast_furnace}): TFC's {@code BlastFurnaceBlock} +
 * {@code BlastFurnaceBlockEntity} on the device framework. Slot 0 is the tuyere, then {@link #GROUP} slots each for the
 * molten inputs, their catalysts (one to one with the inputs) and the fuel, one item per slot, compacted to the front.
 *
 * <ul>
 *   <li><b>Lazy replay</b> ({@link #catchUp}): the kind's catch-up runs TFC's live {@code serverTick} over the ticks since
 *       the last touch ({@link BlastFurnaceSim}): the burn at the config multiplier (twice that with air), the device
 *       temperature, the heating and melting of each input into the molten input tank, the conversion to the output tank and
 *       the tuyere damage. Nothing ticks.</li>
 *   <li><b>Run</b> ({@link #run}), at every callback, neighbour change, click and lighting: the chimney is scanned
 *       ({@link BlastFurnaceStructure}; capacity = levels times the capacity per level) and items above the capacity are
 *       popped off the end (which puts the furnace out); the items lying in the chimney are taken by TFC's intake rules
 *       ({@link BlastFurnaceRules#intake}); the output drips into the crucible below, at most one mB per tick since it
 *       started ({@link BlastFurnaceBelow}); the molten tower shows the contents; a hot or dripping furnace queues its
 *       next callback {@link #PERIOD} ticks on (TFC runs these steps every 20 ticks).</li>
 *   <li><b>Light</b> ({@link #lightAt}), a strong fire start: a furnace with fuel is lit. <b>Air</b> ({@link #intakeAir}),
 *       from the bellows: only with a tuyere in slot 0.</li>
 *   <li><b>Right click</b> opens the screen ({@link #screen}); the contents drop when the block is broken.</li>
 * </ul>
 */
public final class BlastFurnaceDevice {
    public static final String ID = "tfp:blast_furnace";
    public static final String LIT = "lit";
    /** The slot of the tuyere (TFC {@code inventory.getStackInSlot(0)}). */
    public static final int TUYERE = 0;
    /** The most items of each group (inputs, catalysts, fuel); the configured capacity is capped to it. */
    public static final int GROUP = 32;
    public static final int INPUT = 1;
    public static final int CATALYST = INPUT + GROUP;
    public static final int FUEL = CATALYST + GROUP;
    public static final int SLOTS = FUEL + GROUP;
    /** How often a hot or dripping furnace runs at most, in ticks (TFC checks structure and intake every 20 ticks). */
    public static final long PERIOD = 20;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param itemId     the item id of a stack
     * @param melt       TFC {@code HeatingRecipe.getRecipe(stack)} as the rules see it
     * @param fuel       TFC {@code Fuel.get(stack)}
     * @param fuelTag    whether the stack is in {@code #tfc:blast_furnace_fuel}
     * @param tuyereTag  whether the stack is in {@code #tfc:blast_furnace_tuyeres}
     * @param capacities the item's heat capacity; 0 or less means it cannot be heated
     * @param heatConfig the item cooling and heating modifiers
     * @param recipes    the blast furnace recipes
     * @param config     the capacity, chimney height, tank and fuel multiplier
     * @param probe      the block tests of the chimney
     * @param effects    the world effects
     * @param below      the container under the furnace
     * @param state      the custom block state at a block
     */
    public record Services(Function<ItemStack, Optional<String>> itemId, Function<ItemStack, Optional<CrucibleRecipe>> melt,
            Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> fuelTag, Predicate<ItemStack> tuyereTag,
            ItemHeatHooks.Capacities capacities, HeatConfig heatConfig, Supplier<List<BlastFurnaceRecipe>> recipes,
            Supplier<BlastFurnaceRules.Config> config, BlastFurnaceProbe probe, BlastFurnaceEffects effects,
            BlastFurnaceBelow below, Function<Block, Optional<BlockStateRef>> state) {
        public Services {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(melt, "melt");
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(fuelTag, "fuelTag");
            Objects.requireNonNull(tuyereTag, "tuyereTag");
            Objects.requireNonNull(capacities, "capacities");
            Objects.requireNonNull(heatConfig, "heatConfig");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(probe, "probe");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(below, "below");
            Objects.requireNonNull(state, "state");
        }
    }

    private final Services services;
    private final StackCodec stacks;
    private final DeviceKind<BlastFurnaceData> kind;
    private volatile BiConsumer<Player, Block> screen;

    public BlastFurnaceDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, BlastFurnaceData.CODEC, BlastFurnaceData.FRESH)
            .slots(SLOTS)
            .slotLimit(1)
            .filter((slot, stack) -> slot == TUYERE && services.tuyereTag().test(stack)) // TFC isItemValid: tuyeres only
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<BlastFurnaceData> kind() {
        return kind;
    }

    public Services services() {
        return services;
    }

    /** Installs the screen: {@code open} must open it later on the player's thread, never inline. {@code null} removes it. */
    public void screen(BiConsumer<Player, Block> open) {
        this.screen = open;
    }

    /** The block state of a blast furnace. */
    public static BlockStateRef state(boolean lit) {
        return BlockStateRef.of(ID).with(LIT, Boolean.toString(lit));
    }

    public static boolean lit(BlockStateRef state) {
        return Boolean.parseBoolean(state.properties().getOrDefault(LIT, "false"));
    }

    // ---- the contents ----------------------------------------------------------------------------------------

    /** The non-empty stacks of one group, in slot order. */
    public List<ItemStack> group(DeviceRecord<BlastFurnaceData> record, int from) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = from; i < from + GROUP; i++) {
            ItemBlob blob = record.slot(i);
            if (!blob.isEmpty()) {
                out.add(stacks.decode(blob));
            }
        }
        return out;
    }

    /** {@code record} with group {@code from} holding {@code contents} compacted to the front. */
    DeviceRecord<BlastFurnaceData> withGroup(DeviceRecord<BlastFurnaceData> record, int from, List<ItemStack> contents) {
        DeviceRecord<BlastFurnaceData> next = record;
        for (int i = 0; i < GROUP; i++) {
            ItemBlob want = i < contents.size() ? stacks.encode(contents.get(i)) : ItemBlob.EMPTY;
            if (!want.equals(next.slot(from + i))) {
                next = next.withSlot(from + i, want);
            }
        }
        return next;
    }

    /** The tuyere stack, empty for none. */
    public ItemStack tuyere(DeviceRecord<BlastFurnaceData> record) {
        return stacks.decode(record.slot(TUYERE));
    }

    /** TFC {@code hasTuyere}. */
    public boolean hasTuyere(DeviceRecord<BlastFurnaceData> record) {
        return !record.slot(TUYERE).isEmpty();
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    private static boolean idle(BlastFurnaceData data) {
        BlastFurnaceState s = data.state();
        return !s.lit() && s.burnTemperature() == 0f && s.temperature() == 0f && s.airTicks() == 0;
    }

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}. */
    DeviceRecord<BlastFurnaceData> catchUp(DeviceRecord<BlastFurnaceData> record, long from, long to) {
        if (to <= from || idle(record.state())) {
            return record;
        }
        List<ItemStack> inputs = group(record, INPUT);
        List<ItemStack> catalysts = group(record, CATALYST);
        List<ItemStack> fuels = group(record, FUEL);
        List<Fuel> queue = new ArrayList<>(fuels.size());
        for (ItemStack f : fuels) {
            queue.add(services.fuel().apply(f).orElse(null));
        }
        List<Charge> charges = new ArrayList<>(inputs.size());
        for (ItemStack in : inputs) {
            charges.add(charge(in, from));
        }
        ItemStack tuyere = tuyere(record);
        BlastFurnaceSim.Result r = BlastFurnaceSim.advance(record.state(), from, to, queue, !StackCodec.isEmpty(tuyere), charges,
            services.recipes().get(), services.config().get(), FirepitRules.DeviceConfig.DEFAULT, services.heatConfig());
        List<ItemStack> keptInputs = new ArrayList<>();
        List<ItemStack> keptCatalysts = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < inputs.size(); i++) {
            if (r.melted().contains(i)) {
                continue;
            }
            ItemStack in = inputs.get(i);
            Charge after = r.charges().get(k++);
            if (after.heat() != null && !after.heat().equals(charges.get(i).heat())) {
                HeatPdc.write(in, after.heat());
            }
            keptInputs.add(in);
            if (i < catalysts.size()) {
                keptCatalysts.add(catalysts.get(i));
            }
        }
        List<ItemStack> keptFuel = new ArrayList<>(fuels.subList(Math.min(r.fuelUsed(), fuels.size()), fuels.size()));
        DeviceRecord<BlastFurnaceData> next = record.withState(r.data());
        next = withGroup(next, INPUT, keptInputs);
        next = withGroup(next, CATALYST, keptCatalysts);
        next = withGroup(next, FUEL, keptFuel);
        if (r.tuyereDamage() > 0) {
            next = next.withSlot(TUYERE, stacks.encode(worn(tuyere, r.tuyereDamage())));
        }
        return next;
    }

    /** TFC {@code Helpers.damageItem} {@code n} times: the item breaks (an empty stack) at its maximum damage. */
    ItemStack worn(ItemStack tuyere, int n) {
        ItemMeta meta = tuyere.getItemMeta();
        if (!(meta instanceof Damageable d) || meta.isUnbreakable()) {
            return tuyere;
        }
        int max = d.hasMaxDamage() ? d.getMaxDamage() : tuyere.getType().getMaxDurability();
        if (max <= 0) {
            return tuyere;
        }
        int damage = d.getDamage() + n;
        if (damage >= max) {
            return ItemStack.empty();
        }
        d.setDamage(damage);
        ItemStack copy = tuyere.clone();
        copy.setItemMeta(d);
        return copy;
    }

    /** The sim's view of one input: its heat record, capacity and heating recipe. */
    Charge charge(ItemStack stack, long tick) {
        float capacity = services.capacities().of(stack);
        Optional<CrucibleRecipe> melt = services.melt().apply(stack);
        int damage = 0;
        int max = 0;
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta instanceof Damageable d && !meta.isUnbreakable()) {
            max = Math.max(d.hasMaxDamage() ? d.getMaxDamage() : stack.getType().getMaxDurability(), 0);
            damage = d.getDamage();
        }
        HeatState heat = capacity > 0f ? HeatPdc.read(stack).orElseGet(() -> HeatState.cold(tick)) : null;
        return new Charge(heat, capacity, melt.orElse(null), damage, max);
    }

    // ---- the run -----------------------------------------------------------------------------------------------

    /** TFC {@code getChimneyLevels}. Region thread of {@code furnace}. */
    int levels(Block furnace) {
        return BlastFurnaceStructure.chimneyLevels(new BlastFurnaceWorld(furnace, services.probe()),
            services.config().get().maxChimneyHeight());
    }

    /**
     * TFC {@code serverTick}'s 20 tick branch and the drip, on a record that is already caught up. Region thread of
     * {@code furnace}. The block shows the result and the next callback is queued.
     *
     * @param shownLit the block's lit property now
     */
    DeviceRecord<BlastFurnaceData> run(Block furnace, boolean shownLit, DeviceRecord<BlastFurnaceData> record, long now) {
        BlastFurnaceEffects effects = services.effects();
        if (!effects.owns(furnace)) {
            return record; // another region's blocks: not read from here
        }
        BlastFurnaceRules.Config cfg = services.config().get();
        BlastFurnaceData data = record.state();
        int levels = levels(furnace);
        int capacity = BlastFurnaceRules.capacity(levels, cfg.capacityPerLevel());
        List<ItemStack> inputs = group(record, INPUT);
        List<ItemStack> catalysts = group(record, CATALYST);
        List<ItemStack> fuels = group(record, FUEL);

        // 1. the structure: units above the capacity are popped off the end, which halts a lit furnace
        boolean modified = inputs.size() > capacity || catalysts.size() > capacity || fuels.size() > capacity;
        for (List<ItemStack> list : List.of(inputs, catalysts, fuels)) {
            while (list.size() > capacity) {
                effects.spawn(furnace, list.removeLast());
            }
        }
        if (modified && data.lit()) {
            data = data.withState(new BlastFurnaceState(false, data.state().burnTicks(), data.state().burnTemperature(),
                data.state().airTicks(), data.state().temperature()));
        }

        // 2. the items lying in the chimney
        List<BlastFurnaceRecipe> recipes = services.recipes().get();
        List<String> melts = new ArrayList<>(inputs.size());
        for (ItemStack in : inputs) {
            melts.add(services.melt().apply(in).map(CrucibleRecipe::resultFluid).filter(f -> !f.isEmpty()).orElse(null));
        }
        BlastFurnaceRecipe cached = BlastFurnaceRules.cachedRecipe(recipes, melts);
        if (BlastFurnaceRules.mustDump(melts, cached)) {
            for (List<ItemStack> list : List.of(inputs, catalysts, fuels)) {
                list.forEach(s -> effects.spawn(furnace, s));
                list.clear();
            }
            cached = null;
        }
        if (capacity > 0 && (inputs.size() < capacity || fuels.size() < capacity)) {
            intake(furnace, levels, capacity, cached, recipes, inputs, catalysts, fuels);
        }
        DeviceRecord<BlastFurnaceData> next = withGroup(withGroup(withGroup(record, INPUT, inputs), CATALYST, catalysts), FUEL, fuels);

        // 3. the output drips into the container below
        data = drip(furnace, data, now);
        next = next.withState(data);

        // 4. the molten tower shows the inputs and the fuel
        effects.molten(furnace.getRelative(0, 1, 0),
            BloomeryRules.moltenTower(inputs.size() + fuels.size(), 2 * cfg.capacityPerLevel(), cfg.maxChimneyHeight()), data.lit());
        return finish(furnace, shownLit, next, now);
    }

    /** TFC {@code addItemsFromWorld}: the items in the chimney box that the recipe takes. */
    private void intake(Block furnace, int levels, int capacity, BlastFurnaceRecipe cached, List<BlastFurnaceRecipe> recipes,
            List<ItemStack> inputs, List<ItemStack> catalysts, List<ItemStack> fuels) {
        List<BlastFurnaceEffects.Drop> drops = services.effects().dropped(furnace, levels);
        if (drops.isEmpty()) {
            return;
        }
        List<WorldItem> world = new ArrayList<>(drops.size());
        for (BlastFurnaceEffects.Drop d : drops) {
            ItemStack s = d.stack();
            world.add(new WorldItem(services.itemId().apply(s).orElse(s.getType().getKey().toString()), d.count(),
                services.melt().apply(s).map(CrucibleRecipe::resultFluid).filter(f -> !f.isEmpty()).orElse(null),
                services.fuelTag().test(s)));
        }
        BlastFurnaceRecipe recipe = cached != null ? cached : BlastFurnaceRules.worldRecipe(recipes, world);
        BlastFurnaceRules.Intake take = BlastFurnaceRules.intake(recipe, world, inputs.size(), catalysts.size(), fuels.size(),
            capacity);
        for (int i = 0; i < drops.size(); i++) {
            int n = take.inputs()[i] + take.catalysts()[i] + take.fuel()[i];
            if (n <= 0) {
                continue;
            }
            BlastFurnaceEffects.Drop drop = drops.get(i);
            ItemStack one = drop.stack().clone();
            one.setAmount(1);
            for (int u = 0; u < take.inputs()[i]; u++) {
                inputs.add(one.clone());
            }
            for (int u = 0; u < take.catalysts()[i]; u++) {
                catalysts.add(one.clone());
            }
            for (int u = 0; u < take.fuel()[i]; u++) {
                fuels.add(one.clone());
            }
            drop.take(n);
        }
    }

    /** TFC's {@code transferExact(outputTank, below, 1)} each tick, since the output started to drip. */
    private BlastFurnaceData drip(Block furnace, BlastFurnaceData data, long now) {
        Tank out = data.output();
        if (out.isEmpty()) {
            return data;
        }
        long window = now - data.dripTick();
        if (window <= 0) {
            return data;
        }
        int want = (int) Math.min(out.amount(), window);
        int taken = Math.min(want, services.below().accepts(furnace, out.fluid()));
        if (taken <= 0) {
            return data.withDripTick(now);
        }
        services.below().give(furnace, out.fluid(), taken, data.state().temperature(), now);
        return data.withOutput(new Tank(out.fluid(), out.amount() - taken)).withDripTick(now);
    }

    /** The block follows the record, and a hot or dripping furnace queues its next callback. */
    private DeviceRecord<BlastFurnaceData> finish(Block furnace, boolean shownLit, DeviceRecord<BlastFurnaceData> record, long now) {
        BlastFurnaceData data = record.state();
        if (data.lit() != shownLit) {
            services.effects().show(furnace, data.lit());
        }
        BlastFurnaceState s = data.state();
        boolean hot = s.lit() || s.temperature() > 0f || s.burnTemperature() > 0f;
        boolean dripping = !data.output().isEmpty() && services.below().accepts(furnace, data.output().fluid()) > 0;
        if (hot || dripping) {
            services.effects().callbackIn(furnace, PERIOD);
        }
        return record;
    }

    // ---- lighting and air -----------------------------------------------------------------------------------

    /**
     * TFC {@code light(level, pos, state)} on a strong fire start: a furnace with fuel in its queue is lit. Region thread of
     * {@code furnace}. Returns whether the furnace is lit after.
     */
    public boolean lightAt(DeviceService devices, Block furnace) {
        boolean[] lit = {false};
        devices.edit(furnace, kind, (record, now) -> {
            Optional<BlockStateRef> st = services.state().apply(furnace);
            if (st.isEmpty() || !st.get().id().equals(ID)) {
                return record;
            }
            DeviceRecord<BlastFurnaceData> next = run(furnace, lit(st.get()), record, now);
            BlastFurnaceState before = next.state().state();
            BlastFurnaceState after = BlastFurnaceRules.light(before, !group(next, FUEL).isEmpty());
            lit[0] = after.lit();
            if (after.equals(before)) {
                return next;
            }
            // a furnace lit only now: the dripping window starts now
            next = next.withState(next.state().withState(after));
            services.effects().show(furnace, true);
            services.effects().callbackIn(furnace, PERIOD);
            return next;
        });
        return lit[0];
    }

    /** TFC {@code IBellowsConsumer.canAcceptAir}: whether a tuyere is in the furnace. Region thread of {@code furnace}. */
    public boolean canAcceptAir(DeviceService devices, Block furnace) {
        return devices.inspect(furnace).flatMap(DeviceService.Inspection::record)
            .map(r -> !r.slot(TUYERE).isEmpty()).orElse(false);
    }

    /** TFC {@code intakeAir}: adds {@code amount} air ticks, capped at the bellows maximum. Region thread of {@code furnace}. */
    public void intakeAir(DeviceService devices, Block furnace, int amount) {
        devices.edit(furnace, kind, (record, now) -> {
            BlastFurnaceData data = record.state();
            return record.withState(data.withState(data.state().intakeAir(amount)));
        });
        if (services.effects().owns(furnace)) {
            services.effects().callbackIn(furnace, PERIOD);
        }
    }

    // ---- reactions --------------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<BlastFurnaceData> {
        /** TFC {@code useItemOn}: any click opens the screen. */
        @Override
        public DeviceResult<BlastFurnaceData> useWithItem(DeviceContext<BlastFurnaceData> ctx) {
            BiConsumer<Player, Block> open = screen;
            if (ctx.player() == null || open == null) {
                return DeviceResult.pass();
            }
            open.accept(ctx.player(), ctx.block());
            return DeviceResult.consume();
        }

        /** The burn, the drip, the structure re-check, intake after a drop, and neighbour changes. */
        @Override
        public DeviceResult<BlastFurnaceData> neighbourChanged(DeviceContext<BlastFurnaceData> ctx) {
            boolean lit = ctx.state() != null && BlastFurnaceDevice.lit(ctx.state());
            DeviceRecord<BlastFurnaceData> next = run(ctx.block(), lit, ctx.record(), ctx.now());
            return next.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(next);
        }

        /** TFC {@code onRemove}: the molten tower goes; the kind's DROP behaviour ejects the contents. */
        @Override
        public void removed(DeviceContext<BlastFurnaceData> ctx) {
            services.effects().removeMolten(ctx.block(), services.config().get().maxChimneyHeight());
        }
    }
}
