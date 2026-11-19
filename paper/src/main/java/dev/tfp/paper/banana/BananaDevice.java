/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java @ c3b4d9536 (randomTick, tick, grow, placeTrunk,
 *       kill, canSurvive, stateAfterPicking),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaSaplingBlock.java @ c3b4d9536 (createTree, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBananaPlantBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn, onUpdate, distanceToGround)
 */
package dev.tfp.paper.banana;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.FruitTreeState;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The banana (PAR-FARM-09) as three device kinds with one handler: {@code tfp:plant/banana_sapling}, {@code tfp:plant/banana_plant}
 * ({@code stage} 0 to 2, {@code lifecycle}) and {@code tfp:plant/dead_banana_plant} ({@code stage}). The rules are {@link BananaRules};
 * nothing is ticked. A block is settled when it is placed, updated by a neighbour, used, and by the catch-up kind {@link #CATCH_UP_KIND}.
 *
 * <p>A growth step places the new top block with its banked cycles and settles it at once (TFC ticks it right after placing it); the
 * cascade is capped by {@link #MAX_CASCADE}.
 */
public final class BananaDevice {
    public static final String ID = "tfp:banana";
    public static final String CATCH_UP_KIND = ID;
    public static final String SAPLING_ID = "tfp:plant/banana_sapling";
    public static final String PLANT_ID = "tfp:plant/banana_plant";
    public static final String DEAD_ID = "tfp:plant/dead_banana_plant";
    public static final String DEAD_BUSH_ID = "tfp:plant/dead_bush";
    public static final String PRODUCT_ID = "tfp:food/banana";
    /** Depth of the cascade of placed blocks in one settle. */
    public static final int MAX_CASCADE = 40;
    private static final int LIFECYCLE_STEPS = 4;

    public enum Part { SAPLING, PLANT, DEAD }

    public record Parsed(Part part, BlockStateRef ref) {
        int stage() {
            String v = ref.properties().get("stage");
            return v == null ? BananaRules.STAGE_GROWING : Integer.parseInt(v);
        }

        Lifecycle lifecycle() {
            return Lifecycle.valueOf(ref.properties().getOrDefault("lifecycle", "healthy").toUpperCase(Locale.ROOT));
        }
    }

    public record Services(BananaEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings, long saplingGrowthTicks,
                           long plantGrowthTicks) {
        public Services {
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(settings, "settings");
        }

        public Services(BananaEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings) {
            this(effects, clock, settings, BananaRules.SAPLING_GROWTH_TICKS, BananaRules.PLANT_GROWTH_TICKS);
        }
    }

    private BananaDevice() {
    }

    // ---- ids and states ------------------------------------------------------------------------------------------------------------

    public static Optional<Parsed> parse(BlockStateRef ref) {
        return switch (ref.id()) {
            case SAPLING_ID -> Optional.of(new Parsed(Part.SAPLING, ref));
            case PLANT_ID -> Optional.of(new Parsed(Part.PLANT, ref));
            case DEAD_ID -> Optional.of(new Parsed(Part.DEAD, ref));
            default -> Optional.empty();
        };
    }

    public static BlockStateRef sapling() {
        return BlockStateRef.of(SAPLING_ID);
    }

    public static BlockStateRef plant(int stage, Lifecycle lifecycle) {
        return BlockStateRef.of(PLANT_ID).with("stage", Integer.toString(stage)).with("lifecycle", lifecycle.name().toLowerCase(Locale.ROOT));
    }

    public static BlockStateRef dead(int stage) {
        return BlockStateRef.of(DEAD_ID).with("stage", Integer.toString(stage));
    }

    public static String[] allIdsForTest() {
        return new String[] {SAPLING_ID, PLANT_ID, DEAD_ID};
    }

    private static Optional<Parsed> parsed(BananaEffects fx, Block block) {
        return fx.blockAt(block).flatMap(BananaDevice::parse);
    }

    /** The device kinds: one per block id, one handler. No slots, no framework catch-up ({@link #tick} is the catch-up). */
    public static List<DeviceKind<FruitTreeState>> kinds(Services services) {
        Handler handler = new Handler(services);
        return List.of(SAPLING_ID, PLANT_ID, DEAD_ID).stream()
            .map(id -> DeviceKind.builder(id, FruitTreeState.CODEC, new FruitTreeState(0, 0, 0, 0L, BerryState.NEVER_PICKED, 50, 10f, true))
                .slots(0)
                .removal(DeviceKind.RemoveBehaviour.NOOP)
                .handler(handler)
                .build())
            .toList();
    }

    private static BerryClock clock(ClockSnapshot snapshot, long now, boolean northern) {
        return new BerryClock(snapshot.playerTicks(), snapshot.calendarTicks(), snapshot.daysInMonth(), northern).at(now);
    }

    private static Block stem(Block block, FruitTreeState s) {
        return block.getWorld().getBlockAt(s.stemX(), s.stemY(), s.stemZ());
    }

    private static FruitTreeState fresh(Block block, long now) {
        return new FruitTreeState(block.getX(), block.getY(), block.getZ(), now, BerryState.NEVER_PICKED, 50, 10f, true);
    }

    // ---- settling ------------------------------------------------------------------------------------------------------------------

    /** The random tick: the climate check that kills the stalk, then the tick. */
    public static Optional<FruitTreeState> tick(Services s, Block block, Parsed p, FruitTreeState stored, long now, int depth) {
        return settle(s, block, p, stored, now, depth, true);
    }

    private static Optional<FruitTreeState> settle(Services s, Block block, Parsed p, FruitTreeState stored, long now, int depth,
                                                   boolean climateCheck) {
        return switch (p.part()) {
            case SAPLING -> sapling(s, block, stored, now, depth);
            case PLANT -> plantBlock(s, block, p, stored, now, depth, climateCheck);
            case DEAD -> {
                if (BananaRules.deadExpired(now - stored.lastUpdate())) {
                    s.effects().remove(block);
                    yield Optional.empty();
                }
                yield Optional.of(stored);
            }
        };
    }

    private static Optional<FruitTreeState> sapling(Services s, Block block, FruitTreeState stored, long now, int depth) {
        BananaEffects fx = s.effects();
        Block stem = stem(block, stored);
        BerryEffects.Environment env = fx.environment(stem, stem.getRelative(0, -1, 0));
        FruitTreeState state = stored.withEnvironment(env.hydration(), env.temperature(), env.northern());
        BerryClock clock = clock(s.clock().get(), now, env.northern());
        Month month = clock.hemisphericMonth(clock.calendarAt(now));
        boolean climateOk = BananaRules.CLIMATE.checkBoth(env.hydration(), env.temperature(), false);
        BananaRules.SaplingTick result = BananaRules.saplingTick(month, now - state.lastUpdate(), s.saplingGrowthTicks(), climateOk);
        if (result instanceof BananaRules.Withered) {
            fx.replaceWithDeadBush(block);
            return Optional.empty();
        }
        if (!(result instanceof BananaRules.Grown grown)) {
            return Optional.of(state);
        }
        FruitTreeState carried = state.withLastUpdate(now - grown.carriedTicks());
        BlockStateRef ref = plant(BananaRules.STAGE_GROWING, Lifecycle.HEALTHY);
        fx.place(block, ref, carried);
        fx.track(block);
        return settle(s, block, parse(ref).orElseThrow(), carried, now, depth + 1, false);
    }

    private static Optional<FruitTreeState> plantBlock(Services s, Block block, Parsed p, FruitTreeState stored, long now, int depth,
                                                       boolean climateCheck) {
        BananaEffects fx = s.effects();
        Block stem = stem(block, stored);
        if (climateCheck) {
            // TFC randomTick reads the hydration at the stem itself, not below it (BananaPlantBlock.randomTick)
            BerryEffects.Environment at = fx.environment(stem, stem);
            boolean ok = BananaRules.CLIMATE.checkBoth(at.hydration(), at.temperature(), false);
            if (BananaRules.killedByClimate(ok)) {
                kill(s, block, now);
                return Optional.of(stored.withEnvironment(at.hydration(), at.temperature(), at.northern()));
            }
        }
        BerryEffects.Environment env = fx.environment(stem, stem.getRelative(0, -1, 0));
        FruitTreeState state = stored.withEnvironment(env.hydration(), env.temperature(), env.northern());
        BerryClock clock = clock(s.clock().get(), now, env.northern());
        Month month = clock.hemisphericMonth(clock.calendarAt(now));
        boolean climateOk = BananaRules.CLIMATE.checkBoth(env.hydration(), env.temperature(), false);
        Lifecycle current = p.lifecycle();
        for (int i = 0; i < LIFECYCLE_STEPS; i++) {
            Lifecycle next = BananaRules.update(current, month, climateOk, now, state.lastPicked(), s.settings().pickBloomDelayTicks());
            if (next == current) {
                break;
            }
            current = next;
        }
        int stage = p.stage();
        if (current != p.lifecycle()) {
            fx.show(block, plant(stage, current), state);
        }
        if (!current.active() || depth >= MAX_CASCADE) {
            return Optional.of(state);
        }
        long cycles = BananaRules.cycles(state.lastUpdate(), now, clock, s.plantGrowthTicks());
        if (cycles < 0) {
            return Optional.of(state);
        }
        FruitTreeState reset = state.withLastUpdate(now); // TFC resets the counter once an interval has passed
        if (cycles >= 1) {
            grow(s, block, stage, current, reset, now, depth, BananaRules.bankedCycles(cycles));
        }
        return Optional.of(reset);
    }

    private static void grow(Services s, Block block, int stage, Lifecycle lifecycle, FruitTreeState state, long now, int depth, int banked) {
        BananaEffects fx = s.effects();
        Block above = block.getRelative(0, 1, 0);
        boolean aboveEmpty = fx.isAir(above);
        boolean sky = aboveEmpty && fx.canSeeSky(above);
        BananaRules.Growth growth = BananaRules.grow(stage, aboveEmpty, sky, distanceToGround(fx, block),
            () -> 3 + fx.random().nextInt(3));
        switch (growth) {
            case NONE -> {
            }
            case TOP -> fx.show(block, plant(BananaRules.STAGE_TOP, lifecycle), state);
            case UP -> {
                FruitTreeState child = state.withLastPicked(BerryState.NEVER_PICKED).withLastUpdate(now - s.plantGrowthTicks() * banked);
                BlockStateRef ref = plant(BananaRules.STAGE_GROWING, lifecycle);
                fx.place(above, ref, child);
                fx.track(above);
                fx.show(block, plant(BananaRules.STAGE_TRUNK, lifecycle), state);
                settle(s, above, parse(ref).orElseThrow(), child, now, depth + 1, false).ifPresent(settled -> fx.store(above, settled));
            }
        }
    }

    /** TFC {@code distanceToGround(level, pos, 6)}: blocks down to the first block that is not a banana plant, at most 6. */
    static int distanceToGround(BananaEffects fx, Block block) {
        for (int i = 1; i <= BananaRules.GROUND_SCAN; i++) {
            Optional<Parsed> p = parsed(fx, block.getRelative(0, -i, 0));
            if (p.isEmpty() || p.get().part() != Part.PLANT) {
                return i;
            }
        }
        return BananaRules.GROUND_SCAN;
    }

    /** TFC {@code BananaPlantBlock.kill}: every plant block below {@code block} (not the block itself) becomes a dead plant of the same stage. */
    static void kill(Services s, Block block, long now) {
        BananaEffects fx = s.effects();
        for (int i = 1; i < 256; i++) {
            Block below = block.getRelative(0, -i, 0);
            Optional<Parsed> p = parsed(fx, below);
            if (p.isEmpty() || p.get().part() != Part.PLANT) {
                return;
            }
            fx.place(below, dead(p.get().stage()), fresh(below, now));
        }
    }

    /** TFC {@code canSurvive}: a plantable block below, or a block of the same kind (a sapling needs the plantable block). */
    static boolean survives(BananaEffects fx, Block block, Part part) {
        Block below = block.getRelative(0, -1, 0);
        if (part == Part.SAPLING) {
            return BananaRules.saplingSurvives(fx.plantable(below));
        }
        boolean same = parsed(fx, below).map(x -> x.part() == part).orElse(false);
        return BananaRules.survives(fx.plantable(below), same);
    }

    private record Handler(Services services) implements DeviceHandler<FruitTreeState> {
        /** TFC {@code setPlacedBy}: a placed sapling is its own stem, with a fresh counter. */
        @Override
        public DeviceResult<FruitTreeState> placed(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() != Part.SAPLING) {
                return DeviceResult.pass();
            }
            BananaEffects fx = services.effects();
            BerryEffects.Environment env = fx.environment(ctx.block(), ctx.block().getRelative(0, -1, 0));
            FruitTreeState state = new FruitTreeState(ctx.block().getX(), ctx.block().getY(), ctx.block().getZ(), ctx.now(),
                BerryState.NEVER_PICKED, env.hydration(), env.temperature(), env.northern());
            fx.track(ctx.block());
            return DeviceResult.update(ctx.record().withState(state));
        }

        /** TFC {@code SeasonalPlantBlock.useItemOn}: a fruiting plant block is picked; the stalk dies. */
        @Override
        public DeviceResult<FruitTreeState> useWithItem(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() != Part.PLANT || ctx.player() == null) {
                return DeviceResult.pass();
            }
            BananaEffects fx = services.effects();
            FruitTreeState state = tick(services, ctx.block(), parsed.get(), ctx.deviceState(), ctx.now(), 0).orElse(ctx.deviceState());
            Parsed current = fx.blockAt(ctx.block()).flatMap(BananaDevice::parse).orElse(parsed.get());
            if (current.part() != Part.PLANT || current.lifecycle() != Lifecycle.FRUITING) {
                return result(ctx, state);
            }
            int count = BananaRules.productCount(fx.random().nextInt(4));
            Optional<ItemStack> product = fx.product(PRODUCT_ID, count);
            if (product.isEmpty()) {
                return result(ctx, state);
            }
            fx.pickSound(ctx.block());
            fx.give(ctx.player(), product.get());
            FruitTreeState deadState = fresh(ctx.block(), ctx.now());
            fx.place(ctx.block(), dead(BananaRules.STAGE_TOP), deadState); // stateAfterPicking
            kill(services, ctx.block(), ctx.now());
            return DeviceResult.consume(ctx.record().withState(deadState));
        }

        /** A neighbour update: unsupported blocks break, everything else is settled. */
        @Override
        public DeviceResult<FruitTreeState> neighbourChanged(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty()) {
                return DeviceResult.pass();
            }
            if (!survives(services.effects(), ctx.block(), parsed.get().part())) {
                services.effects().remove(ctx.block());
                return DeviceResult.pass();
            }
            Optional<FruitTreeState> settled = tick(services, ctx.block(), parsed.get(), ctx.deviceState(), ctx.now(), 0);
            return settled.map(state -> result(ctx, state)).orElse(DeviceResult.pass());
        }

        private DeviceResult<FruitTreeState> result(DeviceContext<FruitTreeState> ctx, FruitTreeState state) {
            return state.equals(ctx.deviceState()) ? DeviceResult.pass() : DeviceResult.update(ctx.record().withState(state));
        }
    }
}
