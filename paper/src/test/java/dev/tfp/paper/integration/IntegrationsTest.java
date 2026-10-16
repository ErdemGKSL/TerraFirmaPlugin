/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.ready.ReadyGate;
import dev.tfp.paper.ready.ReadyGate.Signal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationsTest {
    private final ReadyGate gate = new ReadyGate();
    private final List<LogRecord> logged = new ArrayList<>();
    private IntegrationContext ctx;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        ctx = new IntegrationContext(null, logger, gate, () -> null, new HudSnapshotCache(() -> false), Path.of("plugins"));
    }

    /** A fake adapter that provides the fake services and records its lifecycle. */
    static final class FakeCe implements Integration {
        final List<String> calls = new ArrayList<>();
        final FakeBlockVisuals visuals = new FakeBlockVisuals();
        final FakeCustomBlocks blocks = new FakeCustomBlocks();
        boolean failOnEnable;

        @Override
        public String pluginName() {
            return "CraftEngine";
        }

        @Override
        public Optional<Signal> readySignal() {
            return Optional.of(Signal.CRAFTENGINE_RELOADED);
        }

        @Override
        public void onLoad() {
            calls.add("load");
        }

        @Override
        public void onEnable() {
            calls.add("enable");
            if (failOnEnable) {
                throw new IllegalStateException("boom");
            }
        }

        @Override
        public void onDisable() {
            calls.add("disable");
        }

        @Override
        public <T> Optional<T> service(Class<T> type) {
            if (type == BlockVisuals.class) {
                return Optional.of(type.cast(visuals));
            }
            if (type == CustomBlocks.class) {
                return Optional.of(type.cast(blocks));
            }
            return Optional.empty();
        }
    }

    static IntegrationProvider provider(String plugin, java.util.function.Supplier<Integration> create) {
        return new IntegrationProvider() {
            @Override
            public String pluginName() {
                return plugin;
            }

            @Override
            public Integration create(IntegrationContext context) {
                return create.get();
            }
        };
    }

    @Test
    void installedAdaptersProvideServicesOthersAreAbsent() {
        FakeCe ce = new FakeCe();
        Integrations in = Integrations.create(ctx, Set.of("CraftEngine")::contains, List.of(
                provider("CraftEngine", () -> ce),
                provider("BetterModel", () -> {
                    throw new AssertionError("must not be created: BetterModel is not installed");
                })));
        in.onLoad();
        in.onEnable();
        in.onDisable();
        assertThat(ce.calls).containsExactly("load", "enable", "disable");
        assertThat(in.activePlugins()).containsExactly("CraftEngine");
        assertThat(in.blockVisuals()).isSameAs(ce.visuals);
        assertThat(in.customBlocks()).isSameAs(ce.blocks);
        // Not provided by any adapter: degraded implementations, never null.
        assertThat(in.customItems().create("tfp:x", 1)).isEmpty();
        assertThat(in.entityModels().isReady()).isFalse();
        assertThat(in.hud()).isSameAs(ctx.hudCache());
    }

    @Test
    void failingAdapterIsDroppedAndItsSignalWaived() {
        FakeCe ce = new FakeCe();
        ce.failOnEnable = true;
        Integrations in = Integrations.create(ctx, n -> true, List.of(provider("CraftEngine", () -> ce)));
        in.onLoad();
        in.onEnable();
        assertThat(in.activePlugins()).isEmpty();
        assertThat(in.failures()).containsKey("CraftEngine");
        assertThat(in.blockVisuals().isLoaded()).isFalse();
        assertThat(gate.waived()).containsKey(Signal.CRAFTENGINE_RELOADED);
        assertThat(logged).anyMatch(r -> r.getMessage().contains("CraftEngine adapter disabled"));
    }

    @Test
    void creationFailureIsLoggedNotThrown() {
        Integrations in = Integrations.create(ctx, n -> true, List.of(provider("BetterHud", () -> {
            throw new NoClassDefFoundError("kr/toxicity/hud/api/BetterHudAPI");
        })));
        assertThat(in.activePlugins()).isEmpty();
        assertThat(in.failures()).containsKey("BetterHud");
    }

    @Test
    void fakeEntityModelsReapplyHookRunsOnRecreate() {
        FakeEntityModels models = new FakeEntityModels();
        models.registerModel(new ModelSource("tfp_example_bovine", () -> java.io.InputStream.nullInputStream()));
        Entity cow = entity(UUID.randomUUID());
        List<UUID> reapplied = new ArrayList<>();
        models.onModelCreated(e -> reapplied.add(e.getUniqueId()));
        assertThat(models.attach(cow, "tfp_example_bovine", 1f)).isFalse(); // not ready yet
        models.setReady(true);
        assertThat(models.attach(cow, "tfp_example_bovine", 0.5f)).isTrue();
        assertThat(models.animate(cow, "eat", AnimationSpec.once(10))).isTrue();
        models.recreate(cow);
        assertThat(models.played(cow.getUniqueId())).isEmpty();
        assertThat(reapplied).containsExactly(cow.getUniqueId(), cow.getUniqueId());
        assertThat(models.attachedTo(cow.getUniqueId())).contains(new FakeEntityModels.Attached("tfp_example_bovine", 0.5f));
    }

    static Entity entity(UUID id) {
        return (Entity) java.lang.reflect.Proxy.newProxyInstance(IntegrationsTest.class.getClassLoader(),
                new Class<?>[] {Entity.class}, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> p == a[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
