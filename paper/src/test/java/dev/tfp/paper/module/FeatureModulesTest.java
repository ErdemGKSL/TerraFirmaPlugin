/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.tfp.paper.boot.TfpCommands;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.i18n.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-01: ServiceLoader discovery, enable/disable order, failure isolation and debug literal merging. */
class FeatureModulesTest {
    private static final Logger LOG = Logger.getLogger("tfp-test");
    private static final Messages MESSAGES = Messages.fromJar();
    private static final Set<String> ADMIN = Set.of(TfpCommands.PERMISSION, TfpCommands.ADMIN_PERMISSION);
    /** What the fake modules did, in order. */
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @BeforeEach
    void clear() {
        EVENTS.clear();
    }

    /** First fake: a debug literal with an argument. */
    public static final class Alpha implements FeatureModule {
        @Override
        public String id() {
            return "alpha";
        }

        @Override
        public void enable(ModuleContext context) {
            EVENTS.add("enable alpha");
        }

        @Override
        public void disable() {
            EVENTS.add("disable alpha");
        }

        @Override
        public void debugCommands(DebugTree tree) {
            tree.add("alpha", a -> a
                    .executes(tree.run(ctx -> CompletableFuture.completedFuture(
                            Reply.ok("debug.packets.header").withDetails(List.of("alpha ran")))))
                    .then(Commands.argument("n", IntegerArgumentType.integer(0))
                            .executes(tree.run(ctx -> CompletableFuture.completedFuture(Reply.ok("debug.packets.header")
                                    .withDetails(List.of("alpha " + IntegerArgumentType.getInteger(ctx, "n"))))))));
            tree.add("block", b -> b.executes(tree.run(ctx -> CompletableFuture.completedFuture(Reply.ok("x")))));
        }
    }

    /** Second fake: fails to enable. */
    public static final class Broken implements FeatureModule {
        @Override
        public String id() {
            return "broken";
        }

        @Override
        public void enable(ModuleContext context) {
            EVENTS.add("enable broken");
            throw new IllegalStateException("boom");
        }

        @Override
        public void disable() {
            EVENTS.add("disable broken");
        }

        @Override
        public void debugCommands(DebugTree tree) {
            tree.add("broken", b -> b.executes(tree.run(ctx -> CompletableFuture.completedFuture(Reply.ok("x")))));
        }
    }

    /** Third fake: provides a service and reads the one before it; reuses alpha's literal. */
    public static final class Gamma implements FeatureModule {
        @Override
        public String id() {
            return "gamma";
        }

        @Override
        public void enable(ModuleContext context) {
            context.services().provide(String.class, "from gamma");
            EVENTS.add("enable gamma");
        }

        @Override
        public void disable() {
            EVENTS.add("disable gamma");
        }

        @Override
        public void debugCommands(DebugTree tree) {
            tree.add("alpha", a -> a.executes(tree.run(ctx -> CompletableFuture.completedFuture(Reply.ok("x")))));
            tree.add("Bad Name", a -> { });
        }
    }

    /** A duplicate id. */
    public static final class AlphaAgain implements FeatureModule {
        @Override
        public String id() {
            return "alpha";
        }

        @Override
        public void enable(ModuleContext context) {
            EVENTS.add("enable alpha again");
        }
    }

    private static ModuleContext context(ModuleServices services) {
        return new ModuleContext.Of(null, LOG, null, null, null, null, null, MESSAGES, null, null, null, null, null,
                services);
    }

    /** A class loader that sees our classes plus a service file listing {@code classes}. */
    private static ClassLoader withServiceFile(Path dir, Class<?>... classes) throws IOException {
        Path file = dir.resolve(FeatureModules.SERVICE_FILE);
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder("# fake modules\n");
        for (Class<?> c : classes) {
            sb.append(c.getName()).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        // Parent-last for the service file only: ServiceLoader merges the parent's (real) file and ours.
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, FeatureModulesTest.class.getClassLoader());
    }

    private static List<String> ids(FeatureModules m) {
        return m.modules().stream().map(FeatureModule::id).toList();
    }

    @Test
    void theShippedServiceFileListsTheFoodModule() {
        FeatureModules m = FeatureModules.load(FeatureModulesTest.class.getClassLoader());
        assertThat(ids(m)).contains(FoodModule.ID);
        assertThat(m.problems()).isEmpty();
        assertThat(FeatureModules.shared()).isSameAs(FeatureModules.shared());
    }

    @Test
    void serviceLoaderDiscoveryKeepsFileOrderAndSkipsDuplicates(@TempDir Path dir) throws IOException {
        FeatureModules m = FeatureModules.load(withServiceFile(dir, Alpha.class, Broken.class, Gamma.class, AlphaAgain.class));
        List<String> found = ids(m);
        assertThat(found).containsSubsequence("alpha", "broken", "gamma");
        assertThat(found.stream().filter("alpha"::equals)).hasSize(1);
        assertThat(m.modules().stream().filter(x -> x.id().equals("alpha")).findFirst().orElseThrow())
                .isInstanceOf(Alpha.class);
        assertThat(m.problems()).anyMatch(p -> p.contains("'alpha'") && p.contains(AlphaAgain.class.getName()));
    }

    @Test
    void aMissingClassInTheServiceFileIsAProblemNotACrash(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(FeatureModules.SERVICE_FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "dev.tfp.paper.module.NoSuchModule\n");
        FeatureModules m = FeatureModules.load(new URLClassLoader(new URL[] {dir.toUri().toURL()},
                FeatureModulesTest.class.getClassLoader()));
        assertThat(m.problems()).anyMatch(p -> p.contains("NoSuchModule"));
        assertThat(ids(m)).as("the other entries still load").contains(FoodModule.ID);
    }

    @Test
    void enableInOrderIsolateFailuresDisableInReverse() {
        FeatureModules m = FeatureModules.of(List.of(new Alpha(), new Broken(), new Gamma()));
        ModuleServices services = new ModuleServices();
        assertThat(m.enableAll(context(services), LOG)).containsExactly("alpha", "gamma");
        assertThat(EVENTS).containsExactly("enable alpha", "enable broken", "enable gamma");
        assertThat(m.isEnabled("alpha")).isTrue();
        assertThat(m.isEnabled("broken")).isFalse();
        assertThat(m.problems()).anyMatch(p -> p.contains("broken") && p.contains("boom"));
        assertThat(services.require(String.class)).isEqualTo("from gamma");

        EVENTS.clear();
        m.disableAll(LOG);
        assertThat(EVENTS).containsExactly("disable gamma", "disable alpha");
        assertThat(m.enabledIds()).isEmpty();
        assertThat(m.isEnabled("alpha")).isFalse();
    }

    @Test
    void servicesRefuseASecondProviderAndNameTheMissingOne() {
        ModuleServices s = new ModuleServices();
        s.provide(Integer.class, 1);
        assertThatThrownBy(() -> s.provide(Integer.class, 2)).isInstanceOf(IllegalStateException.class);
        assertThat(s.get(Long.class)).isEmpty();
        assertThatThrownBy(() -> s.require(Long.class)).hasMessageContaining("java.lang.Long");
    }

    // --- debug literals ---------------------------------------------------------------------------------------

    private static TfpCommands.Handler handler() {
        return (TfpCommands.Handler) Proxy.newProxyInstance(FeatureModulesTest.class.getClassLoader(),
                new Class<?>[] {TfpCommands.Handler.class}, (p, method, a) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CommandSourceStack console(List<String> out) {
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(FeatureModulesTest.class.getClassLoader(),
                new Class<?>[] {CommandSender.class}, (p, method, a) -> switch (method.getName()) {
                    case "hasPermission" -> ADMIN.contains(String.valueOf(a[0]));
                    case "sendMessage" -> {
                        if (a.length == 1 && a[0] instanceof Component c) {
                            out.add(PlainTextComponentSerializer.plainText().serialize(c));
                        }
                        yield null;
                    }
                    case "getName" -> "tester";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (CommandSourceStack) Proxy.newProxyInstance(FeatureModulesTest.class.getClassLoader(),
                new Class<?>[] {CommandSourceStack.class}, (p, method, a) -> {
                    if (method.getName().equals("getSender")) {
                        return sender;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(FeatureModules modules,
            TfpCommands.Handler handler) {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.getRoot().addChild(TfpCommands.build("1.2.3", () -> handler, MESSAGES, modules));
        return d;
    }

    @Test
    void moduleLiteralsAreMergedUnderDebugAndClashesSkipped() {
        FeatureModules m = FeatureModules.of(List.of(new Alpha(), new Broken(), new Gamma()));
        CommandDispatcher<CommandSourceStack> d = dispatcher(m, handler());
        CommandNode<CommandSourceStack> debug = d.getRoot().getChild("tfp").getChild("debug");
        List<String> names = debug.getChildren().stream().map(CommandNode::getName).toList();
        assertThat(names).contains("alpha", "broken", "block", "chunk", "packets");
        assertThat(names).doesNotContain("Bad Name");
        assertThat(names.stream().filter("block"::equals)).hasSize(1);
        // the built-in /tfp debug block keeps its own command shape, not alpha's
        assertThat(debug.getChild("alpha").getChild("n")).isNotNull();
        assertThat(m.problems()).anyMatch(p -> p.contains("alpha: debug literal 'block'"))
                .anyMatch(p -> p.contains("gamma: debug literal 'alpha'"))
                .anyMatch(p -> p.contains("'Bad Name'"));
        // the built-in tree is otherwise unchanged
        assertThat(d.getRoot().getChild("tfp").getChildren().stream().map(CommandNode::getName).toList())
                .containsExactlyInAnyOrder("version", "reload", "debug", "time");
        // admins only, like every debug command
        assertThat(debug.getChild("alpha").canUse(console(new ArrayList<>()))).isTrue();
    }

    @Test
    void moduleCommandsRunOnlyWhileThePluginAndTheModuleAreEnabled() throws CommandSyntaxException {
        FeatureModules m = FeatureModules.of(List.of(new Alpha(), new Broken()));
        List<String> out = new ArrayList<>();

        // plugin not enabled
        dispatcher(m, null).execute("tfp debug alpha", console(out));
        assertThat(out).containsExactly(MESSAGES.get(Messages.FALLBACK, "command.not_enabled"));

        // plugin enabled, module not yet
        out.clear();
        CommandDispatcher<CommandSourceStack> d = dispatcher(m, handler());
        assertThat(d.execute("tfp debug alpha", console(out))).isZero();
        assertThat(out).containsExactly(MESSAGES.get(Messages.FALLBACK, "command.not_enabled"));

        m.enableAll(context(new ModuleServices()), LOG);
        out.clear();
        assertThat(d.execute("tfp debug alpha", console(out))).isEqualTo(1);
        assertThat(out).containsExactly(MESSAGES.get(Messages.FALLBACK, "debug.packets.header"), "  alpha ran");
        out.clear();
        d.execute("tfp debug alpha 7", console(out));
        assertThat(out).contains("  alpha 7");

        // a module whose enable failed answers "not enabled"
        out.clear();
        d.execute("tfp debug broken", console(out));
        assertThat(out).containsExactly(MESSAGES.get(Messages.FALLBACK, "command.not_enabled"));

        m.disableAll(LOG);
        out.clear();
        d.execute("tfp debug alpha", console(out));
        assertThat(out).containsExactly(MESSAGES.get(Messages.FALLBACK, "command.not_enabled"));
    }

    @Test
    void theDefaultTreeUsesTheSharedModules() {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        d.getRoot().addChild(TfpCommands.build("1.2.3", () -> null, MESSAGES));
        // the shipped modules add no literal that shadows a built-in one
        assertThat(FeatureModules.shared().problems()).noneMatch(p -> p.contains("already used"));
        assertThat(d.getRoot().getChild("tfp").getChild("debug").getChild("block")).isNotNull();
    }
}
