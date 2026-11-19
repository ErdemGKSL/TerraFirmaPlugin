/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (randomTick, createTree, setPlacedBy,
 *       findBaseOfTree, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (randomTick, tick, grow,
 *       placeGrownFlower, placeBody, addLeaves),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeBranchBlock.java @ c3b4d9536 (testDown, testUp, testHorizontal),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java @ c3b4d9536 (onUpdate, isValid, getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.BerryState;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.FruitTreeGrowth;
import dev.tfp.core.berry.FruitTreeRules;
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
 * The fruit trees (PAR-FARM-08) as one device kind {@code tfp:fruit_tree}. The blocks are {@code tfp:plant/<tree>_sapling} (property
 * {@code saplings}), {@code tfp:plant/<tree>_growing_branch} ({@code stage}, {@code saplings}, {@code natural}, the six connection flags),
 * {@code tfp:plant/<tree>_branch} ({@code stage}, the flags; static, no device) and {@code tfp:plant/<tree>_leaves} ({@code lifecycle},
 * {@code persistent}). The rules are {@link FruitTreeRules} and the geometry {@link FruitTreeGrowth}; nothing is ticked. A sapling or growing
 * branch is settled when it is placed, updated by a neighbour, and by the catch-up kind of the same id.
 *
 * <p>A growing branch that is owed {@code n} cycles takes TFC's growth step once with {@code n - 1} cycles left, and every block it places
 * carries those cycles as banked time and is settled at once (TFC randomly ticks it); the cascade is capped by {@link #MAX_CASCADE} blocks
 * deep and {@link #MAX_CYCLES} cycles.
 */
public final class FruitTreeDevice {
    public static final String ID = "tfp:fruit_tree";
    public static final String PREFIX = "tfp:plant/";
    public static final String DEAD_BUSH_ID = PREFIX + "dead_bush";
    public static final String CATCH_UP_KIND = ID;
    public static final String PRODUCT_PREFIX = "tfp:food/";
    /** Cycles one settle replays at most (TFC replays every random tick it got). */
    public static final int MAX_CYCLES = 12;
    /** Depth of the cascade of banked growing branches in one settle. */
    public static final int MAX_CASCADE = 40;
    static final String[] FLAGS = {"north", "east", "south", "west", "up", "down"};

    /** The block families of the kind. */
    public enum Part { SAPLING, GROWING, BRANCH, LEAVES }

    public record Parsed(Part part, FruitTree tree, BlockStateRef ref) {
        int intProp(String key, int fallback) {
            String v = ref.properties().get(key);
            return v == null ? fallback : Integer.parseInt(v);
        }

        boolean flag(String key) {
            return "true".equals(ref.properties().get(key));
        }
    }

    public record Services(FruitTreeEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings, long branchGrowthTicks) {
        public Services {
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(settings, "settings");
        }

        public Services(FruitTreeEffects effects, Supplier<ClockSnapshot> clock, BerryRules.Settings settings) {
            this(effects, clock, settings, FruitTreeRules.FRUIT_BRANCH_GROWTH_TICKS);
        }
    }

    private FruitTreeDevice() {
    }

    // ---- ids -----------------------------------------------------------------------------------------------------------------------

    public static String blockId(FruitTree tree, Part part) {
        String suffix = switch (part) {
            case SAPLING -> "_sapling";
            case GROWING -> "_growing_branch";
            case BRANCH -> "_branch";
            case LEAVES -> "_leaves";
        };
        return PREFIX + tree.name().toLowerCase(Locale.ROOT) + suffix;
    }

    public static String productId(FruitTree tree) {
        return PRODUCT_PREFIX + tree.name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Parsed> parse(BlockStateRef ref) {
        String id = ref.id();
        if (!id.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String name = id.substring(PREFIX.length());
        Part part;
        String treeName;
        if (name.endsWith("_growing_branch")) {
            part = Part.GROWING;
            treeName = name.substring(0, name.length() - "_growing_branch".length());
        } else if (name.endsWith("_branch")) {
            part = Part.BRANCH;
            treeName = name.substring(0, name.length() - "_branch".length());
        } else if (name.endsWith("_leaves")) {
            part = Part.LEAVES;
            treeName = name.substring(0, name.length() - "_leaves".length());
        } else if (name.endsWith("_sapling")) {
            part = Part.SAPLING;
            treeName = name.substring(0, name.length() - "_sapling".length());
        } else {
            return Optional.empty();
        }
        for (FruitTree t : FruitTree.values()) {
            if (t.name().toLowerCase(Locale.ROOT).equals(treeName)) {
                return Optional.of(new Parsed(part, t, ref));
            }
        }
        return Optional.empty();
    }

    private static boolean branchLike(Optional<Parsed> p) {
        return p.map(x -> x.part() == Part.BRANCH || x.part() == Part.GROWING).orElse(false);
    }

    private static Optional<Parsed> parsed(FruitTreeEffects fx, Block block) {
        return fx.blockAt(block).flatMap(FruitTreeDevice::parse);
    }

    // ---- block states --------------------------------------------------------------------------------------------------------------

    public static BlockStateRef sapling(FruitTree tree, int saplings) {
        return BlockStateRef.of(blockId(tree, Part.SAPLING)).with("saplings", Integer.toString(saplings));
    }

    public static BlockStateRef leaves(FruitTree tree, Lifecycle lifecycle, boolean persistent) {
        return BlockStateRef.of(blockId(tree, Part.LEAVES)).with("lifecycle", lifecycle.name().toLowerCase(Locale.ROOT))
            .with("persistent", Boolean.toString(persistent));
    }

    public static BlockStateRef growing(FruitTree tree, int stage, int saplings, boolean natural, boolean[] flags) {
        return withFlags(BlockStateRef.of(blockId(tree, Part.GROWING)).with("stage", Integer.toString(stage))
            .with("saplings", Integer.toString(saplings)).with("natural", Boolean.toString(natural)), flags);
    }

    public static BlockStateRef branch(FruitTree tree, int stage, boolean[] flags) {
        return withFlags(BlockStateRef.of(blockId(tree, Part.BRANCH)).with("stage", Integer.toString(stage)), flags);
    }

    private static BlockStateRef withFlags(BlockStateRef ref, boolean[] flags) {
        BlockStateRef out = ref;
        for (int i = 0; i < FLAGS.length; i++) {
            out = out.with(FLAGS[i], Boolean.toString(flags[i]));
        }
        return out;
    }

    private static final int[][] SIDES = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

    /** TFC {@code PipePlantBlock.getStateForPlacement} with {@code FruitTreeBranchBlock}'s tests: the six connection flags at {@code block}. */
    static boolean[] connections(FruitTreeEffects fx, Block block) {
        boolean[] flags = new boolean[6];
        for (int i = 0; i < 6; i++) {
            Block n = block.getRelative(SIDES[i][0], SIDES[i][1], SIDES[i][2]);
            Optional<Parsed> p = parsed(fx, n);
            flags[i] = switch (i) {
                case 5 -> branchLike(p) || fx.plantable(n); // down: testDown
                case 4 -> branchLike(p) || p.map(x -> x.part() == Part.SAPLING).orElse(false); // up: testUp
                default -> branchLike(p); // testHorizontal
            };
        }
        return flags;
    }

    /** Rewrites the connection flags of the branch at {@code block}, if it is a branch block. */
    static void refresh(FruitTreeEffects fx, Block block) {
        Optional<Parsed> p = parsed(fx, block);
        if (!branchLike(p)) {
            return;
        }
        boolean[] flags = connections(fx, block);
        BlockStateRef ref = withFlags(p.get().ref(), flags);
        if (!ref.equals(p.get().ref())) {
            fx.show(block, ref, null);
        }
    }

    private static void refreshAround(FruitTreeEffects fx, Block block) {
        refresh(fx, block);
        for (int[] s : SIDES) {
            refresh(fx, block.getRelative(s[0], s[1], s[2]));
        }
    }

    /**
     * The device kinds: one per block id with a device (the framework finds a kind by block id or its parent paths, and every tree block
     * is {@code tfp:plant/<name>}): sapling, growing branch and leaves of each tree, all with the same handler. No slots, no framework
     * catch-up ({@link #tick} is the catch-up).
     */
    public static List<DeviceKind<FruitTreeState>> kinds(Services services) {
        Handler handler = new Handler(services);
        List<DeviceKind<FruitTreeState>> kinds = new java.util.ArrayList<>();
        for (FruitTree tree : FruitTree.values()) {
            for (Part part : new Part[] {Part.SAPLING, Part.GROWING, Part.LEAVES}) {
                kinds.add(DeviceKind.builder(blockId(tree, part), FruitTreeState.CODEC,
                        new FruitTreeState(0, 0, 0, 0L, BerryState.NEVER_PICKED, 50, 10f, true))
                    .slots(0)
                    .removal(DeviceKind.RemoveBehaviour.NOOP)
                    .handler(handler)
                    .build());
            }
        }
        return kinds;
    }

    private static BerryClock clock(ClockSnapshot snapshot, long now, boolean northern) {
        return new BerryClock(snapshot.playerTicks(), snapshot.calendarTicks(), snapshot.daysInMonth(), northern).at(now);
    }

    private static Block stem(Block block, FruitTreeState s) {
        return block.getWorld().getBlockAt(s.stemX(), s.stemY(), s.stemZ());
    }

    // ---- settling ------------------------------------------------------------------------------------------------------------------

    /**
     * Brings the block up to {@code now}. Returns the state to store for {@code block}, or empty when the block is gone or became a static
     * branch (it then holds no device state). Region thread of {@code block}.
     */
    public static Optional<FruitTreeState> tick(Services s, Block block, Parsed p, FruitTreeState stored, long now, int depth) {
        FruitTreeEffects fx = s.effects();
        BerryEffects.Environment env = fx.environment(stem(block, stored));
        FruitTreeState state = stored.withEnvironment(env.hydration(), env.temperature(), env.northern());
        BerryClock clock = clock(s.clock().get(), now, env.northern());
        boolean climateOk = p.tree().climate().checkBoth(env.hydration(), env.temperature(), false);
        Month month = clock.hemisphericMonth(clock.calendarAt(now));
        return switch (p.part()) {
            case SAPLING -> sapling(s, block, p, state, now, month, climateOk, depth);
            case GROWING -> growing(s, block, p, state, now, climateOk, depth);
            case LEAVES -> Optional.of(leaves(s, block, p, state, month, climateOk, now));
            case BRANCH -> Optional.empty();
        };
    }

    private static Optional<FruitTreeState> sapling(Services s, Block block, Parsed p, FruitTreeState state, long now, Month month,
                                                    boolean climateOk, int depth) {
        FruitTreeEffects fx = s.effects();
        int saplings = p.intProp("saplings", 1);
        boolean onBranch = branchLike(parsed(fx, block.getRelative(0, -1, 0)));
        boolean roll = saplings == 1 && !onBranch && fx.random().nextBoolean();
        FruitTreeRules.SaplingTick result = FruitTreeRules.saplingTick(p.tree(), month, now - state.lastUpdate(),
            FruitTreeRules.ticksToGrow(p.tree(), 1.0), climateOk, saplings, onBranch, roll);
        if (result instanceof FruitTreeRules.Withered) {
            fx.replaceWithDeadBush(block);
            return Optional.empty();
        }
        if (!(result instanceof FruitTreeRules.Grown grown)) {
            return Optional.of(state);
        }
        FruitTreeState carried = state.withLastUpdate(now - grown.carriedTicks());
        BlockStateRef ref = growing(p.tree(), grown.stage(), grown.saplings(), false, connections(fx, block));
        fx.place(block, ref, carried);
        fx.track(block);
        refreshAround(fx, block);
        return tick(s, block, parse(ref).orElseThrow(), carried, now, depth + 1);
    }

    private static Optional<FruitTreeState> growing(Services s, Block block, Parsed p, FruitTreeState state, long now, boolean climateOk,
                                                    int depth) {
        FruitTreeEffects fx = s.effects();
        boolean natural = p.flag("natural");
        if (FruitTreeRules.branchResets(climateOk, natural)) {
            return Optional.of(state.withLastUpdate(now)); // TFC resets the counter and does not grow
        }
        long cycles = FruitTreeRules.branchCycles(now - state.lastUpdate(), s.branchGrowthTicks());
        if (cycles < 1 || depth >= MAX_CASCADE) {
            return Optional.of(state);
        }
        FruitTreeState reset = state.withLastUpdate(now);
        int cyclesLeft = (int) Math.min(cycles, MAX_CYCLES) - 1;
        int stage = p.intProp("stage", 0);
        int saplings = p.intProp("saplings", 0);
        List<FruitTreeGrowth.Placement> placements = FruitTreeGrowth.grow(terrain(fx, block, p.tree()), stage, saplings, fx.random());
        if (placements.isEmpty()) {
            return Optional.of(reset);
        }
        for (FruitTreeGrowth.Placement pl : placements) {
            Block target = block.getRelative(pl.dx(), pl.dy(), pl.dz());
            if (pl.type() == FruitTreeGrowth.Type.BODY) {
                fx.place(target, branch(p.tree(), pl.stage(), connections(fx, target)), null);
                refreshAround(fx, target);
                addLeaves(s, target, p.tree(), reset, now);
            } else {
                FruitTreeState child = reset.withLastUpdate(now - s.branchGrowthTicks() * cyclesLeft);
                BlockStateRef ref = growing(p.tree(), pl.stage(), pl.saplings(), natural, connections(fx, target));
                fx.place(target, ref, child);
                fx.track(target);
                refreshAround(fx, target);
                addLeaves(s, target, p.tree(), reset, now);
                tick(s, target, parse(ref).orElseThrow(), child, now, depth + 1).ifPresent(settled -> fx.store(target, settled));
            }
        }
        return Optional.empty(); // the growing block itself became a static branch body
    }

    private static FruitTreeGrowth.Terrain terrain(FruitTreeEffects fx, Block origin, FruitTree tree) {
        return new FruitTreeGrowth.Terrain() {
            private Block at(int dx, int dy, int dz) {
                return origin.getRelative(dx, dy, dz);
            }

            @Override
            public boolean canGrowInto(int dx, int dy, int dz) {
                Block b = at(dx, dy, dz);
                return fx.isAir(b) || parsed(fx, b).map(x -> x.part() == Part.LEAVES).orElse(false);
            }

            @Override
            public boolean isBody(int dx, int dy, int dz) {
                return parsed(fx, at(dx, dy, dz)).map(x -> x.part() == Part.BRANCH && x.tree() == tree).orElse(false);
            }

            @Override
            public boolean plantable(int dx, int dy, int dz) {
                return fx.plantable(at(dx, dy, dz));
            }

            @Override
            public boolean aboveInBuildHeight() {
                return fx.inBuildHeight(origin.getRelative(0, 1, 0));
            }
        };
    }

    /** TFC {@code addLeaves}: leaves on the empty sides of a placed branch block, each carrying the tree's stem; settled at once. */
    private static void addLeaves(Services s, Block center, FruitTree tree, FruitTreeState parent, long now) {
        FruitTreeEffects fx = s.effects();
        List<int[]> offsets = FruitTreeGrowth.leafOffsets(new FruitTreeGrowth.LeafTerrain() {
            @Override
            public boolean isAir(int dx, int dy, int dz) {
                return fx.isAir(center.getRelative(dx, dy, dz));
            }

            @Override
            public boolean airLeavesOrBranch(int dx, int dy, int dz) {
                Block b = center.getRelative(dx, dy, dz);
                return fx.isAir(b) || parsed(fx, b).map(x -> x.part() != Part.SAPLING).orElse(false);
            }
        });
        for (int[] o : offsets) {
            Block target = center.getRelative(o[0], o[1], o[2]);
            FruitTreeState leaf = new FruitTreeState(parent.stemX(), parent.stemY(), parent.stemZ(), now, BerryState.NEVER_PICKED,
                parent.hydration(), parent.temperature(), parent.northern());
            BlockStateRef ref = leaves(tree, Lifecycle.HEALTHY, false);
            fx.place(target, ref, leaf);
            fx.track(target);
            tick(s, target, parse(ref).orElseThrow(), leaf, now, 0).ifPresent(settled -> fx.store(target, settled));
        }
    }

    private static FruitTreeState leaves(Services s, Block block, Parsed p, FruitTreeState state, Month month, boolean climateOk, long now) {
        boolean persistent = p.flag("persistent");
        if (persistent) {
            return state;
        }
        Lifecycle stored = Lifecycle.valueOf(p.ref().properties().getOrDefault("lifecycle", "healthy").toUpperCase(Locale.ROOT));
        Lifecycle expected = p.tree().lifecycleFor(month);
        Lifecycle current = stored;
        for (int i = 0; i < 4; i++) {
            Lifecycle next = FruitTreeRules.leavesLifecycle(false, climateOk, current, expected, now, state.lastPicked(),
                s.settings().pickBloomDelayTicks());
            if (next == current) {
                break;
            }
            current = next;
        }
        if (current != stored) {
            s.effects().show(block, leaves(p.tree(), current, false), state);
        }
        return state;
    }

    /** TFC {@code FruitTreeLeavesBlock.isValid}: persistent, or touching a branch on one of the six sides. */
    static boolean leavesValid(FruitTreeEffects fx, Block block, Parsed p) {
        boolean adjacent = false;
        for (int[] s : SIDES) {
            if (branchLike(parsed(fx, block.getRelative(s[0], s[1], s[2])))) {
                adjacent = true;
                break;
            }
        }
        return FruitTreeRules.leavesValid(p.flag("persistent"), adjacent);
    }

    /** TFC {@code FruitTreeSaplingBlock.canSurvive}. */
    static boolean saplingSurvives(FruitTreeEffects fx, Block block) {
        Block below = block.getRelative(0, -1, 0);
        Optional<Parsed> p = parsed(fx, below);
        if (branchLike(p)) {
            if (p.get().intProp("stage", 0) > 1) {
                return false;
            }
            return p.get().flag("north") || p.get().flag("east") || p.get().flag("south") || p.get().flag("west");
        }
        return fx.plantable(below);
    }

    /** TFC {@code findBaseOfTree}: the lowest branch of the tree a sapling was placed on, else the block under the sapling. */
    static Block findBase(FruitTreeEffects fx, Block belowSapling) {
        Optional<Parsed> start = parsed(fx, belowSapling);
        if (start.isEmpty()) {
            return belowSapling;
        }
        int[][] horizontal = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            if (start.get().flag(FLAGS[i])) {
                Block cur = belowSapling.getRelative(horizontal[i][0], 0, horizontal[i][1]);
                int guard = 0;
                while (branchLike(parsed(fx, cur)) && guard++ < 256) {
                    cur = cur.getRelative(0, -1, 0);
                }
                return cur.getRelative(0, 1, 0);
            }
        }
        return belowSapling;
    }

    private record Handler(Services services) implements DeviceHandler<FruitTreeState> {
        /** TFC {@code setPlacedBy}: the stem is the base of the tree the sapling stands on, else the sapling's own block. */
        @Override
        public DeviceResult<FruitTreeState> placed(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() == Part.BRANCH) {
                return DeviceResult.pass();
            }
            Parsed p = parsed.get();
            FruitTreeEffects fx = services.effects();
            Block stemBlock = ctx.block();
            if (p.part() == Part.SAPLING && branchLike(parsed(fx, ctx.block().getRelative(0, -1, 0)))) {
                stemBlock = findBase(fx, ctx.block().getRelative(0, -1, 0));
            }
            BerryEffects.Environment env = fx.environment(stemBlock);
            FruitTreeState state = new FruitTreeState(stemBlock.getX(), stemBlock.getY(), stemBlock.getZ(), ctx.now(), BerryState.NEVER_PICKED,
                env.hydration(), env.temperature(), env.northern());
            if (p.part() == Part.LEAVES && !p.flag("persistent")) { // TFC getStateForPlacement: leaves a player places are persistent
                fx.show(ctx.block(), leaves(p.tree(), Lifecycle.valueOf(p.ref().properties().getOrDefault("lifecycle", "healthy")
                    .toUpperCase(Locale.ROOT)), true), state);
            }
            fx.track(ctx.block());
            return DeviceResult.update(ctx.record().withState(state));
        }

        /** TFC {@code SeasonalPlantBlock.useItemOn}: fruiting leaves are picked. */
        @Override
        public DeviceResult<FruitTreeState> useWithItem(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() != Part.LEAVES || ctx.player() == null) {
                return DeviceResult.pass();
            }
            FruitTreeEffects fx = services.effects();
            Optional<FruitTreeState> settled = tick(services, ctx.block(), parsed.get(), ctx.deviceState(), ctx.now(), 0);
            FruitTreeState state = settled.orElse(ctx.deviceState());
            Parsed current = fx.blockAt(ctx.block()).flatMap(FruitTreeDevice::parse).orElse(parsed.get());
            Lifecycle lifecycle = Lifecycle.valueOf(current.ref().properties().getOrDefault("lifecycle", "healthy").toUpperCase(Locale.ROOT));
            if (lifecycle != Lifecycle.FRUITING) {
                return result(ctx, state);
            }
            Optional<ItemStack> product = fx.product(productId(current.tree()));
            if (product.isEmpty()) {
                return result(ctx, state);
            }
            fx.pickSound(ctx.block());
            fx.give(ctx.player(), product.get());
            FruitTreeState picked = state.withLastPicked(ctx.now());
            fx.show(ctx.block(), leaves(current.tree(), Lifecycle.HEALTHY, current.flag("persistent")), picked);
            return DeviceResult.consume(ctx.record().withState(picked));
        }

        /** A neighbour update: invalid leaves and unsupported saplings break, everything else is settled. */
        @Override
        public DeviceResult<FruitTreeState> neighbourChanged(DeviceContext<FruitTreeState> ctx) {
            Optional<Parsed> parsed = parse(ctx.state());
            if (parsed.isEmpty() || parsed.get().part() == Part.BRANCH) {
                return DeviceResult.pass();
            }
            Parsed p = parsed.get();
            FruitTreeEffects fx = services.effects();
            boolean ok = switch (p.part()) {
                case LEAVES -> leavesValid(fx, ctx.block(), p);
                case SAPLING -> saplingSurvives(fx, ctx.block());
                default -> true;
            };
            if (!ok) {
                fx.remove(ctx.block());
                return DeviceResult.pass();
            }
            Optional<FruitTreeState> settled = tick(services, ctx.block(), p, ctx.deviceState(), ctx.now(), 0);
            return settled.map(state -> result(ctx, state)).orElse(DeviceResult.pass());
        }

        private DeviceResult<FruitTreeState> result(DeviceContext<FruitTreeState> ctx, FruitTreeState state) {
            return state.equals(ctx.deviceState()) ? DeviceResult.pass() : DeviceResult.update(ctx.record().withState(state));
        }
    }

    /** For tests: every block id of every tree. */
    static String[] allIdsForTest() {
        List<String> ids = new java.util.ArrayList<>();
        for (FruitTree t : FruitTree.values()) {
            for (Part part : Part.values()) {
                ids.add(blockId(t, part));
            }
        }
        return ids.toArray(String[]::new);
    }
}
