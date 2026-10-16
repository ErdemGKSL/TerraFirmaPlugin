/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.config.Yamls;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.ClassPathLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

/**
 * M1-BOOT-01 without a server: the processed paper-plugin.yml, the classes it names, the datapack in
 * the jar, the loader's library list, and the /tfp Brigadier tree.
 */
class BootstrapTest {
    static Map<?, ?> descriptor() throws IOException {
        try (InputStream in = BootstrapTest.class.getClassLoader().getResourceAsStream("paper-plugin.yml")) {
            return Yamls.load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void descriptorDeclaresLifecycleAndDependencies() throws Exception {
        Map<?, ?> d = descriptor();
        assertThat(d.get("load")).isEqualTo("STARTUP");
        assertThat(d.get("folia-supported")).isEqualTo(true);
        assertThat(String.valueOf(d.get("version"))).doesNotContain("${");
        assertThat(Class.forName((String) d.get("main"))).isEqualTo(TfpPlugin.class);
        assertThat(JavaPlugin.class).isAssignableFrom(TfpPlugin.class);
        assertThat(PluginBootstrap.class).isAssignableFrom(Class.forName((String) d.get("bootstrapper")));
        assertThat(PluginLoader.class).isAssignableFrom(Class.forName((String) d.get("loader")));
        Map<?, ?> server = (Map<?, ?>) ((Map<?, ?>) d.get("dependencies")).get("server");
        // Same names as the compat table (exact plugin names from the jars' descriptors).
        assertThat(new java.util.HashSet<Object>(server.keySet())).containsExactlyInAnyOrderElementsOf(CompatCheck.loadSpec().plugins().keySet());
        for (Object dep : server.values()) {
            Map<?, ?> m = (Map<?, ?>) dep;
            assertThat(m.get("load")).isEqualTo("BEFORE");
            assertThat(m.get("join-classpath")).isEqualTo(true);
            assertThat(m.get("required")).isEqualTo(false);
        }
        assertThat(new java.util.HashSet<Object>(((Map<?, ?>) d.get("permissions")).keySet()))
                .contains((Object) TfpCommands.PERMISSION, TfpCommands.ADMIN_PERMISSION);
    }

    @Test
    void datapackIsInTheJarWithAValidPackMcmeta() throws Exception {
        URI uri = TfpBootstrap.datapackUri();
        Path meta = Path.of(uri).resolve("pack.mcmeta");
        JsonObject pack = JsonParser.parseString(Files.readString(meta)).getAsJsonObject().getAsJsonObject("pack");
        // pack_format + supported_formats for 1.21.4-1.21.8, min_format/max_format for 1.21.9+ and 26.x.
        assertThat(pack.get("pack_format").getAsInt()).isLessThanOrEqualTo(61);
        assertThat(pack.getAsJsonObject("supported_formats").get("max_inclusive").getAsInt()).isGreaterThanOrEqualTo(100);
        assertThat(pack.get("min_format").getAsInt()).isEqualTo(pack.get("pack_format").getAsInt());
        assertThat(pack.get("max_format").getAsInt()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void loaderResolvesNothingBecauseLibrariesAreBundled() {
        List<ClassPathLibrary> added = new ArrayList<>();
        PluginClasspathBuilder builder = Fakes.proxy(PluginClasspathBuilder.class, (p, m, a) -> {
            if (m.getName().equals("addLibrary")) {
                added.add((ClassPathLibrary) a[0]);
                return p;
            }
            throw new UnsupportedOperationException(m.getName());
        });
        new TfpLoader().classloader(builder);
        assertThat(added).isEmpty();
        assertThat(TfpLoader.LIBRARIES).isEmpty();
        // Caffeine is on our classpath (bundled + relocated by :dist), not resolved at runtime.
        assertThat(com.github.benmanes.caffeine.cache.Caffeine.class).isNotNull();
        // The resolver path (M0-JAR-01) still builds when a library is listed.
        assertThat(TfpLoader.resolver(List.of("com.github.ben-manes.caffeine:caffeine:3.2.2"))).isPresent();
    }

    static final class FakeHandler implements TfpCommands.Handler {
        int reloads;

        @Override
        public String version() {
            return "TerraFirmaPlugin 1.2.3\n  status: ready";
        }

        @Override
        public CompletableFuture<String> reloadSettings() {
            reloads++;
            return CompletableFuture.completedFuture("config.yml reloaded");
        }
    }

    static CommandDispatcher<CommandSourceStack> dispatcher(TfpCommands.Handler handler) {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.getRoot().addChild(TfpCommands.build("1.2.3", () -> handler));
        return d;
    }

    @Test
    void tfpVersionAnswers() throws CommandSyntaxException {
        List<String> out = new ArrayList<>();
        CommandDispatcher<CommandSourceStack> d = dispatcher(new FakeHandler());
        assertThat(d.execute("tfp version", Fakes.source(Set.of(TfpCommands.PERMISSION), out))).isEqualTo(1);
        assertThat(d.execute("tfp", Fakes.source(Set.of(TfpCommands.PERMISSION), out))).isEqualTo(1);
        assertThat(out).hasSize(2).allMatch(s -> s.startsWith("TerraFirmaPlugin 1.2.3"));
    }

    @Test
    void tfpVersionWorksWhileThePluginIsNotEnabled() throws CommandSyntaxException {
        List<String> out = new ArrayList<>();
        CommandDispatcher<CommandSourceStack> d = dispatcher(null);
        d.execute("tfp version", Fakes.source(Set.of(TfpCommands.PERMISSION), out));
        assertThat(out).singleElement().asString().contains("1.2.3", "not enabled");
    }

    @Test
    void reloadConfigNeedsAdmin() throws CommandSyntaxException {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        assertThatThrownBy(() -> d.execute("tfp reload config", Fakes.source(Set.of(TfpCommands.PERMISSION), out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(h.reloads).isZero();
        d.execute("tfp reload config", Fakes.source(Set.of(TfpCommands.PERMISSION, TfpCommands.ADMIN_PERMISSION), out));
        assertThat(h.reloads).isEqualTo(1);
        assertThat(out).containsExactly("config.yml reloaded");
        assertThatThrownBy(() -> d.execute("tfp version", Fakes.source(Set.of(), out)))
                .isInstanceOf(CommandSyntaxException.class);
    }
}
