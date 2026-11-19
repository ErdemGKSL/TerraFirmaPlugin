/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/JugItem.java @ c3b4d9536 (finishUsingItem, afterFillFailed,
 *       afterEmptyFailed), src/main/java/net/dries007/tfc/common/items/GlassBottleItem.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (use),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenWorldAndItem,
 *       pickupFluidInto), src/main/java/net/dries007/tfc/util/data/Drinkable.java @ c3b4d9536 (onDrink)
 */
package dev.tfp.paper.jug;

import dev.tfp.core.bucket.BucketRules;
import dev.tfp.core.bucket.BucketRules.HandUpdate;
import dev.tfp.core.bucket.WorldFluid;
import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.jug.DrinkContainerRules;
import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.bucket.BucketVisuals;
import dev.tfp.paper.fluid.FluidContainerItems;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.itemdata.ItemFluids;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;

/**
 * PAR-POT-07: the ceramic jug and the glass bottles (TFC {@code JugItem}, {@code GlassBottleItem}). Right-click:
 *
 * <ol>
 *   <li>an empty container looking at a water or lava source (or the water of a waterlogged block) fills from it, as far
 *       as its capacity and {@code tfc:usable_in_jug} allow, and the source is taken ({@code pickupFluidInto} without
 *       infinite filling);</li>
 *   <li>an empty container otherwise blows across the opening (a sound, no change);</li>
 *   <li>a filled container pours everything out when sneaking;</li>
 *   <li>a filled container holding a drinkable fluid drinks: the jug drains everything and a bottle 100 mB, the drinkable's
 *       thirst and effects scale with the amount drunk ({@link Drinkable#drink}), then the container breaks with its break
 *       chance. A drinkable that gives water is refused at full thirst unless it may be drunk when full.</li>
 * </ol>
 * The fluid lives in the item PDC ({@code FluidPdc}); the overlay look is {@link BucketVisuals}. The drink is instant (no
 * 32 tick use animation, see the ledger). Threads: Bukkit events on the player's region thread.
 */
public final class JugListener implements Listener {
    /** The vanilla survival block interaction range, used when the attribute is missing. */
    static final double DEFAULT_REACH = 4.5;
    /** {@code getFlowing(1, false)} aside, a source block of vanilla fluids has level 0. */
    private static final String SPRING_WATER = "tfp:spring_water";

    private final Function<ItemStack, Optional<String>> itemId;
    private final BiPredicate<FluidContainerKind, String> usable;
    private final Function<Block, String> waterFluid;
    private final Predicate<Block> springWater;
    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final HudSnapshotCache hud;
    private final Supplier<Drinkables> drinkables;
    private final Consumer<Player> changed;
    private final Function<Player, Block> targeter;
    /** The player's tick of the last action: one click fires one event per hand and acts once. */
    private final Map<UUID, Integer> lastUse = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> firing = ThreadLocal.withInitial(() -> false);

    /**
     * @param itemId     the item id of a stack (CraftEngine id, else the material key)
     * @param usable     whether a container may hold a fluid (its {@code tfc:usable_in_*} tag)
     * @param waterFluid the container fluid of a water block (the world water rules), on the block's owner thread
     * @param springWater whether a water source block is hot spring water, on the block's owner thread
     * @param changed    called on the player's thread after a drink changed the thirst (HUD refresh)
     */
    public JugListener(Function<ItemStack, Optional<String>> itemId, BiPredicate<FluidContainerKind, String> usable,
            Function<Block, String> waterFluid, Predicate<Block> springWater, PlayerDataStore data,
            LongSupplier playerTicks, HudSnapshotCache hud, Supplier<Drinkables> drinkables,
            Consumer<Player> changed) {
        this(itemId, usable, waterFluid, springWater, data, playerTicks, hud, drinkables, changed,
            JugListener::target);
    }

    /** With a stand-in for the block in view (tests: MockBukkit has no fluid ray trace). */
    JugListener(Function<ItemStack, Optional<String>> itemId, BiPredicate<FluidContainerKind, String> usable,
            Function<Block, String> waterFluid, Predicate<Block> springWater, PlayerDataStore data,
            LongSupplier playerTicks, HudSnapshotCache hud, Supplier<Drinkables> drinkables,
            Consumer<Player> changed, Function<Player, Block> targeter) {
        this.itemId = itemId;
        this.usable = usable;
        this.waterFluid = waterFluid;
        this.springWater = springWater;
        this.data = data;
        this.playerTicks = playerTicks;
        this.hud = hud;
        this.drinkables = drinkables;
        this.changed = changed;
        this.targeter = targeter;
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
        Optional<FluidContainerKind> kind = itemId.apply(held).flatMap(FluidContainerItems::kindOf)
            .filter(DrinkContainerRules::isDrinkContainer);
        if (kind.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        // the block's own use comes first (a chest, a barrel), as in vanilla, unless the player sneaks
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && clicked.getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }
        ThreadAsserts.owned(player);
        int tick = player.getTicksLived();
        Integer last = lastUse.get(player.getUniqueId());
        if (last != null && last == tick) {
            event.setCancelled(true);
            return;
        }
        use(player, hand, held, kind.get());
        lastUse.put(player.getUniqueId(), tick);
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    /** TFC {@code FluidContainerItem.use}: the world transfer first, then the fallback by the content. */
    void use(Player player, EquipmentSlot hand, ItemStack held, FluidContainerKind kind) {
        ItemStack one = held.clone();
        one.setAmount(1);
        FluidAmount content = ItemFluids.content(one);
        if (content.isEmpty()) {
            Block target = targeter.apply(player);
            if (target != null && Bukkit.isOwnedByCurrentRegion(target) && pickup(player, hand, held, one, kind, target)) {
                player.swingHand(hand);
                return;
            }
            blow(player, kind); // afterFillFailed
            player.swingHand(hand);
            return;
        }
        if (player.isSneaking()) {
            pourOut(player, hand, held, one);
            player.swingHand(hand);
            return;
        }
        drink(player, hand, held, one, kind, content);
    }

    /** {@code afterFillFailed}: the jug blows at 0.8 + look.y / 2, a bottle at 1.3 + look.y / 2. */
    private static void blow(Player player, FluidContainerKind kind) {
        float base = kind == FluidContainerKind.JUG ? 0.8f : 1.3f;
        float pitch = Math.clamp(base + (float) (player.getLocation().getDirection().getY() / 2f), 0.5f, 2.0f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.PLAYERS, 1f, pitch);
    }

    /** {@code afterEmptyFailed} while sneaking: BUCKET_EMPTY at 0.5 / 1.2, the whole content gone. */
    private void pourOut(Player player, EquipmentSlot hand, ItemStack held, ItemStack one) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BUCKET_EMPTY, SoundCategory.PLAYERS, 0.5f, 1.2f);
        ItemFluids.drain(one, Integer.MAX_VALUE);
        BucketVisuals.sync(one);
        updateHand(player, hand, held, one);
    }

    /** The drink: gate, drain, thirst and effects, break draw. */
    private void drink(Player player, EquipmentSlot hand, ItemStack held, ItemStack one, FluidContainerKind kind,
            FluidAmount content) {
        UUID id = player.getUniqueId();
        Optional<Drinkable> drinkable = drinkables.get().get(content.fluid());
        if (drinkable.isEmpty() || !data.isLoaded(id)) {
            return; // TFC: pass; a fluid that is not drinkable is only poured out by sneaking
        }
        long now = playerTicks.getAsLong();
        float temperature = (float) hud.snapshot(id).ambientTemperature();
        // draws before the pure update, TFC's order: one per effect (onDrink), then the break draw
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double[] draws = random.doubles(drinkable.get().effects().size()).toArray();
        float breakDraw = random.nextFloat();
        DrinkContainerRules.Drink plan = DrinkContainerRules.drink(kind, content, breakDraw);
        int[] next = {0};
        JugDrink.Result[] result = {null};
        data.update(id, d -> {
            next[0] = 0;
            result[0] = JugDrink.drink(d, now, temperature, drinkable.get(), plan.drained().amount(),
                () -> draws[next[0]++]);
            return result[0].data();
        });
        if (!result[0].drunk()) {
            changed.accept(player);
            return;
        }
        ItemFluids.drain(one, plan.drained().amount());
        player.getWorld().playSound(player, Sound.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1f, 1f);
        player.swingHand(hand);
        player.setSprinting(false);
        for (Drinkable.Effect effect : result[0].effects()) {
            dev.tfp.paper.player.ThirstEffects.apply(player, effect);
        }
        ItemStack after = one;
        if (plan.breaks()) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_DECORATED_POT_SHATTER, SoundCategory.PLAYERS,
                1f, 1f);
            after = new ItemStack(Material.AIR);
        } else {
            BucketVisuals.sync(one);
        }
        updateHand(player, hand, held, after);
        changed.accept(player);
    }

    /** {@code pickupFluidInto}: an empty container takes the fluid of the source, which leaves the world. */
    private boolean pickup(Player player, EquipmentSlot hand, ItemStack held, ItemStack one, FluidContainerKind kind,
            Block block) {
        BlockData blockData = block.getBlockData();
        WorldFluid source;
        boolean waterlogged = false;
        if (isSource(block, Material.WATER)) {
            source = WorldFluid.WATER;
        } else if (isSource(block, Material.LAVA)) {
            source = WorldFluid.LAVA;
        } else if (blockData instanceof Waterlogged w && w.isWaterlogged()) {
            source = WorldFluid.WATER;
            waterlogged = true;
        } else {
            return false;
        }
        String fluid = source == WorldFluid.LAVA ? source.pickedUp(false)
            : waterlogged ? WorldFluid.WATER.pickedUp(false)
            : springWater.test(block) ? SPRING_WATER : waterFluid.apply(block);
        BucketRules.Pickup pickup = BucketRules.pickup(fluid, kind.defaultCapacity(), usable.test(kind, fluid));
        if (!pickup.removesSource()) {
            return false;
        }
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, block, block, BlockFace.UP, Material.BUCKET, held,
            hand);
        firing.set(true);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            firing.set(false);
        }
        if (event.isCancelled()) {
            return false;
        }
        if (waterlogged) {
            Waterlogged w = (Waterlogged) blockData;
            w.setWaterlogged(false);
            block.setBlockData(w, true);
        } else {
            block.setType(Material.AIR, true);
        }
        ItemFluids.fill(one, pickup.content(), kind.defaultCapacity(), true);
        BucketVisuals.sync(one);
        block.getWorld().playSound(block.getLocation().add(0, 0.5, 0),
            source == WorldFluid.LAVA ? Sound.ITEM_BUCKET_FILL_LAVA : Sound.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
        updateHand(player, hand, held, one);
        return true;
    }

    /** TFC {@code FluidHelpers.with}: the hand and the inventory after a change, by {@link BucketRules#handUpdate}. */
    static void updateHand(Player player, EquipmentSlot hand, ItemStack held, ItemStack changed) {
        PlayerInventory inv = player.getInventory();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        HandUpdate update = BucketRules.handUpdate(held.getAmount(), creative);
        switch (update) {
            case REPLACE -> inv.setItem(hand, changed);
            case KEEP -> {
            }
            case SPLIT -> {
                ItemStack rest = held.clone();
                rest.setAmount(held.getAmount() - 1);
                inv.setItem(hand, rest);
                if (!changed.getType().isAir()) {
                    inv.addItem(changed).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                }
            }
            default -> {
            }
        }
    }

    private static boolean isSource(Block block, Material fluid) {
        return block.getType() == fluid && block.getBlockData() instanceof Levelled l && l.getLevel() == 0;
    }

    /** The block in view, counting fluid sources, within the player's block interaction range; or null. */
    static Block target(Player player) {
        Location eye = player.getEyeLocation();
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = range == null ? DEFAULT_REACH : range.getValue();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), reach,
            FluidCollisionMode.SOURCE_ONLY, false);
        return hit == null ? null : hit.getHitBlock();
    }
}
