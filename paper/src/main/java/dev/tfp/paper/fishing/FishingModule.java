/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536 (use, overrideOtherStackedOnMe),
 *       src/main/java/net/dries007/tfc/common/entities/misc/TFCFishingHook.java @ c3b4d9536 (retrieve, pullEntity, eatBait)
 */
package dev.tfp.paper.fishing;

import dev.tfp.core.fishing.BaitRules;
import dev.tfp.core.fishing.BaitType;
import dev.tfp.core.fishing.FishingRod;
import dev.tfp.core.fishing.Hook;
import dev.tfp.core.fishing.HookedSpecies;
import dev.tfp.core.metal.MetalTier;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Fishing with TFC's rods (PAR-FAUNA-18). A vanilla fishing rod carries the CraftEngine rod item {@code tfp:metal/fishing_rod/<metal>};
 * the vanilla hook is the hook entity, with its own bite timer switched off and its catch loot cancelled (TFC's {@code catchingFish}
 * is a no-op).
 * <ul>
 *   <li><b>Cast</b>: a rod without bait throws nothing and shows {@code tfc.fishing.no_bait}; otherwise the hook remembers the bait and
 *       the rod's pull strength ({@link FishingRod#strength}).</li>
 *   <li><b>Bait</b>: a right click on a rod with small or large bait on the cursor puts one bait on it ({@link BaitRules#canLoad}); the
 *       rod's tooltip shows "Bait: name".</li>
 *   <li><b>Fish come to the hook</b>: every fish, dolphin and orca gets TFC's {@code GetHookedGoal} ({@link HookGoal}). On reaching the
 *       hook the animal is hooked, the splash plays and the bait is eaten at once by a large animal, with chance 1/12 by a small one.</li>
 *   <li><b>Retrieve</b>: {@link Hook#retrieve}: a hooked animal is pulled to the owner, a pull within 25 ticks of the last one adds
 *       exhaustion and past 100 the bait is eaten and the hook discarded; the rod takes 1 or 2 damage. The exhaustion is lazy: the hook
 *       stores a value and a time and {@link Hook#after} reads it when someone pulls.</li>
 * </ul>
 * Hooks are not persisted (vanilla hooks are not saved either).
 */
public final class FishingModule implements FeatureModule, Listener, HookGoal.Site {
    static final String ROD_PREFIX = "tfp:metal/fishing_rod/";
    static final String HOLDS_SMALL = "tfc:holds_small_fishing_bait";
    static final String HOLDS_LARGE = "tfc:holds_large_fishing_bait";
    static final String SMALL_BAIT = "tfc:small_fishing_bait";
    static final String LARGE_BAIT = "tfc:large_fishing_bait";
    static final String NEEDS_LARGE = "tfc:needs_large_fishing_bait";
    /** A bite timer no hook ever reaches: TFC's hooks catch nothing by themselves. */
    static final int NEVER = 20 * 60 * 60 * 24;

    /** What the module remembers of one cast hook. */
    static final class HookData {
        final FishHook hook;
        final UUID owner;
        final String baitId;
        final float strength;
        volatile Hook state = Hook.fresh();
        volatile long stamp;
        volatile boolean taken;

        HookData(FishHook hook, UUID owner, String baitId, float strength, long now) {
            this.hook = hook;
            this.owner = owner;
            this.baitId = baitId;
            this.strength = strength;
            this.stamp = now;
        }
    }

    private ModuleContext ctx;
    private Tags tags;
    // seams for tests
    private java.util.function.Function<ItemStack, Optional<String>> itemIds;
    private java.util.function.BiPredicate<String, String> itemTags;

    /** Test seam: item ids and item tag membership without the Tags service. */
    void initForTest(ModuleContext context, java.util.function.Function<ItemStack, Optional<String>> itemIds,
            java.util.function.BiPredicate<String, String> itemTags) {
        this.ctx = context;
        this.itemIds = itemIds;
        this.itemTags = itemTags;
    }

    private Optional<String> itemId(ItemStack stack) {
        return itemIds != null ? itemIds.apply(stack) : tags.itemId(stack);
    }

    private boolean inTag(String itemId, String tag) {
        return itemTags != null ? itemTags.test(itemId, tag) : tags.is(TagType.ITEM, itemId, tag);
    }

    private boolean entityInTag(String species, String tag) {
        return tags.is(TagType.ENTITY_TYPE, species, tag);
    }
    private final Map<UUID, HookData> hooks = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "fishing";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        this.tags = context.services().require(Tags.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        animals.behaviours().register(new FishBehaviour());
        context.services().require(TooltipPipeline.class).add(new BaitTooltipContributor(this::isRodId));
        context.listen(this);
    }

    @Override
    public void disable() {
        hooks.clear();
    }

    // ---- item facts ------------------------------------------------------------------------------------

    boolean isRodId(String id) {
        return id != null && inTag(id, HOLDS_SMALL);
    }

    boolean isRod(ItemStack stack) {
        return itemId(stack).filter(this::isRodId).isPresent();
    }

    BaitType baitType(String id) {
        return BaitType.of(inTag(id, SMALL_BAIT), inTag(id, LARGE_BAIT));
    }

    /** The pull strength of the rod with this id: {@code tier speed / 12 + 1}; a rod of an unknown metal pulls like copper. */
    static float strengthOf(String rodId) {
        MetalTier tier = MetalTier.COPPER;
        if (rodId != null && rodId.startsWith(ROD_PREFIX)) {
            String metal = rodId.substring(ROD_PREFIX.length()).toUpperCase(Locale.ROOT);
            for (MetalTier t : MetalTier.values()) {
                if (t.name().equals(metal)) {
                    tier = t;
                }
            }
        }
        return FishingRod.strength(tier);
    }

    // ---- loading bait ----------------------------------------------------------------------------------

    /** TFC {@code overrideOtherStackedOnMe}: a secondary click on a rod with bait on the cursor, outside creative. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ItemStack rod = event.getCurrentItem();
        ItemStack carried = event.getCursor();
        if (event.getClick() != ClickType.RIGHT || rod == null || carried == null || carried.isEmpty() || rod.isEmpty()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Optional<String> rodId = itemId(rod);
        Optional<String> baitId = itemId(carried);
        if (rodId.isEmpty() || baitId.isEmpty() || !isRodId(rodId.get())) {
            return;
        }
        boolean allowed = player.getGameMode() != GameMode.CREATIVE;
        if (!BaitRules.canLoad(allowed, baitType(baitId.get()), inTag(rodId.get(), HOLDS_SMALL),
                inTag(rodId.get(), HOLDS_LARGE))) {
            return;
        }
        event.setCancelled(true);
        RodBait.set(rod, baitId.get());
        event.setCurrentItem(rod);
        ItemStack rest = carried.clone();
        rest.setAmount(carried.getAmount() - 1);
        player.setItemOnCursor(rest.getAmount() <= 0 ? null : rest);
    }

    // ---- casting and retrieving ------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> cast(event, player, hook);
            case BITE, CAUGHT_FISH, FAILED_ATTEMPT -> {
                if (hooks.containsKey(hook.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
            case REEL_IN, CAUGHT_ENTITY, IN_GROUND -> {
                HookData data = hooks.get(hook.getUniqueId());
                if (data != null) {
                    event.setCancelled(true);
                    retrieve(player, event.getHand(), data);
                }
            }
            default -> {
            }
        }
        hooks.values().removeIf(d -> !d.hook.isValid());
    }

    private void cast(PlayerFishEvent event, Player player, FishHook hook) {
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        ItemStack rod = player.getInventory().getItem(hand);
        Optional<String> rodId = itemId(rod);
        if (rodId.isEmpty() || !isRodId(rodId.get())) {
            return;
        }
        Optional<String> bait = RodBait.get(rod);
        if (!FishingRod.canCast(bait.isPresent())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(ctx.messages().get(player.locale().toString().toLowerCase(Locale.ROOT), "fishing.no_bait")));
            return;
        }
        hook.setMinWaitTime(NEVER);
        hook.setMaxWaitTime(NEVER);
        hooks.put(hook.getUniqueId(), new HookData(hook, player.getUniqueId(), bait.get(), strengthOf(rodId.get()),
                hook.getWorld().getGameTime()));
    }

    /** TFC {@code TFCFishingHook.retrieve}: the pull, the exhaustion and the rod's wear. */
    void retrieve(Player player, EquipmentSlot hand, HookData data) {
        FishHook hook = data.hook;
        long now = hook.getWorld().getGameTime();
        Entity held = hook.getHookedEntity();
        Hook.Hooked hooked = held == null ? Hook.Hooked.NONE : held.isValid() ? Hook.Hooked.LIVE : Hook.Hooked.REMOVED;
        Hook.Retrieve out = data.state.after(now - data.stamp).retrieve(now, hooked, true, hook.isOnGround());
        data.state = out.next();
        data.stamp = now;
        if (out.tooHard()) {
            player.sendActionBar(Component.text(ctx.messages().get(player.locale().toString().toLowerCase(Locale.ROOT),
                    "fishing.pulled_too_hard")));
            eatBait(player);
            hook.getWorld().playSound(hook.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 1f,
                    0.5f + ThreadLocalRandom.current().nextFloat());
        } else if (out.warning()) {
            hook.getWorld().playSound(hook.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, SoundCategory.NEUTRAL, 1f,
                    0.5f + ThreadLocalRandom.current().nextFloat());
        }
        if (out.pulled() && held != null) {
            Hook.Velocity v = Hook.pullVelocity(player.getX(), player.getY(), player.getZ(), hook.getX(), hook.getY(), hook.getZ(),
                    data.strength);
            ctx.scheduler().entity(held, () -> held.setVelocity(new Vector(v.x(), v.y(), v.z())), () -> { });
        }
        if (out.damage() > 0) {
            player.damageItemStack(hand == null ? EquipmentSlot.HAND : hand, out.damage());
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.NEUTRAL, 1f,
                0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f));
        if (out.discarded()) {
            hooks.remove(hook.getUniqueId());
            hook.remove();
        }
    }

    /** TFC {@code eatBait}: removes the bait from the rod in the owner's main hand, else the off hand. */
    void eatBait(Player owner) {
        ItemStack main = owner.getInventory().getItemInMainHand();
        ItemStack off = owner.getInventory().getItemInOffHand();
        ItemStack use = isRod(main) ? main : isRod(off) ? off : null;
        if (use != null) {
            RodBait.clear(use);
            owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 1f, 1f);
        }
    }

    // ---- the goal --------------------------------------------------------------------------------------

    @Override
    public boolean wants(Mob mob, FishHook hook) {
        HookData data = hooks.get(hook.getUniqueId());
        if (data == null || data.taken || hook.getHookedEntity() != null || !hook.isInWater()) {
            return false;
        }
        Optional<String> species = ((TfpPlugin) ctx.plugin()).animals().record(mob).map(r -> r.species());
        boolean large = species.isPresent() && entityInTag(species.get(), NEEDS_LARGE);
        return BaitRules.hookAccepts(baitType(data.baitId), large);
    }

    @Override
    public boolean free(FishHook hook) {
        HookData data = hooks.get(hook.getUniqueId());
        return data != null && !data.taken && hook.getHookedEntity() == null && ownerOnline(data.owner);
    }

    private boolean ownerOnline(UUID owner) {
        Player p = ctx.plugin().getServer().getPlayer(owner);
        return p != null && p.isOnline();
    }

    @Override
    public void hooked(Mob mob, FishHook hook) {
        HookData data = hooks.get(hook.getUniqueId());
        if (data == null || data.taken) {
            return;
        }
        data.taken = true;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        boolean large = ((TfpPlugin) ctx.plugin()).animals().record(mob)
                .map(r -> entityInTag(r.species(), NEEDS_LARGE)).orElse(false);
        boolean eats = BaitRules.eatsBait(large, rng::nextDouble);
        ctx.scheduler().entity(hook, () -> hook.setHookedEntity(mob), () -> { });
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL,
                1f + rng.nextFloat(), rng.nextFloat() + 0.7f + 0.3f);
        if (eats) {
            Player owner = ctx.plugin().getServer().getPlayer(data.owner);
            if (owner != null) {
                ctx.scheduler().entity(owner, () -> eatBait(owner), () -> { });
            }
        }
    }

    /** Gives every fish, dolphin and orca TFC's {@code GetHookedGoal} at priority 1, next to its swimming goals. */
    private final class FishBehaviour implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return HookedSpecies.ids();
        }

        @Override
        public boolean keepVanillaGoals() {
            return true;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, dev.tfp.core.animal.AnimalRecord record) {
            return List.of(new GoalSpec(HookedSpecies.PRIORITY, new HookGoal(mob, FishingModule.this)));
        }
    }
}
