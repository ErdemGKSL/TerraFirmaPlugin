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
import com.mojang.brigadier.tree.CommandNode;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.i18n.Messages;
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
import org.bukkit.entity.Player;
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

    static final Messages MESSAGES = Messages.fromJar();

    static class FakeHandler implements TfpCommands.Handler {
        final List<String> calls = new ArrayList<>();

        @Override
        public String version() {
            return "TerraFirmaPlugin 1.2.3\n  status: ready";
        }

        private CompletableFuture<Reply> call(String name, Reply reply) {
            calls.add(name);
            return CompletableFuture.completedFuture(reply);
        }

        @Override
        public CompletableFuture<Reply> reloadSettings() {
            return call("reloadSettings", Reply.ok("reload.config.done"));
        }

        @Override
        public CompletableFuture<Reply> reloadData() {
            return call("reloadData", Reply.ok("reload.data.done", 10, 2, 3));
        }

        @Override
        public CompletableFuture<Reply> reloadContent() {
            return call("reloadContent", Reply.error("reload.content.unavailable"));
        }

        @Override
        public CompletableFuture<Reply> debugBlock(Player player) {
            return call("debugBlock " + player.getName(), Reply.ok("debug.block.header", "world 1 2 3")
                    .withDetails(List.of("vanilla: minecraft:stone")));
        }

        @Override
        public CompletableFuture<Reply> debugChunk(Player player) {
            return call("debugChunk " + player.getName(), Reply.ok("debug.chunk.header", 0, 0, "world"));
        }

        @Override
        public CompletableFuture<Reply> debugChunkAt(int x, int z) {
            return call("debugChunkAt " + x + " " + z, Reply.ok("debug.chunk.header", 0, 0, "world"));
        }

        @Override
        public CompletableFuture<Reply> debugPlayer(String name) {
            return call("debugPlayer " + name, Reply.ok("debug.player.header", name));
        }

        @Override
        public CompletableFuture<Reply> debugPackets() {
            return call("debugPackets", new Reply(true, "debug.packets.header", List.of(), List.of("noop: calls=0")));
        }

        @Override
        public CompletableFuture<Reply> debugShowcase(Player player, String family) {
            return call("debugShowcase " + player.getName() + " " + family,
                    Reply.ok("debug.showcase.done", family, 3, 4, 5, 3, "world 2 64 2"));
        }

        @Override
        public CompletableFuture<Reply> skipTime(String key, String label, dev.tfp.core.calendar.TimeCommand.Delta delta) {
            // the default calendar (06:00, June 1, 1000) gives the delta; the reply is the fixed date text
            long ticks = delta.ticksFrom(dev.tfp.core.calendar.CalendarState.defaults());
            return call("skipTime " + key + " " + label + " " + ticks,
                    Reply.ok(key, label, String.valueOf(ticks), "June 1, 1000 12:00"));
        }
    }

    static CommandDispatcher<CommandSourceStack> dispatcher(TfpCommands.Handler handler) {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.getRoot().addChild(TfpCommands.build("1.2.3", () -> handler, MESSAGES));
        return d;
    }

    static final Set<String> ADMIN = Set.of(TfpCommands.PERMISSION, TfpCommands.ADMIN_PERMISSION);

    @Test
    void tfpVersionAnswers() throws CommandSyntaxException {
        List<String> out = new ArrayList<>();
        CommandDispatcher<CommandSourceStack> d = dispatcher(new FakeHandler());
        assertThat(d.execute("tfp version", Fakes.source(Set.of(TfpCommands.PERMISSION), out))).isEqualTo(1);
        assertThat(d.execute("tfp", Fakes.source(Set.of(TfpCommands.PERMISSION), out))).isEqualTo(1);
        assertThat(out).hasSize(2).allMatch(s -> s.equals("TerraFirmaPlugin 1.2.3\n  status: ready"));
    }

    @Test
    void tfpVersionWorksWhileThePluginIsNotEnabled() throws CommandSyntaxException {
        List<String> out = new ArrayList<>();
        CommandDispatcher<CommandSourceStack> d = dispatcher(null);
        d.execute("tfp version", Fakes.source(Set.of(TfpCommands.PERMISSION), out));
        assertThat(out).singleElement().asString().contains("1.2.3", "not enabled");
        // a Turkish client gets the Turkish text
        out.clear();
        d.execute("tfp version", Fakes.playerSource("Ayşe", java.util.Locale.forLanguageTag("tr-TR"),
                Set.of(TfpCommands.PERMISSION), out));
        assertThat(out).containsExactly("TerraFirmaPlugin 1.2.3 (etkin değil; başlangıç günlüğüne bakın)");
    }

    @Test
    void reloadConfigNeedsAdmin() throws CommandSyntaxException {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        assertThatThrownBy(() -> d.execute("tfp reload config", Fakes.source(Set.of(TfpCommands.PERMISSION), out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(h.calls).isEmpty();
        d.execute("tfp reload config", Fakes.source(ADMIN, out));
        assertThat(h.calls).containsExactly("reloadSettings");
        assertThat(out).containsExactly("config.yml reloaded.");
        assertThatThrownBy(() -> d.execute("tfp version", Fakes.source(Set.of(), out)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    /** M1-SVC-05: the Brigadier tree itself. */
    @Test
    void commandTreeStructure() {
        CommandDispatcher<CommandSourceStack> d = dispatcher(new FakeHandler());
        CommandNode<CommandSourceStack> tfp = d.getRoot().getChild("tfp");
        assertThat(children(tfp)).containsExactlyInAnyOrder("version", "reload", "debug", "time");
        assertThat(children(tfp.getChild("time"))).containsExactlyInAnyOrder("set", "add");
        assertThat(children(tfp.getChild("time").getChild("set"))).containsExactlyInAnyOrder("day", "noon", "night", "midnight", "hour");
        assertThat(children(tfp.getChild("time").getChild("add"))).containsExactlyInAnyOrder("years", "months", "days", "hours", "ticks");
        assertThat(children(tfp.getChild("reload"))).containsExactlyInAnyOrder("config", "data", "content");
        assertThat(children(tfp.getChild("debug"))).containsExactlyInAnyOrder("block", "chunk", "player", "packets", "showcase");
        assertThat(children(tfp.getChild("debug").getChild("showcase"))).containsExactly("family");
        assertThat(tfp.getChild("debug").getChild("showcase").getCommand()).as("showcase needs a family").isNull();
        assertThat(children(tfp.getChild("debug").getChild("player"))).containsExactly("name");
        assertThat(tfp.getCommand()).isNotNull();
        assertThat(tfp.getChild("reload").getCommand()).as("bare /tfp reload = reload data").isNotNull();
        assertThat(tfp.getChild("debug").getCommand()).as("bare /tfp debug is incomplete").isNull();
        // permissions: /tfp for everyone with tfp.command, reload/debug for admins only
        CommandSourceStack user = Fakes.source(Set.of(TfpCommands.PERMISSION), new ArrayList<>());
        CommandSourceStack admin = Fakes.source(ADMIN, new ArrayList<>());
        assertThat(tfp.canUse(user)).isTrue();
        assertThat(tfp.getChild("version").canUse(user)).isTrue();
        assertThat(tfp.getChild("reload").canUse(user)).isFalse();
        assertThat(tfp.getChild("debug").canUse(user)).isFalse();
        assertThat(tfp.getChild("time").canUse(user)).isFalse();
        assertThat(tfp.getChild("time").canUse(admin)).isTrue();
        assertThat(tfp.getChild("reload").canUse(admin)).isTrue();
        assertThat(tfp.getChild("debug").canUse(admin)).isTrue();
    }

    static List<String> children(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).toList();
    }

    @Test
    void reloadVariantsCallTheHandlerAndReplyTranslated() throws CommandSyntaxException {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        d.execute("tfp reload", Fakes.source(ADMIN, out));
        d.execute("tfp reload data", Fakes.source(ADMIN, out));
        d.execute("tfp reload content", Fakes.source(ADMIN, out));
        assertThat(h.calls).containsExactly("reloadData", "reloadData", "reloadContent");
        assertThat(out).containsExactly("Reloading data...", "Data reloaded: 10 entries in 2 registries (version 3).",
                "Reloading data...", "Data reloaded: 10 entries in 2 registries (version 3).",
                MESSAGES.get("en_us", "reload.content.unavailable"));

        out.clear();
        d.execute("tfp reload data", Fakes.playerSource("Ayşe", java.util.Locale.forLanguageTag("tr-TR"), ADMIN, out));
        assertThat(out).containsExactly("Veriler yeniden yükleniyor...", "Veriler yeniden yüklendi: 2 kayıt defterinde 10 girdi (sürüm 3).");
    }

    @Test
    void debugSubcommands() throws CommandSyntaxException {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        // console: block/chunk need a player, player needs a name
        d.execute("tfp debug block", Fakes.source(ADMIN, out));
        d.execute("tfp debug player Steve", Fakes.source(ADMIN, out));
        d.execute("tfp debug packets", Fakes.source(ADMIN, out));
        d.execute("tfp debug chunk", Fakes.source(ADMIN, out));
        d.execute("tfp debug chunk -380 730", Fakes.source(ADMIN, out));
        assertThat(out).containsExactly("Only a player can use this command.", "Player data of Steve:", "Packet layer:",
                "  noop: calls=0", "The console needs a position: /tfp debug chunk <x> <z>", "Chunk 0, 0 in world:");
        out.clear();
        CommandSourceStack alex = Fakes.playerSource("Alex", java.util.Locale.US, ADMIN, out);
        d.execute("tfp debug block", alex);
        d.execute("tfp debug chunk", alex);
        d.execute("tfp debug player", alex);
        assertThat(h.calls).containsExactly("debugPlayer Steve", "debugPackets", "debugChunkAt -380 730", "debugBlock Alex",
                "debugChunk Alex", "debugPlayer Alex");
        assertThat(out).containsExactly("Block at world 1 2 3:", "  vanilla: minecraft:stone", "Chunk 0, 0 in world:",
                "Player data of Alex:");
        assertThatThrownBy(() -> d.execute("tfp debug block", Fakes.source(Set.of(TfpCommands.PERMISSION), out)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    /** M2-CAL-02: {@code /tfp time set|add} needs tfp.admin, parses TFC's arguments and moves the calendar by TFC's delta. */
    @Test
    void timeCommandsNeedAdminAndSkipTheCalendar() throws CommandSyntaxException {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        d.execute("tfp time set noon", Fakes.source(ADMIN, out));
        d.execute("tfp time set hour 13", Fakes.source(ADMIN, out));
        d.execute("tfp time set hour 24", Fakes.source(ADMIN, out));
        d.execute("tfp time set midnight", Fakes.source(ADMIN, out));
        d.execute("tfp time add days 3", Fakes.source(ADMIN, out));
        d.execute("tfp time add ticks 5", Fakes.source(ADMIN, out));
        assertThat(h.calls).containsExactly(
                "skipTime time.set.done noon 6000",
                "skipTime time.set.done hour 13 7000",
                "skipTime time.set.done hour 24 18000",
                "skipTime time.set.done midnight 18000",
                "skipTime time.add.done 3 days 72000",
                "skipTime time.add.done 5 ticks 5");
        assertThat(out).containsExactly(
                "Time set to noon: moved 6000 calendar ticks forward, now June 1, 1000 12:00.",
                "Time set to hour 13: moved 7000 calendar ticks forward, now June 1, 1000 12:00.",
                "Time set to hour 24: moved 18000 calendar ticks forward, now June 1, 1000 12:00.",
                "Time set to midnight: moved 18000 calendar ticks forward, now June 1, 1000 12:00.",
                "Added 3 days: moved 72000 calendar ticks forward, now June 1, 1000 12:00.",
                "Added 5 ticks: moved 5 calendar ticks forward, now June 1, 1000 12:00.");
        // admin only, and TFC's argument rules
        assertThatThrownBy(() -> d.execute("tfp time set day", Fakes.source(Set.of(TfpCommands.PERMISSION), out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> d.execute("tfp time set hour 25", Fakes.source(ADMIN, out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> d.execute("tfp time add days 0", Fakes.source(ADMIN, out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> d.execute("tfp time add weeks 1", Fakes.source(ADMIN, out)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(h.calls).hasSize(6);
    }

    /** M1-SVC-05: {@code /tfp debug showcase <family>} reaches the handler for players only; families are suggested. */
    @Test
    void debugShowcase() throws Exception {
        FakeHandler h = new FakeHandler();
        CommandDispatcher<CommandSourceStack> d = dispatcher(h);
        List<String> out = new ArrayList<>();
        d.execute("tfp debug showcase ores", Fakes.source(ADMIN, out));
        assertThat(out).containsExactly("Only a player can use this command.");
        out.clear();
        d.execute("tfp debug showcase rocks", Fakes.playerSource("Alex", java.util.Locale.US, ADMIN, out));
        assertThat(h.calls).containsExactly("debugShowcase Alex rocks");
        assertThat(out).containsExactly("Showcase rocks: placed 3 of 4 blocks in a 5 x 3 grid at world 2 64 2.");
        CommandSourceStack alex = Fakes.playerSource("Alex", java.util.Locale.US, ADMIN, out);
        var suggestions = d.getCompletionSuggestions(d.parse("tfp debug showcase ", alex)).get();
        assertThat(suggestions.getList()).extracting(com.mojang.brigadier.suggestion.Suggestion::getText)
                .containsExactly("ores", "rocks", "soils");
        assertThatThrownBy(() -> d.execute("tfp debug showcase", alex)).isInstanceOf(CommandSyntaxException.class);
    }

    /**
     * M1-BOOT-01: every {@code server} dependency in paper-plugin.yml is a plugin of content/compat.yml (the
     * repository file, not only the jar copy) and the reverse, so the gate checks exactly what we load before.
     */
    @Test
    void paperPluginDependenciesMatchTheCompatTable() throws Exception {
        Path compat = Path.of(System.getProperty("tfp.compatFile", "../content/compat.yml"));
        assertThat(compat).isRegularFile();
        Map<?, ?> table = Yamls.load(Files.readString(compat));
        Set<Object> compatPlugins = new java.util.HashSet<>(((Map<?, ?>) table.get("plugins")).keySet());
        Map<?, ?> server = (Map<?, ?>) ((Map<?, ?>) descriptor().get("dependencies")).get("server");
        assertThat(new java.util.HashSet<Object>(server.keySet())).isEqualTo(compatPlugins);
        // the jar's copy (what CompatCheck reads at runtime) is the same file
        try (InputStream in = BootstrapTest.class.getClassLoader().getResourceAsStream(CompatCheck.RESOURCE)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(Files.readString(compat));
        }
    }

    @Test
    void handlerFailureIsReported() throws CommandSyntaxException {
        FakeHandler failing = new FakeHandler() {
            @Override
            public CompletableFuture<Reply> reloadData() {
                return CompletableFuture.failedFuture(new java.util.concurrent.CompletionException(
                        new IllegalStateException("disk on fire")));
            }
        };
        List<String> out = new ArrayList<>();
        dispatcher(failing).execute("tfp reload data", Fakes.source(ADMIN, out));
        assertThat(out).containsExactly("Reloading data...", "Command failed: disk on fire");
    }

    @Test
    void consoleReplyThatIsReadySoonIsSentBeforeTheCommandReturns() throws CommandSyntaxException {
        // RCON returns only what was sent while the command ran, so a console reply ready within the wait is in it.
        FakeHandler soon = new FakeHandler() {
            @Override
            public CompletableFuture<Reply> debugChunkAt(int x, int z) {
                return CompletableFuture.supplyAsync(() -> Reply.ok("debug.chunk.header", 1, 2, "world"),
                        CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS));
            }
        };
        List<String> out = new ArrayList<>();
        dispatcher(soon).execute("tfp debug chunk -380 730", Fakes.source(ADMIN, out));
        assertThat(out).containsExactly("Chunk 1, 2 in world:");
    }

    @Test
    void consoleReplyThatIsTooSlowIsSentWhenReady() throws CommandSyntaxException {
        CompletableFuture<Reply> late = new CompletableFuture<>();
        FakeHandler slow = new FakeHandler() {
            @Override
            public CompletableFuture<Reply> debugChunkAt(int x, int z) {
                return late;
            }
        };
        List<String> out = new ArrayList<>();
        dispatcher(slow).execute("tfp debug chunk 1 2", Fakes.source(ADMIN, out));
        assertThat(out).isEmpty();
        late.complete(Reply.ok("debug.chunk.header", 0, 0, "world"));
        assertThat(out).containsExactly("Chunk 0, 0 in world:");
    }
}
