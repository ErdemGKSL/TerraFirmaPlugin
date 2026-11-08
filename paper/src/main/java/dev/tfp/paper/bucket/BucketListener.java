/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (use),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenWorldAndItem,
 *       pickupFluidInto, emptyFluidFrom, playTransferSound, with), net.minecraft.world.item.BucketItem (the TFC fluid
 *       buckets' use, which TFC inherits)
 */
package dev.tfp.paper.bucket;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.bucket.BucketKind;
import dev.tfp.core.bucket.BucketRules;
import dev.tfp.core.bucket.BucketRules.HandUpdate;
import dev.tfp.core.bucket.BucketRules.Outcome;
import dev.tfp.core.bucket.BucketRules.Placement;
import dev.tfp.core.bucket.BucketRules.Target;
import dev.tfp.core.bucket.WorldFluid;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.itemdata.ItemFluids;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

/**
 * PAR-FLUID-02: buckets used on the world.
 *
 * <ul>
 *   <li>Tank buckets ({@link BucketKind}): TFC's {@code FluidContainerItem.use}. The target is the block the player looks
 *       at, counting fluid sources (TFC's {@code ClipContext.Fluid.SOURCE_ONLY} ray, the block interaction range). An empty
 *       bucket picks up a water or lava source or the water of a waterlogged block, when its {@code tfc:usable_in_*} tag
 *       allows the fluid (the wooden bucket takes no lava); which water it is (salt, spring, river or fresh) comes from
 *       PAR-FLUID-03's world water rules when that module runs, else salt water in an ocean biome. A bucket
 *       that holds fluid empties all of it into the world ({@link BucketRules#place}): a transient flowing block unless
 *       the config allows sources ({@link BucketConfig}). Only {@link WorldFluid#placedAs placeable} fluids are placed;
 *       any other fluid stays in the bucket (NP-FLUID-01).</li>
 *   <li>TFC's fluid buckets ({@code tfp:bucket/<fluid>}, vanilla {@code BucketItem}s in TFC): emptied by the same
 *       {@code emptyFluidFrom} rules with source placement on ({@link BucketRules#place}): a placeable fluid is placed as
 *       a source and the bucket becomes a vanilla empty bucket (kept in creative); any other fluid is refused.</li>
 *   <li>A vanilla empty bucket filled from salt water gives {@code tfp:bucket/salt_water}, as TFC's salt water source
 *       gives TFC's salt water bucket.</li>
 * </ul>
 * Every world change first fires a {@link PlayerBucketFillEvent} / {@link PlayerBucketEmptyEvent}, so protection plugins
 * can refuse it. The change is one block in reach of the player, decided by the pure {@link BucketRules} and written at
 * once on the player's region thread (the same thread that owns the target, checked), as the vanilla bucket does.
 *
 * <p>Threads: Bukkit events on the player's region thread.
 */
public final class BucketListener implements Listener {
    /** The prefix of TFC's per-fluid vanilla buckets. */
    public static final String FLUID_BUCKET_PREFIX = "tfp:bucket/";
    public static final String SALT_WATER_BUCKET = FLUID_BUCKET_PREFIX + "salt_water";
    /** The vanilla survival block interaction range, used when the attribute is missing. */
    static final double DEFAULT_REACH = 4.5;
    /** {@code getFlowing(1, false)}: a flowing block of amount 1 has the block level {@code 8 - 1}. */
    static final int FLOWING_LEVEL = 7;

    private final Function<ItemStack, Optional<String>> itemId;
    private final BiPredicate<FluidContainerKind, String> usable;
    private final Supplier<BucketConfig> config;
    private final Function<String, Optional<ItemStack>> create;
    private final Function<Block, String> waterFluid;
    /** The last game tick a player's bucket acted: the main and the off hand of one click act once. */
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    /** Set while this listener fires its own bucket events, so {@link #onVanillaFill} leaves them alone. */
    private final ThreadLocal<Boolean> firing = ThreadLocal.withInitial(() -> false);

    /**
     * @param itemId the item id of a stack (CraftEngine id, else the material key)
     * @param usable whether a container may hold a fluid (its {@code tfc:usable_in_*} tag)
     * @param config the current bucket config
     * @param create     a new stack of one item of an id
     * @param waterFluid the container fluid of a water block (PAR-FLUID-03's {@code WorldWaterService.kindOf}), on the
     *                   block's owner thread
     */
    public BucketListener(Function<ItemStack, Optional<String>> itemId, BiPredicate<FluidContainerKind, String> usable,
            Supplier<BucketConfig> config, Function<String, Optional<ItemStack>> create,
            Function<Block, String> waterFluid) {
        this.itemId = itemId;
        this.usable = usable;
        this.config = config;
        this.create = create;
        this.waterFluid = waterFluid;
    }

    /** With the vanilla-biome salt rule for water ({@link #biomeWaterFluid}). */
    public BucketListener(Function<ItemStack, Optional<String>> itemId, BiPredicate<FluidContainerKind, String> usable,
            Supplier<BucketConfig> config, Function<String, Optional<ItemStack>> create) {
        this(itemId, usable, config, create, BucketListener::biomeWaterFluid);
    }

    /** The fallback water rule: salt water in a vanilla ocean biome, else fresh water. */
    static String biomeWaterFluid(Block block) {
        return WorldFluid.WATER.pickedUp(isSaltyBiome(block.getBiome()));
    }

    /** The block the bucket acts on and the clicked face. */
    public record Hit(Block block, BlockFace face) {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        EquipmentSlot hand = event.getHand();
        if (hand == null || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack held = event.getItem();
        Optional<String> id = itemId.apply(held);
        if (id.isEmpty()) {
            return;
        }
        Optional<BucketKind> tank = id.flatMap(BucketKind::byItem);
        boolean fluidBucket = id.get().startsWith(FLUID_BUCKET_PREFIX);
        if (tank.isEmpty() && !fluidBucket) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        // the block's own use comes first (a chest, a door), as in vanilla, unless the player sneaks
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }
        Hit hit = target(player);
        if (hit == null) {
            return;
        }
        long tick = player.getWorld().getFullTime();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && last == tick) {
            event.setCancelled(true);
            return;
        }
        boolean done = tank.isPresent()
                ? useTank(player, hand, held, tank.get(), hit)
                : useFluidBucket(player, hand, held, id.get(), hit);
        if (done) {
            lastUse.put(player.getUniqueId(), tick);
            event.setCancelled(true);
            player.swingHand(hand);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** A vanilla bucket filled with ocean water becomes TFC's salt water bucket. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaFill(PlayerBucketFillEvent event) {
        if (firing.get()) {
            return;
        }
        ItemStack result = event.getItemStack();
        if (result == null || result.getType() != Material.WATER_BUCKET
                || !Drinkables.SALT_WATER.equals(waterFluid.apply(event.getBlock()))) {
            return;
        }
        create.apply(SALT_WATER_BUCKET).ifPresent(event::setItemStack);
    }

    /** The block in view, counting fluid sources, within the player's block interaction range; or null. */
    static Hit target(Player player) {
        Location eye = player.getEyeLocation();
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = range == null ? DEFAULT_REACH : range.getValue();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), reach,
                FluidCollisionMode.SOURCE_ONLY, false);
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }
        return new Hit(hit.getHitBlock(), hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace());
    }

    /**
     * A tank bucket used on {@code hit}. Changes the world and the player's hand when something moved.
     *
     * @return whether a transfer happened
     */
    boolean useTank(Player player, EquipmentSlot hand, ItemStack held, BucketKind kind, Hit hit) {
        if (!Bukkit.isOwnedByCurrentRegion(hit.block())) {
            return false;
        }
        ItemStack one = held.clone();
        one.setAmount(1);
        FluidContainerKind container = kind.container();
        FluidAmount content = ItemFluids.content(one);
        if (content.isEmpty()) {
            return pickup(player, hand, held, one, container, hit);
        }
        Optional<WorldFluid> fluid = WorldFluid.placedAs(content.fluid());
        if (fluid.isEmpty()) {
            return false; // a fluid that has no world block on vanilla clients stays in the bucket
        }
        Block target = place(player, held, hand, fluid.get(), content.amount(), config.get().canPlaceSources(kind), hit);
        if (target == null) {
            return false;
        }
        ItemFluids.drain(one, Integer.MAX_VALUE);
        BucketVisuals.sync(one);
        updateHand(player, hand, held, one);
        return true;
    }

    /**
     * A TFC fluid bucket used on {@code hit}: places a source of a placeable fluid; the bucket becomes a vanilla bucket.
     *
     * @return whether the fluid was placed
     */
    boolean useFluidBucket(Player player, EquipmentSlot hand, ItemStack held, String id, Hit hit) {
        if (!Bukkit.isOwnedByCurrentRegion(hit.block())) {
            return false;
        }
        String fluidId = "tfp:" + id.substring(FLUID_BUCKET_PREFIX.length());
        Optional<WorldFluid> fluid = WorldFluid.placedAs(fluidId);
        if (fluid.isEmpty()) {
            return false;
        }
        if (place(player, held, hand, fluid.get(), BucketRules.BUCKET_VOLUME, true, hit) == null) {
            return false;
        }
        updateHand(player, hand, held, new ItemStack(Material.BUCKET));
        return true;
    }

    private boolean pickup(Player player, EquipmentSlot hand, ItemStack held, ItemStack one, FluidContainerKind container,
            Hit hit) {
        Block block = hit.block();
        BlockData data = block.getBlockData();
        WorldFluid source;
        boolean waterlogged = false;
        if (isSource(block, Material.WATER)) {
            source = WorldFluid.WATER;
        } else if (isSource(block, Material.LAVA)) {
            source = WorldFluid.LAVA;
        } else if (data instanceof Waterlogged w && w.isWaterlogged()) {
            source = WorldFluid.WATER;
            waterlogged = true;
        } else {
            return false;
        }
        String fluid = source == WorldFluid.WATER ? waterFluid.apply(block) : source.pickedUp(false);
        BucketRules.Pickup pickup = BucketRules.pickup(fluid, container.defaultCapacity(), usable.test(container, fluid));
        if (!pickup.removesSource()) {
            return false;
        }
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, block, block, hit.face(), Material.BUCKET, held,
                hand);
        if (!call(event)) {
            return false;
        }
        if (waterlogged) {
            Waterlogged w = (Waterlogged) data;
            w.setWaterlogged(false);
            block.setBlockData(w, true);
        } else {
            block.setType(Material.AIR, true);
        }
        ItemFluids.fill(one, pickup.content(), container.defaultCapacity(), true);
        BucketVisuals.sync(one);
        sound(block, source == WorldFluid.LAVA ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL);
        updateHand(player, hand, held, one);
        return true;
    }

    /**
     * TFC {@code emptyFluidFrom} on the hit block, then on the block on the clicked face. Writes the world change.
     *
     * @return the block that took the fluid, or null when nothing was placed
     */
    private Block place(Player player, ItemStack held, EquipmentSlot hand, WorldFluid fluid, int amount, boolean sources,
            Hit hit) {
        boolean ultrawarm = hit.block().getWorld().isUltraWarm();
        Block target = hit.block();
        Placement placement = BucketRules.place(fluid, amount, sources, facts(target), ultrawarm, true);
        if (placement.outcome() == Outcome.TRY_ADJACENT) {
            target = target.getRelative(hit.face());
            if (!Bukkit.isOwnedByCurrentRegion(target)) {
                return null;
            }
            placement = BucketRules.place(fluid, amount, sources, facts(target), ultrawarm, false);
        }
        if (!placement.drains()) {
            return null;
        }
        PlayerBucketEmptyEvent event = new PlayerBucketEmptyEvent(player, target, hit.block(), hit.face(),
                fluid == WorldFluid.LAVA ? Material.LAVA_BUCKET : Material.WATER_BUCKET, held, hand);
        if (!call(event)) {
            return null;
        }
        apply(target, fluid, placement);
        return target;
    }

    /** Writes a placement into the world, with TFC's sounds. */
    static void apply(Block target, WorldFluid fluid, Placement placement) {
        Material material = fluid == WorldFluid.LAVA ? Material.LAVA : Material.WATER;
        switch (placement.outcome()) {
            case VAPORIZE -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                target.getWorld().playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f,
                        2.6f + (r.nextFloat() - r.nextFloat()) * 0.8f);
                for (int i = 0; i < 8; i++) {
                    target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getX() + r.nextDouble(),
                            target.getY() + r.nextDouble(), target.getZ() + r.nextDouble(), 0, 0d, 0d, 0d);
                }
                return;
            }
            case WATERLOG -> {
                Waterlogged w = (Waterlogged) target.getBlockData();
                w.setWaterlogged(true);
                target.setBlockData(w, true);
            }
            case PLACE_SOURCE -> {
                breakFirst(target, placement);
                target.setType(material, true);
            }
            case PLACE_FLOWING -> {
                breakFirst(target, placement);
                Levelled flowing = (Levelled) material.createBlockData();
                flowing.setLevel(FLOWING_LEVEL);
                target.setBlockData(flowing, true);
            }
            case ABSORB -> {
                // the target already holds this fluid: nothing changes, but it sounds as if it was placed
            }
            default -> {
                return;
            }
        }
        sound(target, fluid == WorldFluid.LAVA ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY);
    }

    private static void breakFirst(Block target, Placement placement) {
        if (placement.breakFirst()) {
            target.breakNaturally();
        }
    }

    /** TFC {@code playTransferSound}: at the block, half a block up, in BLOCKS at 1/1. */
    private static void sound(Block block, Sound sound) {
        block.getWorld().playSound(block.getLocation().add(0, 0.5, 0), sound, SoundCategory.BLOCKS, 1f, 1f);
    }

    /** The facts of a block for {@link BucketRules#place}. */
    static Target facts(Block block) {
        Material type = block.getType();
        BlockData data = block.getBlockData();
        boolean liquid = type == Material.WATER || type == Material.LAVA;
        WorldFluid present = null;
        boolean presentSource = false;
        boolean canPlaceLiquid = false;
        if (liquid) {
            present = type == Material.LAVA ? WorldFluid.LAVA : WorldFluid.WATER;
            presentSource = data instanceof Levelled l && l.getLevel() == 0;
        } else if (data instanceof Waterlogged w) {
            if (w.isWaterlogged()) {
                present = WorldFluid.WATER;
                presentSource = true;
            } else {
                canPlaceLiquid = true;
            }
        }
        boolean air = type.isAir();
        // TFC state.canBeReplaced(fluid): replaceable, or not solid
        boolean replaceable = air || liquid || block.isReplaceable() || !type.isSolid();
        return new Target(air, replaceable, liquid, present, presentSource, canPlaceLiquid);
    }

    /** TFC {@code FluidHelpers.with}: the hand and the inventory after a transfer, by {@link BucketRules#handUpdate}. */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack changed) {
        PlayerInventory inv = player.getInventory();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        HandUpdate update = BucketRules.handUpdate(held.getAmount(), creative);
        switch (update) {
            case REPLACE -> inv.setItem(hand, changed);
            case KEEP -> {
            }
            case SPLIT -> {
                if (!creative) {
                    ItemStack rest = held.clone();
                    rest.setAmount(held.getAmount() - 1);
                    inv.setItem(hand, rest);
                }
                inv.addItem(changed).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            }
            default -> {
            }
        }
    }

    private boolean call(Event event) {
        firing.set(true);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            firing.set(false);
        }
        return !((org.bukkit.event.Cancellable) event).isCancelled();
    }

    private static boolean isSource(Block block, Material fluid) {
        return block.getType() == fluid && block.getBlockData() instanceof Levelled l && l.getLevel() == 0;
    }

    /**
     * Whether water in {@code biome} is TFC's salt water: the vanilla ocean biomes, the same rule as drinking
     * ({@code WaterDrinkListener.isSaltyBiome}).
     */
    @SuppressWarnings("deprecation")
    static boolean isSaltyBiome(Biome biome) {
        return biome != null && biome.getKey().getKey().contains("ocean");
    }
}
