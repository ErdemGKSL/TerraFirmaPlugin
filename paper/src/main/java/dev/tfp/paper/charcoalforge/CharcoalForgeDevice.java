/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CharcoalForgeBlock.java @ c3b4d9536 (useItemOn,
 *       InventoryRemoveBehavior.DROP, the HEAT state),
 *       src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (createFromCharcoalPile,
 *       onFirstCreation, serverTick, isItemValid, getSlotStackLimit, light, consumeFuel, cascadeFuelSlots,
 *       handleInputMelting),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on a charcoal pile, a
 *       forge or the crucible above one)
 */
package dev.tfp.paper.charcoalforge;

import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeRules;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.charcoalforge.CharcoalForgeState;
import dev.tfp.core.cooking.FirepitRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.fuel.Fuel;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.crucible.CrucibleMolds;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.firebox.HeatConsumers;
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
import java.util.function.UnaryOperator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-08: the charcoal forge as a device kind ({@code tfp:charcoal_forge}): TFC's {@code CharcoalForgeBlock} +
 * {@code CharcoalForgeBlockEntity} on the device framework. Fourteen slots of one item each (fuel 0 to 4, inputs 5 to 9,
 * extra 10 to 13); the burn, the temperature and the heating of the input items are replayed lazily by
 * {@link CharcoalForgeSim} on every access, so an item left in the forge keeps heating without clicks and nothing ticks.
 *
 * <ul>
 *   <li><b>Creation</b> ({@link #createFromPile}): a strong fire start on a charcoal pile of seven or eight layers with a
 *       valid forge structure replaces it with the forge, lit with 200 ticks of burn at 500 C
 *       ({@code onFirstCreation}). A strong start on an unlit forge or on the crucible above it lights it from its first
 *       fuel slot ({@link #lightAt}).</li>
 *   <li><b>Heat</b>: every warm tick the forge heats each input item toward its temperature (TFC {@code addTemp}); the
 *       heat records go onto the stacks. An item whose heating recipe fires is replaced by its item result (with the
 *       {@code charcoal_grilled} trait, nothing when the recipe has none) when it reaches the recipe temperature; a fluid
 *       result is poured into the molds of the extra slots in order (PAR-METAL-25) and is lost where none takes it.
 *       Dropped fuel in the pickup box above goes into the fuel slots on every access ({@link #gather}). Every tick of the temperature is also handed to the heat consumer
 *       above ({@link HeatConsumers}, the crucible); while one stands above a lit forge the forge re-runs every
 *       {@link #HEAT_PERIOD} ticks to keep it supplied.</li>
 *   <li><b>Right click</b>: with a screen installed ({@link #screen}, PAR-METAL-09) a click opens it, as TFC. Until then a
 *       fuel goes into the first empty fuel slot, a heatable item into the first empty input slot, and a sneak click with an
 *       empty hand takes the last input back (ledger divergence).</li>
 *   <li>The block shows {@code heat_level} 0 to 7 ({@link CharcoalForgeData#heatLevel}); while lit the forge queues its
 *       next callback so the level follows the temperature and the burn-out shows on time. The structure scan that puts a
 *       forge out when its insulation or chimney is gone is PAR-METAL-09.</li>
 * </ul>
 */
public final class CharcoalForgeDevice {
    public static final String ID = "tfp:charcoal_forge";
    /** TFC {@code TFCBlockStateProperties.HEAT_LEVEL}. */
    public static final String HEAT_LEVEL = "heat_level";
    /** How often a lit forge with a heat consumer above re-runs to supply it, in ticks. */
    public static final long HEAT_PERIOD = 20;
    /** How often a warming or cooling lit forge re-runs so its shown heat level follows, in ticks. */
    public static final long LEVEL_PERIOD = 40;
    /** How often a lit forge re-checks its structure at most, in ticks (TFC's random tick, about once a minute). */
    public static final long STRUCTURE_PERIOD = 1200;
    /** The most conversions one catch-up replays in a row (a guard; each needs its own tick). */
    static final int CONVERSION_LIMIT = 64;

    /** A constant run of heat given to the block above (TFC {@code provideHeatTo} each tick of it). */
    public record HeatRun(long fromTick, long toTick, float temperature) {
    }

    /** TFC {@code HeatingRecipe} lookup and conversion, as the forge uses them. */
    public interface Converter {
        /** The temperature the stack's heating recipe fires at in the forge, NaN when it never does (no recipe). */
        float recipeTemperature(ItemStack stack);

        /** What one {@code heated} item leaves at {@code temperature} (an empty stack for nothing), empty when it does not fire. */
        Optional<ItemStack> convert(ItemStack heated, float temperature);

        /**
         * TFC {@code assembleItem} and {@code assembleFluid} of the recipe (PAR-METAL-25): the item left and the fluid it
         * melts into. The default has no fluid.
         */
        default Optional<Melted> melt(ItemStack heated, float temperature) {
            return convert(heated, temperature).map(item -> new Melted(item, FluidAmount.EMPTY));
        }
    }

    /** What a fired heating recipe leaves: the item (empty for none) and the fluid (empty for none). */
    public record Melted(ItemStack item, FluidAmount fluid) {
        public Melted {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(fluid, "fluid");
        }
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param fuel       TFC {@code Fuel.get(stack)}
     * @param forgeFuel  whether the stack is in {@code #tfc:forge_fuel} (the fuel slots take only those)
     * @param capacities the item's heat capacity; 0 or less means it cannot be heated
     * @param container  whether the stack may sit in an extra slot (a fluid container: a mold or a vessel)
     * @param converter  the heating recipes
     * @param heatConfig the item cooling and heating modifiers
     * @param raining    TFC {@code isRainingAt(pos.above()) || isRainingAt(pos.above(2))}
     * @param consumers  the heat consumers ({@code IHeatConsumer} of the block above)
     * @param blockId    the CraftEngine block id at a block, if any (region thread)
     * @param effects    the world effects
     * @param grilled    TFC {@code FoodCapability.applyTrait(output, CHARCOAL_GRILLED)} on a converted stack
     * @param molds      the molds the melted fluid is poured into (the extra slots)
     */
    public record Services(Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> forgeFuel,
            ItemHeatHooks.Capacities capacities, Predicate<ItemStack> container, Converter converter, HeatConfig heatConfig,
            Predicate<Block> raining, HeatConsumers consumers, Function<Block, Optional<String>> blockId,
            CharcoalForgeEffects effects, UnaryOperator<ItemStack> grilled, CrucibleMolds molds) {
        /** The services without the grilled trait and the molds (a fluid result is lost). */
        public Services(Function<ItemStack, Optional<Fuel>> fuel, Predicate<ItemStack> forgeFuel,
                ItemHeatHooks.Capacities capacities, Predicate<ItemStack> container, Converter converter,
                HeatConfig heatConfig, Predicate<Block> raining, HeatConsumers consumers,
                Function<Block, Optional<String>> blockId, CharcoalForgeEffects effects) {
            this(fuel, forgeFuel, capacities, container, converter, heatConfig, raining, consumers, blockId, effects,
                stack -> stack, CrucibleMolds.NONE);
        }

        public Services {
            Objects.requireNonNull(fuel, "fuel");
            Objects.requireNonNull(forgeFuel, "forgeFuel");
            Objects.requireNonNull(capacities, "capacities");
            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(converter, "converter");
            Objects.requireNonNull(heatConfig, "heatConfig");
            Objects.requireNonNull(raining, "raining");
            Objects.requireNonNull(consumers, "consumers");
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(grilled, "grilled");
            Objects.requireNonNull(molds, "molds");
        }
    }

    /** The heat runs of the last catch-up on this thread and the data it ended in. */
    private record Caught(CharcoalForgeData state, List<HeatRun> runs) {
    }

    private static final ThreadLocal<Caught> RUNS = new ThreadLocal<>();

    private final Services services;
    private final StackCodec stacks;
    private final DeviceKind<CharcoalForgeData> kind;
    private volatile BiConsumer<Player, Block> screen;
    private volatile Predicate<Block> structure;

    public CharcoalForgeDevice(Services services, StackCodec stacks) {
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.kind = DeviceKind.builder(ID, CharcoalForgeData.CODEC, CharcoalForgeData.FRESH)
            .slots(CharcoalForgeSim.SLOTS)
            .slotLimit(slot -> CharcoalForgeSim.SLOT_LIMIT)
            .filter(this::isItemValid)
            .advance(this::catchUp)
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    public DeviceKind<CharcoalForgeData> kind() {
        return kind;
    }

    public Services services() {
        return services;
    }

    /**
     * Installs the forge screen (PAR-METAL-09): {@code open} must open it later on the player's thread, never inline (the
     * handler is still changing the record). {@code null} removes it.
     */
    public void screen(BiConsumer<Player, Block> open) {
        this.screen = open;
    }

    /**
     * Installs the multiblock check (PAR-METAL-09): TFC {@code CharcoalForgeBlock.isValid}. A lit forge for which it answers
     * false is put out on its next callback or neighbour change. Region thread of the forge block. {@code null} removes it.
     */
    public void structure(Predicate<Block> valid) {
        this.structure = valid;
    }

    /** TFC {@code extinguish}: the burn and the burn temperature clear; the temperature and the air stay. */
    static CharcoalForgeState extinguished(CharcoalForgeState s) {
        return new CharcoalForgeState(false, 0, 0f, s.temperature(), s.airTicks());
    }

    /** The block state of a forge showing {@code heatLevel}. */
    public static BlockStateRef state(int heatLevel) {
        return BlockStateRef.of(ID).with(HEAT_LEVEL, Integer.toString(heatLevel));
    }

    /** The {@code heat_level} of a forge block state, 0 when it has none. */
    public static int level(BlockStateRef state) {
        try {
            return Integer.parseInt(state.properties().getOrDefault(HEAT_LEVEL, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** TFC {@code isItemValid}. */
    public boolean isItemValid(int slot, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return false;
        }
        if (slot <= CharcoalForgeSim.SLOT_FUEL_MAX) {
            return services.forgeFuel().test(stack);
        }
        boolean heatable = services.capacities().of(stack) > 0f;
        if (slot <= CharcoalForgeSim.SLOT_INPUT_MAX) {
            return heatable;
        }
        return heatable && services.container().test(stack);
    }

    // ---- the lazy simulation ---------------------------------------------------------------------------

    /** The kind's catch-up: TFC's serverTick over {@code (from, to]}; the heat it gave is kept for {@link #pushHeat}. */
    DeviceRecord<CharcoalForgeData> catchUp(DeviceRecord<CharcoalForgeData> record, long from, long to) {
        List<HeatRun> runs = new ArrayList<>();
        CharcoalForgeSim.HeatSink sink = (a, b, temperature) -> {
            if (!runs.isEmpty()) {
                HeatRun last = runs.getLast();
                if (last.temperature() == temperature && last.toTick() + 1 == a) {
                    runs.set(runs.size() - 1, new HeatRun(last.fromTick(), b, temperature));
                    return;
                }
            }
            runs.add(new HeatRun(a, b, temperature));
        };
        long t = from;
        for (int i = 0; i < CONVERSION_LIMIT && t < to; i++) {
            List<Integer> fuelSlots = fuelSlots(record);
            List<Fuel> queue = new ArrayList<>(fuelSlots.size());
            for (int slot : fuelSlots) {
                queue.add(services.fuel().apply(stacks.decode(record.slot(slot))).orElseThrow());
            }
            List<ItemStack> items = new ArrayList<>(CharcoalForgeSim.INPUTS);
            List<CharcoalForgeSim.Input> inputs = new ArrayList<>(CharcoalForgeSim.INPUTS);
            for (int k = 0; k < CharcoalForgeSim.INPUTS; k++) {
                ItemStack stack = stacks.decode(record.slot(CharcoalForgeSim.SLOT_INPUT_MIN + k));
                items.add(stack);
                inputs.add(input(stack, t));
            }
            CharcoalForgeSim.Result r = CharcoalForgeSim.advance(record.state(), t, to, queue, inputs,
                FirepitRules.DeviceConfig.DEFAULT, services.heatConfig(), sink);
            record = removeFuel(record, fuelSlots, r.consumed()).withState(r.data());
            for (int k = 0; k < CharcoalForgeSim.INPUTS; k++) {
                HeatState heated = r.heats().get(k);
                ItemStack stack = items.get(k);
                if (heated == null || StackCodec.isEmpty(stack) || heated.equals(inputs.get(k).heat())) {
                    continue;
                }
                if (HeatPdc.write(stack, heated)) {
                    record = record.withSlot(CharcoalForgeSim.SLOT_INPUT_MIN + k, stacks.encode(stack));
                }
            }
            for (int k : r.conversions()) {
                ItemStack heated = items.get(k);
                HeatPdc.write(heated, r.heats().get(k));
                float temperature = r.heats().get(k).temperature();
                Optional<Melted> made = services.converter().melt(heated, temperature);
                if (made.isPresent()) {
                    record = melted(record, CharcoalForgeSim.SLOT_INPUT_MIN + k, made.get(), temperature);
                }
            }
            if (r.finished(to)) {
                break;
            }
            t = r.stoppedAt();
        }
        RUNS.set(runs.isEmpty() ? null : new Caught(record.state(), List.copyOf(runs)));
        return record;
    }

    /**
     * TFC {@code handleInputMelting}: the fluid is poured into the molds of the extra slots in order (each fills from the
     * remainder; a fluid no mold takes is lost), then the item output (with the grilled trait) replaces the input.
     */
    DeviceRecord<CharcoalForgeData> melted(DeviceRecord<CharcoalForgeData> record, int slot, Melted made, float temperature) {
        FluidAmount fluid = made.fluid();
        for (int extra = CharcoalForgeSim.SLOT_EXTRA_MIN; extra <= CharcoalForgeSim.SLOT_EXTRA_MAX && !fluid.isEmpty(); extra++) {
            ItemStack mold = stacks.decode(record.slot(extra));
            if (StackCodec.isEmpty(mold) || !services.molds().isMold(mold)) {
                continue;
            }
            int poured = 0;
            while (poured < fluid.amount() && services.molds().pourStep(new FluidAmount(fluid.fluid(), fluid.amount() - poured),
                    temperature, mold) == 1) {
                poured++;
            }
            if (poured > 0) {
                record = record.withSlot(extra, stacks.encode(mold));
                fluid = new FluidAmount(fluid.fluid(), fluid.amount() - poured);
            }
        }
        ItemStack output = made.item();
        if (StackCodec.isEmpty(output)) {
            return record.withSlot(slot, ItemBlob.EMPTY);
        }
        return record.withSlot(slot, stacks.encode(services.grilled().apply(output)));
    }

    /**
     * TFC {@code Helpers.gatherAndConsumeItems(level, bounds, inventory, SLOT_FUEL_MIN, SLOT_FUEL_MAX)}: dropped items of the
     * box that the fuel slots take go in one by one into the first free fuel slot. Region thread of {@code forge}.
     */
    DeviceRecord<CharcoalForgeData> gather(Block forge, DeviceRecord<CharcoalForgeData> record) {
        int free = 0;
        for (int i = CharcoalForgeSim.SLOT_FUEL_MIN; i <= CharcoalForgeSim.SLOT_FUEL_MAX; i++) {
            if (record.slot(i).isEmpty()) {
                free++;
            }
        }
        if (free == 0) {
            return record;
        }
        DeviceRecord<CharcoalForgeData> next = record;
        for (CharcoalForgeEffects.Drop drop : services.effects().dropped(forge)) {
            ItemStack stack = drop.stack();
            if (!isItemValid(CharcoalForgeSim.SLOT_FUEL_MAX, stack)) {
                continue;
            }
            while (free > 0 && drop.count() > 0) {
                ItemStack one = stack.clone();
                one.setAmount(1);
                for (int i = CharcoalForgeSim.SLOT_FUEL_MIN; i <= CharcoalForgeSim.SLOT_FUEL_MAX; i++) {
                    if (next.slot(i).isEmpty()) {
                        next = next.withSlot(i, stacks.encode(one));
                        break;
                    }
                }
                drop.take(1);
                free--;
            }
            if (free == 0) {
                break;
            }
        }
        return next == record ? record : cascade(next);
    }

    /** The sim's view of one input: its heat record, capacity and recipe temperature. {@code null} for an empty slot. */
    CharcoalForgeSim.Input input(ItemStack stack, long tick) {
        if (StackCodec.isEmpty(stack)) {
            return null;
        }
        float capacity = services.capacities().of(stack);
        if (!(capacity > 0f)) {
            return null; // cannot be heated (TFC: no heat capability)
        }
        HeatState heat = HeatPdc.read(stack).orElseGet(() -> HeatState.cold(tick));
        return new CharcoalForgeSim.Input(heat, capacity, services.converter().recipeTemperature(stack));
    }

    /** The fuel slots that hold a fuel, in slot order (TFC takes slot 0 and cascades). */
    List<Integer> fuelSlots(DeviceRecord<CharcoalForgeData> record) {
        List<Integer> out = new ArrayList<>(5);
        for (int i = CharcoalForgeSim.SLOT_FUEL_MIN; i <= CharcoalForgeSim.SLOT_FUEL_MAX; i++) {
            ItemBlob b = record.slot(i);
            if (!b.isEmpty() && services.fuel().apply(stacks.decode(b)).isPresent()) {
                out.add(i);
            }
        }
        return out;
    }

    /** The fuel queue of {@code record}. */
    List<Fuel> queue(DeviceRecord<CharcoalForgeData> record) {
        List<Fuel> out = new ArrayList<>(5);
        for (int slot : fuelSlots(record)) {
            out.add(services.fuel().apply(stacks.decode(record.slot(slot))).orElseThrow());
        }
        return out;
    }

    /** Empties the first {@code n} of {@code fuelSlots}, then TFC {@code cascadeFuelSlots}. */
    static DeviceRecord<CharcoalForgeData> removeFuel(DeviceRecord<CharcoalForgeData> record, List<Integer> fuelSlots, int n) {
        DeviceRecord<CharcoalForgeData> next = record;
        for (int i = 0; i < n && i < fuelSlots.size(); i++) {
            next = next.withSlot(fuelSlots.get(i), ItemBlob.EMPTY);
        }
        return cascade(next);
    }

    /** TFC {@code cascadeFuelSlots}: the fuel moves down to the lowest free fuel slots. */
    static DeviceRecord<CharcoalForgeData> cascade(DeviceRecord<CharcoalForgeData> record) {
        List<ItemBlob> left = new ArrayList<>(5);
        boolean moved = false;
        boolean gap = false;
        for (int i = CharcoalForgeSim.SLOT_FUEL_MIN; i <= CharcoalForgeSim.SLOT_FUEL_MAX; i++) {
            ItemBlob b = record.slot(i);
            if (b.isEmpty()) {
                gap = true;
            } else {
                moved |= gap;
                left.add(b);
            }
        }
        if (!moved) {
            return record;
        }
        DeviceRecord<CharcoalForgeData> next = record;
        for (int i = CharcoalForgeSim.SLOT_FUEL_MIN; i <= CharcoalForgeSim.SLOT_FUEL_MAX; i++) {
            next = next.withSlot(i, i < left.size() ? left.get(i) : ItemBlob.EMPTY);
        }
        return next;
    }

    /**
     * TFC {@code provideHeatTo(level, pos.above(), DOWN, temperature)}: hands the heat runs of this thread's last catch-up
     * to the consumer above {@code forge}, if any, and forgets them. Only when that catch-up ended in {@code caughtUp} (the
     * very data instance this forge's access saw), so runs of another block never leak. Region thread.
     */
    public void pushHeat(Block forge, CharcoalForgeData caughtUp) {
        Caught c = RUNS.get();
        if (c == null) {
            return;
        }
        RUNS.remove();
        if (c.state() != caughtUp) {
            return;
        }
        Block above = forge.getRelative(BlockFace.UP);
        consumerAbove(forge).ifPresent(consumer -> c.runs().forEach(run -> consumer.heat(above, run.fromTick(),
            run.toTick(), run.temperature())));
    }

    /** The heat consumer of the block above {@code forge}. */
    Optional<HeatConsumers.Consumer> consumerAbove(Block forge) {
        if (services.consumers().isEmpty()) {
            return Optional.empty();
        }
        return services.blockId().apply(forge.getRelative(BlockFace.UP)).flatMap(services.consumers()::find);
    }

    // ---- creation and lighting --------------------------------------------------------------------------

    /**
     * TFC {@code CharcoalForgeBlockEntity.createFromCharcoalPile}: the forge block was just placed where the pile stood;
     * its record becomes a lit forge with {@code onFirstCreation}'s burn. Region thread of {@code block}.
     */
    public void createFromPile(DeviceService devices, Block block) {
        CharcoalForgeData[] seen = {null};
        devices.edit(block, kind, (record, now) -> {
            seen[0] = record.state();
            CharcoalForgeData fresh = record.state().withState(CharcoalForgeRules.firstCreation())
                .withRaining(services.raining().test(block));
            DeviceRecord<CharcoalForgeData> next = record.withState(fresh);
            after(block, next);
            return next;
        });
        pushHeat(block, seen[0]);
    }

    /**
     * TFC {@code light(state)} on a strong fire start: a forge with burn ticks left is lit already, otherwise its first
     * fuel is taken. Region thread of {@code block}. Returns whether it is lit after.
     */
    public boolean lightAt(DeviceService devices, Block block) {
        boolean[] lit = {false};
        CharcoalForgeData[] seen = {null};
        Optional<DeviceRecord<CharcoalForgeData>> stored = devices.edit(block, kind, (record, now) -> {
            seen[0] = record.state();
            List<Integer> slots = fuelSlots(record);
            List<Fuel> queue = queue(record);
            CharcoalForgeRules.Light l = CharcoalForgeRules.light(record.state().state(), queue.isEmpty() ? null : queue.getFirst());
            lit[0] = l.lit();
            if (l.state().equals(record.state().state()) && !l.consumedFuel()) {
                return record;
            }
            DeviceRecord<CharcoalForgeData> next = record.withState(record.state().withState(l.state()));
            if (l.consumedFuel()) {
                next = removeFuel(next, slots, 1);
            }
            after(block, next);
            return next;
        });
        pushHeat(block, seen[0]);
        return stored.isPresent() && lit[0];
    }

    /** TFC {@code intakeAir} (a bellows push). Region thread of {@code block}. */
    public boolean intakeAir(DeviceService devices, Block block, int amount) {
        return devices.edit(block, kind, (record, now) -> {
            DeviceRecord<CharcoalForgeData> next = record.withState(
                record.state().withState(CharcoalForgeRules.intakeAir(record.state().state(), amount)));
            after(block, next);
            return next;
        }).isPresent();
    }

    /** The block follows the record, and a lit forge queues its next callback. */
    private void after(Block block, DeviceRecord<CharcoalForgeData> record) {
        CharcoalForgeData data = record.state();
        services.effects().show(block, data.heatLevel());
        if (data.state().lit()) {
            services.effects().callbackIn(block, nextCallback(block, record));
        }
    }

    /** The ticks to the next callback of a lit forge: the burn-out, or sooner while the level or a consumer needs it. */
    long nextCallback(Block block, DeviceRecord<CharcoalForgeData> record) {
        CharcoalForgeData data = record.state();
        long out = Math.max(1, CharcoalForgeSim.ticksUntilOut(data, queue(record)));
        if (consumerAbove(block).isPresent()) {
            return Math.min(out, HEAT_PERIOD);
        }
        CharcoalForgeState s = data.state();
        boolean settled = s.temperature() == s.burnTemperature() && s.airTicks() == 0;
        return Math.min(settled ? out : Math.min(out, LEVEL_PERIOD), STRUCTURE_PERIOD);
    }

    // ---- reactions --------------------------------------------------------------------------------------

    private final class Handler implements DeviceHandler<CharcoalForgeData> {
        @Override
        public DeviceResult<CharcoalForgeData> useWithItem(DeviceContext<CharcoalForgeData> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            DeviceRecord<CharcoalForgeData> record = gather(ctx.block(), sampled(ctx));
            BiConsumer<Player, Block> open = screen;
            if (open != null) {
                // serverPlayer.openMenu(forge, pos), after this handler stored the record
                open.accept(player, ctx.block());
                return finish(ctx, record, true);
            }
            ItemStack stack = ctx.item();
            if (!StackCodec.isEmpty(stack)) {
                int slot = firstEmpty(record, stack);
                if (slot >= 0) {
                    ItemStack one = stack.clone();
                    one.setAmount(1);
                    shrink(player, ctx.hand());
                    return finish(ctx, cascade(record.withSlot(slot, stacks.encode(one))), true);
                }
            } else if (player.isSneaking()) {
                for (int i = CharcoalForgeSim.SLOT_INPUT_MAX; i >= CharcoalForgeSim.SLOT_INPUT_MIN; i--) {
                    if (!record.slot(i).isEmpty()) {
                        services.effects().give(player, stacks.decode(record.slot(i)));
                        return finish(ctx, record.withSlot(i, ItemBlob.EMPTY), true);
                    }
                }
            }
            return finish(ctx, record, false);
        }

        /** The burn-out and heat callbacks, and neighbour changes. */
        @Override
        public DeviceResult<CharcoalForgeData> neighbourChanged(DeviceContext<CharcoalForgeData> ctx) {
            DeviceRecord<CharcoalForgeData> record = gather(ctx.block(), sampled(ctx));
            Predicate<Block> check = structure;
            if (check != null && record.state().state().lit() && !check.test(ctx.block())) {
                // updateShape / randomTick: a lit forge whose insulation or chimney is gone is put out
                record = record.withState(record.state().withState(extinguished(record.state().state())));
            }
            return finish(ctx, record, false);
        }

        /** The first empty slot of the stack's kind: a fuel slot for a fuel, else an input slot. */
        private int firstEmpty(DeviceRecord<CharcoalForgeData> record, ItemStack stack) {
            int from = isItemValid(CharcoalForgeSim.SLOT_FUEL_MIN, stack) ? CharcoalForgeSim.SLOT_FUEL_MIN
                : CharcoalForgeSim.SLOT_INPUT_MIN;
            int to = from == CharcoalForgeSim.SLOT_FUEL_MIN ? CharcoalForgeSim.SLOT_FUEL_MAX : CharcoalForgeSim.SLOT_INPUT_MAX;
            if (!isItemValid(from, stack)) {
                return -1;
            }
            for (int i = from; i <= to; i++) {
                if (record.slot(i).isEmpty()) {
                    return i;
                }
            }
            return -1;
        }

        /** The caught-up record with the rain sampled now. */
        private DeviceRecord<CharcoalForgeData> sampled(DeviceContext<CharcoalForgeData> ctx) {
            return ctx.record().withState(ctx.deviceState().withRaining(services.raining().test(ctx.block())));
        }

        private DeviceResult<CharcoalForgeData> finish(DeviceContext<CharcoalForgeData> ctx,
                DeviceRecord<CharcoalForgeData> record, boolean consumed) {
            Block block = ctx.block();
            CharcoalForgeData data = record.state();
            if (level(ctx.state()) != data.heatLevel()) {
                services.effects().show(block, data.heatLevel());
            }
            if (data.state().lit()) {
                services.effects().callbackIn(block, nextCallback(block, record));
            }
            pushHeat(block, ctx.record().state());
            if (consumed) {
                return DeviceResult.consume(record);
            }
            return record.equals(ctx.record()) ? DeviceResult.pass() : DeviceResult.update(record);
        }
    }

    /** TFC {@code stack.split(1)} on the hand's stack. */
    static void shrink(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }
}
