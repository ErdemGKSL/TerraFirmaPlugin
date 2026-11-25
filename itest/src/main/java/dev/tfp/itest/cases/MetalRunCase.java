/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/SelfTests.java @ c3b4d9536 (the idea of an in-game run of the content),
 *       src/generated/resources/data/tfc/recipe/alloy/{bronze,weak_steel,weak_red_steel,weak_blue_steel}.json @ c3b4d9536,
 *       src/generated/resources/data/tfc/recipe/anvil/refined_iron_bloom.json @ c3b4d9536,
 *       src/generated/resources/data/tfc/recipe/anvil/metal/ingot/*.json @ c3b4d9536,
 *       src/generated/resources/data/tfc/recipe/welding/metal/ingot/*.json @ c3b4d9536
 */
package dev.tfp.itest.cases;

import dev.tfp.core.alloy.AlloyRange;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.AnvilTiers;
import dev.tfp.core.anvil.ForgingProgress;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.casting.CastingRecipe;
import dev.tfp.core.casting.CastingRules;
import dev.tfp.core.crucible.CrucibleData;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.metal.Metal;
import dev.tfp.core.metal.MetalTier;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.alloy.AlloyRecipes;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.forge.AnvilForger;
import dev.tfp.paper.anvil.forge.ForgeHeat;
import dev.tfp.paper.anvil.plan.ForgingPdc;
import dev.tfp.paper.anvil.plan.PlanBook;
import dev.tfp.paper.anvil.weld.WeldBook;
import dev.tfp.paper.anvil.weld.Welder;
import dev.tfp.paper.bloomery.BloomDevice;
import dev.tfp.paper.bloomery.BloomeryDevice;
import dev.tfp.paper.crucible.CrucibleDevice;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code metal.run_rules} (M4; PAR-REL-05 extends it to the full metal run). First the ported pure metal rules, compared
 * with the values the core golden tests pin; then the metal chain of TFC's progression on real CraftEngine blocks: a
 * {@code tfp:crucible}, a {@code tfp:bloomery} chimney and a {@code tfp:metal/anvil/red_steel}, driven through the device
 * records (the self-test server has no player to click).
 *
 * <p>Rules, each against its golden source in {@code core/src/test/resources}:
 * <ul>
 *   <li>the metal table ({@code metal/metals.tsv}, {@code MetalTableTest}): 27 metals, 9 tool tiers, bronze
 *       melts at 950 and forges at 570, its tier is level 2 with 1300 uses, 7.3 speed and 4.0 damage; copper
 *       melts at 1080; the unknown fallback id is not a metal;</li>
 *   <li>one alloy ({@code alloy/alloy-recipes.tsv}, {@code FluidAlloyTest}): 900 mB copper and 100 mB tin give bronze;</li>
 *   <li>one casting ({@code casting/casting-recipes.tsv}): {@code copper_fire_ingot} (100 mB, one ingot, chance 0.01);</li>
 *   <li>one forging step count ({@code ForgeRuleTest}): offset 4 is reached in 2 steps (+7 bend, -3 hit light).</li>
 * </ul>
 *
 * <p>The chain, every product of a step feeding the next:
 * <ol>
 *   <li><b>copper</b>: four normal native copper ores (25 mB each) melt in the crucible to 100 mB of copper, poured into a
 *       fired ingot mold in the output slot; the cooled mold casts a copper ingot;</li>
 *   <li><b>bronze</b>: that copper ingot, seven more and one tin ingot melt to 900 mB of bronze (copper 0.89); one ingot is
 *       cast;</li>
 *   <li><b>wrought iron</b>: four cast iron ingots and eight charcoal dropped into a lit three-high bloomery become a bloom
 *       after the 14400 tick burn; its raw iron bloom is forged on the anvil to a refined bloom, and that to a wrought iron
 *       ingot;</li>
 *   <li><b>steel</b>: a pig iron ingot is forged to high carbon steel, that to steel;</li>
 *   <li><b>black steel</b>: that steel ingot with four more, two nickel and two black bronze melt to weak steel (5/2/2);
 *       a cast weak steel ingot is welded with pig iron (flux on the anvil) to high carbon black steel, forged to black
 *       steel;</li>
 *   <li><b>red and blue steel</b>: that black steel ingot with nine more, five steel and three brass plus two rose gold
 *       (red) melt to weak red steel (0.5/0.25/0.15/0.1); weak blue steel takes bismuth bronze and sterling silver
 *       instead; each cast ingot is welded with black steel and the high carbon ingot forged to red and blue steel.</li>
 * </ol>
 *
 * <p>Every forging plan is the recipe of the anvil (TFC's plan selection, target seeded from the world), worked with the
 * fewest steps that meet its rules and target (a search over TFC's step offsets). Substitutes, as the self-test needs no
 * player: the crucible is heated at {@value #CRUCIBLE_HEAT} C the way a heat source below hands it runs
 * ({@link CrucibleDevice#heat}, a charcoal forge with bellows in TFC); items for the anvil get their working heat written
 * directly (the forge's heating is {@code forge.continuous}); the crucible is emptied between two alloys; the side metals
 * (tin, nickel, black bronze, brass, rose gold, bismuth bronze, sterling silver), the cast iron and the pig iron are stock
 * ingots. The pig iron should come from a blast furnace, whose chimney needs {@code tfp:reinforced_fire_bricks}
 * (PAR-DECO-16, open): that step is PAR-REL-13.
 *
 * <p>Any difference fails the case with an {@link AssertionError}; nothing is skipped.
 */
public final class MetalRunCase implements SelfTestCase {
    static final int METAL_COUNT = 27;
    static final int TIER_COUNT = 9;
    /** Chunks east of spawn: away from every other block case (40 to 80). */
    static final int CHUNK_OFFSET = 88;
    static final float CRUCIBLE_HEAT = 1600f;
    static final String ANVIL = "tfp:metal/anvil/red_steel";
    static final String HAMMER = "tfp:metal/hammer/red_steel";
    static final String FLUX = "tfp:powder/flux";
    static final String MOLD = "tfp:ceramic/ingot_mold";
    static final String ORE = "tfp:ore/normal_native_copper";
    static final String CHARCOAL = "minecraft:charcoal";
    static final String RAW_BLOOM = "tfp:raw_iron_bloom";
    static final int BURN_TICKS = 14_400;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "metal.run_rules: the core metal golden values, then copper -> bronze -> bloomery wrought iron -> steel -> black "
            + "steel -> red and blue steel on a real crucible, bloomery and anvil (M4, PAR-REL-05)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(120 + 20 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null) {
            throw new Skip("the feature modules are not enabled");
        }
        for (Class<?> c : List.of(DeviceService.class, BloomeryDevice.class, BloomDevice.class, Recipes.class, AlloyRecipes.class,
                MoldItems.class, Tags.class, ItemHeatHooks.class, ItemHeatDefinitionsCache.class)) {
            if (services.get(c).isEmpty()) {
                throw new Skip(c.getSimpleName() + " is not provided (its module is not enabled)");
            }
        }
        return OnThread.script("metal-run", () -> {
            String rules = check();
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            return rules + "; chain: " + new Chain(tfp, services).run();
        });
    }

    static String check() {
        // Metal table (metal/metals.tsv, MetalTableTest).
        expect("metal count", METAL_COUNT, Metal.values().length);
        expect("tool tier count", TIER_COUNT, MetalTier.values().length);
        expect("unknown fallback is not a metal", true, Metal.fromId(Metal.UNKNOWN_ID).isEmpty());
        Metal bronze = Metal.fromId("tfc:metal/bronze").orElseThrow(() -> new AssertionError("tfc:metal/bronze is not a metal"));
        expect("bronze melt temperature", 950.0f, bronze.meltTemperature());
        expect("bronze ingot forging temperature", 570.0f, bronze.ingotForgingTemperature());
        expect("bronze tier level", 2, bronze.tier());
        MetalTier bronzeTier = bronze.toolTier().orElseThrow(() -> new AssertionError("bronze has no tool tier"));
        expect("bronze tier uses", 1300, bronzeTier.durability());
        expect("bronze tier speed", 7.3f, bronzeTier.speed());
        expect("bronze tier damage", 4.0f, bronzeTier.attackDamage());
        expect("copper melt temperature", 1080.0f, Metal.COPPER.meltTemperature());

        // One alloy (alloy/alloy-recipes.tsv, FluidAlloyTest): bronze is copper 0.88-0.92 and tin 0.08-0.12.
        AlloyRecipe bronzeRecipe = new AlloyRecipe("tfc:metal/bronze", List.of(
                new AlloyRange("tfc:metal/copper", 0.88, 0.92),
                new AlloyRange("tfc:metal/tin", 0.08, 0.12)));
        FluidAlloy mix = FluidAlloy.empty()
                .fill("tfc:metal/copper", 900, 16_000).alloy()
                .fill("tfc:metal/tin", 100, 16_000).alloy();
        expect("900 copper and 100 tin make bronze", "tfc:metal/bronze", mix.result(List.of(bronzeRecipe)));

        // One casting (casting/casting-recipes.tsv): copper_fire_ingot, chance 0.01.
        CastingRecipe fireIngot = new CastingRecipe("copper_fire_ingot", "tfc:ceramic/fire_ingot_mold",
                "tfc:metal/copper", 100, "tfc:metal/ingot/copper", 1, 0.01f);
        List<CastingRecipe> castings = List.of(fireIngot);
        expect("fire ingot mold with 100 copper matches", true,
                CastingRules.find(castings, "tfc:ceramic/fire_ingot_mold", "tfc:metal/copper", 100).isPresent());
        expect("fire ingot mold with 99 copper does not match", true,
                CastingRules.find(castings, "tfc:ceramic/fire_ingot_mold", "tfc:metal/copper", 99).isEmpty());
        expect("fire ingot yields one copper ingot", new CastingRules.Output("tfc:metal/ingot/copper", 1),
                CastingRules.assemble(fireIngot));
        expect("fire ingot breaks below its chance", true, CastingRules.breaksOnUse(fireIngot, 0.005f));
        expect("fire ingot does not break at its chance", false, CastingRules.breaksOnUse(fireIngot, 0.01f));

        // One forging check (ForgeRuleTest): offset 4 is two steps (+7 bend, -3 hit light).
        expect("forging offset 4 takes 2 steps", 2, ForgeStep.getOptimalStepsToTarget(4));
        return "27 metals, 9 tiers, bronze 950 / tier 2 / 1300 uses, 900 cu + 100 sn = bronze, copper_fire_ingot "
                + "100 mB -> 1 ingot (0.01), forge offset 4 = 2 steps";
    }

    /**
     * The fewest steps from a fresh work that finish {@code recipe} at {@code target} with no leeway: a breadth-first search
     * over the work offset and the last three steps, the only state the rules and the target read.
     */
    static List<ForgeStep> solve(AnvilRecipe recipe, int target) {
        record Node(ForgingProgress progress, Node previous, ForgeStep step) {
        }
        record Key(int work, List<ForgeStep> last) {
        }
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<Key> seen = new HashSet<>();
        queue.add(new Node(ForgingProgress.EMPTY, null, null));
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            for (ForgeStep step : ForgeStep.VALUES) {
                if (!n.progress().canApply(step)) {
                    continue;
                }
                ForgingProgress next = n.progress().apply(step);
                if (next.isDestroyed()) {
                    continue;
                }
                Node child = new Node(next, n, step);
                if (recipe.checkComplete(next, target, 0)) {
                    List<ForgeStep> path = new ArrayList<>();
                    for (Node at = child; at.step() != null; at = at.previous()) {
                        path.add(at.step());
                    }
                    Collections.reverse(path);
                    return path;
                }
                if (seen.add(new Key(next.work(), next.steps().steps()))) {
                    queue.add(child);
                }
            }
        }
        throw new AssertionError("no step sequence finishes " + recipe.id() + " at target " + target);
    }

    /** One run of the chain in its own chunk. Script thread; world access goes through region tasks. */
    private static final class Chain {
        private final TfpPlugin tfp;
        private final Scheduler s;
        private final DeviceService devices;
        private final StackCodec stacks;
        private final CustomBlocks blocks;
        private final CustomItems items;
        private final Recipes recipes;
        private final AlloyRecipes alloys;
        private final MoldItems molds;
        private final Tags tags;
        private final ItemHeatHooks heat;
        private final ItemHeatDefinitionsCache definitions;
        private final BloomeryDevice bloomery;
        private final BloomDevice bloom;
        private final World world;
        private final int cx;
        private final int cz;
        private final int x;
        private final int z;
        private final List<String> log = new ArrayList<>();
        private int y = Integer.MIN_VALUE;
        private Block crucible;
        private Block anvil;
        private DeviceKind<CrucibleData> crucibleKind;
        private DeviceKind<AnvilState> anvilKind;
        private int tier;
        private PlanBook plans;
        private AnvilForger forger;
        private Welder welder;
        /** The smith: the forger reads only its name (for the forging bonus); the hammer is in the anvil's slot. */
        private final Player smith = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "SelfTest";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "metal.run smith";
                default -> defaultValue(method.getReturnType());
            });

        Chain(TfpPlugin tfp, ModuleServices services) {
            this.tfp = tfp;
            this.s = tfp.scheduler();
            this.devices = services.require(DeviceService.class);
            this.stacks = devices.stacks();
            this.blocks = tfp.integrations().customBlocks();
            this.items = tfp.integrations().customItems();
            this.recipes = services.require(Recipes.class);
            this.alloys = services.require(AlloyRecipes.class);
            this.molds = services.require(MoldItems.class);
            this.tags = services.require(Tags.class);
            this.heat = services.require(ItemHeatHooks.class);
            this.definitions = services.require(ItemHeatDefinitionsCache.class);
            this.bloomery = services.require(BloomeryDevice.class);
            this.bloom = services.require(BloomDevice.class);
            this.world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            this.cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            this.cz = spawn.getBlockZ() >> 4;
            this.x = (cx << 4) + 8;
            this.z = (cz << 4) + 8;
        }

        String run() throws Exception {
            for (String id : List.of(CrucibleDevice.ID, ANVIL, BloomeryDevice.ID, BloomDevice.ID)) {
                check(blocks.exists(id), id + " is not a loaded CraftEngine block");
            }
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            try {
                build();
                ItemStack copper = melt("copper", repeat(ORE, 4), "tfp:metal/copper", 100, "copper");
                List<ItemStack> bronzeIn = new ArrayList<>(List.of(copper));
                bronzeIn.addAll(repeat(ingot("copper"), 7));
                bronzeIn.add(stock(ingot("tin")));
                melt("bronze", bronzeIn, "tfp:metal/bronze", 900, "bronze");

                ItemStack raw = bloom();
                ItemStack refined = forge(raw, "tfp:refined_iron_bloom");
                ItemStack wrought = forge(refined, ingot("wrought_iron"));
                check(wrought.getAmount() == 1, "the wrought iron forging gave " + wrought.getAmount() + " ingots");

                ItemStack highCarbon = forge(stock(ingot("pig_iron")), ingot("high_carbon_steel"));
                ItemStack steel = forge(highCarbon, ingot("steel"));

                List<ItemStack> weakIn = new ArrayList<>(List.of(steel));
                weakIn.addAll(repeat(ingot("steel"), 4));
                weakIn.addAll(repeat(ingot("nickel"), 2));
                weakIn.addAll(repeat(ingot("black_bronze"), 2));
                ItemStack weak = melt("weak steel", weakIn, "tfp:metal/weak_steel", 900, "weak_steel");
                ItemStack blackHigh = weld(weak, stock(ingot("pig_iron")), ingot("high_carbon_black_steel"));
                ItemStack black = forge(blackHigh, ingot("black_steel"));

                for (String colour : List.of("red", "blue")) {
                    List<ItemStack> in = new ArrayList<>();
                    if (colour.equals("red")) {
                        in.add(black);
                    } else {
                        in.add(stock(ingot("black_steel")));
                    }
                    in.addAll(repeat(ingot("black_steel"), 9));
                    in.addAll(repeat(ingot("steel"), 5));
                    in.addAll(repeat(ingot(colour.equals("red") ? "brass" : "bismuth_bronze"), 3));
                    in.addAll(repeat(ingot(colour.equals("red") ? "rose_gold" : "sterling_silver"), 2));
                    ItemStack weakColour = melt("weak " + colour + " steel", in, "tfp:metal/weak_" + colour + "_steel", 2000,
                        "weak_" + colour + "_steel");
                    ItemStack high = weld(weakColour, stock(ingot("black_steel")), ingot("high_carbon_" + colour + "_steel"));
                    forge(high, ingot(colour + "_steel"));
                }
                return String.join("; ", log);
            } finally {
                cleanup();
            }
        }

        // ---- the site -------------------------------------------------------------------------------------

        /** A cleared box; the bloomery at the centre, the crucible 5 east, the anvil 5 west, each on stone. */
        private void build() throws Exception {
            region(() -> {
                y = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 6, z, log);
                box((b, dy) -> b.setType(dy == -1 ? Material.STONE : Material.AIR, false));
                crucible = world.getBlockAt(x + 5, y, z);
                anvil = world.getBlockAt(x - 5, y, z);
                check(blocks.place(crucible, BlockStateRef.of(CrucibleDevice.ID), false), "place(crucible) returned false");
                check(blocks.place(anvil, BlockStateRef.of(ANVIL).with(AnvilDevice.FACING, "north"), false),
                    "place(anvil) returned false");
                return null;
            });
            crucibleKind = kind(CrucibleDevice.ID);
            anvilKind = kind(ANVIL);
            tier = AnvilTiers.tier(ANVIL).orElseThrow();
            plans = new PlanBook(recipes);
            ForgeHeat forgeHeat = ForgeHeat.of(heat, items::idOf, definitions, this::now);
            forger = new AnvilForger(new AnvilForger.Env(stacks, plans::recipes, recipes::ingredients,
                stack -> tags.is(stack, AnvilDevice.HAMMERS), forgeHeat, items, () -> ForgingConfig.DEFAULTS, () -> 0,
                (player, stack) -> stack)); // no player to damage the hammer through; the hammer's wear is not checked here
            welder = new Welder(new Welder.Env(stacks, new WeldBook(recipes), recipes::ingredients, stack -> {
                Optional<ItemHeatDefinitions.HeatData> data = items.idOf(stack).flatMap(i -> definitions.get().get(i));
                return data.isEmpty() || heat.temperature(stack) >= data.get().weldingTemperature();
            }, forgeHeat, (entry, main) -> recipes.singleOutput(entry.provider(), main), () -> ForgingConfig.DEFAULTS));
            ItemStack hammer = stock(HAMMER);
            check(tags.is(hammer, AnvilDevice.HAMMERS), HAMMER + " is not in #" + AnvilDevice.HAMMERS);
            ItemStack flux = items.create(FLUX, 8).orElseThrow(() -> new AssertionError(FLUX + " is not a CraftEngine item"));
            check(tags.is(flux, AnvilDevice.FLUXES), FLUX + " is not in #" + AnvilDevice.FLUXES);
            region(() -> edit(anvil, anvilKind, r -> r.withSlot(AnvilSlots.HAMMER, stacks.encode(hammer))
                .withSlot(AnvilSlots.CATALYST, stacks.encode(flux))));
            log.add("site at " + x + "," + y + "," + z + ": crucible, " + ANVIL + " (tier " + tier + ") with hammer and flux");
        }

        private interface BoxCell {
            void at(Block b, int dy);
        }

        private void box(BoxCell cell) {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -3; dz <= 4; dz++) {
                    for (int dy = -1; dy <= 5; dy++) {
                        cell.at(world.getBlockAt(x + dx, y + dy, z + dz), dy);
                    }
                }
            }
        }

        private void cleanup() {
            if (y != Integer.MIN_VALUE) {
                try {
                    region(() -> {
                        box((b, dy) -> {
                            blocks.remove(b);
                            b.setType(Material.AIR, false);
                        });
                        for (var e : world.getNearbyEntities(new Location(world, x + 0.5, y + 0.5, z + 0.5), 8, 6, 5)) {
                            if (e instanceof org.bukkit.entity.Item item) {
                                item.remove();
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("metal.run cleanup failed: " + e);
                }
            }
            try {
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            } catch (Exception | AssertionError e) {
                tfp.getLogger().warning("metal.run chunk release failed: " + e);
            }
        }

        // ---- crucible: melt, alloy, cast ------------------------------------------------------------------

        /**
         * Empties the crucible, melts {@code inputs} (nine slots at a time) at {@link #CRUCIBLE_HEAT}, checks the alloy is
         * {@code amount} mB of {@code fluid}, pours 100 mB into a fired ingot mold in the output slot, cools the mold and
         * casts it: returns the {@code tfp:metal/ingot/<metal>}.
         */
        private ItemStack melt(String what, List<ItemStack> inputs, String fluid, int amount, String metal) throws Exception {
            region(() -> edit(crucible, crucibleKind, r -> {
                DeviceRecord<CrucibleData> out = r.withState(r.state().withAlloy(FluidAlloy.empty()));
                for (int slot = 0; slot < CrucibleDevice.SLOTS; slot++) {
                    out = out.withSlot(slot, stacks.encode(ItemStack.empty()));
                }
                return out;
            }));
            ArrayDeque<ItemStack> queue = new ArrayDeque<>(inputs);
            int rounds = 0;
            while (true) {
                int[] left = {0};
                region(() -> edit(crucible, crucibleKind, r -> {
                    DeviceRecord<CrucibleData> out = r;
                    for (int slot = 0; slot < CrucibleRules.INPUT_SLOTS; slot++) {
                        if (StackCodec.isEmpty(stacks.decode(out.slot(slot))) && !queue.isEmpty()) {
                            ItemStack one = queue.poll();
                            check(crucibleKind.filter().canInsert(slot, one), "the crucible refuses " + items.idOf(one));
                            out = out.withSlot(slot, stacks.encode(one));
                        }
                        if (!StackCodec.isEmpty(stacks.decode(out.slot(slot)))) {
                            left[0]++;
                        }
                    }
                    return out;
                }));
                if (left[0] == 0 && queue.isEmpty()) {
                    break;
                }
                check(++rounds <= 24, what + ": " + left[0] + " inputs still unmelted after " + (rounds - 1) + " heat runs");
                heatCrucible(1_200);
            }
            DeviceRecord<CrucibleData> melted = region(() -> edit(crucible, crucibleKind, r -> r));
            FluidAlloy alloy = melted.state().alloy();
            check(alloy.amount() == amount, what + ": the crucible holds " + alloy.amount() + " mB, expected " + amount);
            String result = alloy.result(alloys.get());
            check(result.equals(fluid), what + ": the alloy " + alloy.contents() + " is " + result + ", expected " + fluid);
            check(melted.state().degrees() >= MoldItems.meltTemperature(fluid),
                what + ": the crucible is at " + melted.state().degrees() + " C, below the melt of " + fluid);

            ItemStack empty = stock(MOLD);
            region(() -> edit(crucible, crucibleKind, r -> r.withSlot(CrucibleDevice.SLOT_OUTPUT, stacks.encode(empty))));
            heatCrucible(200);
            ItemStack[] taken = {null};
            DeviceRecord<CrucibleData> poured = region(() -> edit(crucible, crucibleKind, r -> {
                taken[0] = stacks.decode(r.slot(CrucibleDevice.SLOT_OUTPUT));
                return r.withSlot(CrucibleDevice.SLOT_OUTPUT, stacks.encode(ItemStack.empty()));
            }));
            ItemStack mold = taken[0];
            check(!StackCodec.isEmpty(mold), what + ": the mold left the output slot");
            FluidAmount content = molds.content(mold);
            check(content.fluid().equals(fluid) && content.amount() == 100,
                what + ": the mold holds " + content + ", expected 100 mB of " + fluid);
            check(poured.state().alloy().amount() == amount - 100,
                what + ": the crucible kept " + poured.state().alloy().amount() + " mB after the pour");
            float hot = molds.temperature(mold);
            int cooled = 0;
            while (molds.isMolten(mold)) {
                check(++cooled <= 40, what + ": the mold is still molten at " + molds.temperature(mold) + " C");
                skip(1_200);
            }
            MoldItems.Cast cast = molds.cast(mold).orElseThrow(() -> new AssertionError(what + ": the mold casts nothing"));
            ItemStack ingot = cast.result();
            String want = ingot(metal);
            check(items.idOf(ingot).filter(want::equals).isPresent(), what + ": the mold cast " + items.idOf(ingot) + ", expected "
                + want);
            log.add(what + ": " + inputs.size() + " items -> " + amount + " mB " + result + " (" + rounds + " heat runs), mold "
                + Math.round(hot) + " C cooled in " + cooled + " skips -> " + want);
            ItemStack one = ingot.clone();
            one.setAmount(1);
            return one;
        }

        /** {@code ticks} pass, and the crucible is caught up as a heat source would give it {@link #CRUCIBLE_HEAT} over them. */
        private void heatCrucible(int ticks) throws Exception {
            skip(ticks);
            long now = now();
            region(() -> CrucibleDevice.heat(now - ticks + 1, now, CRUCIBLE_HEAT,
                () -> devices.edit(crucible, crucibleKind, (r, t) -> r)
                    .orElseThrow(() -> new AssertionError("the crucible has no device record"))));
        }

        // ---- bloomery -------------------------------------------------------------------------------------

        /** {@link BloomeryCase}'s chimney at the centre of the site: four cast iron ingots become a bloom; one raw bloom. */
        private ItemStack bloom() throws Exception {
            ItemStack iron = items.create(ingot("cast_iron"), 4)
                .orElseThrow(() -> new AssertionError(ingot("cast_iron") + " is not a CraftEngine item"));
            ItemStack coal = items.create(CHARCOAL, 8).orElse(new ItemStack(Material.CHARCOAL, 8));
            Block inside = world.getBlockAt(x, y, z);
            Block gate = inside.getRelative(0, 0, 1); // faces south
            region(() -> {
                int[][] walls = {{0, -1, 0}, {0, -1, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {-1, 0, 1}, {1, 0, 1}};
                for (int[] w : walls) {
                    inside.getRelative(w[0], w[1], w[2]).setType(Material.STONE, false);
                }
                for (int level = 1; level <= 3; level++) {
                    inside.getRelative(1, level, 0).setType(Material.STONE, false);
                    inside.getRelative(-1, level, 0).setType(Material.STONE, false);
                    inside.getRelative(0, level, -1).setType(Material.STONE, false);
                    inside.getRelative(0, level, 1).setType(Material.STONE, false);
                }
                check(blocks.place(gate, BloomeryDevice.state("south", false, false), false), "place(bloomery) returned false");
                world.dropItem(inside.getLocation().add(0.5, 1.5, 0.5), iron);
                world.dropItem(inside.getLocation().add(0.5, 1.5, 0.5), coal);
                return null;
            });
            pollRegion(() -> {
                devices.neighbourChanged(gate);
                return bloomery.contents(devices.edit(gate, bloomery.kind(), (r, now) -> r)
                    .orElseThrow(() -> new AssertionError("no bloomery record"))).size() >= 12;
            }, "the bloomery took 4 ingots and 8 charcoal");
            check(region(() -> bloomery.lightAt(devices, gate)), "the bloomery did not light");
            long before = now();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, BURN_TICKS + 100)));
            long skipped = now() - before;
            check(skipped >= BURN_TICKS, "the calendar skip moved " + skipped + " player ticks, expected " + BURN_TICKS);
            pollRegion(() -> {
                devices.neighbourChanged(gate);
                return blocks.stateAt(inside).map(BlockStateRef::id).filter(BloomDevice.ID::equals).isPresent();
            }, "the bloom block");
            ItemStack raw = region(() -> {
                var r = bloom.read(devices, inside).orElseThrow(() -> new AssertionError("the bloom has no record"));
                ItemStack held = stacks.decode(r.slot(0));
                check(items.idOf(held).filter(RAW_BLOOM::equals).isPresent(), "the bloom holds " + items.idOf(held));
                return held;
            });
            ItemStack one = raw.clone();
            one.setAmount(1);
            log.add("bloomery: 4 cast iron + 8 charcoal, +" + skipped + " ticks -> " + RAW_BLOOM);
            return one;
        }

        // ---- anvil: forge and weld ------------------------------------------------------------------------

        /**
         * Forges {@code input} on the anvil to {@code result}: the anvil recipe for it as the plan (target from the world
         * seed), the input at its working heat, the fewest steps; the last one must complete the recipe.
         */
        private ItemStack forge(ItemStack input, String result) throws Exception {
            ItemStack main = input.clone();
            warm(main);
            String from = items.idOf(main).orElse(main.getType().getKey().toString());
            return region(() -> {
                List<AnvilRecipe> candidates = plans.recipes().candidates(main, tier, recipes.ingredients());
                AnvilRecipe recipe = candidates.stream().filter(r -> sameItem(r.output().item(), result)).findFirst()
                    .orElseThrow(() -> new AssertionError("no anvil plan makes " + result + " from " + from + " on tier " + tier
                        + " (plans: " + candidates.stream().map(AnvilRecipe::id).toList() + ")"));
                int target = recipe.computeTarget(world.getSeed());
                ForgingPdc.write(main, new ForgingPdc.Forging(recipe.id(), target, 0, ForgeSteps.EMPTY));
                edit(anvil, anvilKind, r -> r.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(main))
                    .withSlot(AnvilSlots.INPUT_SECOND, stacks.encode(ItemStack.empty())));
                List<ForgeStep> steps = solve(recipe, target);
                for (int i = 0; i < steps.size(); i++) {
                    ForgeStep step = steps.get(i);
                    AnvilForger.Outcome[] o = {null};
                    edit(anvil, anvilKind, r -> {
                        o[0] = forger.work(smith, r, tier, step);
                        return o[0].record();
                    });
                    AnvilForger.Result want = i == steps.size() - 1 ? AnvilForger.Result.COMPLETED : AnvilForger.Result.WORKED;
                    check(o[0].result() == want, recipe.id() + ": step " + (i + 1) + "/" + steps.size() + " " + step + " was "
                        + o[0].result() + (o[0].message() == null ? "" : " (" + o[0].message() + ")") + ", expected " + want);
                }
                ItemStack[] out = {null};
                edit(anvil, anvilKind, r -> {
                    out[0] = stacks.decode(r.slot(AnvilSlots.INPUT_MAIN));
                    return r.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(ItemStack.empty()));
                });
                check(items.idOf(out[0]).filter(result::equals).isPresent(),
                    recipe.id() + " left " + items.idOf(out[0]) + " on the anvil, expected " + result);
                log.add("anvil " + from + " -> " + result + " (target " + target + ", " + steps.size() + " steps)");
                return out[0];
            });
        }

        /** Welds {@code main} and {@code second} on the anvil with one flux, both at their welding heat. */
        private ItemStack weld(ItemStack main, ItemStack second, String result) throws Exception {
            ItemStack a = main.clone();
            ItemStack b = second.clone();
            warm(a);
            warm(b);
            String what = items.idOf(a).orElse("?") + " + " + items.idOf(b).orElse("?");
            return region(() -> {
                int[] fluxBefore = {0};
                edit(anvil, anvilKind, r -> {
                    fluxBefore[0] = stacks.decode(r.slot(AnvilSlots.CATALYST)).getAmount();
                    return r.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(a)).withSlot(AnvilSlots.INPUT_SECOND, stacks.encode(b));
                });
                Welder.Outcome[] o = {null};
                DeviceRecord<AnvilState> after = edit(anvil, anvilKind, r -> {
                    o[0] = welder.weld(tier, r);
                    return o[0].record();
                });
                check(o[0].result() == Welder.Result.SUCCESS, "welding " + what + " was " + o[0].result()
                    + (o[0].message() == null ? "" : " (" + o[0].message() + ")"));
                ItemStack out = stacks.decode(after.slot(AnvilSlots.INPUT_MAIN));
                check(items.idOf(out).filter(result::equals).isPresent(), "welding " + what + " gave " + items.idOf(out)
                    + ", expected " + result);
                check(StackCodec.isEmpty(stacks.decode(after.slot(AnvilSlots.INPUT_SECOND))), "the second input stayed");
                int fluxAfter = StackCodec.isEmpty(stacks.decode(after.slot(AnvilSlots.CATALYST))) ? 0
                    : stacks.decode(after.slot(AnvilSlots.CATALYST)).getAmount();
                check(fluxAfter == fluxBefore[0] - 1, "the weld used " + (fluxBefore[0] - fluxAfter) + " flux, expected 1");
                edit(anvil, anvilKind, r -> r.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(ItemStack.empty())));
                log.add("weld " + what + " -> " + result);
                return out;
            });
        }

        /** Writes the item's working heat now: 10 C over its welding temperature (below its melt), as a forge would. */
        private void warm(ItemStack stack) {
            String id = items.idOf(stack).orElseThrow(() -> new AssertionError(stack + " is not a custom item"));
            Optional<ItemHeatDefinitions.HeatData> data = definitions.get().get(id);
            if (data.isEmpty()) {
                return; // no heat capability: the anvil does not check it (TFC)
            }
            float t = data.get().canWeld() ? data.get().weldingTemperature() + 10f : data.get().forgingTemperature() + 10f;
            check(HeatPdc.write(stack, new HeatState(t, now())), "the heat of " + id + " was not written");
        }

        // ---- helpers --------------------------------------------------------------------------------------

        @SuppressWarnings("unchecked")
        private <S> DeviceKind<S> kind(String blockId) {
            return (DeviceKind<S>) devices.kinds().find(blockId)
                .orElseThrow(() -> new AssertionError("no device kind for " + blockId));
        }

        private <S> DeviceRecord<S> edit(Block b, DeviceKind<S> kind, java.util.function.UnaryOperator<DeviceRecord<S>> change) {
            return devices.edit(b, kind, (r, now) -> change.apply(r))
                .orElseThrow(() -> new AssertionError("no " + kind.id() + " record at " + b.getX() + "," + b.getY() + "," + b.getZ()));
        }

        private <T> T region(Callable<T> body) throws Exception {
            return OnThread.region(s, world, cx, cz, STEP, body);
        }

        private void pollRegion(Callable<Boolean> read, String what) throws Exception {
            long deadline = System.nanoTime() + STEP.toNanos();
            while (System.nanoTime() < deadline) {
                if (region(read)) {
                    return;
                }
                Thread.sleep(500);
            }
            throw new AssertionError(what + ": not reached after " + STEP.toSeconds() + " s");
        }

        private void skip(int ticks) throws Exception {
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, ticks)));
        }

        private long now() {
            return tfp.clock().get().playerTicks();
        }

        private ItemStack stock(String id) {
            return items.create(id, 1).orElseThrow(() -> new AssertionError(id + " is not a CraftEngine item"));
        }

        private List<ItemStack> repeat(String id, int n) {
            List<ItemStack> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(stock(id));
            }
            return out;
        }
    }

    static String ingot(String metal) {
        return "tfp:metal/ingot/" + metal;
    }

    /** A recipe output id ({@code tfc:} or {@code tfp:}) names the item {@code id}. */
    static boolean sameItem(String output, String id) {
        return output.equals(id) || output.substring(output.indexOf(':') + 1).equals(id.substring(id.indexOf(':') + 1));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == int.class) {
            return 0;
        }
        return type == short.class ? (short) 0 : (byte) 0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(String what, Object want, Object got) {
        if (!want.equals(got)) {
            throw new AssertionError(what + ": expected " + want + " but got " + got);
        }
    }
}
