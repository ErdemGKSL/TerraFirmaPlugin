/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/pet/TamableMammal.java @ c3b4d9536 (mobInteract, tame,
 * receiveCommand, die, canAttack, createAttributes), livestock/pet/TFCCat.java (receiveCommand), network/PetCommandPacket.java
 */
package dev.tfp.paper.pet;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;
import dev.tfp.core.pet.PetAi;
import dev.tfp.core.pet.PetCommand;
import dev.tfp.core.pet.PetRules;
import dev.tfp.core.pet.PetSpecies;
import dev.tfp.core.pet.PetState;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.animal.SpeciesBehaviour;
import dev.tfp.paper.livestock.LivestockFood;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.core.tag.TagType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Pets (PAR-FAUNA-14): the dog and the cat. Feeding the species' food to an untamed pet with enough familiarity tames it
 * (TFC {@code familiarity + 0.06 > 0.15}); the owner sneak-right-clicks with an empty hand to open a command window (relax,
 * home, sit, follow, hunt), and the pet runs TFC's pet brain as a goal ({@link PetGoal}): idle around its home, rest and
 * sleep on soft blocks, follow the owner, fight what its species hunts, flee when hurt below 5 health, sit for up to 2000
 * ticks. A dye changes the collar colour.
 *
 * <p>Threads: every goal and check runs on the pet's owning thread; the decisions are the pure {@code dev.tfp.core.pet} rules
 * on a snapshot, and only their result is applied. A command clicked on the player's thread is handed to the pet's thread.
 */
public final class PetModule implements FeatureModule, Listener {
    static final Set<String> SPECIES = Set.of("tfc:dog", "tfc:cat");
    /** The pet attributes use the carrier's own entry when it has none (a cat has no attack damage in vanilla). */
    private final Map<UUID, PetBrain> brains = new ConcurrentHashMap<>();
    private final FightLog fights = new FightLog();
    private ModuleContext ctx;
    private PetBody body;
    private LongSupplier clock;
    private RandomGenerator random = ThreadLocalRandom.current();
    private PetGoal.PetSenses senses;
    private Function<String, Predicate<ItemStack>> food;
    private Function<Entity, Optional<AnimalRecord>> records;
    private Function<String, Optional<Carrier>> carriers;
    private BiConsumer<Entity, Runnable> onEntity;
    private BiFunction<Player, String, String> messages;

    @Override
    public String id() {
        return "pet";
    }

    @Override
    public void enable(ModuleContext context) {
        this.ctx = context;
        Tags tags = context.services().require(Tags.class);
        dev.tfp.paper.food.ItemFoodHooks foodHooks = context.services().require(dev.tfp.paper.food.ItemFoodHooks.class);
        AnimalService animals = ((TfpPlugin) context.plugin()).animals();
        this.body = new PaperPetBody(() -> context.integrations().entityModels());
        this.clock = () -> context.clock().get().playerTicks();
        this.records = animals::record;
        this.carriers = id -> animals.table().find(id);
        this.senses = new TagSenses(tags);
        this.food = species -> {
            String tag = animals.table().find(species).map(LivestockFood::tag).orElse("tfp:none");
            boolean eatsRotten = animals.table().find(species).flatMap(Carrier::livestock)
                    .map(LivestockSpecies::eatsRottenFood).orElse(false);
            // TFCAnimalProperties.isFood: (eatsRottenFood() || !isRotten(stack)) && isItem(stack, getFoodTag())
            return stack -> (eatsRotten || !foodHooks.isRotten(stack)) && tags.is(stack, tag);
        };
        this.onEntity = (e, task) -> context.scheduler().entity(e, task, () -> {
        });
        this.messages = (p, key) -> context.messages().get(p.locale().toString().toLowerCase(Locale.ROOT), key);
        animals.behaviours().register(new Ai());
        context.listen(this);
    }

    /** Test seam: the world, the clock, the draws, the senses, the food, the records and the thread hand-off. */
    void initForTest(PetBody body, LongSupplier clock, RandomGenerator random, PetGoal.PetSenses senses,
            Function<String, Predicate<ItemStack>> food, Function<Entity, Optional<AnimalRecord>> records,
            BiFunction<Player, String, String> messages) {
        this.body = body;
        this.clock = clock;
        this.random = random;
        this.senses = senses;
        this.food = food;
        this.records = records;
        this.carriers = id -> dev.tfp.core.animal.CarrierTable.get().find(id);
        this.onEntity = (e, task) -> task.run();
        this.messages = messages;
    }

    PetBrain brain(Entity e) {
        return brains.get(e.getUniqueId());
    }

    SpeciesBehaviour behaviour() {
        return new Ai();
    }

    FightLog fights() {
        return fights;
    }

    /** The dog and the cat: TFC's pet brain is their only AI. */
    private final class Ai implements SpeciesBehaviour {
        @Override
        public Set<String> species() {
            return SPECIES;
        }

        @Override
        public List<GoalSpec> goals(Mob mob, AnimalRecord record) {
            PetBrain brain = new PetBrain(mob, speciesOf(record.species()), body);
            brain.load();
            brains.put(mob.getUniqueId(), brain);
            applyCollar(mob);
            return List.of(new GoalSpec(0, new PetGoal(brain, new PetGoal.Deps(body, clock, random, senses, fights))));
        }

        @Override
        public void onAdd(Entity entity, AnimalRecord record) {
            if (entity instanceof Mob mob) {
                mob.getPathfinder().setCanFloat(true);
                applyAttributes(mob);
            }
        }
    }

    static PetSpecies speciesOf(String id) {
        return id.equals("tfc:cat") ? PetSpecies.CAT : PetSpecies.DOG;
    }

    /** TFC {@code TamableMammal.createAttributes}: 20 health, 0.3 speed, 2 attack damage. */
    static void applyAttributes(LivingEntity e) {
        set(e, Attribute.MOVEMENT_SPEED, PetAi.MOVEMENT_SPEED);
        set(e, Attribute.ATTACK_DAMAGE, PetAi.ATTACK_DAMAGE);
        if (set(e, Attribute.MAX_HEALTH, PetAi.MAX_HEALTH)) {
            e.setHealth(PetAi.MAX_HEALTH);
        }
    }

    private static boolean set(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance i = e.getAttribute(attribute);
        if (i == null) {
            e.registerAttribute(attribute);
            i = e.getAttribute(attribute);
        }
        if (i == null || i.getBaseValue() == value) {
            return false;
        }
        i.setBaseValue(value);
        return true;
    }

    /** The carrier's own collar mirrors the saved colour (the carrier is a wolf or a cat). */
    static void applyCollar(Mob mob) {
        DyeColor color = DyeColor.values()[Math.floorMod(PetPdc.collar(mob.getPersistentDataContainer()), DyeColor.values().length)];
        if (mob instanceof Wolf w) {
            w.setCollarColor(color);
        } else if (mob instanceof Cat c) {
            c.setCollarColor(color);
        }
    }

    // ---- interaction

    /**
     * TFC {@code TamableMammal.mobInteract}: a dye changes the collar; the owner's sneak-right-click with an empty hand opens
     * the command window; an untamed pet fed its food with enough familiarity is tamed. Runs before the livestock feed
     * (a later priority), which then takes the food as TFC's {@code super.mobInteract} does.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        PetBrain brain = brains.get(event.getRightClicked().getUniqueId());
        if (brain == null || !Bukkit.isOwnedByCurrentRegion(event.getRightClicked())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        DyeColor dye = dye(held);
        if (dye != null && dye.ordinal() != PetPdc.collar(brain.mob.getPersistentDataContainer())) {
            PetPdc.setCollar(brain.mob.getPersistentDataContainer(), dye.ordinal());
            applyCollar(brain.mob);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.subtract(1);
            }
            event.setCancelled(true);
            return;
        }
        if (PetRules.canOpenMenu(held.isEmpty(), player.isSneaking(), brain.isOwnedBy(player), brain.mob.getFireTicks() > 0)) {
            event.setCancelled(true);
            openMenu(player, brain);
            return;
        }
        tryTame(player, brain, held);
    }

    private void tryTame(Player player, PetBrain brain, ItemStack held) {
        long now = clock.getAsLong();
        Optional<AnimalRecord> record = records.apply(brain.mob);
        if (record.isEmpty() || held.isEmpty() || brain.tamed() || !food.apply(record.get().species()).test(held)) {
            return;
        }
        // familiarity + 0.06f > 0.15f && no owner && isFood(held) && isHungry()
        float familiarity = LivestockRules.familiarityAt(record.get().state(), now);
        if (PetRules.isTameable(familiarity, brain.tamed(), true, LivestockRules.isHungry(record.get().state(), now))) {
            tame(player, brain);
        }
    }

    /** TFC {@code TamableMammal.tame}: the player owns the pet; hearts. */
    void tame(Player player, PetBrain brain) {
        brain.ownerId = player.getUniqueId();
        brain.save();
        body.hearts(brain.mob);
    }

    static DyeColor dye(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String name = stack.getType().name();
        if (!name.endsWith("_DYE")) {
            return null;
        }
        try {
            return DyeColor.valueOf(name.substring(0, name.length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void openMenu(Player player, PetBrain brain) {
        PetMenu menu = PetMenu.create(brain.mob.getUniqueId(), (float) brain.mob.getHealth(), message(player, "pet.menu.title"),
                (key, command) -> message(player, key));
        player.openInventory(menu.getInventory());
    }

    private String message(Player player, String key) {
        return messages.apply(player, key);
    }

    /** A click in the command window is the command: the click thread hands it to the pet's thread. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PetCommand command = menu.command(event.getSlot());
        if (command == null) {
            return;
        }
        Entity pet = Bukkit.getEntity(menu.pet());
        player.closeInventory();
        if (pet instanceof Mob mob) {
            command(player, mob, command);
        }
    }

    /** {@code PetCommandPacket.handle}: the command goes to the pet's own thread. */
    void command(Player player, Mob pet, PetCommand command) {
        UUID playerId = player.getUniqueId();
        onEntity.accept(pet, () -> {
            PetBrain brain = brains.get(pet.getUniqueId());
            Player sender = Bukkit.getPlayer(playerId);
            if (brain != null && sender != null) {
                receive(sender, brain, command);
            }
        });
    }

    /** TFC {@code receiveCommand} behind the server gate ({@code willListenTo}) and the owner check. */
    void receive(Player player, PetBrain brain, PetCommand command) {
        long now = clock.getAsLong();
        org.bukkit.Location l = player.getLocation();
        PetState.Home here = new PetState.Home(body.dimension(brain.mob), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        PetRules.Outcome out = PetRules.handle(brain.state, brain.species, (float) brain.mob.getHealth(), random.nextFloat(),
                new PetRules.Sender(brain.isOwnedBy(player), here), command, now);
        switch (out.feedback()) {
            case NOT_OWNER -> player.sendActionBar(Component.text(message(player, "pet.not_owner")));
            case WILL_NOT_LISTEN -> player.sendActionBar(Component.text(message(player, "pet.will_not_listen")));
            case NONE -> {
                if (brain.species == PetSpecies.CAT) {
                    // TFCCat.receiveCommand: the owner's command is answered with a purr
                    body.sound(brain.mob, "minecraft:entity.cat.purreow", 1f, 1f);
                }
                brain.apply(out, now);
            }
        }
    }

    // ---- events that feed the brains

    private static LivingEntity causing(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity d = byEntity.getDamager();
        if (d instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return d instanceof LivingEntity living ? living : null;
    }

    /**
     * The hurt-by memory of a pet, and the owners' fights ({@code getLastHurtByMob}, {@code getLastHurtMob}) the hunt
     * activity reads.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        long now = clock.getAsLong();
        LivingEntity by = causing(event);
        if (by == null) {
            return;
        }
        PetBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain != null) {
            brain.attacker = by;
            brain.hurtAt = now;
        }
        if (event.getEntity() instanceof Player victim) {
            fights.hurtBy(victim.getUniqueId(), by, now);
        }
        if (by instanceof Player attacker && event.getEntity() instanceof LivingEntity victim) {
            fights.hurt(attacker.getUniqueId(), victim, now);
        }
    }

    /** TFC {@code TamableMammal.die}: the owner is told, unless death messages are off. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        PetBrain brain = brains.get(event.getEntity().getUniqueId());
        if (brain == null || !brain.tamed()) {
            return;
        }
        Player owner = brain.owner();
        if (owner != null && deathMessages(event.getEntity().getWorld())) {
            owner.sendMessage(Component.text(message(owner, "pet.died")));
        }
    }

    /** The {@code showDeathMessages} rule (named {@code show_death_messages} on 26.x), true when unknown. */
    @SuppressWarnings("deprecation")
    private static boolean deathMessages(org.bukkit.World world) {
        String v = world.getGameRuleValue("showDeathMessages");
        if (v == null) {
            v = world.getGameRuleValue("show_death_messages");
        }
        return !"false".equals(v);
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        brains.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fights.forget(event.getPlayer().getUniqueId());
    }

    // ---- shared

    /** The tag memberships by species id (our animals ride vanilla carriers, so the entity type is not the species). */
    private final class TagSenses implements PetGoal.PetSenses {
        private final Tags tags;

        TagSenses(Tags tags) {
            this.tags = tags;
        }

        @Override
        public boolean sitsOn(org.bukkit.block.Block block) {
            return tags.is(block, "tfp:pet_sits_on");
        }

        @Override
        public boolean huntedBy(PetSpecies species, LivingEntity e) {
            String id = species(e);
            return id != null && tags.is(TagType.ENTITY_TYPE, "tfp:" + id.substring(id.indexOf(':') + 1),
                    species == PetSpecies.DOG ? "tfp:hunted_by_dogs" : "tfp:hunted_by_cats");
        }

        @Override
        public boolean pest(LivingEntity e) {
            String id = species(e);
            return id != null && Set.of("tfc:rat", "tfc:jerboa", "tfc:lemming", "tfc:mongoose").contains(id);
        }

        @Override
        public boolean monster(LivingEntity e) {
            return e instanceof Monster && species(e) == null;
        }

        private String species(LivingEntity e) {
            return records.apply(e).map(AnimalRecord::species).orElse(null);
        }
    }

    /** The last fights of the owners; entries are read by pets on any thread and used only when their region owns them. */
    static final class FightLog implements PetGoal.Fights {
        private record Entry(LivingEntity entity, long tick) {
        }

        private final Map<UUID, Entry> attackers = new ConcurrentHashMap<>();
        private final Map<UUID, Entry> victims = new ConcurrentHashMap<>();

        void hurtBy(UUID owner, LivingEntity attacker, long now) {
            attackers.put(owner, new Entry(attacker, now));
        }

        void hurt(UUID owner, LivingEntity victim, long now) {
            victims.put(owner, new Entry(victim, now));
        }

        void forget(UUID owner) {
            attackers.remove(owner);
            victims.remove(owner);
        }

        @Override
        public LivingEntity attackerOf(UUID owner) {
            Entry e = attackers.get(owner);
            return e == null ? null : e.entity();
        }

        @Override
        public long attackerStamp(UUID owner) {
            Entry e = attackers.get(owner);
            return e == null ? Long.MIN_VALUE / 2 : e.tick();
        }

        @Override
        public LivingEntity victimOf(UUID owner) {
            Entry e = victims.get(owner);
            return e == null ? null : e.entity();
        }

        @Override
        public long victimStamp(UUID owner) {
            Entry e = victims.get(owner);
            return e == null ? Long.MIN_VALUE / 2 : e.tick();
        }
    }
}
