/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (tickInfestation, choosePest),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java (container right click), common/entities/prey/Pest.java,
 * common/entities/ai/prey/PestFeastBehavior.java, common/entities/aquatic/AquaticCritter.java,
 * common/container/PestContainer.java
 */
package dev.tfp.paper.critter;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.google.gson.JsonParser;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.aquatic.AquaticSpawns;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.critter.CritterFluid;
import dev.tfp.core.critter.CritterSpecies;
import dev.tfp.core.critter.PestAppearance;
import dev.tfp.core.critter.PestClimate;
import dev.tfp.core.critter.PestFeast;
import dev.tfp.core.critter.PestInfestation;
import dev.tfp.core.critter.PestLifetime;
import dev.tfp.core.critter.PestSpecies;
import dev.tfp.core.spawn.FaunaRule;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.core.spawn.SpawnRules;
import dev.tfp.core.spawn.SpawnSite;
import dev.tfp.core.spawn.SpawnTables;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.hud.HudHemisphere;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import io.papermc.paper.command.brigadier.Commands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Pests and critters (PAR-FAUNA-13).
 *
 * <ul>
 *   <li><b>Infestation</b>: right-clicking a chest rolls TFC's {@code tickInfestation} with the food stacks in it (at most five).
 *       A pass spawns a rat, lemming, jerboa or mongoose by the climate ({@link PestClimate}) on a land spot near the chest; it
 *       smells the chest and walks onto it ({@link PestFeastGoal}), takes one food item, holds it for 402 ticks (one timer,
 *       not a counter; the pest does not move) and eats it, and each feast rolls infestation levels 1 to 5 again.</li>
 *   <li><b>Lazy discard</b>: a pest older than three minutes goes away on a one in 500 per-tick roll in TFC; here one
 *       geometric age is drawn when it first appears and a single delayed task removes it, unless it is named.</li>
 *   <li><b>Water critters</b> (isopod, lobster, crayfish, horseshoe crab): spawn in water with TFC's fluid gate
 *       ({@link CritterSpecies#canSpawnIn}) and climate gate; hurt, they run from players at speed 2; they always avoid ocean
 *       predators.</li>
 * </ul>
 *
 * <p>Threads: the right click, the goals and the timers run on the thread that owns the chest, the pest or the critter; the
 * decisions are the pure {@code dev.tfp.core.critter} rules. Nothing ticks per container: an infestation costs one random draw
 * per opening.
 */
public final class CritterModule implements FeatureModule, Listener {
    static final NamespacedKey SMELLY = new NamespacedKey("tfp", "pest_smelly");
    static final NamespacedKey DISCARD_AT = new NamespacedKey("tfp", "pest_discard_at");
    static final NamespacedKey EAT_END = new NamespacedKey("tfp", "pest_eat_end");
    static final String MESSAGE = "critter.infestation";
    static final String FOOD_TAG = "c:foods";
    static final long TOP_UP_TICKS = Long.getLong("tfp.critter.topUpTicks", 400L);
    static final int TOP_UP_ATTEMPTS = Integer.getInteger("tfp.critter.topUpAttempts", 8);
    static final int CHUNK_CAP = Integer.getInteger("tfp.critter.chunkCap", 8);
    static final boolean ENABLED = !Boolean.getBoolean("tfp.critter.disabled");
    /** {@code LandRandomPos.getPos(mob, 15, 5)}. */
    static final int SPAWN_RADIUS = 15;
    static final int SPAWN_HEIGHT = 5;
    static final int SPAWN_TRIES = 10;
    static final Set<String> PEST_IDS = java.util.Arrays.stream(PestSpecies.values()).map(s -> "tfc:" + s.id()).collect(java.util.stream.Collectors.toSet());
    static final Set<String> CRITTER_IDS = java.util.Arrays.stream(CritterSpecies.values())
            .map(s -> "tfc:" + s.name().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());

    private ModuleContext ctx;
    private Tags tags;
    private volatile SpawnTables tables;
    private final Map<UUID, Scheduler.TaskHandle> topUps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hurtAt = new ConcurrentHashMap<>();

    // seams for tests
    private SpawnRandom random = threadRandom();
    private LongSupplier now = () -> 0L;
    private Predicate<ItemStack> food = s -> false;
    private Function<Location, ChunkClimate> climate = l -> ChunkClimate.NEUTRAL;
    private java.util.function.ToIntFunction<Location> nearbyPests = this::countPests;
    private PestSpawner spawner = this::spawnPest;

    /** What an infestation spawns: the species at a land spot, smelling the container. */
    @FunctionalInterface
    interface PestSpawner {
        void spawn(PestSpecies species, Location container, Player player);
    }

    @Override
    public String id() {
        return "critter";
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        this.now = () -> context.clock().get().playerTicks();
        this.food = s -> s != null && !s.isEmpty() && tags.is(s, FOOD_TAG);
        this.climate = l -> dev.tfp.paper.climate.ChunkClimateSource.climateAt(l.getWorld().getSeed(), l.getBlockX(), l.getBlockZ());
        var file = RegistryService.jarDataRoot(CritterModule.class.getClassLoader()).resolve("tfp/data_maps/spawn_table.json");
        tables = SpawnTables.parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        TfpPlugin plugin = (TfpPlugin) context.plugin();
        plugin.animals().behaviours().register(new PestAi());
        plugin.animals().behaviours().register(new CritterAi());
        context.services().provide(CritterModule.class, this);
        context.listen(this);
    }

    @Override
    public void disable() {
        topUps.values().forEach(Scheduler.TaskHandle::cancel);
        topUps.clear();
        hurtAt.clear();
    }

    /** Test seam: the world reads, the clock, the draws and the spawn. */
    void initForTest(ModuleContext context, LongSupplier now, Predicate<ItemStack> food, Function<Location, ChunkClimate> climate,
            java.util.function.ToIntFunction<Location> nearbyPests, SpawnRandom random, PestSpawner spawner) {
        this.ctx = context;
        this.now = now;
        this.food = food;
        this.climate = climate;
        this.nearbyPests = nearbyPests;
        this.random = random;
        this.spawner = spawner;
    }

    SpeciesBehaviour pestBehaviour() {
        return new PestAi();
    }

    SpeciesBehaviour critterBehaviour() {
        return new CritterAi();
    }

    private AnimalService animals() {
        return ((TfpPlugin) ctx.plugin()).animals();
    }

    // ---- infestation -----------------------------------------------------------------------------------------------

    /** The food flags of a container's slots. */
    boolean[] foodSlots(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        boolean[] out = new boolean[contents.length];
        for (int i = 0; i < contents.length; i++) {
            out[i] = contents[i] != null && !contents[i].isEmpty() && food.test(contents[i]);
        }
        return out;
    }

    /**
     * TFC's right click on a pest container: a chest counts its own food stacks (one half of a double chest, as TFC's block
     * entity), at most five, and rolls once. TFC does not check the {@code enableInfestations} option on this path.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!ENABLED || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }
        BlockState state = block.getState(false);
        if (state instanceof Chest chest) {
            infest(block.getLocation(), PestInfestation.clickLevel(PestInfestation.countFood(foodSlots(chest.getBlockInventory()))), player);
        }
    }

    /**
     * TFC {@code Helpers.tickInfestation}: the level gate, the nearby-pest chance, the climate pick, then the spawn, with the
     * draws in TFC's order. {@code player} gets the foul-smell message when a pest appears (null for a pest's own feast).
     * Owning thread of the container.
     */
    public void infest(Location container, int level, Player player) {
        int clamped = PestInfestation.clampLevel(level);
        if (!PestInfestation.rollsThisTick(clamped, random)) {
            return;
        }
        if (random.nextFloat() > PestInfestation.spawnChance(nearbyPests.applyAsInt(container))) {
            return;
        }
        ChunkClimate c = climate.apply(container);
        PestClimate.choose(random, c.averageSeaLevelTemperature(), c.averageRainfall())
                .ifPresent(species -> spawner.spawn(species, container, player));
    }

    /** Pests within 40 blocks, counted over the chunks this region owns (an entity count must stay on the owner thread). */
    int countPests(Location at) {
        World world = at.getWorld();
        int r = PestInfestation.NEARBY_RADIUS;
        int n = 0;
        for (int cx = (at.getBlockX() - r) >> 4; cx <= (at.getBlockX() + r) >> 4; cx++) {
            for (int cz = (at.getBlockZ() - r) >> 4; cz <= (at.getBlockZ() + r) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz) || !Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
                    continue;
                }
                for (Entity e : world.getChunkAt(cx, cz).getEntities()) {
                    Location l = e.getLocation();
                    if (Math.abs(l.getX() - at.getBlockX() - 0.5) <= r + 0.5 && Math.abs(l.getZ() - at.getBlockZ() - 0.5) <= r + 0.5
                            && Math.abs(l.getY() - at.getBlockY() - 0.5) <= r + 0.5 && isPest(e)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    static boolean isPest(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer()).map(r -> PEST_IDS.contains(r.species())).orElse(false);
    }

    /** {@code LandRandomPos.getPos(mob, 15, 5)}: a random standable spot around the container, or null. */
    Location landSpot(Location container) {
        World world = container.getWorld();
        for (int i = 0; i < SPAWN_TRIES; i++) {
            int x = container.getBlockX() + random.nextInt(2 * SPAWN_RADIUS + 1) - SPAWN_RADIUS;
            int z = container.getBlockZ() + random.nextInt(2 * SPAWN_RADIUS + 1) - SPAWN_RADIUS;
            int y = container.getBlockY() + random.nextInt(2 * SPAWN_HEIGHT + 1) - SPAWN_HEIGHT;
            Location probe = new Location(world, x + 0.5, y, z + 0.5);
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(probe)) {
                continue;
            }
            int top = Math.min(world.getMaxHeight() - 2, y + SPAWN_HEIGHT);
            while (y < top && world.getBlockAt(x, y, z).isSolid()) {
                y++;
            }
            Block below = world.getBlockAt(x, y - 1, z);
            Block at = world.getBlockAt(x, y, z);
            if (below.isSolid() && !at.isLiquid() && at.isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** Creates the pest: TFC's name and baby draws, then the animal service's spawn; the pest smells the container. */
    void spawnPest(PestSpecies species, Location container, Player player) {
        Location spot = landSpot(container);
        Carrier carrier = animals().table().find("tfc:" + species.id()).orElse(null);
        if (spot == null || carrier == null) {
            return;
        }
        PestAppearance look = PestAppearance.roll(random);
        AnimalSpawnRoll.AgeHint hint = look.baby() ? AnimalSpawnRoll.AgeHint.CHILD : AnimalSpawnRoll.AgeHint.RANDOM;
        animals().spawn(spot, carrier, hint, null).thenAccept(pest -> ctx.scheduler().entity(pest, () -> {
            setSmelly(pest, container);
            if (look.named()) {
                pest.customName(Component.text("Pak"));
                if (pest instanceof Mob mob) {
                    mob.setRemoveWhenFarAway(false);
                }
            }
            if (player != null && player.isOnline()) {
                ctx.scheduler().entity(player, () -> player.sendActionBar(Component.text(ctx.messages().get(player, MESSAGE))), null);
            }
        }, null)).exceptionally(e -> {
            ctx.logger().fine("Pest spawn failed: " + e.getMessage());
            return null;
        });
    }

    // ---- pest behaviour --------------------------------------------------------------------------------------------

    static void setSmelly(Entity pest, Location block) {
        pest.getPersistentDataContainer().set(SMELLY, PersistentDataType.INTEGER_ARRAY,
                new int[] {block.getBlockX(), block.getBlockY(), block.getBlockZ()});
    }

    static Location smelly(Entity pest) {
        int[] p = pest.getPersistentDataContainer().get(SMELLY, PersistentDataType.INTEGER_ARRAY);
        return p == null || p.length != 3 ? null : new Location(pest.getWorld(), p[0], p[1], p[2]);
    }

    private final class PestAi implements SpeciesBehaviour, PestFeastGoal.Site {
        @Override
        public Set<String> species() {
            return PEST_IDS;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            return List.of(new GoalSpec(3, new PestFeastGoal(mob, this)));
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (!(entity instanceof Mob mob)) {
                return;
            }
            // Pest.removeWhenFarAway: not while persistent (named)
            mob.setRemoveWhenFarAway(PestLifetime.removesWhenFarAway(mob.customName() != null));
            scheduleDiscard(mob);
            Long end = mob.getPersistentDataContainer().get(EAT_END, PersistentDataType.LONG);
            if (end != null) {
                scheduleEatEnd(mob, end);
            }
        }

        @Override
        public Location smelly(Mob mob) {
            return CritterModule.smelly(mob);
        }

        @Override
        public boolean holdsItem(Mob mob) {
            return mob.getPersistentDataContainer().has(EAT_END, PersistentDataType.LONG)
                    || !mob.getEquipment().getItemInMainHand().isEmpty();
        }

        @Override
        public boolean isContainer(Location block) {
            return block.getWorld().isChunkLoaded(block.getBlockX() >> 4, block.getBlockZ() >> 4)
                    && block.getBlock().getState(false) instanceof org.bukkit.block.Container;
        }

        @Override
        public void reached(Mob mob, Location block) {
            feast(mob, block);
        }
    }

    /** {@code PestFeastBehavior.afterReached}: take one food item, hold it, and worsen the infestation. */
    void feast(Mob mob, Location block) {
        if (!(block.getBlock().getState(false) instanceof org.bukkit.block.Container container)) {
            return;
        }
        Inventory inventory = container.getInventory();
        int slot = PestFeast.firstFoodSlot(foodSlots(inventory));
        if (slot < 0) {
            // nothing smelly: stop trying
            mob.getPersistentDataContainer().remove(SMELLY);
            return;
        }
        ItemStack stack = inventory.getItem(slot);
        ItemStack taken = stack.clone();
        taken.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
        mob.getEquipment().setItemInMainHand(taken);
        long end = now.getAsLong() + PestFeast.ticksUntilCleared();
        mob.getPersistentDataContainer().set(EAT_END, PersistentDataType.LONG, end);
        mob.setAware(false);
        scheduleEatEnd(mob, end);
        for (int i = 1; i < 6; i++) {
            // the infestation is getting worse
            infest(block, i, null);
        }
    }

    private void scheduleEatEnd(Mob mob, long end) {
        long left = Math.max(1, end - now.getAsLong());
        ctx.scheduler().entityDelayed(mob, left, () -> finishEating(mob), null);
    }

    /** The held item is eaten: gone, with a burp, and the pest moves again. */
    void finishEating(Mob mob) {
        if (!mob.isValid() || !mob.getPersistentDataContainer().has(EAT_END, PersistentDataType.LONG)) {
            return;
        }
        mob.getEquipment().setItemInMainHand(null);
        mob.getPersistentDataContainer().remove(EAT_END);
        mob.setAware(true);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
    }

    /** One geometric age drawn on the first add, one delayed task per add; a named pest is never discarded. */
    private void scheduleDiscard(Mob mob) {
        Long at = mob.getPersistentDataContainer().get(DISCARD_AT, PersistentDataType.LONG);
        long current = now.getAsLong();
        if (at == null) {
            at = current + PestLifetime.discardAgeFromUniform(ThreadLocalRandom.current().nextDouble());
            mob.getPersistentDataContainer().set(DISCARD_AT, PersistentDataType.LONG, at);
        }
        long left = Math.max(1, at - current);
        ctx.scheduler().entityDelayed(mob, left, () -> discard(mob), null);
    }

    void discard(Mob mob) {
        Long at = mob.getPersistentDataContainer().get(DISCARD_AT, PersistentDataType.LONG);
        if (!mob.isValid() || at == null || at > now.getAsLong() || mob.customName() != null) {
            return;
        }
        mob.remove();
    }

    // ---- water critters --------------------------------------------------------------------------------------------

    private final class CritterAi implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return CRITTER_IDS;
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            return List.of(
                    new GoalSpec(1, new CritterGoals.Escape(mob, m -> {
                        Integer at = hurtAt.get(m.getUniqueId());
                        return at == null ? -1 : m.getTicksLived() - at;
                    })),
                    new GoalSpec(2, new CritterGoals.AvoidPredators(mob, e -> oceanPredator(e))));
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (entity instanceof Mob mob) {
                // the carrier fish flees players and panics; TFC's critter only flees once hurt
                for (GoalKey<?> key : List.<GoalKey<?>>of(VanillaGoal.AVOID_ENTITY, VanillaGoal.PANIC)) {
                    removeVanilla(mob, key);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void removeVanilla(Mob mob, GoalKey<?> key) {
        Bukkit.getMobGoals().removeGoal(mob, (GoalKey) key);
    }

    boolean oceanPredator(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer())
                .map(r -> tags.is(TagType.ENTITY_TYPE, "tfp:" + r.species().substring(r.species().indexOf(':') + 1), "tfp:ocean_predators"))
                .orElse(false);
    }

    /** {@code LivingEntity.lastHurtByMob}: the tick of the last hit by a living attacker. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && CRITTER_IDS.contains(speciesOf(mob))) {
            Entity damager = event.getDamager();
            boolean living = damager instanceof LivingEntity
                    || (damager instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof LivingEntity);
            if (living) {
                hurtAt.put(mob.getUniqueId(), mob.getTicksLived());
            }
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        hurtAt.remove(event.getEntity().getUniqueId());
    }

    private static String speciesOf(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer()).map(AnimalRecord::species).orElse("");
    }

    // ---- critter spawning ------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!ENABLED) {
            return;
        }
        Player p = event.getPlayer();
        long delay = TOP_UP_TICKS + Math.floorMod(p.getUniqueId().hashCode(), 100);
        Scheduler.TaskHandle h = ctx.scheduler().entityRepeating(p, delay, TOP_UP_TICKS, () -> topUp(p), null);
        if (h != null) {
            Scheduler.TaskHandle old = topUps.put(p.getUniqueId(), h);
            if (old != null) {
                old.cancel();
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Scheduler.TaskHandle h = topUps.remove(event.getPlayer().getUniqueId());
        if (h != null) {
            h.cancel();
        }
    }

    private void topUp(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int px = player.getLocation().getBlockX() >> 4;
        int pz = player.getLocation().getBlockZ() >> 4;
        for (int i = 0; i < TOP_UP_ATTEMPTS; i++) {
            int cx = px + (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(7));
            int cz = pz + (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(7));
            ctx.scheduler().region(world, cx, cz, () -> {
                if (world.isChunkLoaded(cx, cz) && countCritters(world.getChunkAt(cx, cz)) < CHUNK_CAP) {
                    populate(world, cx, cz);
                }
            });
        }
    }

    static int countCritters(org.bukkit.Chunk chunk) {
        int n = 0;
        for (Entity e : chunk.getEntities()) {
            if (CRITTER_IDS.contains(speciesOf(e))) {
                n++;
            }
        }
        return n;
    }

    /** One spawn pass over a loaded chunk's water lists, keeping only the four critters. Region thread of the chunk. */
    void populate(World world, int cx, int cz) {
        AnimalService animals = animals();
        ClockSnapshot snapshot = ctx.clock().get();
        if (animals == null || !snapshot.isSet() || tables == null) {
            return;
        }
        int bx = cx << 4;
        int bz = cz << 4;
        Block centre = world.getBlockAt(bx + 8, world.getHighestBlockYAt(bx + 8, bz + 8), bz + 8);
        var biome = tables.biome(centre.getBiome().getKey().getKey());
        if (biome.isEmpty()) {
            return;
        }
        SpawnRandom rand = threadRandom();
        for (AquaticSpawns.Member m : AquaticSpawns.pass(biome.get(), rand)) {
            Optional<CritterSpecies> species = CRITTER_IDS.contains(m.entry().type())
                    ? Optional.of(CritterSpecies.valueOf(m.entry().type().substring(4).toUpperCase(java.util.Locale.ROOT)))
                    : Optional.empty();
            Carrier carrier = animals.table().find(m.entry().type()).orElse(null);
            FaunaRule rule = tables.fauna().get(m.entry().type());
            if (species.isEmpty() || carrier == null || rule == null) {
                continue;
            }
            Location at = waterPlace(world, bx + m.x(), bz + m.z(), carrier, ThreadLocalRandom.current().nextDouble());
            if (at == null || !species.get().canSpawnIn(fluidAt(at.getBlock()))) {
                continue;
            }
            if (!SpawnRules.accepts(rule, site(world, at, snapshot), rand)) {
                continue;
            }
            animals.spawn(at, carrier, AnimalSpawnRoll.AgeHint.RANDOM, null).exceptionally(e -> {
                ctx.logger().fine("Critter spawn of " + carrier.id() + " failed: " + e.getMessage());
                return null;
            });
        }
    }

    /** The critter fluid of a block: ocean water is salt water, other water fresh; only source blocks match. */
    static CritterFluid fluidAt(Block block) {
        boolean water = block.getType() == Material.WATER
                || (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged());
        if (!water) {
            return CritterFluid.OTHER;
        }
        boolean source = !(block.getBlockData() instanceof org.bukkit.block.data.Levelled l) || l.getLevel() == 0;
        boolean ocean = block.getBiome().getKey().getKey().contains("ocean");
        if (ocean) {
            return source ? CritterFluid.SALT_SOURCE : CritterFluid.SALT_FLOWING;
        }
        return source ? CritterFluid.FRESH_SOURCE : CritterFluid.FRESH_FLOWING;
    }

    static Location waterPlace(World world, int x, int z, Carrier carrier, double draw) {
        int top = world.getHighestBlockYAt(x, z);
        if (world.getBlockAt(x, top, z).getType() != Material.WATER) {
            return null;
        }
        int bottom = top;
        while (bottom - 1 > world.getMinHeight() && world.getBlockAt(x, bottom - 1, z).getType() == Material.WATER) {
            bottom--;
        }
        int clear = Math.max(1, (int) Math.ceil(carrier.height()));
        int highest = top - clear + 1;
        if (highest < bottom) {
            return null;
        }
        int y = bottom + (int) (draw * (highest - bottom + 1));
        return new Location(world, x + 0.5, Math.min(y, highest), z + 0.5);
    }

    private SpawnSite site(World world, Location at, ClockSnapshot snapshot) {
        long seed = world.getSeed();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        ChunkClimate c = dev.tfp.paper.climate.ChunkClimateSource.climateAt(seed, x, z);
        int density = ChunkDataGenerator.forLevel(seed).forestType(x >> 4, z >> 4).density();
        boolean north = HudHemisphere.north(true, seed, z);
        Block feet = at.getBlock();
        int brightness = Math.max(feet.getLightFromBlocks(), feet.getLightFromSky());
        return new SpawnSite(at.getBlockY(), ChunkData.SEA_LEVEL_Y, c, density, north, brightness,
                CalendarTicks.month(snapshot.calendarTicks(), snapshot.daysInMonth()));
    }

    static SpawnRandom threadRandom() {
        return new SpawnRandom() {
            @Override
            public int nextInt(int bound) {
                return ThreadLocalRandom.current().nextInt(bound);
            }

            @Override
            public float nextFloat() {
                return ThreadLocalRandom.current().nextFloat();
            }
        };
    }

    // ---- debug -----------------------------------------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("pest", node -> {
            var force = Commands.literal("force").executes(tree.player((player, c) -> force(player, PestSpecies.RAT)));
            for (PestSpecies s : PestSpecies.values()) {
                force.then(Commands.literal(s.id()).executes(tree.player((player, c) -> force(player, s))));
            }
            node.then(force);
        });
    }

    /** {@code /tfp debug pest force [species]}: a pest of that species near the container you look at, smelling it, no rolls. */
    private CompletableFuture<Reply> force(Player player, PestSpecies species) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        ctx.scheduler().entity(player, () -> {
            Block target = player.getTargetBlockExact(6);
            Location container = target != null && target.getState(false) instanceof org.bukkit.block.Container
                    ? target.getLocation() : player.getLocation().getBlock().getLocation();
            spawnPest(species, container, player);
            out.complete(Reply.ok("debug.pest.force.done", species.id(), container.getBlockX(), container.getBlockY(), container.getBlockZ()));
        }, () -> out.complete(Reply.error("command.player_only")));
        return out;
    }
}
