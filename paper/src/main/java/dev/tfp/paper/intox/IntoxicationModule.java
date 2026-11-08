/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onServerChat, onPlayerDeath: a death
 * gives a fresh PlayerInfo), src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (intoxication)
 */
package dev.tfp.paper.intox;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tfp.core.intox.ChatSlur;
import dev.tfp.core.intox.Intoxicants;
import dev.tfp.core.intox.Intoxication;
import dev.tfp.core.registry.Registries;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Alcohol and intoxication (PAR-SURV-08), a feature module. Provides {@link IntoxicationService} (the lazy
 * intoxication clock in the player data, the drinkables' intoxication) and does what TFC does with it:
 * <ul>
 *   <li><b>Drunk chat</b> (TFC {@code onServerChat}): above 0.2 intoxication every chat message is slurred
 *       ({@link ChatSlur}) and sent as plain text, as TFC's {@code Component.literal}. Runs on Paper's async chat
 *       thread: it only reads the store (lock-free) and one clock snapshot, and writes nothing.</li>
 *   <li><b>Death</b>: TFC gives the respawned player a fresh {@code PlayerInfo} (only nutrition may be copied), so a
 *       respawn after a death is sober again. Leaving the End is no death and keeps it (TFC reads a fresh
 *       {@code PlayerInfo} there until relog; see the ledger divergence).</li>
 * </ul>
 * TFC has no other effect of intoxication: no nausea or other potion effect (the alcohol drinkable defines none) and no
 * HUD element. Game modes: TFC slurs in every game mode (intoxication is no need that creative turns off); kept.
 *
 * <p>Debug: {@code /tfp debug intox [name] [add <ticks> | drink <fluid> | clear]} prints (and changes) a player's
 * intoxication: the level, ticks left and the slur chance.
 */
public final class IntoxicationModule implements FeatureModule, Listener {
    private volatile IntoxicationService service;
    private volatile Scheduler scheduler;
    private volatile ChatSlur.Random random = new ThreadRandom();

    /** For the service file. */
    public IntoxicationModule() {
    }

    /** With an explicit service and random (tests). */
    IntoxicationModule(IntoxicationService service, Scheduler scheduler, ChatSlur.Random random) {
        this.service = service;
        this.scheduler = scheduler;
        this.random = random;
    }

    @Override
    public String id() {
        return "intoxication";
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        IntoxicantsCache intoxicants = new IntoxicantsCache(() -> context.registryService().registries());
        service = new IntoxicationService(context.playerData(), () -> context.clock().get().playerTicks(),
                intoxicants, Intoxication.DEFAULT_MAX_TICKS);
        context.services().provide(IntoxicationService.class, service);
        context.listen(this);
    }

    /** The service (tests and the debug command). */
    IntoxicationService service() {
        return service;
    }

    /**
     * Async chat thread: TFC's drunk chat. A slurred message replaces the whole component with plain text (TFC's
     * {@code Component.literal}), so styling or click events set by chat plugins before this LOW listener are lost
     * on drunk messages; plugins listening later decorate the slurred text as usual.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        IntoxicationService s = service;
        if (s == null) {
            return;
        }
        float level = s.level(event.getPlayer().getUniqueId());
        if (!ChatSlur.slurs(level)) {
            return;
        }
        String original = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(Component.text(ChatSlur.slur(original, level, random)));
    }

    /** Player thread: a respawn after a death starts sober (TFC's fresh {@code PlayerInfo}). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        IntoxicationService s = service;
        if (s != null && event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
            s.clear(event.getPlayer().getUniqueId());
        }
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("intox", node -> node
                .executes(tree.player((player, ctx) -> debug(player, p -> null)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(tree.run(ctx -> named(ctx.getArgument("name", String.class), p -> null)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(tree.run(ctx -> {
                                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                            return named(ctx.getArgument("name", String.class), p -> {
                                                service.add(p.getUniqueId(), ticks);
                                                return null;
                                            });
                                        }))))
                        .then(Commands.literal("drink")
                                .then(Commands.argument("fluid", StringArgumentType.greedyString())
                                        .executes(tree.run(ctx -> {
                                            String fluid = StringArgumentType.getString(ctx, "fluid").trim();
                                            return named(ctx.getArgument("name", String.class), p -> {
                                                int ticks = service.drink(p.getUniqueId(), fluid);
                                                return ticks == 0 ? Reply.error("debug.intox.not_alcohol", fluid)
                                                        : null;
                                            });
                                        }))))
                        .then(Commands.literal("clear")
                                .executes(tree.run(ctx -> named(ctx.getArgument("name", String.class), p -> {
                                    service.clear(p.getUniqueId());
                                    return null;
                                }))))));
    }

    private CompletableFuture<Reply> named(String name, Function<Player, Reply> change) {
        Player target = Bukkit.getPlayerExact(name);
        return target == null ? CompletableFuture.completedFuture(Reply.error("command.player_not_found", name))
                : debug(target, change);
    }

    /**
     * Any thread: runs {@code change} on the player's thread (writes follow the store's convention), then answers with
     * the state; a non-null reply from {@code change} answers instead.
     */
    CompletableFuture<Reply> debug(Player player, Function<Player, Reply> change) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            return CompletableFuture.completedFuture(debugNow(player, change));
        }
        CompletableFuture<Reply> out = new CompletableFuture<>();
        scheduler.entity(player, () -> {
            try {
                out.complete(debugNow(player, change));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
        return out;
    }

    private Reply debugNow(Player player, Function<Player, Reply> change) {
        Reply refused = change.apply(player);
        return refused != null ? refused : state(player.getName(), service.level(player.getUniqueId()),
                service.remaining(player.getUniqueId()), service.maxTicks());
    }

    /** The debug reply of a state. */
    static Reply state(String name, float level, long remaining, int maxTicks) {
        return Reply.ok("debug.intox.state", name, String.format(Locale.ROOT, "%.3f", level), remaining, maxTicks,
                String.format(Locale.ROOT, "%.3f", Intoxication.slurChance(level)));
    }

    /** The drinkables' intoxication of the current registry version. */
    static final class IntoxicantsCache implements Supplier<Intoxicants> {
        private record Entry(long version, Intoxicants intoxicants) {
        }

        private final Supplier<Registries> registries;
        private volatile Entry entry = new Entry(0, Intoxicants.EMPTY);

        IntoxicantsCache(Supplier<Registries> registries) {
            this.registries = registries;
        }

        @Override
        public Intoxicants get() {
            Registries r = registries.get();
            if (r == null) {
                return Intoxicants.EMPTY;
            }
            long version = r.version();
            Entry e = entry;
            if (e.version() == version) {
                return e.intoxicants();
            }
            Intoxicants i = version == 0 ? Intoxicants.EMPTY : Intoxicants.fromRegistry(r.get());
            entry = new Entry(version, i);
            return i;
        }
    }

    /** TFC's {@code player.getRandom()} on whichever thread chat arrives: the thread's own random. */
    private static final class ThreadRandom implements ChatSlur.Random {
        @Override
        public float nextFloat() {
            return ThreadLocalRandom.current().nextFloat();
        }

        @Override
        public int nextInt(int bound) {
            return ThreadLocalRandom.current().nextInt(bound);
        }
    }
}
