/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ScrapingBlock.java @ c3b4d9536 (useItemOn: wax, dye, knife),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ScrapingBlockEntity.java @ c3b4d9536 (onClicked: the result and
 * extra drop when complete; isItemValid; the one-slot inventory, dropped on removal),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (the item that is put on the new block)
 */
package dev.tfp.paper.scraping;

import dev.tfp.core.scraping.ScrapingAim;
import dev.tfp.core.scraping.ScrapingState;
import dev.tfp.core.scraping.ScrapingVisuals;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The scraping surface as a device kind (PAR-PROC-05): TFC's {@code ScrapingBlock} + {@code ScrapingBlockEntity} on the
 * device framework (PAR-INFRA-02). One slot (the hide or paper), dropped when the block goes ({@code DROP}, as TFC's
 * {@code InventoryRemoveBehavior.DROP}); no lazy simulation.
 *
 * <ul>
 *   <li>{@link DeviceHandler#placed}: {@link ScrapingListener} places the block on a log and hands over one item, which goes
 *       into the slot; the look of its recipe is kept in the state (TFC {@code updateDisplayCache}).</li>
 *   <li>{@link DeviceHandler#useWithItem}, in TFC's order: a scraping wax on an unwaxed surface waxes it; on a waxed surface a
 *       dye colours it (twice at most); on an unwaxed one a knife scrapes the cell under the look ray. A knife on a scraped
 *       cell is consumed without effect (TFC {@code FAIL}). The 16th cell turns the item into the recipe's result and
 *       spawns the extra drop.</li>
 *   <li>{@link DeviceHandler#removed}: the drawn surface goes with the block.</li>
 * </ul>
 */
public final class ScrapingDevice {
    /** The block id of the scraping surface. */
    public static final String ID = "tfp:scraping";
    /** The one slot. */
    public static final int SLOT = 0;
    /** TFC {@code TFCTags.Items.SCRAPING_WAXES}. */
    public static final String WAXES = "tfc:scraping_waxes";
    /** TFC {@code TFCTags.Items.TOOLS_KNIFE} ({@code c:tools/knife}). */
    public static final String KNIVES = "c:tools/knife";
    /** TFC {@code TFCTags.Blocks.SCRAPING_SURFACE}. */
    public static final String SURFACE = "tfc:scraping_surface";

    /**
     * One scraping recipe as the device needs it.
     *
     * @param inputTexture the recipe's input texture (names the look, {@link ScrapingVisuals#itemModel})
     * @param result       TFC {@code assemble(input)}: the result for the scraped item
     * @param extraDrop    TFC {@code getExtraDrop().getSingleStack(input)}: the extra drop, empty for none
     */
    public record Recipe(String inputTexture, UnaryOperator<ItemStack> result, UnaryOperator<ItemStack> extraDrop) {
        public Recipe {
            Objects.requireNonNull(inputTexture, "inputTexture");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(extraDrop, "extraDrop");
        }
    }

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param recipes the scraping recipe of an item (TFC {@code ScrapingRecipe.getRecipe}), any thread
     * @param wax     whether an item is a scraping wax
     * @param knife   whether an item is a knife
     * @param dye     the vanilla dye colour id of an item (TFC {@code DyeColor.getColor(stack)}), empty for none
     * @param effects the world effects
     */
    public record Services(Function<ItemStack, Optional<Recipe>> recipes, Predicate<ItemStack> wax, Predicate<ItemStack> knife,
            Function<ItemStack, OptionalInt> dye, ScrapingEffects effects) {
        public Services {
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(wax, "wax");
            Objects.requireNonNull(knife, "knife");
            Objects.requireNonNull(dye, "dye");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private ScrapingDevice() {
    }

    /** The device kind of {@link #ID}. */
    public static DeviceKind<ScrapingState> kind(Services services) {
        return DeviceKind.builder(ID, ScrapingState.CODEC, ScrapingState.FRESH)
            .slots(1)
            .filter((slot, stack) -> services.recipes().apply(stack).isPresent()) // TFC isItemValid
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** The cell the player's look ray points at on the surface at {@code block}; empty for a ray parallel to it. */
    public static OptionalInt aimedCell(Player player, Block block) {
        return aimedPoint(player, block).map(p -> OptionalInt.of(ScrapingAim.cell(p))).orElse(OptionalInt.empty());
    }

    static Optional<ScrapingAim.Point> aimedPoint(Player player, Block block) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        return ScrapingAim.point(look.getX(), look.getY(), look.getZ(),
            eye.getX() - block.getX(), eye.getY() - block.getY(), eye.getZ() - block.getZ());
    }

    /** TFC's {@code if (!player.isCreative()) stack.shrink(1)} on the stack in {@code hand}. */
    static void shrink(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held)) {
            return;
        }
        held = held.clone();
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
    }

    private record Handler(Services services) implements DeviceHandler<ScrapingState> {
        @Override
        public DeviceResult<ScrapingState> placed(DeviceContext<ScrapingState> ctx) {
            ItemStack item = ctx.item();
            if (StackCodec.isEmpty(item) || !StackCodec.isEmpty(ctx.inventory().get(SLOT))) {
                return DeviceResult.pass();
            }
            Optional<Recipe> recipe = services.recipes().apply(item);
            if (recipe.isEmpty()) {
                return DeviceResult.pass();
            }
            ItemStack one = item.clone();
            one.setAmount(1);
            ScrapingState state = ctx.deviceState().withLook(ScrapingVisuals.itemModel(recipe.get().inputTexture()));
            services.effects().show(ctx.block(), state);
            return DeviceResult.update(ctx.inventory().set(SLOT, one).record().withState(state));
        }

        @Override
        public DeviceResult<ScrapingState> useWithItem(DeviceContext<ScrapingState> ctx) {
            ItemStack stack = ctx.item();
            Player player = ctx.player();
            if (StackCodec.isEmpty(stack) || player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            ScrapingState state = ctx.deviceState();
            if (!state.waxed() && services.wax().test(stack)) {
                shrink(player, ctx.hand());
                return DeviceResult.consume(ctx.record().withState(state.waxedState()));
            }
            Optional<ScrapingAim.Point> point = aimedPoint(player, ctx.block());
            if (point.isEmpty()) {
                return DeviceResult.pass();
            }
            double x = ScrapingAim.clamp(point.get().x());
            double z = ScrapingAim.clamp(point.get().z());
            ItemStack contents = ctx.inventory().get(SLOT);
            if (state.waxed()) {
                OptionalInt color = services.dye().apply(stack);
                if (color.isPresent()) {
                    Optional<ScrapingState> dyed = state.dye(color.getAsInt());
                    if (dyed.isPresent()) {
                        services.effects().particles(ctx.block(), x, z, contents);
                        shrink(player, ctx.hand());
                        services.effects().show(ctx.block(), dyed.get());
                        return DeviceResult.consume(ctx.record().withState(dyed.get()));
                    }
                }
                return DeviceResult.pass();
            }
            if (!services.knife().test(stack)) {
                return DeviceResult.pass();
            }
            ScrapingState.Scraped scraped = state.scrape(x, z);
            if (!scraped.step().changed()) {
                return DeviceResult.consume(); // TFC ItemInteractionResult.FAIL: handled, nothing happens
            }
            var record = ctx.record().withState(scraped.state());
            if (scraped.step().complete() && !StackCodec.isEmpty(contents)) {
                Optional<Recipe> recipe = services.recipes().apply(contents);
                if (recipe.isPresent()) {
                    ItemStack extra = recipe.get().extraDrop().apply(contents.clone());
                    if (!StackCodec.isEmpty(extra)) {
                        services.effects().spawn(ctx.block(), extra);
                    }
                    ItemStack result = recipe.get().result().apply(contents.clone());
                    record = ctx.inventory().set(SLOT, StackCodec.isEmpty(result) ? ItemStack.empty() : result).record()
                        .withState(scraped.state());
                }
            }
            services.effects().damageTool(player, ctx.hand());
            // TFC doParticles after onClicked: the particles show the slot as it is now (the result once complete).
            services.effects().particles(ctx.block(), x, z, new DeviceInventory<>(ctx.kind(), record, ctx.stacks()).get(SLOT));
            services.effects().show(ctx.block(), scraped.state());
            return DeviceResult.consume(record);
        }

        @Override
        public void removed(DeviceContext<ScrapingState> ctx) {
            services.effects().hide(ctx.block());
        }
    }
}
