/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.EntityModels;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * {@code bm.spawn_persist} (M1-INT-03): the example bovine from our jar ({@code bettermodel/models}, registered
 * through {@code ModelAssetsEvent}) attaches to a vanilla cow carrier, takes a tint, and the tint is re-applied
 * by an {@code onModelCreated} hook (BetterModel's {@code CreateEntityTrackerEvent}) when the model is created
 * again. The tint lives in the carrier's PDC, as features will keep it (the {@code bettermodel} skill: BetterModel
 * does not persist tints). Restart persistence needs a second server start and is out of scope for one run.
 */
public final class BetterModelSpawnCase implements SelfTestCase {
    /** {@code integration/bettermodel/src/main/resources/bettermodel/models/index.txt}. */
    static final String MODEL = "tfp_example_bovine";
    static final int TINT = 0xA0522D;
    private static final Duration STEP = Duration.ofSeconds(15);
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    /** Carrier → hook calls that re-applied the tint successfully. */
    private static final Map<UUID, AtomicInteger> REAPPLIED = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "bm.spawn_persist: example bovine spawns on a carrier, tint re-applied on model re-creation (M1-INT-03)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(90);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("BetterModel")) {
            throw new Skip("BetterModel is not installed or its adapter is disabled");
        }
        EntityModels models = tfp.integrations().entityModels();
        if (!models.isReady()) {
            throw new AssertionError("BetterModel models are not ready (no successful PluginEndReloadEvent)");
        }
        NamespacedKey tintKey = new NamespacedKey(tfp, "selftest_tint");
        if (HOOKED.compareAndSet(false, true)) {
            // The feature pattern: re-apply what BetterModel forgets, from the entity's own data. Owner thread.
            models.onModelCreated(e -> {
                Integer rgb = e.getPersistentDataContainer().get(tintKey, PersistentDataType.INTEGER);
                if (rgb != null && models.tint(e, rgb)) {
                    REAPPLIED.computeIfAbsent(e.getUniqueId(), k -> new AtomicInteger()).incrementAndGet();
                }
            });
        }
        Scheduler s = tfp.scheduler();
        return OnThread.script("bm-spawn", () -> {
            World world = Bukkit.getWorlds().getFirst();
            Location at = world.getSpawnLocation().add(0.5, 0, 0.5);
            int cx = at.getBlockX() >> 4;
            int cz = at.getBlockZ() >> 4;
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, true);
                return null;
            });
            Entity[] carrier = {null};
            try {
                Entity cow = OnThread.region(s, world, cx, cz, STEP, () -> world.spawn(at, Cow.class, c -> {
                    c.getPersistentDataContainer().set(tintKey, PersistentDataType.INTEGER, TINT);
                    c.setAI(false);
                    c.setSilent(true);
                    c.setInvulnerable(true);
                    c.setPersistent(false);
                }));
                carrier[0] = cow;
                UUID id = cow.getUniqueId();
                boolean attached = OnThread.entity(s, cow, STEP, () -> models.attach(cow, MODEL, 1f));
                if (!attached) {
                    throw new AssertionError("attach(" + MODEL + ") returned false: the model is not registered "
                            + "(see 'BetterModel: registered n/m model(s)' in the log)");
                }
                if (!OnThread.poll(STEP, () -> count(id) >= 1)) {
                    throw new AssertionError("the onModelCreated hook did not re-apply the tint after attach "
                            + "(CreateEntityTrackerEvent not received?)");
                }
                int models1 = OnThread.entity(s, cow, STEP, () -> models.modelCount(cow));
                boolean tinted = OnThread.entity(s, cow, STEP, () -> models.tint(cow, TINT));
                if (models1 != 1 || !tinted) {
                    throw new AssertionError("after attach: " + models1 + " model(s), tint applied=" + tinted);
                }
                // Re-creation (what chunk reload / restart / BetterModel reload do): detach, attach again.
                int gone = OnThread.entity(s, cow, STEP, () -> {
                    models.detach(cow);
                    return models.modelCount(cow);
                });
                if (gone != 0) {
                    throw new AssertionError(gone + " model(s) left after detach");
                }
                OnThread.entity(s, cow, STEP, () -> models.attach(cow, MODEL, 1f));
                if (!OnThread.poll(STEP, () -> count(id) >= 2)) {
                    throw new AssertionError("the tint was not re-applied when the model was created again ("
                            + count(id) + " re-apply call(s))");
                }
                return MODEL + " on cow " + id + ": attached, tint 0x" + Integer.toHexString(TINT) + " re-applied "
                        + count(id) + "x (attach + re-creation)";
            } finally {
                if (carrier[0] != null) {
                    Entity cow = carrier[0];
                    REAPPLIED.remove(cow.getUniqueId());
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        models.detach(cow);
                        cow.remove();
                        return null;
                    });
                }
                OnThread.global(s, STEP, () -> {
                    world.setChunkForceLoaded(cx, cz, false);
                    return null;
                });
            }
        });
    }

    private static int count(UUID id) {
        AtomicInteger n = REAPPLIED.get(id);
        return n == null ? 0 : n.get();
    }
}
