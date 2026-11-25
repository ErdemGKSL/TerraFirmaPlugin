/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (useItemOn: a click that
 *       is not a sneak opens the menu),
 *       src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (serverTick: the
 *       temperature, the heating and melting of the inputs; isItemValid; getSlotStackLimit; onCalendarUpdate),
 *       src/main/java/net/dries007/tfc/common/component/heat/IHeatConsumer.java @ c3b4d9536 (setTemperature)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.crucible.CrucibleSim;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.HeatingRecipe;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The crucible as a device kind (PAR-METAL-12): TFC's {@code CrucibleBlock} + {@code CrucibleBlockEntity} on the device
 * framework. Ten slots, one item each: nine inputs (0 to 8) and the output (9). The temperature, the heating of the inputs
 * and their melting are replayed lazily by {@link CrucibleSim} on every access; nothing ticks.
 *
 * <p>Heat: the heat source below hands the crucible the runs of temperature it gave ({@code IHeatConsumer.setTemperature},
 * {@link dev.tfp.paper.firebox.HeatConsumers}); {@link #heat} keeps them for the catch-up of the same call, so the replay
 * sees the source's temperature on exactly the ticks it gave it, and cools the crucible on the others
 * ({@code onCalendarUpdate}). A melt stops the replay at that tick: the melted item is replaced by its recipe's item (if
 * any) and the rest is replayed.
 *
 * <p>Right click, in TFC's {@code useItemOn} order: anything but a sneak opens the crucible screen. The device is removed
 * as {@code SAVE}: the whole record (alloy, temperature, items) goes onto the dropped crucible item.
 *
 * <p>Molds (PAR-METAL-23): after the heat replay, {@link #moldPass} replays TFC's pouring over the same span. A molten mold
 * in an input slot drains into the alloy ({@link CrucibleRules#pourTick}: one mB per pouring-rate ticks, fast for the slot
 * of the last {@link #pourFaster}); while the alloy is molten, one mB per tick fills the mold in the output slot at the
 * crucible's temperature. The pass uses the temperature at the end of the span (ledger divergence).
 */
public final class CrucibleDevice {
    public static final String ID = "tfp:crucible";
    /** TFC {@code SLOT_INPUT_START..SLOT_INPUT_END}. */
    public static final int SLOT_INPUT_START = 0;
    public static final int SLOT_OUTPUT = CrucibleRules.INPUT_SLOTS;
    public static final int SLOTS = CrucibleRules.INPUT_SLOTS + 1;
    /** The most melts one catch-up replays in a row (a guard; each needs its own tick). */
    static final int MELT_LIMIT = 64;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param capacities  TFC {@code HeatCapability.has} and the item's heat capacity; 0 or less means it cannot be heated
     * @param heating     TFC {@code HeatingRecipe.getRecipe} and {@code assembleItem}
     * @param heatConfig  the cooling and heating modifiers
     * @param capacity    TFC {@code crucibleCapacity} in mB
     * @param output      whether a stack may go into the output slot (TFC: it has a fluid handler)
     * @param open        opens the crucible screen (TFC {@code openMenu}); must open it later, never inline
     * @param heatBelow   brings the heat source below up to date (its catch-up pushes its runs here); region thread
     * @param molds       the molds of the input and output slots (PAR-METAL-23)
     */
    public record Services(ItemHeatHooks.Capacities capacities, Heating heating, HeatConfig heatConfig, int capacity,
            Predicate<ItemStack> output, BiConsumer<Player, Block> open, Consumer<Block> heatBelow, CrucibleMolds molds) {
        /** Without molds. */
        public Services(ItemHeatHooks.Capacities capacities, Heating heating, HeatConfig heatConfig, int capacity,
                Predicate<ItemStack> output, BiConsumer<Player, Block> open, Consumer<Block> heatBelow) {
            this(capacities, heating, heatConfig, capacity, output, open, heatBelow, CrucibleMolds.NONE);
        }

        public Services {
            Objects.requireNonNull(capacities, "capacities");
            Objects.requireNonNull(heating, "heating");
            Objects.requireNonNull(heatConfig, "heatConfig");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(open, "open");
            Objects.requireNonNull(heatBelow, "heatBelow");
            Objects.requireNonNull(molds, "molds");
        }
    }

    /** The heating recipes as the crucible uses them. */
    public interface Heating {
        /** The melt of one item of {@code stack}: the recipe's temperature and fluid; empty when the item never melts. */
        Optional<CrucibleRecipe> recipe(ItemStack stack);

        /** What the melted item leaves (TFC {@code assembleItem}): the recipe's item, empty for most ores. */
        ItemStack melted(ItemStack heated);

        /** The heating recipes of the plugin's {@link HeatingService}. */
        static Heating of(HeatingService service) {
            return of(service, UnaryOperator.identity());
        }

        /** As {@link #of(HeatingService)}; {@code afterMelt} is applied to what a melt leaves (TFC: burnt to a crisp). */
        static Heating of(HeatingService service, UnaryOperator<ItemStack> afterMelt) {
            return new Heating() {
                @Override
                public Optional<CrucibleRecipe> recipe(ItemStack stack) {
                    return service.recipe(stack).map(r -> melt(r, stack));
                }

                @Override
                public ItemStack melted(ItemStack heated) {
                    ItemStack left = service.convertIfReady(heated, Float.MAX_VALUE).map(HeatingService.Conversion::item)
                        .orElse(ItemStack.empty());
                    return StackCodec.isEmpty(left) ? left : afterMelt.apply(left);
                }
            };
        }
    }

    /** The heat runs handed to the catch-up of this thread's next access to one crucible. */
    private static final ThreadLocal<List<CrucibleSim.Run>> RUNS = new ThreadLocal<>();

    private final Services services;
    private final StackCodec stacks;
    private final DeviceKind<CrucibleData> kind;

    public CrucibleDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, CrucibleData.CODEC, CrucibleData.FRESH)
            .slots(SLOTS)
            .slotLimit(slot -> 1)
            .filter(this::isItemValid)
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.SAVE)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<CrucibleData> kind() {
        return kind;
    }

    public Services services() {
        return services;
    }

    /** TFC {@code isItemValid}: a heatable item; the output slot also needs a fluid container (a mold). */
    public boolean isItemValid(int slot, ItemStack stack) {
        if (StackCodec.isEmpty(stack) || services.capacities().of(stack) <= 0f) {
            return false;
        }
        return slot != SLOT_OUTPUT || services.output().test(stack);
    }

    /**
     * IHeatConsumer.setTemperature: the heat source gave {@code temperature} on every tick of {@code fromTick..toTick}.
     * Runs {@code apply} (a device access that catches the crucible up) with the run known to the replay. Region thread of
     * {@code block}.
     */
    public static <T> T heat(long fromTick, long toTick, float temperature, java.util.function.Supplier<T> apply) {
        RUNS.set(List.of(new CrucibleSim.Run(fromTick, toTick, temperature)));
        try {
            return apply.get();
        } finally {
            RUNS.remove();
        }
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}; each melt replaces its item and the replay goes on. */
    DeviceRecord<CrucibleData> catchUp(DeviceRecord<CrucibleData> record, long from, long to) {
        List<CrucibleSim.Run> runs = RUNS.get() == null ? List.of() : RUNS.get();
        long t = from;
        for (int i = 0; i < MELT_LIMIT && t < to; i++) {
            List<ItemStack> items = new ArrayList<>(CrucibleRules.INPUT_SLOTS);
            List<CrucibleSim.Input> inputs = new ArrayList<>(CrucibleRules.INPUT_SLOTS);
            for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
                ItemStack stack = stacks.decode(record.slot(slot));
                items.add(stack);
                inputs.add(input(stack, t));
            }
            CrucibleSim.Result r = CrucibleSim.advance(record.state(), t, to, runs, inputs, services.capacity(),
                FirepitRules.DeviceConfig.DEFAULT, services.heatConfig());
            record = record.withState(r.data());
            for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
                HeatState heated = r.heats().get(slot);
                ItemStack stack = items.get(slot);
                if (heated == null || StackCodec.isEmpty(stack) || heated.equals(inputs.get(slot).heat())) {
                    continue;
                }
                if (HeatPdc.write(stack, heated)) {
                    record = record.withSlot(slot, stacks.encode(stack));
                }
            }
            for (CrucibleSim.Conversion c : r.conversions()) {
                record = record.withSlot(c.slot(), stacks.encode(melted(items.get(c.slot()))));
            }
            if (r.finished()) {
                break;
            }
            t = r.stoppedAt();
        }
        return moldPass(record, to - from);
    }

    /** The most ticks one pass replays (a full crucible pours in 4000 ticks at one mB each; the pour rate is slower). */
    static final int POUR_LIMIT = 20_000;

    /**
     * TFC's serverTick pouring and output, replayed over {@code ticks} ticks at the crucible's temperature now. Per tick: if
     * a pour is allowed every input slot with a molten mold gives one mB to the alloy ({@link CrucibleRules#pourTick} sets
     * the wait), then one mB of a molten alloy goes into the output slot's mold. Stops as soon as a tick moves nothing and no
     * wait is left, since nothing it depends on changes by itself.
     */
    DeviceRecord<CrucibleData> moldPass(DeviceRecord<CrucibleData> record, long ticks) {
        CrucibleMolds molds = services.molds();
        if (ticks <= 0 || molds == CrucibleMolds.NONE) {
            return record;
        }
        CrucibleData data = record.state();
        ItemStack[] stacks = new ItemStack[SLOTS];
        boolean any = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            stacks[slot] = this.stacks.decode(record.slot(slot));
            any |= !StackCodec.isEmpty(stacks[slot]) && molds.isContainer(stacks[slot]);
        }
        if (!any) {
            return record;
        }
        FluidAlloy alloy = data.alloy();
        CrucibleRules.Pour pour = data.pour();
        float temperature = data.degrees();
        boolean[] dirty = new boolean[SLOTS];
        boolean changed = false;
        for (long i = 0; i < Math.min(ticks, POUR_LIMIT); i++) {
            boolean canFill = pour.lastFillTicks() <= 0;
            List<Integer> transferred = new ArrayList<>(0);
            if (canFill) {
                for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
                    ItemStack mold = stacks[slot];
                    if (StackCodec.isEmpty(mold) || !molds.isMolten(mold)) {
                        continue;
                    }
                    FluidAmount content = molds.content(mold);
                    FluidAlloy.Fill fill = CrucibleRules.fill(alloy, content.fluid(), 1, services.capacity());
                    if (fill.accepted() == 1 && molds.drainOne(mold)) {
                        alloy = fill.alloy();
                        transferred.add(slot);
                        dirty[slot] = true;
                    }
                }
            }
            pour = CrucibleRules.pourTick(pour, transferred, CrucibleRules.POURING_RATE, CrucibleRules.FAST_POURING_RATE);
            boolean output = false;
            ItemStack out = stacks[SLOT_OUTPUT];
            if (!alloy.isEmpty() && !StackCodec.isEmpty(out) && molds.isContainer(out)) {
                String result = molds.result(alloy);
                if (CrucibleRules.isMolten(result, temperature)
                    && molds.pourStep(new FluidAmount(result, 1), temperature, out) == 1) {
                    alloy = CrucibleRules.outputTick(alloy, result, temperature, true);
                    dirty[SLOT_OUTPUT] = true;
                    output = true;
                }
            }
            changed |= output || !transferred.isEmpty();
            if (canFill && transferred.isEmpty() && !output) {
                break; // nothing moved and nothing waits: nothing will
            }
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (dirty[slot]) {
                record = record.withSlot(slot, this.stacks.encode(stacks[slot]));
            }
        }
        return changed || !pour.equals(data.pour()) ? record.withState(data.withAlloy(alloy).withPour(pour)) : record;
    }

    /**
     * TFC {@code setFastPouring} (PourFasterPacket): the next {@link CrucibleRules#FAST_POUR_TICKS} units from input
     * {@code slot} pour at the fast rate. Returns the record with it set, or {@code null} when the slot holds no molten mold.
     */
    public DeviceRecord<CrucibleData> pourFaster(DeviceRecord<CrucibleData> record, int slot) {
        if (slot < 0 || slot >= CrucibleRules.INPUT_SLOTS) {
            return null;
        }
        ItemStack mold = stacks.decode(record.slot(slot));
        if (StackCodec.isEmpty(mold) || !services.molds().isMolten(mold)) {
            return null;
        }
        return record.withState(record.state().withPour(record.state().pour().setFastPouring(slot)));
    }

    /** The sim's view of one input: its heat record, capacity and recipe. {@code null} for an empty slot. */
    CrucibleSim.Input input(ItemStack stack, long tick) {
        if (StackCodec.isEmpty(stack)) {
            return null;
        }
        float capacity = services.capacities().of(stack);
        if (!(capacity > 0f)) {
            return null; // cannot be heated (TFC: no heat capability, the slot never takes it)
        }
        HeatState heat = HeatPdc.read(stack).orElseGet(() -> HeatState.cold(tick));
        Optional<CrucibleRecipe> recipe = services.heating().recipe(stack);
        int damage = 0;
        int max = 0;
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta instanceof Damageable d && !meta.isUnbreakable()) {
            max = Math.max(d.hasMaxDamage() ? d.getMaxDamage() : stack.getType().getMaxDurability(), 0);
            damage = d.getDamage();
        }
        return new CrucibleSim.Input(heat, capacity, recipe.orElse(null), damage, max);
    }

    /** The core rule's view of a heating recipe: its temperature and fluid. A recipe with no fluid melts to nothing. */
    static CrucibleRecipe melt(HeatingRecipe recipe, ItemStack stack) {
        String fluid = recipe.resultFluid().map(f -> f.fluid()).orElse("");
        int amount = recipe.resultFluid().map(f -> f.amount()).orElse(0);
        return new CrucibleRecipe(stack.getType().getKey().toString(), recipe.temperature(), fluid, amount,
            recipe.useDurability(), recipe.hasItemResult());
    }

    /**
     * TFC {@code recipe.assembleItem(inputStack)}: what the melted item leaves (the recipe's item with the input's heat,
     * nothing for most ores). The heat record is already on {@code heated}.
     */
    ItemStack melted(ItemStack heated) {
        return services.heating().melted(heated);
    }

    // ---- reactions --------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<CrucibleData> {
        /** TFC {@code useItemOn}: a click that is not a sneak opens the menu, whatever the hand holds. */
        @Override
        public DeviceResult<CrucibleData> useWithItem(DeviceContext<CrucibleData> ctx) {
            Player player = ctx.player();
            if (player == null || player.isSneaking()) {
                return DeviceResult.pass();
            }
            services.heatBelow().accept(ctx.block());
            services.open().accept(player, ctx.block());
            return DeviceResult.consume();
        }

        /** The dropped crucible item shows its alloy (PAR-METAL-23): see {@link CrucibleDrops}. */
        @Override
        public void removed(DeviceContext<CrucibleData> ctx) {
            CrucibleDrops.expect(ctx.block(), ctx.deviceState().alloy());
        }
    }
}
