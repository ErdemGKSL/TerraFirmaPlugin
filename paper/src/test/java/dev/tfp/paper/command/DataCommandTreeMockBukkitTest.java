/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import dev.tfp.paper.boot.TfpCommands;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.testing.MockServerTest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-01: the {@code /tfp} command literals, built the way {@code TfpCommands} adds them, run on MockBukkit. */
class DataCommandTreeMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1c3");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final List<java.util.concurrent.CompletableFuture<Reply>> futures = new ArrayList<>();
    final QueueScheduler scheduler = new QueueScheduler();
    final List<String> weather = new ArrayList<>();
    CommandDispatcher<CommandSourceStack> dispatcher;
    PlayerMock alex;
    PlayerMock bob;
    boolean admin = true;

    @BeforeEach
    void setUp() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        bob = new PlayerMock(server, "Bob", UUID.fromString("00000000-0000-0000-0000-00000000b0b1"));
        server.addPlayer(bob);
        server.addSimpleWorld("tree");
        DataCommandTree.Ops ops = new DataCommandTree.Ops(
                new PlayerCommands(scheduler, store, () -> 1_000L, id -> 20.0),
                new WorldCommands(scheduler, new FakeCustomBlocks(), (s, t) -> false, (m, t) -> false, System::nanoTime),
                new TrimCommands(scheduler, new FakeItems()), new WeatherCommands((w, on) -> weather.add(on ? "on" : "off")),
                Messages.fromJar());
        dispatcher = new CommandDispatcher<>();
        var root = Commands.literal("tfp");
        for (var literal : DataCommandTree.literals((enabled, action) -> ctx -> {
            futures.add(action.apply(ctx));
            return 1;
        }, () -> true, () -> ops)) {
            root.then(literal);
        }
        dispatcher.getRoot().addChild(root.build());
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private CommandSourceStack source() {
        Location at = new Location(server.getWorld("tree"), 0, 64, 0);
        return (CommandSourceStack) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {CommandSourceStack.class}, (p, m, a) -> switch (m.getName()) {
                    case "getSender" -> new AdminSender(alex, admin).sender();
                    case "getLocation" -> at;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    /** The sender is the player; permission checks go to {@code admin}. */
    private record AdminSender(PlayerMock player, boolean admin) {
        org.bukkit.entity.Player sender() {
            return (org.bukkit.entity.Player) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {org.bukkit.entity.Player.class}, (p, m, a) -> {
                        if (m.getName().equals("hasPermission")) {
                            return admin && a[0].equals(TfpCommands.ADMIN_PERMISSION);
                        }
                        try {
                            return m.invoke(player, a);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    private static List<String> children(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).toList();
    }

    private Reply run(String line) throws CommandSyntaxException {
        futures.clear();
        dispatcher.execute(line, source());
        scheduler.drain();
        assertThat(futures).hasSize(1);
        return futures.getFirst().join();
    }

    @Test
    void theTreeHasTfcsShapeUnderTfp() {
        CommandNode<CommandSourceStack> tfp = dispatcher.getRoot().getChild("tfp");
        assertThat(children(tfp)).containsExactlyInAnyOrder("player", "count", "clearworld", "trim", "weather");
        CommandNode<CommandSourceStack> target = tfp.getChild("player").getChild("target");
        assertThat(children(target)).containsExactlyInAnyOrder("query", "set", "reset", "add");
        assertThat(children(target.getChild("query"))).containsExactlyInAnyOrder("hunger", "saturation", "water", "nutrition");
        assertThat(children(target.getChild("set"))).containsExactlyInAnyOrder("hunger", "saturation", "water");
        assertThat(children(target.getChild("reset"))).containsExactlyInAnyOrder("hunger", "saturation", "water");
        assertThat(children(target.getChild("add"))).containsExactlyInAnyOrder("hunger", "saturation", "water");
        assertThat(children(tfp.getChild("weather"))).containsExactlyInAnyOrder("enable", "disable", "clear", "rain");
        assertThat(children(tfp.getChild("count"))).containsExactly("radius");
        assertThat(children(tfp.getChild("clearworld").getChild("radius"))).containsExactly("preset");
        assertThat(tfp.getChild("clearworld").getChild("radius").getCommand()).as("the preset is optional").isNotNull();
    }

    @Test
    void everyCommandNeedsTheAdminPermission() throws Exception {
        admin = false;
        for (String line : List.of("tfp player Alex query hunger", "tfp count 1 stone", "tfp clearworld 1", "tfp trim a b",
                "tfp weather enable")) {
            assertThatThrownBy(() -> dispatcher.execute(line, source())).as(line).isInstanceOf(CommandSyntaxException.class);
        }
        assertThat(futures).isEmpty();
    }

    @Test
    void playerCommandsRunAndKeepTfcsRanges() throws Exception {
        alex.setFoodLevel(12);
        assertThat(run("tfp player Alex query hunger")).isEqualTo(Reply.ok("command.data.player.query_hunger", 12));
        run("tfp player Alex set hunger 5");
        assertThat(alex.getFoodLevel()).isEqualTo(5);
        run("tfp player Alex reset hunger");
        assertThat(alex.getFoodLevel()).isEqualTo(20);
        run("tfp player Alex reset saturation");
        assertThat(alex.getSaturation()).isEqualTo(5f);
        assertThat(run("tfp player Alex query water").key()).isEqualTo("command.data.player.query_water");
        assertThat(run("tfp player Nobody query hunger")).isEqualTo(Reply.error("command.player_not_found", "Nobody"));
        for (String bad : List.of("tfp player Alex set hunger 21", "tfp player Alex set water 101", "tfp player Alex add water -1",
                "tfp player Alex add hunger 21", "tfp player Alex set hunger -1", "tfp player Alex set nutrition 1")) {
            assertThatThrownBy(() -> dispatcher.execute(bad, source())).as(bad).isInstanceOf(CommandSyntaxException.class);
        }
        run("tfp player Alex add hunger -20");
        assertThat(alex.getFoodLevel()).isZero();
        assertThat(run("tfp player Bob set water 40")).as("Bob has no stored data")
                .isEqualTo(Reply.error("command.data.player.no_data"));
    }

    @Test
    void wordsTheCoreRefusesBecomeParseReplies() throws Exception {
        assertThat(run("tfp count 3 stone[").key()).isEqualTo("command.data.parse.bad_syntax");
        assertThat(run("tfp clearworld 3 bogus")).isEqualTo(Reply.error("command.data.parse.unknown_value", "bogus"));
        assertThat(run("tfp trim minecraft:iron_ingot").key()).isEqualTo("command.data.parse.missing_argument");
        assertThat(run("tfp trim minecraft:iron_ingot minecraft:x unobtainium"))
                .isEqualTo(Reply.error("command.data.parse.unknown_value", "unobtainium"));
        assertThat(run("tfp trim minecraft:iron_ingot minecraft:x steel").key()).as("no steel armour in the fake items")
                .isEqualTo("command.data.trim.not_armor");
        assertThatThrownBy(() -> dispatcher.execute("tfp count 0 stone", source())).isInstanceOf(CommandSyntaxException.class);
        assertThatThrownBy(() -> dispatcher.execute("tfp clearworld 251", source())).isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void weatherDecidesAsTfcDoes() throws Exception {
        assertThat(run("tfp weather enable")).isEqualTo(Reply.ok("command.data.weather.enabled"));
        assertThat(run("tfp weather disable")).isEqualTo(Reply.ok("command.data.weather.disabled"));
        assertThat(run("tfp weather rain").args()).containsExactly("/tfp time set rain");
        assertThat(weather).containsExactly("on", "off");
    }

    @Test
    void countAndClearReplyOnceTheChunksAreWalked() throws Exception {
        org.bukkit.World world = server.getWorld("tree");
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                world.loadChunk(cx, cz);
            }
        }
        world.getBlockAt(0, 80, 0).setType(org.bukkit.Material.GOLD_BLOCK);
        assertThat(run("tfp count 2 gold_block")).isEqualTo(Reply.ok("command.data.count.done", 1L));
        assertThat(run("tfp count 2 minecraft:gold_block")).isEqualTo(Reply.ok("command.data.count.done", 1L));
        Reply cleared = run("tfp clearworld 2 RAW_ROCK");
        assertThat(cleared).isEqualTo(Reply.ok("command.data.clear.done", 0L));
        assertThat(run("tfp clearworld 2").args()).as("everything below the surface, the gold block too").isNotEqualTo(List.of(0L));
        assertThat(world.getBlockAt(0, 80, 0).getType()).isEqualTo(org.bukkit.Material.AIR);
    }

    private static final class FakeItems implements dev.tfp.paper.integration.CustomItems {
        @Override
        public boolean exists(String id) {
            return false;
        }

        @Override
        public java.util.Optional<org.bukkit.inventory.ItemStack> create(String id, int amount) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<String> idOf(org.bukkit.inventory.ItemStack stack) {
            return java.util.Optional.empty();
        }
    }
}
