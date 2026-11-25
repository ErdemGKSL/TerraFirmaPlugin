/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.gather.GatherPiece;
import dev.tfp.core.gather.GatherPieces;
import dev.tfp.core.gather.GatherRules;
import dev.tfp.core.gather.GatherYield;
import dev.tfp.core.item.ContainerRules;
import dev.tfp.core.kiln.PitKilnFiring;
import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnRules;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.core.knap.KnappingDefinitions;
import dev.tfp.core.knap.KnappingPattern;
import dev.tfp.core.knap.KnappingRecipe;
import dev.tfp.core.knap.KnappingRules;
import dev.tfp.core.knap.KnappingSession;
import dev.tfp.core.knap.KnappingType;
import dev.tfp.core.placed.PlacedRules;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.kiln.PaperKilnItems;
import dev.tfp.paper.kiln.PitKilnDevice;
import dev.tfp.paper.mold.MoldItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.placed.PlacedItemDevice;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * {@code stone_age.run} (PAR-REL-04, the M3 exit criterion): the stone-age progression scripted on a running server, each
 * step feeding the next with the items the previous one made.
 *
 * <ol>
 *   <li><b>spawn</b>: the world's spawn is known and a chunk {@value #CHUNK_OFFSET} chunks east of it loads.</li>
 *   <li><b>gather</b> (PAR-GATHER-01): a {@code tfp:rock/loose/granite} block of count 2 is picked up by TFC's
 *       {@code GroundcoverBlock.useItemOn} rule ({@link GatherRules#pickup}): two granite rocks as CraftEngine items. When
 *       the loose rock block is a loaded CraftEngine block (PAR-WORLD-31) it is also placed, read back and removed in the
 *       world; until then that part is listed as skipped.</li>
 *   <li><b>knap</b> (PAR-PROC-01): the rocks are a {@code tfp:rock} knapping input; a session clicks the grid into the
 *       igneous intrusive knife head pattern, {@link KnappingRules#find} gives the recipe and the recipe runtime builds
 *       {@code tfp:stone/knife_head/igneous_intrusive}.</li>
 *   <li><b>tool</b> (PAR-TOOL-01): the head over a stick in a crafting grid is matched by the server's recipe manager
 *       (CraftEngine's shaped recipe) and gives {@code tfp:stone/knife/igneous_intrusive}.</li>
 *   <li><b>fire</b> (PAR-HEAT-05/06): three sticks and one TFC oak log on a stone block, a strong start (the
 *       firestarter's draws fixed to strong with firepit base 1.0) builds a lit {@code tfp:firepit}.</li>
 *   <li><b>cook</b> (PAR-HEAT-06/19): raw beef goes into the lit firepit's input slot, the calendar moves
 *       {@value #COOK_TICKS} ticks and the catch-up leaves {@code tfp:food/cooked_beef} in output slot 5.</li>
 *   <li><b>pottery</b> (PAR-POT-01/02/03): five clay balls are knapped into two unfired ingot molds; they are put into a
 *       placed item ({@link PlacedRules#insert}), straw ({@code #tfc:pit_kiln_straw}) and eight logs
 *       ({@code #tfc:pit_kiln_logs}) build the pit kiln stages to 15, it is lit and fired by the lazy rules
 *       ({@link PitKilnRules#evaluate}) and every content slot is resolved by the kiln's real item operations
 *       ({@link PaperKilnItems}: heat data and the heating recipe), giving {@code tfp:ceramic/ingot_mold}. The world part
 *       (the {@code tfp:placed_item} and {@code tfp:pit_kiln} CraftEngine blocks) does not exist yet: listed as skipped
 *       until PAR-POT-14, whose {@code kiln.fire} case then covers it.</li>
 *   <li><b>casting</b> (PAR-POT-05): molten copper (1250 C) is poured into a fired ingot mold step by step
 *       ({@link MoldItems#pourStep}) until it holds 100 mB, the calendar moves until it is solid, and
 *       {@link MoldItems#cast} gives {@code tfp:metal/ingot/copper}. Melting the copper itself (vessel in a fire) is not
 *       scripted: the source is a molten fluid.</li>
 * </ol>
 *
 * <p>A step whose prerequisite is not shipped is recorded as skipped with its reason; every other step still runs. A
 * failing check fails the case. When any step or step part was skipped the whole case reports itself skipped, with the
 * passed steps and the skip reasons in the message, never as passed.
 *
 * <p>Threads: the script runs on its own virtual thread; world work goes through the {@link Scheduler} (region thread of
 * the chunk, global thread for chunk tickets, the calendar and the crafting lookup). Item-only steps work on stacks the
 * script owns.
 */
public final class StoneAgeRunCase implements SelfTestCase {
    /** Chunks east of spawn: away from every other case's chunk (24..64, rotation 80). */
    static final int CHUNK_OFFSET = 72;
    static final String LOOSE_ROCK = "tfp:rock/loose/granite";
    static final String KNIFE_HEAD = "tfp:stone/knife_head/igneous_intrusive";
    static final String KNIFE = "tfp:stone/knife/igneous_intrusive";
    static final String ROCK_TYPE = "tfp:rock";
    static final String CLAY_TYPE = "tfp:clay";
    static final String UNFIRED_MOLD = "tfp:ceramic/unfired_ingot_mold";
    static final String MOLD = "tfp:ceramic/ingot_mold";
    static final String STRAW = "tfp:straw";
    static final String BEEF = "tfp:food/beef";
    static final String COOKED_BEEF = "tfp:food/cooked_beef";
    static final String COPPER = "tfp:metal/copper";
    static final String COPPER_INGOT = "tfp:metal/ingot/copper";
    static final float COPPER_POUR_TEMPERATURE = 1250f;
    static final int COOK_TICKS = 1_000;
    static final int COOL_STEP_TICKS = 2_400;
    static final int COOL_STEPS = 20;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "stone_age.run: loose rocks -> knapped knife -> fire -> cooked beef -> fired ingot mold -> copper ingot "
                + "(PAR-REL-04)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(30 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled: no TFC item exists");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null) {
            throw new Skip("the feature modules are not enabled");
        }
        List<Class<?>> needed = List.of(Tags.class, Recipes.class, DeviceService.class, FireStarting.class,
                HeatingService.class, ItemHeatDefinitionsCache.class, ItemSizes.class, MoldItems.class);
        for (Class<?> c : needed) {
            if (services.get(c).isEmpty()) {
                throw new Skip("the module providing " + c.getSimpleName() + " is not enabled");
            }
        }
        return OnThread.script("stone-age", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            if (!OnThread.poll(STEP, () -> tfp.registries() != null && tfp.registries().version() != 0)) {
                throw new AssertionError("the data registries were never published");
            }
            services.require(Recipes.class).rebuild().get(STEP.toSeconds(), TimeUnit.SECONDS);
            return new Run(tfp, services).run();
        });
    }

    /** One run of the chain; the steps share the items they make. */
    private static final class Run {
        private final TfpPlugin tfp;
        private final Scheduler s;
        private final CustomItems items;
        private final CustomBlocks blocks;
        private final Tags tags;
        private final Recipes recipes;
        private final DeviceService devices;
        private final FireStarting fire;
        private final HeatingService heating;
        private final ItemHeatDefinitionsCache heatDefinitions;
        private final ItemSizes sizes;
        private final MoldItems molds;
        private final Map<String, String> passed = new LinkedHashMap<>();
        private final Map<String, String> skipped = new LinkedHashMap<>();
        private World world;
        private int cx;
        private int cz;
        private int x;
        private int z;
        private int groundY = Integer.MIN_VALUE;
        private ItemStack rocks;
        private Block firepit;

        Run(TfpPlugin tfp, ModuleServices services) {
            this.tfp = tfp;
            this.s = tfp.scheduler();
            this.items = tfp.integrations().customItems();
            this.blocks = tfp.integrations().customBlocks();
            this.tags = services.require(Tags.class);
            this.recipes = services.require(Recipes.class);
            this.devices = services.require(DeviceService.class);
            this.fire = services.require(FireStarting.class);
            this.heating = services.require(HeatingService.class);
            this.heatDefinitions = services.require(ItemHeatDefinitionsCache.class);
            this.sizes = services.require(ItemSizes.class);
            this.molds = services.require(MoldItems.class);
        }

        String run() throws Exception {
            try {
                step("spawn", this::spawn);
                step("gather", this::gather);
                step("knap", this::knap);
                step("tool", this::tool);
                step("fire", this::fire);
                step("cook", this::cook);
                step("pottery", this::pottery);
                step("casting", this::casting);
            } finally {
                cleanup();
            }
            String report = "passed: " + passed;
            if (!skipped.isEmpty()) {
                throw new Skip("skipped: " + skipped + "; " + report);
            }
            return report;
        }

        /** Runs one step; a {@link Skip} is recorded, anything else (an assertion) fails the case. */
        private void step(String name, java.util.concurrent.Callable<String> body) throws Exception {
            try {
                String detail = body.call();
                passed.put(name, detail);
            } catch (Skip e) {
                skipped.put(name, e.getMessage());
            } catch (AssertionError e) {
                throw new AssertionError("step " + name + ": " + e.getMessage() + " (passed before: " + passed.keySet()
                        + ", skipped: " + skipped + ")", e);
            }
        }

        /** A part of a step that cannot run yet: listed as skipped under {@code step/part}, the step goes on. */
        private void skipPart(String step, String part, String reason) {
            skipped.put(step + "/" + part, reason);
        }

        // 1. spawn ------------------------------------------------------------------------------------------------------

        private String spawn() throws Exception {
            world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
            cz = spawn.getBlockZ() >> 4;
            x = (cx << 4) + 8;
            z = (cz << 4) + 8;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            // the ground: a stone block (sturdy top face) with three cells of air above it
            OnThread.region(s, world, cx, cz, STEP, () -> {
                int y = Math.min(world.getHighestBlockYAt(x, z) + 1, world.getMaxHeight() - 5);
                groundY = y;
                world.getBlockAt(x, y, z).setType(Material.STONE, false);
                for (int dy = 1; dy <= 3; dy++) {
                    Block b = world.getBlockAt(x, y + dy, z);
                    if (blocks.stateAt(b).isPresent()) {
                        blocks.remove(b);
                    }
                    b.setType(Material.AIR, false);
                }
                clearItems(x, y + 1, z);
                return null;
            });
            return world.getName() + " spawn " + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ()
                    + ", work cell " + x + "," + (groundY + 1) + "," + z;
        }

        // 2. gather -----------------------------------------------------------------------------------------------------

        private String gather() throws Exception {
            Map<String, String> props = Map.of("count", "2");
            GatherPiece piece = GatherPieces.of(LOOSE_ROCK, props)
                    .orElseThrow(() -> new AssertionError(LOOSE_ROCK + " is no gather piece"));
            check(piece instanceof GatherPiece.LooseRock r && r.count() == 2, "piece " + piece + " is not 2 loose rocks");
            List<GatherYield> yields = GatherRules.pickup(piece, false);
            check(yields.size() == 1, "pickup yields " + yields);
            String itemId = GatherPieces.ourItem(yields.getFirst());
            check(itemId.equals(LOOSE_ROCK), "pickup gives " + itemId + ", expected " + LOOSE_ROCK);
            check(GatherRules.pickup(piece, true).isEmpty(), "creative pickup gives items");
            rocks = items.create(itemId, yields.getFirst().count())
                    .orElseThrow(() -> new AssertionError(itemId + " is not a CraftEngine item"));
            check(rocks.getAmount() == 2, "pickup stack holds " + rocks.getAmount());
            String where;
            if (blocks.exists(LOOSE_ROCK)) {
                where = worldPickup(props);
            } else {
                skipPart("gather", "world", LOOSE_ROCK + " is not a CraftEngine block yet (PAR-WORLD-31): the pickup "
                        + "ran through the gather rules only, no block was placed or removed");
                where = "no world block";
            }
            return "2 x " + itemId + " (" + where + ")";
        }

        /** The block placed on the ground, read back as the same piece, removed as the pickup does. */
        private String worldPickup(Map<String, String> props) throws Exception {
            return OnThread.region(s, world, cx, cz, STEP, () -> {
                Block b = world.getBlockAt(x, groundY + 1, z);
                BlockStateRef state = BlockStateRef.of(LOOSE_ROCK).with("count", props.get("count"));
                check(blocks.place(b, state, false), "place(" + state + ") returned false");
                BlockStateRef read = blocks.stateAt(b)
                        .orElseThrow(() -> new AssertionError("no CraftEngine block after placing " + state));
                Optional<GatherPiece> piece = GatherPieces.of(read.id(), read.properties());
                check(piece.isPresent() && piece.get() instanceof GatherPiece.LooseRock r && r.count() == 2,
                        "the placed block reads as " + piece + " (" + read + ")");
                check(blocks.remove(b), "the loose rock block was not removed");
                check(blocks.stateAt(b).isEmpty(), "a block is left after the pickup: " + blocks.stateAt(b));
                return "placed, read back as " + read + " and removed";
            });
        }

        // 3. knap -------------------------------------------------------------------------------------------------------

        private String knap() {
            requireItem(rocks, "gather");
            KnappingDefinitions defs = KnappingDefinitions.fromRegistry(tfp.registries().get());
            TagLookup lookup = ItemIngredientContext.lookup(tags.snapshot());
            String rockId = tags.itemId(rocks).orElseThrow();
            KnappingType type = KnappingRules.typeOf(defs.types().values(), rockId, lookup)
                    .orElseThrow(() -> new AssertionError(rockId + " is no knapping input"));
            check(type.id().equals(ROCK_TYPE), rockId + " knaps as " + type.id() + ", expected " + ROCK_TYPE);
            check(rocks.getAmount() >= type.inputCount(), "knapping " + type.id() + " needs " + type.inputCount()
                    + " rocks, the pickup gave " + rocks.getAmount());
            KnappingSession session = new KnappingSession(type, false);
            ItemStack head = knapInto(defs, session, KNIFE_HEAD, rockId, lookup);
            int consumed = session.takeResult();
            int consumedOnClick = type.consumeAfterComplete() ? 0 : type.amountToConsume();
            rocks.setAmount(rocks.getAmount() - consumed - consumedOnClick);
            knifeHead = head;
            return type.id() + " (input " + type.inputCount() + ", " + (consumed + consumedOnClick) + " used) -> "
                    + head.getAmount() + " x " + KNIFE_HEAD;
        }

        private ItemStack knifeHead;

        /** Clicks a session's grid into the pattern of the first recipe giving {@code result} and builds the result. */
        private ItemStack knapInto(KnappingDefinitions defs, KnappingSession session, String result, String inputId,
                                   TagLookup lookup) {
            String typeId = session.type().id();
            KnappingRecipe recipe = defs.recipesOf(typeId).stream()
                    .filter(r -> r.result().item().equals(result) && r.matchesItem(inputId, lookup))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no " + typeId + " recipe gives " + result + " from " + inputId));
            KnappingPattern p = recipe.pattern();
            int removedNow = 0;
            for (int gy = 0; gy < KnappingPattern.MAX_HEIGHT; gy++) {
                for (int gx = 0; gx < KnappingPattern.MAX_WIDTH; gx++) {
                    boolean on = gx < p.getWidth() && gy < p.getHeight() ? p.get(gx, gy) : p.defaultIsOn();
                    if (!on) {
                        removedNow += session.click(KnappingRules.buttonIndex(gx, gy));
                    }
                }
            }
            check(session.modified(), "the " + typeId + " grid was never clicked");
            check(removedNow == (session.type().consumeAfterComplete() ? 0 : session.type().amountToConsume()),
                    "the clicks consumed " + removedNow);
            KnappingRecipe found = KnappingRules.find(defs.recipes(), typeId, session.grid(), inputId, lookup)
                    .orElseThrow(() -> new AssertionError("the knapped grid " + session.grid().rows()
                            + " matches no " + typeId + " recipe for " + inputId));
            check(found.result().item().equals(result), "the grid gives " + found.id() + " (" + found.result().item()
                    + "), expected " + result);
            ItemStack out = recipes.modifiers().create(defs.resultTemplate(found));
            check(out != null && !out.isEmpty(), "recipe " + found.id() + " built no stack");
            String id = items.idOf(out).orElse(out.getType().getKey().toString());
            check(id.equals(result), "recipe " + found.id() + " built " + id);
            return out;
        }

        // 4. tool -------------------------------------------------------------------------------------------------------

        private String tool() throws Exception {
            requireItem(knifeHead, "knap");
            ItemStack head = knifeHead.asOne();
            ItemStack[] grid = new ItemStack[9];
            grid[1] = head;
            grid[4] = new ItemStack(Material.STICK);
            Recipe recipe = OnThread.global(s, STEP, () -> Bukkit.getCraftingRecipe(grid, world));
            check(recipe != null, "the server matches no crafting recipe for " + KNIFE_HEAD + " over a stick");
            ItemStack result = recipe.getResult();
            String id = items.idOf(result).orElse(result.getType().getKey().toString());
            check(id.equals(KNIFE), "the crafting grid gives " + id + ", expected " + KNIFE);
            String key = recipe instanceof org.bukkit.Keyed k ? k.getKey().toString() : recipe.getClass().getSimpleName();
            return KNIFE_HEAD + " + stick -> " + KNIFE + " (recipe " + key + ")";
        }

        // 5. fire -------------------------------------------------------------------------------------------------------

        private String fire() throws Exception {
            if (!blocks.exists(FirepitDevice.ID)) {
                // shipped content (PAR-HEAT-19): its absence is a failure, as in firepit.cook
                throw new AssertionError(FirepitDevice.ID + " is not a loaded CraftEngine block (configuration/devices/"
                        + "firepit.yml missing from the installed content, or CraftEngine ran out of block states?)");
            }
            ItemStack log = FireStartCase.logStack(tfp, tags);
            if (items.idOf(log).isEmpty()) {
                throw new Skip(FireStartCase.LOG + " is no CraftEngine item: the pile would use a vanilla log without "
                        + "TFC fuel data, which cannot light");
            }
            int py = groundY + 1;
            OnThread.region(s, world, cx, cz, STEP, () -> {
                Location at = new Location(world, x + 0.5, py + 0.1, z + 0.5);
                for (ItemStack stack : List.of(new ItemStack(Material.STICK, 3), log.clone())) {
                    Item item = world.dropItem(at, stack);
                    item.setVelocity(new Vector());
                    item.setPickupDelay(Integer.MAX_VALUE);
                }
                return null;
            });
            CompletableFuture<FireStarting.Result> result = new CompletableFuture<>();
            ItemStack starter = items.create("tfp:firestarter", 1).orElse(new ItemStack(Material.STICK));
            OnThread.region(s, world, cx, cz, STEP, () -> {
                fire.start(world.getBlockAt(x, groundY, z), BlockFace.UP, null, starter,
                        FireStarting.Starter.fixed(FireStart.Strength.STRONG, 1.0), result::complete);
                return null;
            });
            FireStarting.Result r = OnThread.await(result, STEP, "the fire start");
            check(r == FireStarting.Result.FIREPIT, "a strong start on the stick and log pile gave " + r + ", not FIREPIT");
            Block b = world.getBlockAt(x, py, z);
            String detail = OnThread.region(s, world, cx, cz, STEP, () -> {
                String id = blocks.stateAt(b).map(BlockStateRef::id).orElse(b.getType().getKey().toString());
                check(id.equals(FirepitDevice.ID), "the pile cell holds " + id + ", not " + FirepitDevice.ID);
                DeviceRecord<FirepitData> rec = record(b);
                check(rec.state().lit(), "the firepit built from the pile is not lit: " + rec.state());
                int left = items(x, py, z).size();
                check(left == 0, left + " item entities are left on the ground");
                return "lit " + FirepitDevice.ID + " at " + rec.state().temperature() + " C, pile consumed";
            });
            firepit = b;
            return "3 sticks + " + items.idOf(log).orElseThrow() + ", strong start -> " + detail;
        }

        // 6. cook -------------------------------------------------------------------------------------------------------

        private String cook() throws Exception {
            if (firepit == null) {
                throw new Skip("no lit firepit: " + skipped.getOrDefault("fire", "the fire step did not run"));
            }
            ItemStack beef = items.create(BEEF, 1).orElseThrow(() -> new AssertionError(BEEF + " is not a CraftEngine item"));
            DeviceKind<FirepitData> kind = firepitKind();
            OnThread.region(s, world, cx, cz, STEP, () -> {
                Optional<DeviceRecord<FirepitData>> filled = devices.edit(firepit, kind,
                        (r, now) -> r.withSlot(FirepitSim.SLOT_ITEM_INPUT, devices.stacks().encode(beef)));
                check(filled.isPresent(), "the firepit has no device record");
                check(filled.get().state().lit(), "the firepit went out before the beef went in: " + filled.get().state());
                return null;
            });
            long skippedTicks = skipCalendar(COOK_TICKS);
            return OnThread.region(s, world, cx, cz, STEP, () -> {
                check(devices.catchUp(firepit).isPresent(), "the catch-up found no device");
                DeviceRecord<FirepitData> r = record(firepit);
                ItemBlob input = r.slot(FirepitSim.SLOT_ITEM_INPUT);
                ItemBlob output = r.slot(FirepitSim.SLOT_OUTPUT_1);
                check(input.isEmpty(), "the input slot still holds " + id(input));
                check(!output.isEmpty(), "output slot 5 is empty after " + skippedTicks + " ticks: " + r.state());
                String out = id(output);
                check(COOKED_BEEF.equals(out), "output slot 5 holds " + out + ", expected " + COOKED_BEEF);
                return BEEF + " in the pile's firepit, +" + skippedTicks + " ticks -> " + COOKED_BEEF;
            });
        }

        // 7. pottery ----------------------------------------------------------------------------------------------------

        private ItemStack firedMold;

        private String pottery() {
            // clay knapping: five clay balls -> two unfired ingot molds
            KnappingDefinitions defs = KnappingDefinitions.fromRegistry(tfp.registries().get());
            TagLookup lookup = ItemIngredientContext.lookup(tags.snapshot());
            ItemStack clay = new ItemStack(Material.CLAY_BALL, 5);
            String clayId = tags.itemId(clay).orElse("minecraft:clay_ball");
            KnappingType type = KnappingRules.typeOf(defs.types().values(), clayId, lookup)
                    .orElseThrow(() -> new AssertionError(clayId + " is no knapping input"));
            check(type.id().equals(CLAY_TYPE), clayId + " knaps as " + type.id() + ", expected " + CLAY_TYPE);
            check(clay.getAmount() >= type.inputCount(), "clay knapping needs " + type.inputCount());
            KnappingSession session = new KnappingSession(type, false);
            ItemStack unfired = knapInto(defs, session, UNFIRED_MOLD, clayId, lookup);
            session.takeResult();
            check(unfired.getAmount() >= 1, "no unfired mold");

            // the placed item: each mold into its own quadrant
            ItemStack one = unfired.asOne();
            ContainerRules.PlacedSlot size = ContainerRules.placed(sizes.sizing(one), sizes.limits());
            check(size == ContainerRules.PlacedSlot.GROUP, UNFIRED_MOLD + " places as " + size + ", expected GROUP");
            PlacedState placed = PlacedState.EMPTY;
            boolean[] occupied = new boolean[PlacedRules.SLOTS];
            ItemStack[] contents = new ItemStack[PitKilnState.CONTENT_SLOTS];
            int molds = Math.min(unfired.getAmount(), PlacedRules.SLOTS);
            for (int i = 0; i < molds; i++) {
                PlacedRules.Insert in = PlacedRules.insert(placed, occupied, i, size, true, 0f)
                        .orElseThrow(() -> new AssertionError("the placed item refused a mold"));
                placed = in.state();
                occupied[in.slot()] = true;
                contents[in.slot()] = one.clone();
            }

            // the kiln: straw from the placed item, up to the full straw layer, then eight logs
            ItemStack straw = items.create(STRAW, 1).orElseThrow(() -> new AssertionError(STRAW + " is not a CraftEngine item"));
            int strawValue = PitKilnLayers.strawValue(tags.is(straw, "tfc:pit_kiln_4_straw"), tags.is(straw, "tfc:pit_kiln_straw"));
            check(strawValue == PitKilnLayers.STRAW_SINGLE, STRAW + " has straw value " + strawValue);
            int stage = PitKilnLayers.stageFromPlaced(strawValue);
            int strawItems = 1;
            while (PitKilnLayers.canAddStraw(stage, strawValue)) {
                stage = PitKilnLayers.stageAfterStraw(stage, strawValue);
                strawItems++;
            }
            check(stage == PitKilnLayers.STRAW_END, "the straw stops at stage " + stage);
            ItemStack log = FireStartCase.logStack(tfp, tags);
            check(tags.is(log, "tfc:pit_kiln_logs"), "the log is not in #tfc:pit_kiln_logs");
            int logs = 0;
            while (PitKilnLayers.canAddLog(stage)) {
                stage = PitKilnLayers.stageAfterLog(stage);
                logs++;
            }
            check(PitKilnLayers.hasFuel(stage) && logs == PitKilnLayers.MAX_LOGS, "the kiln has stage " + stage
                    + " with " + logs + " logs");
            // the world checks of tryLight (sides, base, fire above) need the kiln block: taken as a valid kiln here
            check(PitKilnLayers.canLight(stage, false, true, true, true), "a full kiln cannot light");

            // light and fire, lazily: the state at the end of the burn
            PitKilnRules.Config config = PitKilnRules.Config.DEFAULT;
            long litTick = tfp.clock().get().playerTicks();
            PitKilnState kiln = PitKilnState.fromPlaced(placed).light(litTick);
            check(kiln.lit() && kiln.litTick() == litTick, "the kiln did not light: " + kiln);
            PitKilnRules.Outcome halfway = PitKilnRules.evaluate(true, litTick, litTick + config.durationTicks() / 2,
                    config, PitKilnRules.NEVER);
            check(halfway.phase() == PitKilnRules.Phase.LIT, "half way the kiln is " + halfway.phase());
            PitKilnRules.Outcome outcome = PitKilnRules.evaluate(true, litTick, litTick + config.durationTicks(),
                    config, PitKilnRules.NEVER);
            check(outcome.phase() == PitKilnRules.Phase.FIRED, "after the burn the kiln is " + outcome.phase());
            PaperKilnItems ops = new PaperKilnItems(heating, ItemHeatHooks.Capacities.byId(items::idOf, heatDefinitions));
            check(ops.heatable(one), UNFIRED_MOLD + " has no heat capacity");
            float recipeTemperature = ops.recipeTemperature(one);
            check(!Float.isNaN(recipeTemperature), UNFIRED_MOLD + " has no heating recipe");
            int fired = 0;
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] == null) {
                    continue;
                }
                ItemStack halfwayStack = PitKilnFiring.resolve(contents[slot], litTick, halfway, config, ops);
                check(UNFIRED_MOLD.equals(items.idOf(halfwayStack).orElse("")), "slot " + slot + " fired half way: "
                        + items.idOf(halfwayStack));
                ItemStack out = PitKilnFiring.resolve(contents[slot], litTick, outcome, config, ops);
                String id = items.idOf(out).orElse(out.getType().getKey().toString());
                check(MOLD.equals(id), "slot " + slot + " fired into " + id + ", expected " + MOLD);
                firedMold = out;
                fired++;
            }
            if (!blocks.exists(PlacedItemDevice.ID) || !blocks.exists(PitKilnDevice.ID)) {
                skipPart("pottery", "world", PlacedItemDevice.ID + " / " + PitKilnDevice.ID + " are not CraftEngine "
                        + "blocks yet (PAR-POT-14): the placed item and kiln ran through their pure rules and the kiln's "
                        + "item operations only, no block was built");
            }
            return "5 clay -> " + unfired.getAmount() + " x " + UNFIRED_MOLD + "; placed " + molds + ", " + strawItems
                    + " straw + " + logs + " logs (stage " + stage + "), lit at " + litTick + ", fired after "
                    + config.durationTicks() + " ticks (recipe " + recipeTemperature + " C) -> " + fired + " x " + MOLD;
        }

        // 8. casting ----------------------------------------------------------------------------------------------------

        private String casting() throws Exception {
            requireItem(firedMold, "pottery");
            ItemStack mold = firedMold.asOne();
            check(molds.isMold(mold), MOLD + " is no mold");
            FluidAmount source = new FluidAmount(COPPER, 1_000);
            int moved = 0;
            for (int i = 0; i < 200 && molds.content(mold).amount() < 100; i++) {
                int step = molds.pourStep(source, COPPER_POUR_TEMPERATURE, mold);
                if (step == 0) {
                    break;
                }
                moved += step;
            }
            FluidAmount content = molds.content(mold);
            check(COPPER.equals(content.fluid()) && content.amount() == 100, "the mold holds " + content + " after "
                    + moved + " mB poured");
            check(molds.isMolten(mold), "the poured copper is not molten: " + molds.temperature(mold) + " C");
            float poured = molds.temperature(mold);
            long cooled = 0;
            for (int i = 0; i < COOL_STEPS && molds.isMolten(mold); i++) {
                cooled += skipCalendar(COOL_STEP_TICKS);
            }
            check(!molds.isMolten(mold), "the copper is still molten after " + cooled + " ticks: " + molds.temperature(mold)
                    + " C");
            MoldItems.Cast cast = molds.cast(mold)
                    .orElseThrow(() -> new AssertionError("no casting recipe for " + MOLD + " with " + content));
            String id = items.idOf(cast.result()).orElse(cast.result().getType().getKey().toString());
            check(COPPER_INGOT.equals(id), "the casting gives " + id + ", expected " + COPPER_INGOT);
            return content.amount() + " mB " + COPPER + " at " + poured + " C into " + MOLD + ", solid after +" + cooled
                    + " ticks -> " + id + " (break chance " + cast.breakChance() + ")";
        }

        // helpers -------------------------------------------------------------------------------------------------------

        private long skipCalendar(int ticks) throws Exception {
            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, ticks)));
            long moved = tfp.clock().get().playerTicks() - before;
            check(moved >= ticks, "the calendar skip moved " + moved + " player ticks, expected " + ticks);
            return moved;
        }

        @SuppressWarnings("unchecked")
        private DeviceKind<FirepitData> firepitKind() {
            return (DeviceKind<FirepitData>) devices.kinds().all().stream().filter(k -> k.id().equals(FirepitDevice.ID))
                    .findFirst().orElseThrow(() -> new AssertionError("no device kind " + FirepitDevice.ID));
        }

        @SuppressWarnings("unchecked")
        private DeviceRecord<FirepitData> record(Block b) {
            DeviceService.Inspection i = devices.inspect(b).orElseThrow(() -> new AssertionError("the block at "
                    + b.getX() + "," + b.getY() + "," + b.getZ() + " is no device"));
            check(i.problem().isEmpty() && i.record().isPresent() && i.kindId().equals(FirepitDevice.ID),
                    "device " + i.kindId() + ": " + i.problem());
            return (DeviceRecord<FirepitData>) i.record().get();
        }

        private String id(ItemBlob blob) {
            ItemStack stack = devices.stacks().decode(blob);
            return items.idOf(stack).orElse(stack.getType().getKey().toString());
        }

        private void requireItem(ItemStack stack, String fromStep) {
            if (stack == null) {
                throw new Skip("the " + fromStep + " step made nothing: " + skipped.getOrDefault(fromStep, "it did not run"));
            }
        }

        /** Item entities in TFC's pile scan box. Region thread. */
        private List<Item> items(int px, int py, int pz) {
            BoundingBox box = new BoundingBox(px - 0.5, py, pz - 0.5, px + 1.5, py + 1, pz + 1.5);
            List<Item> out = new ArrayList<>();
            for (Entity e : world.getNearbyEntities(box, e -> e instanceof Item && e.isValid())) {
                out.add((Item) e);
            }
            return out;
        }

        private void clearItems(int px, int py, int pz) {
            for (Item item : items(px, py, pz)) {
                item.remove();
            }
        }

        private void cleanup() {
            if (world == null) {
                return;
            }
            if (groundY != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        Block p = world.getBlockAt(x, groundY + 1, z);
                        if (blocks.stateAt(p).isPresent()) {
                            blocks.remove(p);
                        }
                        p.setType(Material.AIR, false);
                        for (Entity e : world.getNearbyEntities(p.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2)) {
                            if (e instanceof Item item) {
                                item.remove(); // the slots and ash the removed firepit dropped
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("stone_age.run cleanup failed: " + e);
                }
            }
            try {
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            } catch (Exception | AssertionError e) {
                tfp.getLogger().warning("stone_age.run cleanup failed: " + e);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
