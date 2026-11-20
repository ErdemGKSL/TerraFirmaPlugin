/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/TFCPumpkinBlock.java @ c3b4d9536 (useItemOn: carving),
 *       src/main/java/net/dries007/tfc/common/blockentities/DecayingBlockEntity.java @ c3b4d9536 (serverTick: a rotten stack turns the block rotten)
 */
package dev.tfp.paper.crop.wild;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.tags.Tags;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;

/**
 * The fruit blocks of the spreading crops {@code tfp:fruit/pumpkin}, {@code tfp:fruit/melon} and their rotten forms
 * {@code tfp:fruit/rotten_pumpkin}, {@code tfp:fruit/rotten_melon} (PAR-FARM-05) as one device kind. TFC's {@code DecayingBlockEntity} holds a
 * food stack and every second checks whether it is rotten; here the record is the food state of that stack (creation date, wild trait) and
 * the block is checked when it is visited: placed, clicked, updated by a neighbour, and by the catch-up kind of the same id (chunk load,
 * visible-area sweep). A rotten fruit becomes the rotten block. A pumpkin cut with a knife or shears becomes a carved pumpkin.
 */
public final class FruitDevice {
    public static final String ID = "tfp:fruit";
    public static final String CATCH_UP_KIND = ID;
    public static final String ROTTEN_PREFIX = "rotten_";
    public static final String KNIVES = "c:tools/knife";
    public static final String SHEARS = "c:tools/shear";
    /** The food item whose decay modifier the fruit uses. */
    public static final String FOOD_PREFIX = "tfp:food/";

    /** The food state of the block's stack: creation date (a flag when rotten) and whether it is a worldgen (wild) fruit. */
    public record FruitState(long creationDate, boolean wild) {
    }

    static final VersionedCodec<FruitState> CODEC = VersionedCodec.<FruitState>builder("fruit", 1)
        .build((f, out) -> out.putLong("created", f.creationDate()).putBoolean("wild", f.wild()),
            in -> new FruitState(in.getLong("created", FoodDecay.ROTTEN_FLAG), in.getBoolean("wild", false)));

    public record Services(WildWorld world, Supplier<ClockSnapshot> clock, Supplier<FoodDefinitions> foods, Tags tags, FoodConfig config) {
    }

    private FruitDevice() {
    }

    public static String fruitId(CropType type) {
        return ID + "/" + type.serializedName();
    }

    public static String rottenId(CropType type) {
        return ID + "/" + ROTTEN_PREFIX + type.serializedName();
    }

    /** The crop (pumpkin or melon) of a fruit block id, rotten or not. */
    public static Optional<CropType> type(String blockId) {
        if (!blockId.startsWith(ID + "/")) {
            return Optional.empty();
        }
        String name = blockId.substring(ID.length() + 1);
        if (name.startsWith(ROTTEN_PREFIX)) {
            name = name.substring(ROTTEN_PREFIX.length());
        }
        try {
            CropType t = CropType.valueOf(name.toUpperCase(Locale.ROOT));
            return t.shape() == CropType.Shape.SPREADING ? Optional.of(t) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static boolean isRottenBlock(String blockId) {
        return blockId.startsWith(ID + "/" + ROTTEN_PREFIX);
    }

    public static DeviceKind<FruitState> kind(Services services) {
        return DeviceKind.builder(ID, CODEC, new FruitState(FoodDecay.ROTTEN_FLAG, false))
            .slots(0).removal(DeviceKind.RemoveBehaviour.NOOP).handler(new Handler(services)).build();
    }

    /** Whether the stored fruit is rotten at player tick {@code now}. */
    static boolean rotten(Services s, CropType type, FruitState state, long now) {
        float modifier = s.foods().get().decayModifier(FOOD_PREFIX + type.serializedName());
        return WildRules.rotten(state.creationDate(), state.wild(), modifier, s.config().decayModifier(), now);
    }

    /**
     * Visits one fruit block: a rotten one becomes the rotten block. Returns true while the block is a fresh fruit that stays listed.
     * Region thread of {@code block}.
     */
    public static boolean settle(Services s, Block block, BlockStateRef state, FruitState stored, long now) {
        Optional<CropType> type = type(state.id());
        if (type.isEmpty() || isRottenBlock(state.id())) {
            return false;
        }
        if (rotten(s, type.get(), stored, now)) {
            s.world().place(block, BlockStateRef.of(rottenId(type.get())), true);
            s.world().untrack(block);
            return false;
        }
        s.world().track(block, CATCH_UP_KIND);
        return true;
    }

    private record Handler(Services services) implements DeviceHandler<FruitState> {
        /** The block carries the food state of the placed item (TFC's decaying block takes the stack). */
        @Override
        public DeviceResult<FruitState> placed(DeviceContext<FruitState> ctx) {
            if (type(ctx.state().id()).isEmpty() || isRottenBlock(ctx.state().id())) {
                return DeviceResult.pass();
            }
            FruitState fresh = FoodPdc.read(ctx.item()).map(f -> new FruitState(f.creationDate(), f.hasTrait(dev.tfp.core.food.FoodTrait.WILD)))
                .orElseGet(() -> new FruitState(FoodDecay.roundedCreationDate(ctx.now(), services.config().stackTicks()), false));
            settle(services, ctx.block(), ctx.state(), fresh, ctx.now());
            return DeviceResult.update(ctx.record().withState(fresh));
        }

        @Override
        public DeviceResult<FruitState> neighbourChanged(DeviceContext<FruitState> ctx) {
            settle(services, ctx.block(), ctx.state(), ctx.deviceState(), ctx.now());
            return DeviceResult.pass();
        }

        /** TFC {@code TFCPumpkinBlock.useItemOn}: a knife or shears carve a fresh pumpkin into a carved pumpkin facing the player. */
        @Override
        public DeviceResult<FruitState> useWithItem(DeviceContext<FruitState> ctx) {
            Player player = ctx.player();
            Optional<CropType> type = type(ctx.state().id());
            if (player == null || type.isEmpty() || type.get() != CropType.PUMPKIN || isRottenBlock(ctx.state().id())
                    || !(services.tags().is(ctx.item(), KNIVES) || services.tags().is(ctx.item(), SHEARS))) {
                return DeviceResult.pass();
            }
            if (!settle(services, ctx.block(), ctx.state(), ctx.deviceState(), ctx.now())) {
                return DeviceResult.consume(); // it just rotted: the click goes nowhere
            }
            Block block = ctx.block();
            var hit = player.rayTraceBlocks(6);
            BlockFace face = hit == null ? null : hit.getHitBlockFace();
            BlockFace facing = face == null || face == BlockFace.UP || face == BlockFace.DOWN
                ? player.getFacing().getOppositeFace() : face;
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_PUMPKIN_CARVE, SoundCategory.BLOCKS, 1f, 1f);
            services.world().untrack(block);
            services.world().remove(block);
            block.setType(Material.CARVED_PUMPKIN, false);
            if (block.getBlockData() instanceof Directional d) {
                d.setFacing(facing);
                block.setBlockData(d, false);
            }
            if (player.getGameMode() != GameMode.CREATIVE && ctx.hand() != null) {
                player.damageItemStack(ctx.hand(), 1);
            }
            return DeviceResult.consume();
        }
    }
}
