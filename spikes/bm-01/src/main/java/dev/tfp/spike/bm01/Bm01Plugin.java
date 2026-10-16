package dev.tfp.spike.bm01;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.BetterModelBukkit;
import kr.toxicity.model.api.bukkit.entity.BaseBukkitEntity;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.event.CreateEntityTrackerEvent;
import kr.toxicity.model.api.event.ModelAssetsEvent;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import kr.toxicity.model.api.util.function.BonePredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-BM-01 spike: BetterModel carrier and lifecycle checks. Throwaway code, see docs/spikes/M0-BM-01.md.
 *
 * <p>Carriers are vanilla Cow (bovine model) and Horse (deer model). Their state lives in the entity PDC
 * under the {@code bm01_*} keys and is re-applied in {@link CreateEntityTrackerEvent}.
 */
public final class Bm01Plugin extends JavaPlugin implements Listener {
    static final String BOVINE = "tfp_example_bovine";
    static final String DEER = "tfp_deer";
    static final String[] MODEL_IDS = {BOVINE, DEER};
    static final String COW = "cow";
    static final String HORSE = "horse";

    final Map<UUID, Carrier> carriers = new ConcurrentHashMap<>();
    final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    final NamespacedKey kindKey;
    final NamespacedKey tintKey;
    final NamespacedKey hiddenKey;
    final NamespacedKey babyKey;
    /** When false, CreateEntityTrackerEvent does not re-apply PDC state (control run). */
    volatile boolean reapply = true;

    public Bm01Plugin() {
        this.kindKey = new NamespacedKey(this, "bm01_kind");
        this.tintKey = new NamespacedKey(this, "bm01_tint");
        this.hiddenKey = new NamespacedKey(this, "bm01_hidden");
        this.babyKey = new NamespacedKey(this, "bm01_baby");
    }

    /** A spawned carrier. {@code last} is the last known location, used to find it on its region. */
    static final class Carrier {
        final UUID id;
        final String kind;
        volatile Location last;

        Carrier(UUID id, String kind, Location last) {
            this.id = id;
            this.kind = kind;
            this.last = last;
        }
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        BetterModelBukkit bm = BetterModelBukkit.platform();
        bm.eventBus().subscribe(this, ModelAssetsEvent.class, e -> guard("ModelAssetsEvent", () -> onAssets(e)));
        bm.eventBus().subscribe(this, PluginEndReloadEvent.class, e -> guard("PluginEndReloadEvent", () -> onReload(e)));
        bm.eventBus().subscribe(this, CreateEntityTrackerEvent.class,
                e -> guard("CreateEntityTrackerEvent", () -> onTrackerCreated(e)));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("bm01", new Bm01Command(this)));
        Report.line("onEnable: BetterModel " + BetterModelBukkit.class.getName() + " subscriptions registered; folia="
                + isFolia() + " server=" + Bukkit.getVersion());
    }

    static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    void count(String key) {
        counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    void guard(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            // Throwing from a BetterModel subscriber silently unsubscribes all our listeners, so never let it escape.
            getLogger().severe("[bm01] " + what + " failed: " + t);
            Report.line("ERROR in " + what + ": " + t);
        }
    }

    // ---- BetterModel events --------------------------------------------------------------------

    void onAssets(ModelAssetsEvent e) {
        String thread = Thread.currentThread().getName();
        count("ModelAssetsEvent:" + e.type() + ":" + thread);
        if (e.type() != ModelRenderer.Type.GENERAL) {
            return;
        }
        for (String id : MODEL_IDS) {
            e.addAsset(ModelAsset.of(id, readResource("models/" + id + ".bbmodel")));
        }
        Report.line("ModelAssetsEvent GENERAL: added " + List.of(MODEL_IDS));
    }

    void onReload(PluginEndReloadEvent e) {
        String thread = Thread.currentThread().getName();
        count("PluginEndReloadEvent:" + thread);
        Report.line("PluginEndReloadEvent result=" + e.result().getClass().getSimpleName()
                + " modelKeys=" + BetterModel.modelKeys());
        for (String id : MODEL_IDS) {
            Report.line("  model " + id + " present=" + BetterModel.model(id).isPresent());
        }
        // Carriers that loaded before the models were registered have no tracker (EntitiesLoadEvent runs first
        // on restart). Re-attach them once the models exist. attach() is idempotent (getOrCreate).
        for (Carrier c : new ArrayList<>(carriers.values())) {
            onCarrier(c, "reattach-on-reload", entity -> attach(entity, kindOf(entity)));
        }
    }

    void onTrackerCreated(CreateEntityTrackerEvent e) {
        String thread = Thread.currentThread().getName();
        count("CreateEntityTrackerEvent:" + thread);
        EntityTracker tracker = e.tracker();
        Entity entity = sourceOf(tracker);
        if (entity == null) {
            Report.line("CreateEntityTrackerEvent: tracker " + tracker.name() + " has no bukkit source");
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(kindKey)) {
            return; // not one of ours
        }
        String kind = pdc.get(kindKey, PersistentDataType.STRING);
        if (!reapply) {
            Report.line("CreateEntityTrackerEvent kind=" + kind + " entity=" + entity.getUniqueId()
                    + " reapply=off (control: nothing re-applied)");
            return;
        }
        applyState(tracker, entity, kind);
        Report.line("CreateEntityTrackerEvent kind=" + kind + " entity=" + entity.getUniqueId()
                + " re-applied from PDC on thread " + thread);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        String thread = Thread.currentThread().getName();
        count("EntitiesLoadEvent:" + thread);
        for (Entity entity : e.getEntities()) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            if (!pdc.has(kindKey)) {
                continue;
            }
            String kind = pdc.get(kindKey, PersistentDataType.STRING);
            carriers.put(entity.getUniqueId(), new Carrier(entity.getUniqueId(), kind, entity.getLocation()));
            boolean trackerOnLoad = BetterModel.registry(BukkitAdapter.adapt(entity))
                    .map(r -> !r.trackers().isEmpty()).orElse(false);
            Report.line("EntitiesLoadEvent carrier " + entity.getUniqueId() + " kind=" + kind
                    + " trackerExistsOnLoad=" + trackerOnLoad + " thread=" + thread);
        }
    }

    // ---- helpers used by the command ------------------------------------------------------------

    static Entity sourceOf(EntityTracker tracker) {
        if (tracker.sourceEntity() instanceof BaseBukkitEntity b) {
            return b.entity();
        }
        return null;
    }

    /** Attaches the model for this carrier kind. Must run on the carrier's owning thread. */
    void attach(Entity entity, String kind) {
        String modelId = COW.equals(kind) ? BOVINE : DEER;
        Optional<ModelRenderer> renderer = BetterModel.model(modelId);
        if (renderer.isEmpty()) {
            Report.line("attach: model " + modelId + " is not registered (yet)");
            return;
        }
        EntityTracker tracker = renderer.get().getOrCreate(BukkitAdapter.adapt(entity), TrackerModifier.DEFAULT,
                t -> t.scaler(scalerFor(entity)));
        Report.line("attach " + modelId + " to " + entity.getUniqueId() + " tracker=" + tracker.name()
                + " thread=" + Thread.currentThread().getName());
    }

    ModelScaler scalerFor(Entity entity) {
        String mode = entity.getPersistentDataContainer().get(babyKey, PersistentDataType.STRING);
        return "mult".equals(mode) ? ModelScaler.entity().multiply(0.5f) : ModelScaler.entity();
    }

    /** Re-applies tint, hidden parts and baby scaler from the entity PDC. Idempotent. */
    void applyState(EntityTracker tracker, Entity entity, String kind) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Integer tint = pdc.get(tintKey, PersistentDataType.INTEGER);
        if (tint != null) {
            tracker.update(TrackerUpdateAction.tint(tint), BonePredicate.name(bodyName(kind)).withChildren());
        }
        String hidden = pdc.get(hiddenKey, PersistentDataType.STRING);
        if (hidden != null && !hidden.isEmpty()) {
            for (String part : hidden.split(",")) {
                tracker.update(TrackerUpdateAction.togglePart(false), BonePredicate.name(part).withChildren());
            }
        }
        String baby = pdc.get(babyKey, PersistentDataType.STRING);
        if (baby != null) {
            tracker.scaler("mult".equals(baby) ? ModelScaler.entity().multiply(0.5f) : ModelScaler.entity());
        }
    }

    static String bodyName(String kind) {
        return "body"; // both carrier models (bovine, deer) have a "body" root group
    }

    /** Finds the carrier near its last known location on that location's owning thread, then runs the action. */
    void onCarrier(Carrier c, String label, java.util.function.Consumer<Entity> action) {
        Location at = c.last;
        Bukkit.getRegionScheduler().execute(this, at, () -> guard(label, () -> {
            Entity found = null;
            for (Entity e : at.getWorld().getNearbyEntities(at, 32, 32, 32)) {
                if (e.getUniqueId().equals(c.id)) {
                    found = e;
                    break;
                }
            }
            if (found == null) {
                Report.line(label + ": carrier " + c.id + " not found within 32 blocks of " + at + " on this thread");
                return;
            }
            action.accept(found);
            c.last = found.getLocation();
        }));
    }

    void forEachCarrier(String label, String kindFilter, java.util.function.Consumer<Entity> action) {
        for (Carrier c : new ArrayList<>(carriers.values())) {
            if (kindFilter == null || kindFilter.equals(c.kind)) {
                onCarrier(c, label, action);
            }
        }
    }

    /** Spawns a carrier on the region that owns the location. */
    void spawnCarrier(String kind, Location loc) {
        World world = loc.getWorld();
        Bukkit.getRegionScheduler().execute(this, loc, () -> guard("spawn", () -> {
            java.util.function.Consumer<Entity> init = ent -> {
                PersistentDataContainer pdc = ent.getPersistentDataContainer();
                pdc.set(kindKey, PersistentDataType.STRING, kind);
                ent.setInvisible(true);
                ent.setSilent(true);
                ent.setCustomNameVisible(false);
                ent.customName(null);
                ent.setPersistent(true);
                ent.setInvulnerable(true);
                if (ent instanceof Mob mob) {
                    mob.setRemoveWhenFarAway(false);
                }
                if (ent instanceof Horse horse) {
                    horse.setTamed(true);
                    horse.getInventory().setSaddle(new org.bukkit.inventory.ItemStack(org.bukkit.Material.SADDLE));
                }
                if (ent instanceof Ageable ageable) {
                    ageable.setAdult();
                }
            };
            LivingEntity entity = COW.equals(kind)
                    ? world.spawn(loc, Cow.class, init)
                    : world.spawn(loc, Horse.class, init);
            Carrier c = new Carrier(entity.getUniqueId(), kind, entity.getLocation());
            carriers.put(c.id, c);
            Report.line("spawn " + kind + " " + c.id + " at " + loc + " thread="
                    + Thread.currentThread().getName() + " folia=" + isFolia());
            attach(entity, kind);
        }));
    }

    static byte[] readResource(String path) {
        try (InputStream in = Bm01Plugin.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasTracker(Entity entity) {
        return BetterModel.registry(BukkitAdapter.adapt(entity)).map(EntityTrackerRegistry::trackers)
                .map(c -> !c.isEmpty()).orElse(false);
    }

    /** Helper for commands that need the bukkit wrapper type of a tracker's source. */
    static double scaleOf(EntityTracker tracker) {
        return tracker.scaler().scale(tracker);
    }

    static double attributeScale(Entity e) {
        if (e instanceof LivingEntity living && living.getAttribute(Attribute.SCALE) != null) {
            return living.getAttribute(Attribute.SCALE).getValue();
        }
        return Double.NaN;
    }

    static EntityTracker trackerOf(Entity e) {
        return BetterModel.registry(BukkitAdapter.adapt(e))
                .flatMap(r -> r.trackers().stream().findFirst()).orElse(null);
    }

    static double bmScale(Entity e) {
        EntityTracker t = trackerOf(e);
        return t == null ? Double.NaN : scaleOf(t);
    }

    String kindOf(Entity e) {
        return e.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
    }

    Map<String, AtomicInteger> counts() {
        return counts;
    }

}
