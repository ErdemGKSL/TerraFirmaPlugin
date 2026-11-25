/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Faunas.java @ c3b4d9536 (registerFish), aquatic/TFCSquid.java
 * (finalizeSpawn, setSize), aquatic/AquaticMob.java (canSpawnIn, mobInteract), items/TFCItems.java (the mob buckets)
 */
package dev.tfp.paper.aquatic;

import com.google.gson.JsonParser;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.aquatic.AquaticBuckets;
import dev.tfp.core.aquatic.AquaticFluid;
import dev.tfp.core.aquatic.AquaticSpawns;
import dev.tfp.core.aquatic.AquaticSpecies;
import dev.tfp.core.aquatic.SquidSize;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.spawn.FaunaRule;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.core.spawn.SpawnRules;
import dev.tfp.core.spawn.SpawnSite;
import dev.tfp.core.spawn.SpawnTables;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.hud.HudHemisphere;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;

/**
 * Aquatic animals (PAR-FAUNA-11).
 *
 * <ul>
 *   <li><b>Spawning</b>: water mobs spawn the way vanilla's periodic cycle does for its water lists (chunk generation spawns only
 *       creatures). Per player every {@value #TOP_UP_TICKS} ticks, {@value #TOP_UP_ATTEMPTS} loaded chunks 2 to 8 chunks away get one
 *       pass of {@link AquaticSpawns}; each member needs a water column, TFC's fluid gate ({@link AquaticSpecies#passesFluidGate}:
 *       ocean water is salt water, other water is fresh) and TFC's climate/depth gate ({@link SpawnRules#accepts}).</li>
 *   <li><b>Squid size</b>: a new squid or octopoteuthis rolls its TFC size from the spawn range, shrinking by 0.8 until it fits
 *       or the spawn is cancelled ({@link SquidSize}); its scale is {@code size / 128 * 5}.</li>
 *   <li><b>Mob buckets</b>: a right click with a bucket of the species' fluid on a fish picks it up into the TFC mob bucket
 *       {@code tfp:bucket/<species>} ({@link AquaticBuckets}); using the mob bucket on water releases a fresh animal.</li>
 * </ul>
 */
public final class AquaticModule implements FeatureModule, Listener {
    static final long TOP_UP_TICKS = Long.getLong("tfp.aquatic.topUpTicks", 400L);
    static final int TOP_UP_ATTEMPTS = Integer.getInteger("tfp.aquatic.topUpAttempts", 8);
    static final int CHUNK_CAP = Integer.getInteger("tfp.aquatic.chunkCap", 12);
    static final boolean ENABLED = !Boolean.getBoolean("tfp.aquatic.disabled");
    static final String BUCKET_PREFIX = "tfp:bucket/";
    static final NamespacedKey SIZE_KEY = new NamespacedKey("tfp", "squid_size");
    static final Set<String> SQUIDS = Set.of("tfc:squid", "tfc:octopoteuthis");

    private ModuleContext ctx;
    private Tags tags;
    private volatile SpawnTables tables;
    private final Map<UUID, Scheduler.TaskHandle> topUps = new ConcurrentHashMap<>();

    // seams for tests
    private java.util.function.Function<Entity, Optional<AquaticSpecies>> speciesOf;
    private java.util.function.Function<ItemStack, AquaticFluid> bucketFluid;
    private java.util.function.Function<ItemStack, Optional<AquaticSpecies>> filledBucket;
    private java.util.function.BiFunction<String, Integer, Optional<ItemStack>> createItem;

    @Override
    public String id() {
        return "aquatic";
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        var file = RegistryService.jarDataRoot(AquaticModule.class.getClassLoader()).resolve("tfp/data_maps/spawn_table.json");
        tables = SpawnTables.parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        ((TfpPlugin) context.plugin()).animals().behaviours().register(new SquidBehaviour());
        context.listen(this);
    }

    @Override
    public void disable() {
        topUps.values().forEach(Scheduler.TaskHandle::cancel);
        topUps.clear();
    }

    /** Test seam for the bucket flow. */
    void initForTest(ModuleContext context, java.util.function.Function<Entity, Optional<AquaticSpecies>> speciesOf,
            java.util.function.Function<ItemStack, AquaticFluid> bucketFluid,
            java.util.function.Function<ItemStack, Optional<AquaticSpecies>> filledBucket,
            java.util.function.BiFunction<String, Integer, Optional<ItemStack>> createItem) {
        this.ctx = context;
        this.speciesOf = speciesOf;
        this.bucketFluid = bucketFluid;
        this.filledBucket = filledBucket;
        this.createItem = createItem;
    }

    private AnimalService animals() {
        return ((TfpPlugin) ctx.plugin()).animals();
    }

    // ---- fluids ------------------------------------------------------------------------------------------

    /** The fluid class of a block: vanilla water in an ocean biome is TFC's salt water, any other water is fresh. */
    static AquaticFluid fluidAt(Block block) {
        if (block.getType() != Material.WATER && !(block.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged())) {
            return AquaticFluid.NOT_WATER;
        }
        return oceanBiome(block.getBiome().getKey().getKey()) ? AquaticFluid.SALT_WATER : AquaticFluid.VANILLA_WATER;
    }

    static boolean oceanBiome(String biomeKey) {
        return biomeKey.contains("ocean");
    }

    // ---- spawning ----------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
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
    public void onQuit(PlayerQuitEvent event) {
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
                if (world.isChunkLoaded(cx, cz) && countAquatic(world.getChunkAt(cx, cz)) < CHUNK_CAP) {
                    populate(world, cx, cz);
                }
            });
        }
    }

    static int countAquatic(org.bukkit.Chunk chunk) {
        int n = 0;
        for (Entity e : chunk.getEntities()) {
            Optional<AnimalRecord> record = AnimalPdc.read(e.getPersistentDataContainer());
            if (record.isPresent() && AquaticSpecies.ofEntityType(record.get().species()).isPresent()) {
                n++;
            }
        }
        return n;
    }

    /** One spawn pass over a loaded chunk. Region thread of the chunk. */
    void populate(World world, int cx, int cz) {
        AnimalService animals = animals();
        ClockSnapshot now = ctx.clock().get();
        if (animals == null || !now.isSet() || tables == null) {
            return;
        }
        int bx = cx << 4;
        int bz = cz << 4;
        Block centre = world.getBlockAt(bx + 8, world.getHighestBlockYAt(bx + 8, bz + 8), bz + 8);
        var biome = tables.biome(centre.getBiome().getKey().getKey());
        if (biome.isEmpty()) {
            return;
        }
        SpawnRandom rand = random();
        for (AquaticSpawns.Member m : AquaticSpawns.pass(biome.get(), rand)) {
            Carrier carrier = animals.table().find(m.entry().type()).orElse(null);
            FaunaRule rule = tables.fauna().get(m.entry().type());
            Optional<AquaticSpecies> species = AquaticSpecies.ofEntityType(m.entry().type());
            if (carrier == null || rule == null || species.isEmpty()) {
                continue;
            }
            Location at = waterPlace(world, bx + m.x(), bz + m.z(), carrier, ThreadLocalRandom.current().nextDouble());
            if (at == null || !species.get().passesFluidGate(fluidAt(at.getBlock()))) {
                continue;
            }
            if (!SpawnRules.accepts(rule, site(world, at, now), rand)) {
                continue;
            }
            spawn(animals, species.get(), carrier, at, world);
        }
    }

    /** A spot of the column's water with room for the carrier, {@code draw} in [0,1) picking the depth; null without water. */
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

    SpawnSite site(World world, Location at, ClockSnapshot now) {
        long seed = world.getSeed();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        ChunkClimate climate = dev.tfp.paper.climate.ChunkClimateSource.climateAt(seed, x, z);
        int density = ChunkDataGenerator.forLevel(seed).forestType(x >> 4, z >> 4).density();
        boolean north = HudHemisphere.north(true, seed, z);
        Block feet = at.getBlock();
        int brightness = Math.max(feet.getLightFromBlocks(), feet.getLightFromSky());
        return new SpawnSite(at.getBlockY(), ChunkData.SEA_LEVEL_Y, climate, density, north, brightness,
                CalendarTicks.month(now.calendarTicks(), now.daysInMonth()));
    }

    private void spawn(AnimalService animals, AquaticSpecies species, Carrier carrier, Location at, World world) {
        animals.spawn(at, carrier, AnimalSpawnRoll.AgeHint.RANDOM, null).thenAccept(entity -> {
            if (SQUIDS.contains(carrier.id())) {
                ctx.scheduler().entity(entity, () -> sizeSquid(entity, carrier.id(), true), null);
            }
        }).exceptionally(e -> {
            ctx.logger().fine("Aquatic spawn of " + carrier.id() + " failed: " + e.getMessage());
            return null;
        });
    }

    // ---- squid size --------------------------------------------------------------------------------------

    /**
     * TFC's {@code finalizeSpawn} for a squid: the size rolled from the species' spawn range, shrunk by 0.8 while the body would
     * collide, the spawn cancelled below the range's minimum. The size (1..127, beyond the animal record's 1..32 genetic size) is kept in the entity PDC {@code tfp:squid_size}.
     */
    void sizeSquid(Entity entity, String species, boolean natural) {
        Optional<AnimalRecord> record = AnimalPdc.read(entity.getPersistentDataContainer());
        if (record.isEmpty() || !entity.isValid()) {
            return;
        }
        SquidSize.SizeRange range = species.equals("tfc:octopoteuthis") ? SquidSize.OCTOPOTEUTHIS_SPAWN : SquidSize.SQUID_SPAWN;
        int rolled = SquidSize.rolledSize(range, random());
        Optional<Integer> size = SquidSize.spawnedSize(range, rolled, natural, s -> collides(entity, s));
        if (size.isEmpty()) {
            entity.remove();
            return;
        }
        entity.getPersistentDataContainer().set(SIZE_KEY, PersistentDataType.INTEGER, size.get());
        applySquidScale(entity, size.get());
    }

    private static boolean collides(Entity entity, int size) {
        double half = 0.4 * SquidSize.scaleOf(size);
        double height = 0.8 * SquidSize.scaleOf(size);
        Location l = entity.getLocation();
        BoundingBox box = new BoundingBox(l.getX() - half, l.getY(), l.getZ() - half, l.getX() + half, l.getY() + height, l.getZ() + half);
        return entity.getWorld().hasCollisionsIn(box);
    }

    static void applySquidScale(Entity entity) {
        Integer size = entity.getPersistentDataContainer().get(SIZE_KEY, PersistentDataType.INTEGER);
        if (size != null) {
            applySquidScale(entity, size);
        }
    }

    static void applySquidScale(Entity entity, int size) {
        if (entity instanceof LivingEntity living) {
            AttributeInstance scale = living.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.setBaseValue(Math.min(16.0, SquidSize.scaleOf(size)));
            }
        }
    }

    /** Squid and octopoteuthis keep the carrier's vanilla goals; their size sets the scale after every age step. */
    private static final class SquidBehaviour implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return SQUIDS;
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            applySquidScale(entity);
        }

        @Override
        public void onCheck(Check check) {
            applySquidScale(check.entity());
        }
    }

    // ---- mob buckets -------------------------------------------------------------------------------------

    private Optional<AquaticSpecies> speciesOf(Entity e) {
        if (speciesOf != null) {
            return speciesOf.apply(e);
        }
        return animals().record(e).flatMap(r -> AquaticSpecies.ofEntityType(r.species()));
    }

    private AquaticFluid bucketFluid(ItemStack held) {
        if (bucketFluid != null) {
            return bucketFluid.apply(held);
        }
        if (held.getType() == Material.WATER_BUCKET) {
            return AquaticFluid.VANILLA_WATER;
        }
        return tags.itemId(held).map(id -> switch (id) {
            case "tfp:bucket/salt_water" -> AquaticFluid.SALT_WATER;
            case "tfp:bucket/river_water" -> AquaticFluid.RIVER_WATER;
            case "tfp:bucket/spring_water" -> AquaticFluid.SPRING_WATER;
            default -> AquaticFluid.NOT_WATER;
        }).orElse(AquaticFluid.NOT_WATER);
    }

    private Optional<AquaticSpecies> filledBucket(ItemStack held) {
        if (filledBucket != null) {
            return filledBucket.apply(held);
        }
        return tags.itemId(held).filter(id -> id.startsWith(BUCKET_PREFIX))
                .flatMap(id -> AquaticSpecies.ofEntityType("tfc:" + id.substring(BUCKET_PREFIX.length())));
    }

    private Optional<ItemStack> create(String id, int amount) {
        if (createItem != null) {
            return createItem.apply(id, amount);
        }
        return ctx.integrations().customItems().create(id, amount);
    }

    static String bucketId(AquaticSpecies species) {
        return BUCKET_PREFIX + species.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** A bucket of the species' fluid on an aquatic mob picks it up into its mob bucket. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickUp(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Entity target = event.getRightClicked();
        if (held.isEmpty() || !(target instanceof LivingEntity living)) {
            return;
        }
        AquaticFluid fluid = bucketFluid(held);
        if (fluid == AquaticFluid.NOT_WATER) {
            return;
        }
        Optional<AquaticSpecies> species = speciesOf(target);
        if (species.isEmpty() || !AquaticBuckets.canPickUp(species.get(), fluid, !living.isDead())) {
            return;
        }
        Optional<ItemStack> bucket = create(bucketId(species.get()), 1);
        if (bucket.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        target.remove();
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.getInventory().addItem(bucket.get()).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        } else if (held.getAmount() == 1) {
            player.getInventory().setItemInMainHand(bucket.get());
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().addItem(bucket.get()).values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BUCKET_FILL_FISH, 1f, 1f);
    }

    /** A mob bucket used on water releases a fresh animal there and gives back the empty bucket. */
    @EventHandler(priority = EventPriority.LOW)
    public void onRelease(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            return;
        }
        Optional<AquaticSpecies> species = filledBucket(held);
        if (species.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        RayTraceResult hit = player.rayTraceBlocks(5.0, FluidCollisionMode.ALWAYS);
        if (hit == null || hit.getHitBlock() == null) {
            return;
        }
        Block at = hit.getHitBlock();
        if (at.getType() != Material.WATER && hit.getHitBlockFace() != null) {
            at = at.getRelative(hit.getHitBlockFace());
        }
        Carrier carrier = animals().table().find(species.get().entityType()).orElse(null);
        if (carrier == null) {
            return;
        }
        Location where = at.getLocation().add(0.5, 0.0, 0.5);
        animals().spawn(where, carrier, AnimalSpawnRoll.AgeHint.RANDOM, null).exceptionally(e -> null);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.BUCKET));
        }
    }

    private static SpawnRandom random() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new SpawnRandom() {
            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }

            @Override
            public float nextFloat() {
                return r.nextFloat();
            }
        };
    }
}
