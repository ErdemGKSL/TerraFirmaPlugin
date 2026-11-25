/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.ProductPdc;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.hunt.HuntModule;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.livestock.LivestockModule;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.products.ProductsModule;
import dev.tfp.paper.scheduler.Scheduler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * {@code husbandry.run} (PAR-REL-06, M5 exit criteria): the husbandry chain scripted through the shipped paper services,
 * in one force-loaded chunk:
 * <ol>
 *   <li>AI: the cow carries the TFC livestock goals (PAR-FAUNA-08), the deer the prey goals (PAR-FAUNA-09);</li>
 *   <li>tame: an adult cow pair, a ewe and a hen are fed once a day through {@link LivestockModule#onFeed} (PAR-FAUNA-05)
 *       until the cows reach TFC's ready-to-mate familiarity (0.3);</li>
 *   <li>breed: the female's staggered check pairs her with the bull (fertilized, pregnancy day, male uses +5); after the
 *       gestation the check gives birth and a calf with a record appears;</li>
 *   <li>products (PAR-FAUNA-06): the cow is milked into a wooden bucket and the ewe sheared through
 *       {@link ProductsModule#onInteract}; the hen lays an egg on her check;</li>
 *   <li>slaughter (PAR-FAUNA-17): the bull and the deer are killed; their TFC drops (beef, venison, hides) appear and the
 *       vanilla carrier loot does not.</li>
 * </ol>
 * The interactions call the modules' own listener methods with a stand-in player (an interface proxy: no player can join
 * a self-test server), on the animal's owning thread, so no other plugin sees the stand-in. The animals are made
 * unaware (no goals run) so the pair stays within TFC's 8-block mating range; the goals are still installed and checked.
 *
 * <p>{@link Load} is the 200-animal load test of the same item.
 */
public final class HusbandryRunCase implements SelfTestCase {
    private static final Duration STEP = Duration.ofSeconds(20);
    private static final int MAX_FEED_ROUNDS = 8;
    /** Chunks west and south of the spawn chunk: away from the other animal cases. */
    private static final int CHUNK_DX = -6;
    private static final int CHUNK_DZ = 6;

    @Override
    public String name() {
        return "husbandry.run: tame, breed, milk, shear, lay and slaughter through the livestock, products, AI and hunt services (PAR-REL-06)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(240);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled: the food, bucket, wool and meat items are CraftEngine items");
        }
        AnimalService animals = tfp.animals();
        if (animals == null) {
            throw new AssertionError("no animal service");
        }
        LivestockModule livestock = module(tfp, LivestockModule.class, "livestock");
        ProductsModule products = module(tfp, ProductsModule.class, "products");
        module(tfp, HuntModule.class, "hunt");
        CustomItems items = tfp.integrations().customItems();
        for (String id : List.of(FOOD, BUCKET, WOOL)) {
            if (!items.exists(id)) {
                throw new Skip(id + " is not loaded: the generated pack was not installed this run");
            }
        }
        return OnThread.script("husbandry-run", () -> {
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            return chain(tfp, animals, livestock, products, items);
        });
    }

    static final String FOOD = "tfp:food/wheat_grain";
    static final String BUCKET = "tfp:wooden_bucket";
    static final String WOOL = "tfp:wool";

    /** The enabled module of {@code type}, or a skip naming the module id. */
    static <M extends FeatureModule> M module(TfpPlugin tfp, Class<M> type, String id) {
        if (!tfp.modules().isEnabled(id)) {
            throw new Skip("module '" + id + "' is not enabled this run");
        }
        return tfp.modules().modules().stream().filter(type::isInstance).map(type::cast).findFirst()
                .orElseThrow(() -> new Skip("module '" + id + "' (" + type.getSimpleName() + ") is not loaded"));
    }

    private static String chain(TfpPlugin tfp, AnimalService animals, LivestockModule livestock, ProductsModule products,
            CustomItems items) throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_DX;
        int cz = (spawn.getBlockZ() >> 4) + CHUNK_DZ;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        Location at = OnThread.region(s, world, cx, cz, STEP, () -> world.getHighestBlockAt((cx << 4) + 8, (cz << 4) + 8)
                .getLocation().add(0.5, 1, 0.5));
        List<Entity> spawned = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        try {
            Entity cow = spawnAt(animals, at, "tfc:cow", false, spawned);
            Entity bull = spawnAt(animals, at, "tfc:cow", true, spawned);
            Entity ewe = spawnAt(animals, at.clone().add(3, 0, 0), "tfc:sheep", false, spawned);
            Entity hen = spawnAt(animals, at.clone().add(-3, 0, 0), "tfc:chicken", false, spawned);
            Entity deer = spawnAt(animals, at.clone().add(0, 0, 3), "tfc:deer", null, spawned);
            for (Entity e : spawned) {
                OnThread.entity(s, e, STEP, () -> {
                    if (e instanceof Mob mob) {
                        mob.setAware(false);
                    }
                    return null;
                });
            }

            // 1. AI (FAUNA-08, FAUNA-09): our goals are on the carriers.
            String cowGoals = goalsFrom(s, cow, "dev.tfp.paper.livestockai.");
            String deerGoals = goalsFrom(s, deer, "dev.tfp.paper.prey.");
            steps.add("ai: cow " + cowGoals + ", deer " + deerGoals);

            // 2. Tame (FAUNA-05): one feed a day until the cows are ready to mate.
            List<Entity> herd = List.of(cow, bull, ewe, hen);
            int rounds = 0;
            while (true) {
                rounds++;
                for (Entity e : herd) {
                    feed(tfp, s, livestock, items, e);
                }
                if (familiarity(tfp, s, animals, cow) >= LivestockRules.READY_TO_MATE_FAMILIARITY
                        && familiarity(tfp, s, animals, bull) >= LivestockRules.READY_TO_MATE_FAMILIARITY) {
                    break;
                }
                if (rounds >= MAX_FEED_ROUNDS) {
                    throw new AssertionError("after " + rounds + " daily feeds the cows are at "
                            + familiarity(tfp, s, animals, cow) + " / " + familiarity(tfp, s, animals, bull)
                            + ", below " + LivestockRules.READY_TO_MATE_FAMILIARITY);
                }
                skipDays(tfp, 1);
                boolean hungry = OnThread.poll(STEP, () -> herd.stream().allMatch(e -> hungry(tfp, s, animals, e)));
                if (!hungry) {
                    throw new AssertionError("the herd is not hungry one day after round " + rounds);
                }
            }
            steps.add("tame: " + rounds + " daily feeds, cow familiarity " + familiarity(tfp, s, animals, cow));

            // 3. Breed: the female's check pairs her with the bull, then the gestation ends in a birth.
            if (!OnThread.poll(STEP, () -> state(s, animals, cow).fertilized())) {
                throw new AssertionError("the cow was not fertilized by her check within " + STEP.toSeconds() + " s: "
                        + state(s, animals, cow) + " / bull " + state(s, animals, bull));
            }
            AnimalState pregnant = state(s, animals, cow);
            AnimalState bullAfter = state(s, animals, bull);
            if (pregnant.pregnantDay() <= 0 || bullAfter.uses() != LivestockRules.MALE_USES_PER_FERTILIZATION) {
                throw new AssertionError("pairing: pregnant day " + pregnant.pregnantDay() + ", bull uses " + bullAfter.uses());
            }
            int gestation = LivestockSpecies.COW.gestationDays();
            skipDays(tfp, gestation + 1);
            if (!OnThread.poll(STEP, () -> !state(s, animals, cow).fertilized())) {
                throw new AssertionError("no birth " + (gestation + 1) + " days after fertilization: " + state(s, animals, cow));
            }
            List<Entity> calves = new ArrayList<>();
            boolean born = OnThread.poll(STEP, () -> {
                calves.clear();
                calves.addAll(near(s, world, cx, cz, cow, 6, e -> !spawned.contains(e)));
                return !calves.isEmpty();
            });
            if (!born) {
                throw new AssertionError("the cow gave birth (uses " + state(s, animals, cow).uses() + ") but no calf was found");
            }
            spawned.addAll(calves);
            Entity calf = calves.getFirst();
            AnimalRecord calfRecord = OnThread.entity(s, calf, STEP, () -> animals.record(calf).orElseThrow());
            long now = tfp.clock().get().playerTicks();
            if (!calfRecord.species().equals("tfc:cow") || AnimalCheck.ageAt(calfRecord, now) != LivestockAge.CHILD) {
                throw new AssertionError("the newborn is not a tfc:cow child: " + calfRecord);
            }
            if (state(s, animals, cow).uses() < LivestockRules.USES_PER_BIRTH) {
                throw new AssertionError("the mother did not gain the birth uses: " + state(s, animals, cow));
            }
            steps.add("breed: fertilized on day " + pregnant.pregnantDay() + ", " + calves.size() + " calf/calves after "
                    + (gestation + 1) + " days, calf familiarity " + calfRecord.state().familiarity());

            // 4. Products (FAUNA-06): milk, wool, eggs.
            ItemStack bucket = items.create(BUCKET, 1).orElseThrow();
            boolean milkTaken = interact(s, cow, bucket, e -> products.onInteract(e));
            var milk = ItemFluids.content(bucket);
            if (!milkTaken || milk.amount() != 1000 || !milk.fluid().contains("milk")) {
                throw new AssertionError("milking: event cancelled " + milkTaken + ", bucket holds " + milk);
            }
            if (OnThread.entity(s, cow, STEP, () -> ProductPdc.read(cow).producedTick()) <= 0L) {
                throw new AssertionError("milking did not start the cow's product cooldown");
            }
            Set<UUID> before = itemIds(s, world, cx, cz, ewe.getLocation(), 6);
            boolean sheared = interact(s, ewe, new ItemStack(Material.SHEARS), e -> products.onInteract(e));
            List<String> wool = new ArrayList<>();
            boolean woolDropped = OnThread.poll(STEP, () -> {
                wool.clear();
                wool.addAll(dropsNear(s, world, cx, cz, entityLocation(s, ewe), 6, before, items));
                return wool.contains(WOOL);
            });
            if (!sheared || !woolDropped) {
                throw new AssertionError("shearing: event cancelled " + sheared + ", drops " + wool);
            }
            long nowShear = tfp.clock().get().playerTicks();
            AnimalRecord eweRecord = OnThread.entity(s, ewe, STEP, () -> animals.record(ewe).orElseThrow());
            if (!OnThread.entity(s, ewe, STEP, () -> ProductPdc.isSheared(ewe, eweRecord, nowShear))) {
                throw new AssertionError("the ewe does not read as sheared after shearing");
            }
            List<String> eggs = new ArrayList<>();
            boolean laid = OnThread.poll(STEP, () -> {
                eggs.clear();
                eggs.addAll(dropsNear(s, world, cx, cz, entityLocation(s, hen), 8, Set.of(), items));
                return eggs.contains("minecraft:egg");
            });
            if (!laid) {
                throw new AssertionError("the hen laid no egg on her checks (familiarity " + familiarity(tfp, s, animals, hen)
                        + "), items near her: " + eggs);
            }
            steps.add("products: 1000 mB " + milk.fluid() + ", " + count(wool, WOOL) + " wool, egg laid");

            // 5. Slaughter (FAUNA-17): TFC drops replace the carrier loot.
            List<String> beef = slaughter(s, world, cx, cz, bull, items);
            expectDrops("bull", beef, List.of("tfp:food/beef", "tfp:large_raw_hide"), List.of("minecraft:beef",
                    "minecraft:leather"));
            spawned.remove(bull);
            List<String> venison = slaughter(s, world, cx, cz, deer, items);
            expectDrops("deer", venison, List.of("tfp:food/venison", "tfp:medium_raw_hide"), List.of());
            spawned.remove(deer);
            steps.add("slaughter: bull " + summary(beef) + "; deer " + summary(venison));
            return String.join("; ", steps);
        } finally {
            cleanup(tfp, s, world, cx, cz, at, spawned);
        }
    }

    // ---- steps ------------------------------------------------------------------------------------------

    private static Entity spawnAt(AnimalService animals, Location at, String species, Boolean male, List<Entity> into)
            throws Exception {
        Carrier carrier = animals.table().find(species)
                .orElseThrow(() -> new Skip(species + " is not in the carrier table"));
        Entity e = OnThread.await(animals.spawn(at, carrier, AnimalSpawnRoll.AgeHint.ADULT, male), STEP, "spawning " + species);
        into.add(e);
        return e;
    }

    /** The simple names of the goals on {@code e} from {@code pkg}; fails when there is none. */
    private static String goalsFrom(Scheduler s, Entity e, String pkg) throws Exception {
        List<String> names = OnThread.entity(s, e, STEP, () -> {
            List<String> out = new ArrayList<>();
            for (Goal<Mob> g : Bukkit.getMobGoals().getAllGoals((Mob) e)) {
                if (g.getClass().getName().startsWith(pkg)) {
                    out.add(g.getClass().getSimpleName());
                }
            }
            return out;
        });
        if (names.isEmpty()) {
            throw new AssertionError(e.getType().getKey() + " carries no goal from " + pkg);
        }
        return names.size() + " goals";
    }

    /** One feed through the livestock module, which must accept it (the animal is hungry). */
    private static void feed(TfpPlugin tfp, Scheduler s, LivestockModule livestock, CustomItems items, Entity e)
            throws Exception {
        ItemStack food = items.create(FOOD, 16).orElseThrow();
        long fedBefore = OnThread.entity(s, e, STEP, () -> tfp.animals().record(e).orElseThrow().state().lastFedTick());
        boolean cancelled = interact(s, e, food, livestock::onFeed);
        long fedAfter = OnThread.entity(s, e, STEP, () -> tfp.animals().record(e).orElseThrow().state().lastFedTick());
        if (!cancelled || fedAfter == fedBefore || food.getAmount() != 15) {
            throw new AssertionError("feeding " + e.getType().getKey() + " was not accepted: cancelled " + cancelled
                    + ", last fed " + fedBefore + " -> " + fedAfter + ", food left " + food.getAmount());
        }
    }

    /**
     * Right-clicks {@code target} with {@code held} through {@code listener}, on the target's thread, as a stand-in
     * survival player. Returns whether the listener cancelled the event (it handled the interaction).
     */
    private static boolean interact(Scheduler s, Entity target, ItemStack held, Consumer<PlayerInteractEntityEvent> listener)
            throws Exception {
        return OnThread.entity(s, target, STEP, () -> {
            PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(standIn(held, target.getWorld()), target,
                    EquipmentSlot.HAND);
            listener.accept(event);
            return event.isCancelled();
        });
    }

    /** A survival player holding {@code held} in the main hand; every other call returns a default. */
    static Player standIn(ItemStack held, World world) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(HusbandryRunCase.class.getClassLoader(),
                new Class<?>[] {PlayerInventory.class}, (p, m, args) -> switch (m.getName()) {
                    case "getItemInMainHand" -> held;
                    case "getItemInOffHand" -> ItemStack.empty();
                    default -> defaultValue(p, m, args);
                });
        UUID id = new UUID(0L, 0x7f9L);
        return (Player) Proxy.newProxyInstance(HusbandryRunCase.class.getClassLoader(), new Class<?>[] {Player.class},
                (p, m, args) -> switch (m.getName()) {
                    case "getInventory" -> inventory;
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "locale" -> Locale.US;
                    case "getLocale" -> "en_us";
                    case "getWorld" -> world;
                    case "getUniqueId" -> id;
                    case "getName" -> "tfp-selftest";
                    default -> defaultValue(p, m, args);
                });
    }

    private static Object defaultValue(Object proxy, java.lang.reflect.Method m, Object[] args) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "tfp-selftest stand-in " + m.getDeclaringClass().getSimpleName();
            default -> {
                Class<?> r = m.getReturnType();
                if (r == boolean.class) {
                    yield false;
                } else if (r == int.class || r == short.class || r == byte.class || r == char.class) {
                    yield r == char.class ? (Object) '\0' : (Object) 0;
                } else if (r == long.class) {
                    yield 0L;
                } else if (r == float.class) {
                    yield 0f;
                } else if (r == double.class) {
                    yield 0d;
                } else if (r == Optional.class) {
                    yield Optional.empty();
                }
                yield null;
            }
        };
    }

    private static AnimalState state(Scheduler s, AnimalService animals, Entity e) {
        try {
            return OnThread.entity(s, e, STEP, () -> animals.record(e).orElseThrow().state());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static float familiarity(TfpPlugin tfp, Scheduler s, AnimalService animals, Entity e) {
        return LivestockRules.familiarityAt(state(s, animals, e), tfp.clock().get().playerTicks());
    }

    private static boolean hungry(TfpPlugin tfp, Scheduler s, AnimalService animals, Entity e) {
        return LivestockRules.isHungry(state(s, animals, e), tfp.clock().get().playerTicks());
    }

    private static void skipDays(TfpPlugin tfp, int days) throws Exception {
        long before = tfp.clock().get().playerTicks();
        OnThread.global(tfp.scheduler(), STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.DAYS, days)));
        if (!OnThread.poll(STEP, () -> tfp.clock().get().playerTicks() >= before + (long) days * LivestockRules.TICKS_IN_DAY)) {
            throw new AssertionError("the clock did not move " + days + " day(s)");
        }
    }

    private static Location entityLocation(Scheduler s, Entity e) {
        try {
            return OnThread.entity(s, e, STEP, e::getLocation);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Our animals of the same species as {@code of} within {@code r} blocks of it that match {@code filter}. */
    private static List<Entity> near(Scheduler s, World world, int cx, int cz, Entity of, double r,
            java.util.function.Predicate<Entity> filter) {
        Location at = entityLocation(s, of);
        try {
            return OnThread.region(s, world, cx, cz, STEP, () -> {
                List<Entity> out = new ArrayList<>();
                for (LivingEntity e : world.getNearbyLivingEntities(at, r)) {
                    if (e != of && e.getType() == of.getType() && filter.test(e)
                            && dev.tfp.paper.animal.AnimalPdc.has(e.getPersistentDataContainer())) {
                        out.add(e);
                    }
                }
                return out;
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Set<UUID> itemIds(Scheduler s, World world, int cx, int cz, Location at, double r) throws Exception {
        return OnThread.region(s, world, cx, cz, STEP, () -> {
            Set<UUID> out = new HashSet<>();
            for (Item i : world.getNearbyEntitiesByType(Item.class, at, r)) {
                out.add(i.getUniqueId());
            }
            return out;
        });
    }

    /** The item ids (custom id, else the vanilla key) of the dropped items near {@code at} that are not in {@code except}. */
    private static List<String> dropsNear(Scheduler s, World world, int cx, int cz, Location at, double r, Set<UUID> except,
            CustomItems items) {
        try {
            return OnThread.region(s, world, cx, cz, STEP, () -> {
                List<String> out = new ArrayList<>();
                for (Item i : world.getNearbyEntitiesByType(Item.class, at, r)) {
                    if (!except.contains(i.getUniqueId())) {
                        ItemStack st = i.getItemStack();
                        String id = items.idOf(st).orElse(st.getType().getKey().toString());
                        for (int n = 0; n < st.getAmount(); n++) {
                            out.add(id);
                        }
                    }
                }
                return out;
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Kills {@code e} (no attacker) and returns the drops that appear where it died. */
    private static List<String> slaughter(Scheduler s, World world, int cx, int cz, Entity e, CustomItems items)
            throws Exception {
        Location at = entityLocation(s, e);
        Set<UUID> before = itemIds(s, world, cx, cz, at, 4);
        OnThread.entity(s, e, STEP, () -> {
            ((LivingEntity) e).setHealth(0);
            return null;
        });
        List<String> drops = new ArrayList<>();
        // The hunt drops are rolled off-thread and dropped on the region: wait until they appear, then a little longer.
        OnThread.poll(STEP, () -> {
            drops.clear();
            drops.addAll(dropsNear(s, world, cx, cz, at, 4, before, items));
            return drops.stream().anyMatch(d -> d.startsWith("tfp:"));
        });
        Thread.sleep(1000);
        drops.clear();
        drops.addAll(dropsNear(s, world, cx, cz, at, 4, before, items));
        return drops;
    }

    private static void expectDrops(String what, List<String> drops, List<String> required, List<String> forbidden) {
        for (String id : required) {
            if (!drops.contains(id)) {
                throw new AssertionError(what + ": expected " + id + " in the drops, got " + drops);
            }
        }
        for (String id : forbidden) {
            if (drops.contains(id)) {
                throw new AssertionError(what + ": the vanilla carrier loot " + id + " was not replaced: " + drops);
            }
        }
    }

    private static int count(List<String> list, String id) {
        return (int) list.stream().filter(id::equals).count();
    }

    private static String summary(List<String> drops) {
        Map<String, Integer> m = new TreeMap<>();
        drops.forEach(d -> m.merge(d, 1, Integer::sum));
        return m.toString();
    }

    private static void cleanup(TfpPlugin tfp, Scheduler s, World world, int cx, int cz, Location at, List<Entity> spawned) {
        try {
            for (Entity e : spawned) {
                try {
                    OnThread.entity(s, e, STEP, () -> {
                        tfp.integrations().entityModels().detach(e);
                        e.remove();
                        return null;
                    });
                } catch (Exception | AssertionError ignored) {
                    // already dead or removed
                }
            }
            OnThread.region(s, world, cx, cz, STEP, () -> {
                for (Item i : world.getNearbyEntitiesByType(Item.class, at, 12)) {
                    i.remove();
                }
                return null;
            });
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        } catch (Exception | AssertionError ex) {
            tfp.getLogger().warning("husbandry.run cleanup failed: " + ex);
        }
    }

    // ---- the load test ----------------------------------------------------------------------------------

    /**
     * {@code husbandry.load_200} (PAR-REL-06): 200 animals of ten species in a 3x3 chunk area, with their goals on, for
     * {@link #WINDOW}. The plugin's region-thread slice per tick is measured as
     * <ul>
     *   <li>the time spent in our goals (every TFC goal on the carriers is re-installed at its priority wrapped in a
     *       timer; vanilla goals and the carrier's own ticking are not ours and are not counted), read and reset by a
     *       region task every tick, plus</li>
     *   <li>the staggered check: each animal's check (age step and every species behaviour hook) is timed three times on
     *       its thread, and the heaviest of the {@link AnimalService#PERIOD} check phases (the sum of the costs of the
     *       animals whose check falls on that tick) is added to every tick, an upper bound.</li>
     * </ul>
     * Fails when a tick's slice is above PERF-TICK-01 (2 ms) or the 99th percentile is above PERF-COMMIT-01 (1.5 ms),
     * {@code docs/perf/budgets.md}. The whole-server tick times (Paper) and the region tick interval are reported only.
     */
    public static final class Load implements SelfTestCase {
        static final int ANIMALS = 200;
        static final Duration WINDOW = Duration.ofSeconds(60);
        static final Duration WARMUP = Duration.ofSeconds(5);
        static final double TICK_BUDGET_MS = 2.0;
        static final double P99_BUDGET_MS = 1.5;
        static final List<String> SPECIES = List.of("tfc:cow", "tfc:sheep", "tfc:pig", "tfc:goat", "tfc:chicken",
                "tfc:duck", "tfc:alpaca", "tfc:horse", "tfc:deer", "tfc:rabbit");
        /** Chunks from the spawn chunk: away from every other case. */
        private static final int CHUNK_DX = -40;
        private static final int CHUNK_DZ = 40;
        /** Random columns tried per animal for a dry surface. */
        private static final int LAND_ATTEMPTS = 20;
        /**
         * Force-loaded chunks around the centre: the herd spawns in the middle 3x3 and walks (the calendar runs), so a
         * margin of two chunks keeps strollers loaded for the window.
         */
        private static final int FORCE_RADIUS = 3;

        @Override
        public String name() {
            return "husbandry.load_200: 200 animals of 10 species keep the plugin's region-thread slice under PERF-TICK-01 / PERF-COMMIT-01 over 60 s (PAR-REL-06)";
        }

        @Override
        public Duration timeout() {
            return Duration.ofSeconds(240);
        }

        @Override
        public CompletableFuture<String> run(SelfTestContext ctx) {
            TfpPlugin tfp = ctx.tfp();
            AnimalService animals = tfp.animals();
            if (animals == null) {
                throw new AssertionError("no animal service");
            }
            return OnThread.script("husbandry-load", () -> {
                if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                    throw new AssertionError("the calendar was never published");
                }
                return load(tfp, animals);
            });
        }

        private static String load(TfpPlugin tfp, AnimalService animals) throws Exception {
            Scheduler s = tfp.scheduler();
            World world = Bukkit.getWorlds().getFirst();
            Location spawn = world.getSpawnLocation();
            // a land area (PAR-FAUNA-33: the first area was ocean on the self-test world, the herd only swam)
            int[] area = landArea(s, world, spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4);
            int cx = area[0];
            int cz = area[1];
            List<Carrier> mix = new ArrayList<>();
            for (String id : SPECIES) {
                animals.table().find(id).ifPresent(mix::add);
            }
            if (mix.size() < 2) {
                throw new Skip("fewer than two of " + SPECIES + " are in the carrier table");
            }
            for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
                for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                    OnThread.await(world.getChunkAtAsync(cx + dx, cz + dz), STEP, "loading chunk");
                }
            }
            OnThread.global(s, STEP, () -> {
                forceArea(world, cx, cz, true);
                return null;
            });
            List<Entity> herd = new ArrayList<>();
            // why a herd animal left the world (reported in the failure): species, cause, position
            Map<UUID, String> removals = new java.util.concurrent.ConcurrentHashMap<>();
            Set<UUID> herdIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
            org.bukkit.event.Listener removalTap = new org.bukkit.event.Listener() {
            };
            Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityRemoveEvent.class, removalTap,
                    org.bukkit.event.EventPriority.MONITOR, (l, ev) -> {
                        if (ev instanceof org.bukkit.event.entity.EntityRemoveEvent re
                                && herdIds.contains(re.getEntity().getUniqueId())) {
                            Location at = re.getEntity().getLocation();
                            removals.put(re.getEntity().getUniqueId(), String.format(Locale.ROOT, "%s %s at %d,%d,%d%s",
                                    re.getEntity().getType().getKey().getKey(), re.getCause(), at.getBlockX(), at.getBlockY(),
                                    at.getBlockZ(), re.getEntity() instanceof LivingEntity l2 && l2.isDead()
                                            ? " (dead: " + (l2.getLastDamageCause() == null ? "?"
                                            : l2.getLastDamageCause().getCause()) + ")" : ""));
                        }
                    }, tfp);
            AtomicLong goalNanos = new AtomicLong();
            Map<String, java.util.concurrent.atomic.LongAdder> byClass = new java.util.concurrent.ConcurrentHashMap<>();
            ConcurrentLinkedQueue<long[]> samples = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Long> serverTicks = new ConcurrentLinkedQueue<>();
            Slowest slowest = new Slowest();
            Scheduler.TaskHandle sampler = null;
            // The calendar runs as with a player online (PAR-FAUNA-33): on an empty server TFC's time stop freezes it, and
            // every calendar-timed goal (idle waits, flight, panic, cooldowns) would hold its first answer for the window.
            var calendar = tfp.calendar();
            boolean calendarWasForced = calendar != null && calendar.setRunWhenEmpty(true);
            try {
                java.util.Random r = new java.util.Random(0x7f9L);
                List<CompletableFuture<Entity>> pending = new ArrayList<>();
                for (int i = 0; i < ANIMALS; i++) {
                    Carrier c = mix.get(i % mix.size());
                    Location at = null;
                    for (int attempt = 0; attempt < LAND_ATTEMPTS && at == null; attempt++) {
                        int x = ((cx - 1) << 4) + 2 + r.nextInt(44);
                        int z = ((cz - 1) << 4) + 2 + r.nextInt(44);
                        boolean last = attempt == LAND_ATTEMPTS - 1;
                        at = OnThread.region(s, world, x >> 4, z >> 4, STEP, () -> {
                            org.bukkit.block.Block top = world.getHighestBlockAt(x, z);
                            return top.isLiquid() && !last ? null : top.getLocation().add(0.5, 1, 0.5);
                        });
                    }
                    pending.add(animals.spawn(at, c, AnimalSpawnRoll.AgeHint.RANDOM, null));
                }
                for (CompletableFuture<Entity> f : pending) {
                    Entity e = OnThread.await(f, STEP, "spawning the herd");
                    herd.add(e);
                    herdIds.add(e.getUniqueId());
                }
                // Re-install our goals at their priorities, each wrapped in the timer.
                int wrapped = 0;
                int lost = 0;
                for (Entity e : herd) {
                    try {
                        wrapped += OnThread.entity(s, e, STEP,
                                () -> wrapGoals(animals, (Mob) e, goalNanos, byClass, slowest));
                    } catch (Exception | AssertionError gone) {
                        lost++; // removed before its goals could be wrapped (an entity exception, say)
                    }
                }
                Thread.sleep(WARMUP.toMillis());
                // The staggered check cost per animal (max of three runs), grouped by its check phase.
                long period = AnimalService.PERIOD;
                long[] phase = new long[(int) period];
                long checkSum = 0;
                long checkMax = 0;
                for (Entity e : herd) {
                    long cost;
                    try {
                        cost = OnThread.entity(s, e, STEP, () -> timeCheck(tfp, animals, e));
                    } catch (Exception | AssertionError gone) {
                        continue;
                    }
                    phase[Math.floorMod(e.getUniqueId().hashCode(), (int) period)] += cost;
                    checkSum += cost;
                    checkMax = Math.max(checkMax, cost);
                }
                long worstPhase = Arrays.stream(phase).max().orElse(0);
                long[] last = {0L, 0L};
                sampler = s.regionRepeating(world, cx, cz, 1, 1, () -> {
                    long now = System.nanoTime();
                    long goals = goalNanos.getAndSet(0);
                    if (last[0] != 0L) {
                        // goal ns, interval ns, the JVM's total GC ms at the end of this tick (reported, not asserted)
                        samples.add(new long[] {goals, now - last[0], gcMillis()});
                        slowest.tick.incrementAndGet();
                    }
                    last[0] = now;
                    if (++last[1] % 100 == 0) {
                        try {
                            for (long t : Bukkit.getServer().getTickTimes()) {
                                serverTicks.add(t);
                            }
                        } catch (Throwable unsupported) {
                            // Folia: no global tick times
                        }
                    }
                });
                goalNanos.set(0);
                byClass.values().forEach(java.util.concurrent.atomic.LongAdder::reset);
                slowest.reset();
                dev.tfp.paper.prey.PathBudget.resetCounters();
                long gc0 = gcMillis();
                long gcCount0 = gcCount();
                Thread.sleep(WINDOW.toMillis());
                long gcWindow = gcMillis() - gc0;
                long gcCountWindow = gcCount() - gcCount0;
                long pathsStarted = dev.tfp.paper.prey.PathBudget.started();
                long pathsDeferred = dev.tfp.paper.prey.PathBudget.deferred();
                sampler.cancel();
                sampler = null;
                int alive = 0;
                // what the herd is doing at the end (reported): animals in water, our running goals by key
                Map<String, Integer> running = new TreeMap<>();
                int inWater = 0;
                for (Entity e : herd) {
                    try {
                        String[] state = OnThread.entity(s, e, STEP, () -> {
                            if (!e.isValid()) {
                                return null;
                            }
                            StringBuilder goalsNow = new StringBuilder();
                            for (Goal<Mob> g : Bukkit.getMobGoals().getRunningGoals((Mob) e)) {
                                goalsNow.append(g.getKey().getNamespacedKey().getKey()).append(' ');
                            }
                            return new String[] {Boolean.toString(e.isInWater()), goalsNow.toString()};
                        });
                        if (state != null) {
                            alive++;
                            inWater += Boolean.parseBoolean(state[0]) ? 1 : 0;
                            for (String k : state[1].trim().split(" ")) {
                                running.merge(k.isEmpty() ? "(none)" : k, 1, Integer::sum);
                            }
                        }
                    } catch (Exception | AssertionError gone) {
                        // removed: not alive
                    }
                }
                List<long[]> rows = new ArrayList<>(samples);
                if (rows.size() < WINDOW.toSeconds() * 10) {
                    throw new AssertionError("only " + rows.size() + " region ticks sampled in " + WINDOW.toSeconds() + " s");
                }
                double[] slice = rows.stream().mapToDouble(row -> (row[0] + worstPhase) / 1e6).sorted().toArray();
                double[] goalsMs = rows.stream().mapToDouble(row -> row[0] / 1e6).sorted().toArray();
                double[] interval = rows.stream().mapToDouble(row -> row[1] / 1e6).sorted().toArray();
                long over15 = Arrays.stream(slice).filter(v -> v > P99_BUDGET_MS).count();
                String serverMspt = serverTicks.isEmpty() ? "n/a (Folia)" : stats(serverTicks.stream()
                        .mapToDouble(t -> t / 1e6).sorted().toArray());
                int ticks = rows.size();
                String top = byClass.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum())).limit(6)
                        .map(en -> String.format(Locale.ROOT, "%s %.3f", en.getKey(), en.getValue().sum() / 1e6 / ticks))
                        .collect(java.util.stream.Collectors.joining(", "));
                String slow = slowest.calls().stream().map(c -> String.format(Locale.ROOT, "%s#%s %.3f (row %d%s)",
                        c.goal(), c.method(), c.nanos() / 1e6, c.tick(), gcIn(rows, c.tick())))
                        .collect(java.util.stream.Collectors.joining(", "));
                String started = slowest.starts.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                        .map(en -> en.getKey() + " " + en.getValue().sum())
                        .collect(java.util.stream.Collectors.joining(", "));
                String detail = String.format(Locale.ROOT,
                        "%d animals (%d species, %d alive at end), %d goals timed, %d ticks over %d s; plugin slice/tick ms %s"
                                + " (goals %s + worst check phase %.3f); check per animal mean %.1f us max %.1f us;"
                                + " ticks > 1.5 ms: %d; region tick interval ms %s; server MSPT ms %s; goal ms/tick by class: %s;"
                                + " slowest goal calls ms: %s; GC in the window: %d ms in %d collections (not asserted);"
                                + " path starts %d, deferred %d (PathBudget %d per tick, sensing on %s); goal starts: %s;"
                                + " at the end: %d in water, running goals %s; area centre chunk %d,%d, calendar running %s",
                        herd.size(), mix.size(), alive, wrapped, rows.size(), WINDOW.toSeconds(), stats(slice),
                        stats(goalsMs), worstPhase / 1e6, checkSum / 1e3 / herd.size(), checkMax / 1e3, over15,
                        stats(interval), serverMspt, top, slow, gcWindow, gcCountWindow, pathsStarted, pathsDeferred,
                        dev.tfp.paper.prey.PathBudget.PER_TICK, dev.tfp.paper.prey.Ticks.source(), started,
                        inWater, running, cx, cz, tfp.clock().get().running());
                tfp.getLogger().info("husbandry.load_200: " + detail);
                double max = slice[slice.length - 1];
                double p99 = pct(slice, 0.99);
                if (alive < herd.size()) {
                    throw new AssertionError((herd.size() - alive) + " of " + herd.size() + " animals were removed during the"
                            + " run (" + lost + " before the goals were wrapped; removals: " + removals.values()
                            + "; see the server log for entity exceptions): " + detail);
                }
                if (max > TICK_BUDGET_MS || p99 > P99_BUDGET_MS) {
                    throw new AssertionError("plugin slice above budget (max " + max + " ms vs PERF-TICK-01 "
                            + TICK_BUDGET_MS + ", p99 " + p99 + " ms vs PERF-COMMIT-01 " + P99_BUDGET_MS + "): " + detail);
                }
                return detail;
            } finally {
                org.bukkit.event.HandlerList.unregisterAll(removalTap);
                if (sampler != null) {
                    sampler.cancel();
                }
                if (calendar != null) {
                    calendar.setRunWhenEmpty(calendarWasForced);
                }
                for (Entity e : herd) {
                    try {
                        OnThread.entity(s, e, STEP, () -> {
                            tfp.integrations().entityModels().detach(e);
                            e.remove();
                            return null;
                        });
                    } catch (Exception | AssertionError ignored) {
                        // died or was removed
                    }
                }
                try {
                    OnThread.global(s, STEP, () -> {
                        forceArea(world, cx, cz, false);
                        return null;
                    });
                } catch (Exception | AssertionError ex) {
                    tfp.getLogger().warning("husbandry.load_200 cleanup failed: " + ex);
                }
            }
        }

        /**
         * Replaces our goals on {@code mob} with fresh ones from its species behaviours, at the same priorities, each
         * wrapped in a timer adding to {@code sink}. Owning thread. Returns the number of goals wrapped.
         */
        static int wrapGoals(AnimalService animals, Mob mob, AtomicLong sink,
                Map<String, java.util.concurrent.atomic.LongAdder> byClass, Slowest slowest) {
            Optional<AnimalRecord> record = animals.record(mob);
            if (record.isEmpty()) {
                return 0;
            }
            var goals = Bukkit.getMobGoals();
            for (Goal<Mob> g : List.copyOf(goals.getAllGoals(mob))) {
                if (g.getClass().getName().startsWith("dev.tfp.")) {
                    goals.removeGoal(mob, g);
                }
            }
            int n = 0;
            for (SpeciesBehaviour b : animals.behaviours().all(record.get().species())) {
                for (SpeciesBehaviour.GoalSpec spec : b.goals(mob, record.get())) {
                    String key = spec.goal().getClass().getName().replace("dev.tfp.paper.", "");
                    goals.addGoal(mob, spec.priority(), new Timed(spec.goal(), sink,
                            byClass.computeIfAbsent(key, k -> new java.util.concurrent.atomic.LongAdder()), slowest, key));
                    n++;
                }
            }
            return n;
        }

        /** The worst of three runs of the animal's check body (age step and every behaviour hook), in ns. Owning thread. */
        static long timeCheck(TfpPlugin tfp, AnimalService animals, Entity e) {
            long worst = 0;
            for (int i = 0; i < 3; i++) {
                long t0 = System.nanoTime();
                Optional<AnimalRecord> record = animals.record(e);
                ClockSnapshot now = tfp.clock().get();
                if (record.isEmpty() || !now.isSet()) {
                    return 0;
                }
                LivestockAge age = AnimalCheck.ageAt(record.get(), now.playerTicks());
                AnimalCheck.Decision d = AnimalCheck.decide(record.get(), now.playerTicks(), 0, age);
                for (SpeciesBehaviour b : animals.behaviours().all(d.record().species())) {
                    b.onCheck(new SpeciesBehaviour.Check(e, d.record(), d.age(), now.playerTicks()));
                }
                worst = Math.max(worst, System.nanoTime() - t0);
            }
            return worst;
        }

        /**
         * The centre chunk of the first 3x3 area, at least 40 chunks from spawn, whose surface is mostly dry: 9 sampled
         * columns, at most one liquid. Falls back to the first candidate.
         */
        static int[] landArea(Scheduler s, World world, int spawnX, int spawnZ) throws Exception {
            int[][] candidates = {{CHUNK_DX, CHUNK_DZ}, {-CHUNK_DX, CHUNK_DZ}, {CHUNK_DX, -CHUNK_DZ}, {-CHUNK_DX, -CHUNK_DZ},
                {0, 2 * CHUNK_DZ}, {2 * CHUNK_DX, 0}, {0, -2 * CHUNK_DZ}, {-2 * CHUNK_DX, 0}, {2 * CHUNK_DX, 2 * CHUNK_DZ},
                {-2 * CHUNK_DX, -2 * CHUNK_DZ}};
            for (int[] c : candidates) {
                int cx = spawnX + c[0];
                int cz = spawnZ + c[1];
                int wet = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int x = ((cx + dx) << 4) + 8;
                        int z = ((cz + dz) << 4) + 8;
                        OnThread.await(world.getChunkAtAsync(x >> 4, z >> 4), STEP, "loading chunk");
                        if (OnThread.region(s, world, x >> 4, z >> 4, STEP, () -> world.getHighestBlockAt(x, z).isLiquid())) {
                            wet++;
                        }
                    }
                }
                if (wet <= 1) {
                    return new int[] {cx, cz};
                }
            }
            return new int[] {spawnX + CHUNK_DX, spawnZ + CHUNK_DZ};
        }

        /** The JVM's total collection time over all collectors, in ms. */
        static long gcMillis() {
            long t = 0;
            for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                t += Math.max(0, gc.getCollectionTime());
            }
            return t;
        }

        static long gcCount() {
            long n = 0;
            for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                n += Math.max(0, gc.getCollectionCount());
            }
            return n;
        }

        /**
         * ", GC +n ms" when the JVM's GC total moved during sampler row {@code row} (the region tick a slow call fell in:
         * the call ran before that row was taken), else "".
         */
        static String gcIn(List<long[]> rows, long row) {
            int i = (int) row;
            if (i <= 0 || i >= rows.size()) {
                return "";
            }
            long d = rows.get(i)[2] - rows.get(i - 1)[2];
            return d > 0 ? ", GC +" + d + " ms" : "";
        }

        private static void forceArea(World world, int cx, int cz, boolean on) {
            for (int dx = -FORCE_RADIUS; dx <= FORCE_RADIUS; dx++) {
                for (int dz = -FORCE_RADIUS; dz <= FORCE_RADIUS; dz++) {
                    world.setChunkForceLoaded(cx + dx, cz + dz, on);
                }
            }
        }

        static double pct(double[] sorted, double p) {
            if (sorted.length == 0) {
                return Double.NaN;
            }
            int i = (int) Math.ceil(p * sorted.length) - 1;
            return sorted[Math.clamp(i, 0, sorted.length - 1)];
        }

        static String stats(double[] sorted) {
            double mean = Arrays.stream(sorted).average().orElse(Double.NaN);
            return String.format(Locale.ROOT, "mean %.3f p50 %.3f p99 %.3f max %.3f", mean, pct(sorted, 0.5),
                    pct(sorted, 0.99), sorted.length == 0 ? Double.NaN : sorted[sorted.length - 1]);
        }
    }

    /**
     * The slowest timed goal calls of a window (PAR-FAUNA-33 diagnostic): the {@link #KEEP} longest single calls with their
     * goal class and method and the sampler tick they fell in. Thread-safe; a call faster than the shortest kept one costs
     * one volatile read.
     */
    static final class Slowest {
        static final int KEEP = 5;

        record Call(long nanos, String goal, String method, long tick) {
        }

        private final List<Call> calls = new ArrayList<>();
        private volatile long floor;
        /** The sampler's row counter, read when a slow call is kept. */
        final AtomicLong tick = new AtomicLong();
        /** Goal starts per goal class in the window (which goals the herd actually runs). */
        final Map<String, java.util.concurrent.atomic.LongAdder> starts = new java.util.concurrent.ConcurrentHashMap<>();

        void offer(long nanos, String goal, String method) {
            if ("start".equals(method)) {
                starts.computeIfAbsent(goal, g -> new java.util.concurrent.atomic.LongAdder()).increment();
            }
            if (nanos <= floor) {
                return;
            }
            synchronized (calls) {
                calls.add(new Call(nanos, goal, method, tick.get()));
                calls.sort((a, b) -> Long.compare(b.nanos(), a.nanos()));
                while (calls.size() > KEEP) {
                    calls.removeLast();
                }
                floor = calls.size() < KEEP ? 0 : calls.getLast().nanos();
            }
        }

        void reset() {
            synchronized (calls) {
                calls.clear();
                floor = 0;
            }
            starts.clear();
        }

        List<Call> calls() {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }
    }

    /** A goal that adds the time spent in each of its calls to a shared counter. */
    static final class Timed implements Goal<Mob> {
        private final Goal<Mob> inner;
        private final AtomicLong sink;
        private final java.util.concurrent.atomic.LongAdder total;
        private final Slowest slowest;
        private final String name;

        Timed(Goal<Mob> inner, AtomicLong sink, java.util.concurrent.atomic.LongAdder total, Slowest slowest, String name) {
            this.inner = inner;
            this.sink = sink;
            this.total = total;
            this.slowest = slowest;
            this.name = name;
        }

        private void add(long t, String method) {
            long d = System.nanoTime() - t;
            sink.addAndGet(d);
            total.add(d);
            slowest.offer(d, name, method);
        }

        @Override
        public boolean shouldActivate() {
            long t = System.nanoTime();
            try {
                return inner.shouldActivate();
            } finally {
                add(t, "shouldActivate");
            }
        }

        @Override
        public boolean shouldStayActive() {
            long t = System.nanoTime();
            try {
                return inner.shouldStayActive();
            } finally {
                add(t, "shouldStayActive");
            }
        }

        @Override
        public void start() {
            long t = System.nanoTime();
            try {
                inner.start();
            } finally {
                add(t, "start");
            }
        }

        @Override
        public void stop() {
            long t = System.nanoTime();
            try {
                inner.stop();
            } finally {
                add(t, "stop");
            }
        }

        @Override
        public void tick() {
            long t = System.nanoTime();
            try {
                inner.tick();
            } finally {
                add(t, "tick");
            }
        }

        @Override
        public GoalKey<Mob> getKey() {
            return inner.getKey();
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return inner.getTypes();
        }
    }
}
