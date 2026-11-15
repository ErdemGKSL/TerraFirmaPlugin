/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (onRandomTick, useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCandleCakeBlock.java @ c3b4d9536 (useItemOn, randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/JackOLanternBlock.java @ c3b4d9536 (randomTick, extinguish),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536 (useItemOn: the facing),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (CANDLE, DYED_CANDLE, CANDLE_CAKE,
 *       DYED_CANDLE_CAKES, JACK_O_LANTERN),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (candleTicks, jackOLanternTicks,
 *       enablePumpkinCarving),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, onFireStop: the candle,
 *       candle cake, carved pumpkin and jack-o'-lantern branches)
 */
package dev.tfp.paper.light.candle;

import dev.tfp.core.light.LightSourceRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * PAR-HEAT-11: the pure rules of TFC's candles, candle cakes and jack-o'-lanterns. No Bukkit types: the Paper side
 * ({@link CandleDevice}, {@link CandleService}) asks these.
 *
 * <p>All three hold TFC's {@code TickCounterBlockEntity}: the calendar tick they were lit at. TFC's random tick puts a
 * candle (or candle cake) out once {@code ticksSinceUpdate > candleTicks}, and turns a jack-o'-lantern back into the
 * vanilla carved pumpkin once {@code ticksSinceUpdate > jackOLanternTicks}. The answer only depends on the elapsed time,
 * so it is evaluated lazily here.
 */
public final class CandleRules {
    /** TFC {@code candleTicks} default: 240 in-game hours. */
    public static final int CANDLE_TICKS = LightSourceRules.DEFAULT_CANDLE_TICKS;
    /** TFC {@code jackOLanternTicks} default: 90 in-game hours. */
    public static final int JACK_O_LANTERN_TICKS = 108_000;
    /** TFC {@code enablePumpkinCarving} default. */
    public static final boolean PUMPKIN_CARVING = true;
    /** TFC {@code TickCounterBlockEntity.lastUpdateTick}'s initial value: never lit counts as burnt out. */
    public static final long NEVER_LIT = Integer.MIN_VALUE;

    public static final String CANDLE = "tfp:candle";
    public static final String CANDLE_CAKE = "tfp:candle_cake";
    public static final String JACK_O_LANTERN = "tfp:jack_o_lantern";
    /** TFC's dead jack-o'-lantern: vanilla's carved pumpkin. */
    public static final String CARVED_PUMPKIN = "minecraft:carved_pumpkin";
    /** TFC {@code TFCBlocks.CAKE} (PAR-DECO-12). */
    public static final String CAKE = "tfp:cake";
    /** TFC's pumpkin block (PAR-FARM-05), the one a knife carves. */
    public static final String PUMPKIN = "tfp:pumpkin";

    public static final String LIT = "lit";
    public static final String CANDLES = "candles";
    public static final String WATERLOGGED = "waterlogged";
    public static final String FACING = "facing";
    public static final String BITES = "bites";

    /** Vanilla {@code DyeColor} in its declaration order (TFC {@code Helpers.mapOf(DyeColor.class, ...)}). */
    public static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    /** The three kinds of block that burn out. */
    public enum Family {
        CANDLE(CandleRules.CANDLE), CANDLE_CAKE(CandleRules.CANDLE_CAKE), JACK_O_LANTERN(CandleRules.JACK_O_LANTERN);

        private final String id;

        Family(String id) {
            this.id = id;
        }

        /** The block id (the device kind id; the dyed ones are its sub-paths). */
        public String id() {
            return id;
        }

        /** The limit of this family's counter (the server config). */
        public int limit(int candleTicks, int jackTicks) {
            return this == JACK_O_LANTERN ? jackTicks : candleTicks;
        }
    }

    /** What a right click on a candle or candle cake does (TFC {@code useItemOn}). */
    public enum Use {
        /** Nothing here: the vanilla default (TFC {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}). */
        PASS,
        /** Vanilla {@code AbstractCandleBlock.extinguish}: lit becomes false, the counter is kept. */
        EXTINGUISH,
        /** The candle cake's {@code TFCCakeBlock.eatCake}: a bite of a fresh cake, the candle drops. */
        EAT
    }

    private CandleRules() {
    }

    /** Every candle block id: the plain one, then the 16 dyed ones in {@link #COLORS} order. */
    public static List<String> candles() {
        return withColors(CANDLE);
    }

    /** Every candle cake block id, in the order of {@link #candles()}. */
    public static List<String> candleCakes() {
        return withColors(CANDLE_CAKE);
    }

    private static List<String> withColors(String base) {
        List<String> ids = new ArrayList<>(1 + COLORS.size());
        ids.add(base);
        for (String c : COLORS) {
            ids.add(base + "/" + c);
        }
        return List.copyOf(ids);
    }

    /** The family of {@code blockId}, if it is one of ours. */
    public static Optional<Family> family(String blockId) {
        if (blockId == null) {
            return Optional.empty();
        }
        if (blockId.equals(JACK_O_LANTERN)) {
            return Optional.of(Family.JACK_O_LANTERN);
        }
        if (candles().contains(blockId)) {
            return Optional.of(Family.CANDLE);
        }
        if (candleCakes().contains(blockId)) {
            return Optional.of(Family.CANDLE_CAKE);
        }
        return Optional.empty();
    }

    /**
     * TFC {@code CandleBlockItem(..., candleCakeBlock)}: the candle cake a candle item makes on a fresh cake
     * ({@code tfp:candle} → {@code tfp:candle_cake}, {@code tfp:candle/red} → {@code tfp:candle_cake/red}).
     */
    public static Optional<String> candleCakeOf(String candleItem) {
        int i = candles().indexOf(candleItem);
        return i < 0 ? Optional.empty() : Optional.of(candleCakes().get(i));
    }

    /** The candle a candle cake was made with (its loot: TFC {@code candle_cake/<color>} drops that candle). */
    public static Optional<String> candleOf(String candleCake) {
        int i = candleCakes().indexOf(candleCake);
        return i < 0 ? Optional.empty() : Optional.of(candles().get(i));
    }

    /** TFC {@code getTicksSinceUpdate() > limit && limit > 0}. */
    public static boolean burntOut(long litTick, long now, int limit) {
        return LightSourceRules.burnedOut(now - litTick, limit);
    }

    /** The first calendar tick a source lit at {@code litTick} is burnt out at; {@link LightSourceRules#NEVER} without a limit. */
    public static long burnoutTick(long litTick, int limit) {
        if (limit <= 0) {
            return LightSourceRules.NEVER;
        }
        return litTick + limit + 1L;
    }

    /**
     * TFC's random tick, lazily: whether the block of {@code family} changes at {@code now}. A candle or candle cake
     * changes only while lit (TFC sets {@code LIT false}; on an unlit one that is no change); a jack-o'-lantern always
     * becomes the carved pumpkin.
     */
    public static boolean burnsOut(Family family, boolean lit, long litTick, long now, int candleTicks, int jackTicks) {
        if (family != Family.JACK_O_LANTERN && !lit) {
            return false;
        }
        return burntOut(litTick, now, family.limit(candleTicks, jackTicks));
    }

    /**
     * TFC {@code TFCCandleBlock.useItemOn} / {@code TFCCandleCakeBlock.useItemOn} (with vanilla {@code CandleBlock}'s
     * extinguish).
     *
     * @param family          candle or candle cake (a jack-o'-lantern has no use)
     * @param lit             the block's {@code lit}
     * @param mainFirestarter the player's main hand holds a firestarter (TFC's QoL: never put out what you just lit)
     * @param emptyHand       the clicking hand is empty
     * @param mayBuild        vanilla {@code player.getAbilities().mayBuild} (not adventure or spectator)
     * @param hitHeight       where the click hit the block, 0 (bottom) to 1 (top)
     */
    public static Use use(Family family, boolean lit, boolean mainFirestarter, boolean emptyHand, boolean mayBuild,
                          double hitHeight) {
        if (family == Family.JACK_O_LANTERN || mainFirestarter) {
            return Use.PASS;
        }
        if (family == Family.CANDLE) {
            // vanilla CandleBlock.useItemOn: stack.isEmpty() && mayBuild && lit
            return emptyHand && mayBuild && lit ? Use.EXTINGUISH : Use.PASS;
        }
        if (hitHeight > 0.5D && emptyHand && lit) {
            return Use.EXTINGUISH;
        }
        return Use.EAT;
    }

    /**
     * TFC {@code onFireStart}'s candle branch: lit at any strength unless waterlogged ({@code getFluidState().isEmpty()});
     * the carved pumpkin and jack-o'-lantern branch always lights.
     */
    public static boolean lights(Family family, boolean waterlogged) {
        return family == Family.JACK_O_LANTERN || !waterlogged;
    }

    /** TFC {@code onFireStop}: a lit candle or candle cake is put out; a jack-o'-lantern becomes the carved pumpkin. */
    public static boolean douses(Family family, boolean lit) {
        return family == Family.JACK_O_LANTERN || lit;
    }

    /** TFC {@code candleLight} / {@code litBlockEmission(3)} / {@code alwaysLit()} (15) per block. */
    public static int light(Family family, int candles, boolean lit) {
        return switch (family) {
            case CANDLE -> LightSourceRules.candleLight(candles, lit);
            case CANDLE_CAKE -> LightSourceRules.candleCakeLight(lit);
            case JACK_O_LANTERN -> 15;
        };
    }

    /**
     * TFC {@code TFCPumpkinBlock.useItemOn}'s carved facing: the clicked face, or on the top or bottom the face towards
     * the player ({@code player.getDirection().getOpposite()}).
     *
     * @param clickedFace  the clicked face ({@code north}, {@code up}, ...)
     * @param playerFacing the player's horizontal facing
     */
    public static String carvedFacing(String clickedFace, String playerFacing) {
        String face = clickedFace.toLowerCase(Locale.ROOT);
        if (!face.equals("up") && !face.equals("down")) {
            return face;
        }
        return switch (playerFacing.toLowerCase(Locale.ROOT)) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> throw new IllegalArgumentException("not a horizontal facing: " + playerFacing);
        };
    }

    /** TFC {@code CandleBlockItem.useOn}: a candle goes on a cake only while it has no bite taken. */
    public static boolean takesCandle(int bites) {
        return bites == 0;
    }
}
