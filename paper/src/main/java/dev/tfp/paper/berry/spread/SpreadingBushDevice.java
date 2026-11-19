/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (randomTick, growAndPropagate,
 *       maybeStopGrowing, canSurvive, mayPlaceOn),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java @ c3b4d9536 (growAndPropagate, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/WaterloggedBerryBushBlock.java @ c3b4d9536 (canPlaceNewBushAt, onUpdate),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.berry.spread;

import dev.tfp.core.berry.BerryBush;
import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.BerryTicker;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.berry.CranberryRules;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.berry.SpreadRules;
import dev.tfp.core.berry.SpreadTicker;
import dev.tfp.core.berry.SpreadingSpecies;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.berry.BerryEffects;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The spreading bushes (blackberry, raspberry, blueberry, elderberry), their canes and the cranberry (PAR-FARM-07) as one device kind
 * {@code tfp:spreading_bush}. The blocks are {@code tfp:spreading_bush/<species>} (properties {@code lifecycle}, {@code stage}),
 * {@code tfp:spreading_bush/<species>_cane} (plus {@code facing}), {@code tfp:spreading_bush/cranberry} (a stationary bush in water),
 * {@code tfp:spreading_bush/dead} and {@code tfp:spreading_bush/dead_cane}. The rules are {@link SpreadTicker}/{@link SpreadRules} and
 * {@link CranberryRules}; the bush is never ticked: it is settled when it is placed, clicked, updated by a neighbour, and by the catch-up
 * kind of the same id ({@link SpreadModule}).
 *
 * <p>One settle owes {@code n} growth cycles. Each cycle takes TFC's decision from the real surroundings (TFC's draw order: the side, then
 * the stop flag), places the cane or the bush above, and gives the new block the cycles that are still owed; the new block is settled at
 * once (TFC ticks it), to a depth of {@link BerryTicker#MAX_CASCADE}.
 */
public final class SpreadingBushDevice {
    public static final String ID = "tfp:spreading_bush";
    public static final String DEAD_ID = ID + "/dead";
    public static final String DEAD_CANE_ID = ID + "/dead_cane";
    public static final String CRANBERRY_ID = ID + "/cranberry";
    public static final String LIFECYCLE = "lifecycle";
    public static final String STAGE = "stage";
    public static final String FACING = "facing";
    public static final String PRODUCT_PREFIX = "tfp:food/";
    /** The catch-up kind id of the chunk index. */
    public static final String CATCH_UP_KIND = ID;

    /** The block families of the kind. */
    public enum Part { BUSH, CANE, CRANBERRY, DEAD, DEAD_CANE }

    /** A parsed block state: the family, the species (bush and cane only) and the facing of a cane. */
    public record Parsed(Part part, SpreadingSpecies species, SpreadRules.Side facing) {
    }

    public record Services(SpreadEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings) {
        public Services {
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(settings, "settings");
        }
    }

    private SpreadingBushDevice() {
    }

    // ---- block ids -----------------------------------------------------------------------------------------------------------------

    public static String bushId(SpreadingSpecies species) {
        return ID + "/" + species.name().toLowerCase(Locale.ROOT);
    }

    public static String caneId(SpreadingSpecies species) {
        return bushId(species) + "_cane";
    }

    /** The product item id of a bush or cane species. */
    public static String productId(SpreadingSpecies species) {
        return PRODUCT_PREFIX + species.name().toLowerCase(Locale.ROOT);
    }

    public static String cranberryProductId() {
        return PRODUCT_PREFIX + "cranberry";
    }

    /** The block family and species of a state of this kind. */
    public static Optional<Parsed> parse(BlockStateRef ref) {
        String id = ref.id();
        String prefix = ID + "/";
        if (!id.startsWith(prefix)) {
            return Optional.empty();
        }
        SpreadRules.Side facing = side(ref.properties().get(FACING));
        return switch (id) {
            case DEAD_ID -> Optional.of(new Parsed(Part.DEAD, null, null));
            case DEAD_CANE_ID -> Optional.of(new Parsed(Part.DEAD_CANE, null, facing));
            case CRANBERRY_ID -> Optional.of(new Parsed(Part.CRANBERRY, null, null));
            default -> {
                String name = id.substring(prefix.length());
                boolean cane = name.endsWith("_cane");
                String speciesName = (cane ? name.substring(0, name.length() - "_cane".length()) : name).toUpperCase(Locale.ROOT);
                for (SpreadingSpecies s : SpreadingSpecies.values()) {
                    if (s.name().equals(speciesName)) {
                        yield Optional.of(new Parsed(cane ? Part.CANE : Part.BUSH, s, facing));
                    }
                }
                yield Optional.empty();
            }
        };
    }

    private static SpreadRules.Side side(String facing) {
        if (facing == null) {
            return null;
        }
        return SpreadRules.Side.valueOf(facing.toUpperCase(Locale.ROOT));
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    /** The visual state of a living block. */
    public static BlockStateRef state(Parsed p, BerryBush bush) {
        String lifecycle = lower(bush.lifecycle());
        String stage = Integer.toString(bush.stage());
        return switch (p.part()) {
            case BUSH -> BlockStateRef.of(bushId(p.species())).with(LIFECYCLE, lifecycle).with(STAGE, stage);
            case CANE -> BlockStateRef.of(caneId(p.species())).with(LIFECYCLE, lifecycle).with(STAGE, stage).with(FACING, lower(p.facing()));
            case CRANBERRY -> BlockStateRef.of(CRANBERRY_ID).with(LIFECYCLE, lifecycle).with(STAGE, stage);
            case DEAD, DEAD_CANE -> deadState(p, bush.stage());
        };
    }

    /** The dead block that a withered block becomes: the bush and the cranberry become the dead bush, the cane the dead cane. */
    public static BlockStateRef deadState(Parsed p, int stage) {
        if (p.part() == Part.CANE || p.part() == Part.DEAD_CANE) {
            return BlockStateRef.of(DEAD_CANE_ID).with(STAGE, Integer.toString(stage)).with(FACING, lower(p.facing()));
        }
        return BlockStateRef.of(DEAD_ID).with(STAGE, Integer.toString(stage));
    }

    /** The device kind: no slots, no framework catch-up (the position-aware {@link #tick} is the catch-up). */
    public static DeviceKind<BerryState> kind(Services services) {
        BerryBush initial = new BerryBush(Lifecycle.HEALTHY, 0, BerryRules.GROWTHS_INITIAL, 0L, 0L);
        return DeviceKind.builder(ID, BerryState.CODEC, new BerryState(false, initial, 0L, 50, 10f, true))
            .slots(0)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(services))
            .build();
    }

    static BerryClock clock(ClockSnapshot snapshot, long now, boolean northern) {
        return new BerryClock(snapshot.playerTicks(), snapshot.calendarTicks(), snapshot.daysInMonth(), northern).at(now);
    }

    // ---- settling ------------------------------------------------------------------------------------------------------------------

    /**
     * Brings the block up to {@code now} and runs its owed growth cycles. Returns the state to store for {@code block}, or empty when the
     * block was removed. Region thread of {@code block}.
     */
    public static Optional<BerryState> tick(Services s, Block block, Parsed p, BerryState stored, long now, int depth) {
        SpreadEffects fx = s.effects();
        BerryEffects.Environment env = fx.environment(block);
        BerryState sampled = stored.withEnvironment(env.hydration(), env.temperature(), env.northern());
        BerryClock clock = clock(s.clock().get(), now, env.northern());
        return switch (p.part()) {
            case DEAD, DEAD_CANE -> {
                // TFC removes a dead bush on a random tick; here the first catch-up after the wither tick does (ledger).
                if (new BerryRules.DeadBush(stored.bush().stage(), stored.witherTick()).removableAt(now)) {
                    fx.remove(block);
                    yield Optional.empty();
                }
                yield Optional.of(sampled);
            }
            case CRANBERRY -> cranberry(s, block, p, sampled, now, clock, depth);
            case BUSH, CANE -> bush(s, block, p, sampled, now, clock, depth);
        };
    }

    private static Optional<BerryState> bush(Services s, Block block, Parsed p, BerryState sampled, long now, BerryClock clock, int depth) {
        SpreadEffects fx = s.effects();
        SpreadTicker.Settled st = SpreadTicker.settle(p.species(), sampled, now, clock, s.settings());
        BerryState state = st.state();
        if (state.dead()) { // withered: the block becomes the dead bush or cane, keeping the stage
            fx.show(block, deadState(p, state.bush().stage()), state);
            fx.track(block);
            return Optional.of(state);
        }
        BerryBush cur = state.bush();
        BerryEffects.Environment env = new BerryEffects.Environment(state.hydration(), state.temperature(), state.northern());
        for (int i = 0; i < st.cycles() && cur.growthsRemaining() > 0 && depth < BerryTicker.MAX_CASCADE; i++) {
            int remaining = st.cycles() - i - 1;
            if (p.part() == Part.BUSH) {
                SpreadRules.Side side = SpreadRules.Side.values()[fx.random().nextInt(4)];
                boolean stopRoll = fx.random().nextBoolean();
                SpreadRules.SpreadStep step = SpreadRules.spread(cur.stage(), p.species().maxHeight(), side, stopRoll,
                    surroundings(fx, block, p.species()));
                if (step.kind() == SpreadRules.Kind.NONE) {
                    break;
                }
                SpreadTicker.Growth g = SpreadTicker.grow(cur, step, remaining, now, s.settings());
                cur = g.source();
                if (step.kind() == SpreadRules.Kind.CANE) {
                    Block target = relative(block, side);
                    Parsed cane = new Parsed(Part.CANE, p.species(), side);
                    child(s, target, cane, new BerryState(false, g.child(), 0L, env.hydration(), env.temperature(), env.northern()), now, depth);
                } else if (step.kind() == SpreadRules.Kind.BUSH_UP) {
                    child(s, block.getRelative(0, 1, 0), new Parsed(Part.BUSH, p.species(), null),
                        new BerryState(false, g.child(), 0L, env.hydration(), env.temperature(), env.northern()), now, depth);
                }
            } else {
                SpreadRules.CaneKind kind = SpreadRules.caneStep(cur.stage(), bushSurvives(fx, block, p.species()));
                if (kind == SpreadRules.CaneKind.NONE) {
                    break;
                }
                BerryBush next = SpreadTicker.growCane(cur, kind, remaining, now, s.settings());
                if (kind == SpreadRules.CaneKind.CONVERT_TO_BUSH) {
                    // The cane block becomes a bush block at stage 1 and is settled again at once with the cycles that are left (TFC ticks it).
                    Parsed bush = new Parsed(Part.BUSH, p.species(), null);
                    BerryState converted = state.withBush(next);
                    fx.show(block, state(bush, next), converted);
                    fx.track(block);
                    return tick(s, block, bush, converted, now, depth + 1);
                }
                cur = next;
            }
        }
        BerryState done = state.withBush(cur);
        fx.show(block, state(p, cur), done);
        fx.track(block);
        return Optional.of(done);
    }

    private static Optional<BerryState> cranberry(Services s, Block block, Parsed p, BerryState sampled, long now, BerryClock clock, int depth) {
        SpreadEffects fx = s.effects();
        BerryBush bush = sampled.bush();
        BerryRules.Outcome outcome = CranberryRules.catchUp(bush, now, fx.waterlogged(block), clock, sampled.climate(), s.settings());
        BerryState state;
        List<BerryRules.Propagation> propagations;
        switch (outcome) {
            case BerryRules.Alive a -> {
                state = sampled.withBush(a.bush());
                propagations = a.propagations();
            }
            case BerryRules.Withered w -> {
                BerryBush kept = new BerryBush(Lifecycle.DORMANT, w.dead().stage(), bush.growthsRemaining(), bush.lastUpdateTick(),
                    bush.lastPickedTick());
                state = sampled.withered(w.dead().witherTick(), kept);
                propagations = w.propagations();
            }
        }
        fx.show(block, state.dead() ? deadState(p, state.bush().stage()) : state(p, state.bush()), state);
        fx.track(block);
        if (depth >= BerryTicker.MAX_CASCADE) {
            return Optional.of(state);
        }
        for (BerryRules.Propagation prop : propagations) {
            if (BerryTicker.crowded(fx.countSame(block, CRANBERRY_ID, 2, -1, 1))) {
                break;
            }
            Optional<BerryTicker.Child> child = BerryTicker.propagate(fx.random(), prop, now, s.settings(),
                d -> fx.canPlantCranberry(block.getRelative(d[0], d[1], d[2])));
            if (child.isEmpty()) {
                continue;
            }
            BerryTicker.Child c = child.get();
            child(s, block.getRelative(c.dx(), c.dy(), c.dz()), p,
                new BerryState(false, c.bush(), 0L, state.hydration(), state.temperature(), state.northern()), now, depth);
        }
        return Optional.of(state);
    }

    /** Places a new block of family {@code p} with its stored state and settles it at once (TFC's tick of the new block). */
    private static void child(Services s, Block target, Parsed p, BerryState state, long now, int depth) {
        SpreadEffects fx = s.effects();
        fx.place(target, state(p, state.bush()), state);
        fx.track(target);
        tick(s, target, p, state, now, depth + 1).ifPresent(settled -> fx.store(target, settled));
    }

    private static Block relative(Block block, SpreadRules.Side side) {
        return switch (side) {
            case NORTH -> block.getRelative(0, 0, -1);
            case EAST -> block.getRelative(1, 0, 0);
            case SOUTH -> block.getRelative(0, 0, 1);
            case WEST -> block.getRelative(-1, 0, 0);
        };
    }

    private static SpreadRules.Side opposite(SpreadRules.Side side) {
        return switch (side) {
            case NORTH -> SpreadRules.Side.SOUTH;
            case EAST -> SpreadRules.Side.WEST;
            case SOUTH -> SpreadRules.Side.NORTH;
            case WEST -> SpreadRules.Side.EAST;
        };
    }

    /** The facts around a bush block (TFC's reads in {@code growAndPropagate} and {@code maybeStopGrowing}). */
    static SpreadRules.Surroundings surroundings(SpreadEffects fx, Block block, SpreadingSpecies species) {
        EnumSet<SpreadRules.Side> empty = EnumSet.noneOf(SpreadRules.Side.class);
        for (SpreadRules.Side side : SpreadRules.Side.values()) {
            if (fx.isEmpty(relative(block, side))) {
                empty.add(side);
            }
        }
        String id = bushId(species);
        int ground = SpreadRules.distanceToGround(i -> fx.blockAt(block.getRelative(0, -i, 0)).map(r -> r.id().equals(id)).orElse(false),
            species.maxHeight());
        return new SpreadRules.Surroundings(empty, fx.isEmpty(block.getRelative(0, 1, 0)), ground, fx.countSame(block, id, 2, 0, 1));
    }

    /** TFC {@code SpreadingBushBlock.canSurvive} for a bush block of {@code species} standing at {@code block}. */
    static boolean bushSurvives(SpreadEffects fx, Block block, SpreadingSpecies species) {
        Block below = block.getRelative(0, -1, 0);
        boolean grown = fx.blockAt(below).map(r -> r.id().equals(bushId(species)) && !"0".equals(r.properties().get(STAGE))).orElse(false);
        return SpreadRules.bushSurvives(fx.mayPlaceOn(below), grown);
    }

    /** TFC {@code SpreadingCaneBlock.canSurvive}: the block behind the cane is a spreading bush. */
    static boolean caneSurvives(SpreadEffects fx, Block block, Parsed p) {
        Block behind = relative(block, opposite(p.facing()));
        return SpreadRules.caneSurvives(fx.blockAt(behind).map(r -> parse(r).map(q -> q.part() == Part.BUSH).orElse(false)).orElse(false));
    }

    private static Lifecycle placedLifecycle(Parsed p, long now, BerryClock clock) {
        if (p.part() == Part.CRANBERRY) {
            return BerryRules.place(BushSpecies.CRANBERRY, now, clock).lifecycle();
        }
        boolean active = p.species().lifecycleFor(clock.hemisphericMonth(clock.calendarAt(now))).active();
        return active ? Lifecycle.HEALTHY : Lifecycle.DORMANT;
    }

    private record Handler(Services services) implements DeviceHandler<BerryState> {
        /** TFC {@code getStateForPlacement} and {@code setPlacedBy}: healthy in the growing season, dormant otherwise; counters reset. */
        @Override
        public DeviceResult<BerryState> placed(DeviceContext<BerryState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() == Part.DEAD || parsed.get().part() == Part.DEAD_CANE) {
                return DeviceResult.pass();
            }
            Parsed p = parsed.get();
            BerryEffects.Environment env = services.effects().environment(ctx.block());
            BerryClock clock = clock(services.clock().get(), ctx.now(), env.northern());
            BerryBush bush = new BerryBush(placedLifecycle(p, ctx.now(), clock), 0, BerryRules.GROWTHS_INITIAL, ctx.now(), ctx.now());
            BerryState state = new BerryState(false, bush, 0L, env.hydration(), env.temperature(), env.northern());
            services.effects().show(ctx.block(), state(p, bush), state);
            services.effects().track(ctx.block());
            return DeviceResult.update(ctx.record().withState(state));
        }

        /** TFC {@code SeasonalPlantBlock.useItemOn}: a fruiting bush, cane or cranberry is picked; everything else passes. */
        @Override
        public DeviceResult<BerryState> useWithItem(DeviceContext<BerryState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || ctx.player() == null) {
                return DeviceResult.pass();
            }
            Parsed p = parsed.get();
            Optional<BerryState> settled = tick(services, ctx.block(), p, ctx.deviceState(), ctx.now(), 0);
            if (settled.isEmpty()) {
                return DeviceResult.pass();
            }
            BerryState state = settled.get();
            Optional<BlockStateRef> now = services.effects().blockAt(ctx.block());
            Parsed current = now.flatMap(SpreadingBushDevice::parse).orElse(p);
            String product = switch (current.part()) {
                case BUSH, CANE -> productId(current.species());
                case CRANBERRY -> cranberryProductId();
                default -> null;
            };
            if (state.dead() || product == null || state.bush().lifecycle() != Lifecycle.FRUITING) {
                return result(ctx, state);
            }
            Optional<ItemStack> stack = services.effects().product(product);
            if (stack.isEmpty()) {
                return result(ctx, state);
            }
            services.effects().pickSound(ctx.block());
            services.effects().give(ctx.player(), stack.get());
            BerryRules.Pick pick = BerryRules.pick(state.bush(), ctx.now());
            BerryState picked = state.withBush(pick.bush());
            services.effects().show(ctx.block(), state(current, pick.bush()), picked);
            return DeviceResult.consume(ctx.record().withState(picked));
        }

        /** A neighbour update: the block breaks when it can no longer survive, otherwise it is settled and shown. */
        @Override
        public DeviceResult<BerryState> neighbourChanged(DeviceContext<BerryState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty()) {
                return DeviceResult.pass();
            }
            Parsed p = parsed.get();
            SpreadEffects fx = services.effects();
            boolean survives = switch (p.part()) {
                case BUSH -> bushSurvives(fx, ctx.block(), p.species());
                case CANE -> caneSurvives(fx, ctx.block(), p);
                default -> true;
            };
            if (!survives) {
                fx.remove(ctx.block());
                return DeviceResult.pass();
            }
            Optional<BerryState> settled = tick(services, ctx.block(), p, ctx.deviceState(), ctx.now(), 0);
            if (settled.isEmpty()) {
                return DeviceResult.pass();
            }
            return result(ctx, settled.get());
        }

        private DeviceResult<BerryState> result(DeviceContext<BerryState> ctx, BerryState state) {
            DeviceRecord<BerryState> record = ctx.record().withState(state);
            return state.equals(ctx.deviceState()) ? DeviceResult.pass() : DeviceResult.update(record);
        }
    }
}
