/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.bettermodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.paper.integration.IntegrationProvider;
import dev.tfp.paper.integration.ModelSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** The parts of the BetterModel adapter that run without a server (no BetterModel class is loaded). */
class BetterModelAdapterTest {
    @Test
    void guardedSubscriberSwallowsAndLogs() {
        List<LogRecord> logged = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                logged.add(r);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        List<String> seen = new ArrayList<>();
        Consumer<String> h = GuardedSubscriber.guard(logger, "Test", e -> {
            seen.add(e);
            if (e.equals("bad")) {
                throw new IllegalStateException("boom");
            }
            if (e.equals("link")) {
                throw new NoSuchMethodError("kr.toxicity...");
            }
        });
        h.accept("ok");
        h.accept("bad");
        h.accept("link");
        h.accept("ok2");
        assertThat(seen).containsExactly("ok", "bad", "link", "ok2");
        assertThat(logged).hasSize(2).allMatch(r -> r.getMessage().contains("Test handler failed"));
    }

    @Test
    void jarModelsListsTheBovinePlaceholder() throws Exception {
        List<ModelSource> models = JarModels.load(getClass().getClassLoader(), JarModels.DEFAULT_FOLDER);
        assertThat(models).extracting(ModelSource::id).containsExactly("tfp_example_bovine");
        try (InputStream in = models.getFirst().open().open()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("\"elements\"").contains("\"outliner\"");
        }
        // Each open() is a fresh stream (BetterModel may read twice / in parallel).
        try (InputStream a = models.getFirst().open().open(); InputStream b = models.getFirst().open().open()) {
            assertThat(a).isNotSameAs(b);
        }
    }

    @Test
    void jarModelsMissingIndexIsEmptyAndMissingFileFails() {
        assertThat(JarModels.load(getClass().getClassLoader(), "nowhere/")).isEmpty();
        ClassLoader broken = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                return name.endsWith("index.txt")
                        ? new java.io.ByteArrayInputStream("# c\n\nghost_model\n".getBytes(StandardCharsets.UTF_8))
                        : super.getResourceAsStream(name);
            }
        };
        assertThatThrownBy(() -> JarModels.load(broken, "x/")).hasMessageContaining("ghost_model");
    }

    @Test
    void providerIsDiscoverableWithoutBetterModel() {
        List<IntegrationProvider> found = new ArrayList<>();
        ServiceLoader.load(IntegrationProvider.class, getClass().getClassLoader()).forEach(found::add);
        assertThat(found).extracting(IntegrationProvider::pluginName).contains(BetterModelProvider.PLUGIN);
    }
}
