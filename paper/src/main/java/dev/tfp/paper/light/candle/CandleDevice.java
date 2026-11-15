/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (useItemOn, randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCandleCakeBlock.java @ c3b4d9536 (useItemOn, randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/JackOLanternBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCakeBlock.java @ c3b4d9536 (eatCake: the bite of a fresh cake),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick)
 */
package dev.tfp.paper.light.candle;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.light.LightSourceRules;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.light.candle.CandleRules.Family;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-11: candles, candle cakes and the jack-o'-lantern as device kinds ({@code tfp:candle} covering the dyed
 * {@code tfp:candle/<color>}, {@code tfp:candle_cake} likewise, {@code tfp:jack_o_lantern}). The record is TFC's
 * {@code TickCounterBlockEntity}: the calendar tick the block was lit at, {@link CandleRules#NEVER_LIT} until then.
 *
 * <ul>
 *   <li>Right click on a candle or candle cake: {@link CandleRules#use}. Extinguishing writes {@code lit=false} at once
 *       (the player's own action on its region); the candle cake's bite asks the {@link CakeEating} of PAR-DECO-12.</li>
 *   <li>Placed: TFC sets no counter (no {@code setPlacedBy}), so a placed jack-o'-lantern counts as burnt out and is
 *       handed to the burnout pipeline at once (TFC: at its first random tick); a candle is placed unlit.</li>
 *   <li>Clicked or notified past the burn time: handed to the burnout pipeline and treated as the unlit block it is
 *       about to be.</li>
 * </ul>
 */
public final class CandleDevice {
    /** The tick the block was lit at. */
    public record State(long litTick) {
    }

    public static final State FRESH = new State(CandleRules.NEVER_LIT);
    /** TFC {@code TickCounterBlockEntity}'s NBT {@code tick}. */
    public static final VersionedCodec<State> CODEC = VersionedCodec.<State>builder("candle", 1)
        .build((s, out) -> out.putLong("lit", s.litTick()), in -> new State(in.requireLong("lit")));

    /**
     * TFC {@code TFCCakeBlock.eatCake}'s player side: whether the player can eat (TFC {@code player.canEat(false)}) and,
     * if so, the cake's food ({@code FoodData.CAKE}). Provided by the cake block (PAR-DECO-12); without it a candle cake
     * cannot be eaten.
     */
    @FunctionalInterface
    public interface CakeEating {
        /** Feeds {@code player} one slice; false when the player cannot eat now. Region thread of the cake. */
        boolean eat(Player player);
    }

    /**
     * What the candles need from the server.
     *
     * @param candleTicks  TFC {@code candleTicks} (read per use; {@code -1} disables burnout)
     * @param jackTicks    TFC {@code jackOLanternTicks}
     * @param firestarter  whether a stack is TFC's firestarter
     * @param hitHeight    where the player's look ray hits the block, 0 (bottom) to 1 (top)
     * @param cakeEating   the cake's eating (empty until PAR-DECO-12 provides it)
     * @param item         a new stack of a custom item id (the candle a candle cake drops)
     */
    public record Services(IntSupplier candleTicks, IntSupplier jackTicks, Predicate<ItemStack> firestarter,
                           ToDoubleBiFunction<Player, Block> hitHeight, Supplier<Optional<CakeEating>> cakeEating,
                           Function<String, ItemStack> item, CandleEffects effects) {
        public Services {
            Objects.requireNonNull(candleTicks, "candleTicks");
            Objects.requireNonNull(jackTicks, "jackTicks");
            Objects.requireNonNull(firestarter, "firestarter");
            Objects.requireNonNull(hitHeight, "hitHeight");
            Objects.requireNonNull(cakeEating, "cakeEating");
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private final Services services;
    private final List<DeviceKind<State>> kinds;

    public CandleDevice(Services services) {
        this.services = Objects.requireNonNull(services, "services");
        this.kinds = List.of(build(Family.CANDLE), build(Family.CANDLE_CAKE), build(Family.JACK_O_LANTERN));
    }

    private DeviceKind<State> build(Family family) {
        return DeviceKind.builder(family.id(), CODEC, FRESH)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler(family))
            .build();
    }

    /** The three kinds, in {@link Family} order. */
    public List<DeviceKind<State>> kinds() {
        return kinds;
    }

    /** The device kind of {@code family}. */
    public DeviceKind<State> kind(Family family) {
        return kinds.get(family.ordinal());
    }

    Services services() {
        return services;
    }

    /** TFC's random tick at {@code now}: whether the block in {@code state} with {@code litTick} changes. */
    boolean burnsOut(Family family, BlockStateRef state, long litTick, long now) {
        return CandleRules.burnsOut(family, lit(family, state), litTick, now, services.candleTicks().getAsInt(),
            services.jackTicks().getAsInt());
    }

    /** Queues the burnout callback of a block of {@code family} lit at {@code litTick}, if it can burn out. */
    void queueBurnout(Block block, Family family, long litTick, long now) {
        long due = CandleRules.burnoutTick(litTick,
            family.limit(services.candleTicks().getAsInt(), services.jackTicks().getAsInt()));
        if (due != LightSourceRules.NEVER) {
            services.effects().burnoutIn(block, Math.max(1L, due - now));
        }
    }

    /** {@code lit} of a candle or candle cake state; a jack-o'-lantern is always lit. */
    static boolean lit(Family family, BlockStateRef state) {
        return family == Family.JACK_O_LANTERN || "true".equals(state.properties().get(CandleRules.LIT));
    }

    private final class Handler implements DeviceHandler<State> {
        private final Family family;

        Handler(Family family) {
            this.family = family;
        }

        /** TFC has no {@code setPlacedBy}: a placed jack-o'-lantern's counter is {@code Integer.MIN_VALUE}. */
        @Override
        public DeviceResult<State> placed(DeviceContext<State> ctx) {
            if (family == Family.JACK_O_LANTERN && burnsOut(family, ctx.state(), ctx.record().state().litTick(), ctx.now())) {
                services.effects().burnout(ctx.block());
            }
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<State> useWithItem(DeviceContext<State> ctx) {
            if (family == Family.JACK_O_LANTERN || ctx.player() == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            boolean lit = lit(family, ctx.state());
            if (lit && burnsOut(family, ctx.state(), ctx.record().state().litTick(), ctx.now())) {
                services.effects().burnout(ctx.block());
                lit = false; // TFC's random tick would have put it out by now
            }
            Player player = ctx.player();
            ItemStack main = player.getInventory().getItemInMainHand();
            boolean firestarter = !StackCodec.isEmpty(main) && services.firestarter().test(main);
            boolean mayBuild = player.getGameMode() != GameMode.ADVENTURE && player.getGameMode() != GameMode.SPECTATOR;
            double hit = family == Family.CANDLE_CAKE ? services.hitHeight().applyAsDouble(player, ctx.block()) : 0;
            CandleRules.Use use = CandleRules.use(family, lit, firestarter, StackCodec.isEmpty(ctx.item()), mayBuild, hit);
            switch (use) {
                case EXTINGUISH -> {
                    services.effects().setState(ctx.block(), ctx.state().with(CandleRules.LIT, "false"));
                    services.effects().sound(ctx.block(), CandleEffects.Sound.EXTINGUISH);
                    return DeviceResult.consume();
                }
                case EAT -> {
                    return eat(ctx, player) ? DeviceResult.consume() : DeviceResult.pass();
                }
                default -> {
                    return DeviceResult.pass();
                }
            }
        }

        /**
         * TFC {@code TFCCakeBlock.eatCake(level, pos, CAKE.defaultBlockState(), player)} and {@code dropResources}: the
         * player eats a slice of the fresh cake under the candle, the block becomes the cake with one bite, the candle
         * drops (the candle cake's loot).
         */
        private boolean eat(DeviceContext<State> ctx, Player player) {
            Optional<CakeEating> eating = services.cakeEating().get();
            if (eating.isEmpty() || !eating.get().eat(player)) {
                return false;
            }
            Block block = ctx.block();
            String candle = CandleRules.candleOf(ctx.state().id()).orElse(CandleRules.CANDLE);
            services.effects().place(block, BlockStateRef.of(CandleRules.CAKE).with(CandleRules.BITES, "1"));
            ItemStack drop = services.item().apply(candle);
            if (!StackCodec.isEmpty(drop)) {
                services.effects().drop(block, drop);
            }
            return true;
        }

        /** A neighbour update checks the burn time (TFC's random tick would have changed the block by now). */
        @Override
        public DeviceResult<State> neighbourChanged(DeviceContext<State> ctx) {
            if (burnsOut(family, ctx.state(), ctx.record().state().litTick(), ctx.now())) {
                services.effects().burnout(ctx.block());
            }
            return DeviceResult.pass();
        }
    }
}
