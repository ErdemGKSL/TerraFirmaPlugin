/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/QuernBlock.java @ c3b4d9536 (useItemOn, insertOrExtract,
 * attemptGrind, InventoryRemoveBehavior.DROP),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (SLOT_HANDSTONE, SLOT_INPUT,
 * SLOT_OUTPUT, getSlotStackLimit, isItemValid, startGrinding, finishGrinding, serverTick: the handstone damage and the
 * sounds when a grind finishes),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (mergeInsertStack, damageItem)
 */
package dev.tfp.paper.quern;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.quern.QuernAim;
import dev.tfp.core.quern.QuernState;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The quern as a device kind (PAR-PROC-04): TFC's {@code QuernBlock} + {@code QuernBlockEntity} on the device framework
 * (PAR-INFRA-02). Three slots (handstone, input, output; the handstone slot holds 1 and only {@code tfc:quern_handstones},
 * the others 64), dropped with the block ({@code DROP}).
 *
 * <p>Right click ({@link DeviceHandler#useWithItem}), in TFC's order: while a grind runs only the output can be taken; else
 * the place under the look ray ({@link QuernAim}) decides: the handle grinds; the input slot takes the held stack or gives
 * the input back; the handstone takes or gives the handstone (empty hand and sneaking, or a handstone in hand), else grinds;
 * the base gives the output. A grind needs an input with a quern recipe and a handstone; it lasts 90 ticks.
 *
 * <p>Lazy: the grind's start tick is stored ({@link QuernState}); nothing ticks. When it is due, the next routed callback
 * finishes it first ({@link #settle}): the result goes into the output slot (what does not fit is spawned), one input is
 * used, the handstone takes one damage and may break. The visuals schedule a callback at the finish tick
 * ({@link QuernEffects#spin}, through {@link DeviceHandler#neighbourChanged}) so the grind finishes on time while the chunk
 * is loaded; after a restart it finishes on the next interaction.
 */
public final class QuernDevice {
    public static final String ID = "tfp:quern";
    public static final int SLOT_HANDSTONE = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOTS = 3;
    /** TFC {@code TFCTags.Items.QUERN_HANDSTONES}. */
    public static final String HANDSTONES = "tfc:quern_handstones";
    /** TFC {@code QuernBlock.HAS_HANDSTONE}. */
    public static final String HAS_HANDSTONE = "has_handstone";
    /** A finish later than this after its tick (a restart, an unloaded chunk) is silent. */
    static final long LATE_TICKS = 20;

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param recipe    TFC {@code QuernRecipe.getRecipe(input)} + {@code assemble}: the result for an input, empty when
     *                  it has no quern recipe; any thread
     * @param handstone whether a stack may go into the handstone slot
     * @param merge     TFC {@code FoodCapability.mergeItemStacks}' stack rule: whether the second stack merges onto the
     *                  first (same item, food dates close enough); the merged stack keeps {@link #mergedDate}'s date
     * @param mergedDate writes the merged food date into the first stack (TFC: the earlier creation date)
     * @param effects   the world effects
     */
    public record Services(Function<ItemStack, Optional<UnaryOperator<ItemStack>>> recipe, Predicate<ItemStack> handstone,
            BiPredicate<ItemStack, ItemStack> merge, java.util.function.BiConsumer<ItemStack, ItemStack> mergedDate,
            QuernEffects effects) {
        public Services {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(handstone, "handstone");
            Objects.requireNonNull(merge, "merge");
            Objects.requireNonNull(mergedDate, "mergedDate");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private QuernDevice() {
    }

    /** The device kind of {@link #ID}. */
    public static DeviceKind<QuernState> kind(Services services) {
        return DeviceKind.builder(ID, QuernState.CODEC, QuernState.FRESH)
            .slots(SLOTS)
            .slotLimit(slot -> slot == SLOT_HANDSTONE ? 1 : 64) // TFC getSlotStackLimit
            .filter((slot, stack) -> slot != SLOT_HANDSTONE || services.handstone().test(stack)) // TFC isItemValid
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** The place the player's look ray points at on the quern at {@code block}. */
    static QuernAim.Place aimed(Player player, Block block, QuernAim.Situation situation) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        return QuernAim.aim(eye.getX() - block.getX(), eye.getY() - block.getY(), eye.getZ() - block.getZ(),
            look.getX(), look.getY(), look.getZ(), situation);
    }

    /**
     * Finishes a due grind (TFC {@code serverTick} once {@code recipeTimer <= 0}: {@code finishGrinding}, the sound, the
     * handstone damage). Plays the effects unless the finish is late. Returns the record after it, or the same record.
     */
    static DeviceRecord<QuernState> settle(DeviceContext<QuernState> ctx, Services services) {
        QuernState state = ctx.deviceState();
        if (!state.due(ctx.now())) {
            return ctx.record();
        }
        boolean audible = ctx.now() - state.finishTick() <= LATE_TICKS;
        DeviceInventory<QuernState> inv = ctx.inventory();
        // finishGrinding: the result merged into the output (excess spawned), then one input used
        ItemStack input = inv.get(SLOT_INPUT);
        if (!StackCodec.isEmpty(input)) {
            Optional<UnaryOperator<ItemStack>> recipe = services.recipe().apply(input);
            if (recipe.isPresent()) {
                ItemStack result = recipe.get().apply(input.clone());
                if (!StackCodec.isEmpty(result)) {
                    ItemStack[] merged = mergeInsert(inv.get(SLOT_OUTPUT), result, services);
                    inv = inv.set(SLOT_OUTPUT, merged[0]);
                    if (!StackCodec.isEmpty(merged[1])) {
                        services.effects().spawn(ctx.block(), merged[1]);
                    }
                }
                ItemStack left = input.clone();
                left.setAmount(input.getAmount() - 1);
                inv = inv.set(SLOT_INPUT, left.getAmount() <= 0 ? ItemStack.empty() : left);
            }
        }
        // serverTick: the sound, then Helpers.damageItem on the handstone; a broken one plays its own sounds
        ItemStack handstone = inv.get(SLOT_HANDSTONE);
        boolean broke = false;
        if (!StackCodec.isEmpty(handstone)) {
            ItemStack damaged = services.effects().damage(handstone.clone());
            broke = StackCodec.isEmpty(damaged);
            inv = inv.set(SLOT_HANDSTONE, broke ? ItemStack.empty() : damaged);
        }
        if (audible) {
            services.effects().finished(ctx.block(), broke ? handstone : ItemStack.empty());
        }
        return inv.record().withState(state.idle());
    }

    /**
     * TFC {@code Helpers.mergeInsertStack(inventory, SLOT_OUTPUT, stack)}: the output content and the result merged (TFC
     * {@code FoodCapability.mergeItemStacks}: when they stack, as much of the result as fits under the max stack size goes
     * onto the content, which keeps the earlier creation date). Returns {merged content, remainder}.
     */
    static ItemStack[] mergeInsert(ItemStack existing, ItemStack result, Services services) {
        if (StackCodec.isEmpty(existing)) {
            int fit = Math.min(result.getAmount(), result.getMaxStackSize());
            ItemStack in = result.clone();
            in.setAmount(fit);
            ItemStack rest = result.clone();
            rest.setAmount(result.getAmount() - fit);
            return new ItemStack[] {in, rest.getAmount() <= 0 ? ItemStack.empty() : rest};
        }
        if (!services.merge().test(existing, result)) {
            return new ItemStack[] {existing, result};
        }
        int fit = Math.max(0, Math.min(result.getAmount(), existing.getMaxStackSize() - existing.getAmount()));
        ItemStack merged = existing.clone();
        if (fit > 0) {
            services.mergedDate().accept(merged, result);
        }
        merged.setAmount(existing.getAmount() + fit);
        ItemStack rest = result.clone();
        rest.setAmount(result.getAmount() - fit);
        return new ItemStack[] {merged, rest.getAmount() <= 0 ? ItemStack.empty() : rest};
    }

    private record Handler(Services services) implements DeviceHandler<QuernState> {
        @Override
        public DeviceResult<QuernState> useWithItem(DeviceContext<QuernState> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            DeviceRecord<QuernState> settled = settle(ctx, services);
            boolean changed = !settled.equals(ctx.record());
            DeviceInventory<QuernState> inv = new DeviceInventory<>(ctx.kind(), settled, ctx.stacks());
            ItemStack stack = ctx.item();
            QuernState state = settled.state();
            DeviceResult<QuernState> result;
            if (state.grinding(ctx.now())) {
                // TFC: "allow extracting while grinding"
                result = extract(ctx, inv, SLOT_OUTPUT);
            } else {
                boolean hasHandstone = !StackCodec.isEmpty(inv.get(SLOT_HANDSTONE));
                QuernAim.Situation situation = new QuernAim.Situation(hasHandstone, false, StackCodec.isEmpty(stack),
                    StackCodec.isEmpty(inv.get(SLOT_INPUT)), !StackCodec.isEmpty(stack) && services.handstone().test(stack));
                result = switch (aimed(player, ctx.block(), situation)) {
                    case HANDLE -> attemptGrind(ctx, inv);
                    case INPUT_SLOT -> insertOrExtract(ctx, inv, stack, SLOT_INPUT);
                    case HANDSTONE -> player.isSneaking() || situation.heldIsHandstone()
                        ? insertOrExtract(ctx, inv, stack, SLOT_HANDSTONE)
                        : attemptGrind(ctx, inv);
                    case BASE -> extract(ctx, inv, SLOT_OUTPUT);
                };
            }
            DeviceRecord<QuernState> next = result.updated().orElse(settled);
            if (changed || result.updated().isPresent()) {
                services.effects().show(ctx.block(), view(ctx, next));
            }
            if (result.consumed()) {
                return DeviceResult.consume(next);
            }
            return changed || result.updated().isPresent() ? DeviceResult.update(next) : DeviceResult.pass();
        }

        /** The finish callback the visuals schedule at the finish tick (and any real neighbour update): settle only. */
        @Override
        public DeviceResult<QuernState> neighbourChanged(DeviceContext<QuernState> ctx) {
            QuernState state = ctx.deviceState();
            if (state.started() && !state.due(ctx.now())) {
                // the calendar ran slower than the server (players left); come back at the finish tick
                services.effects().finishAt(ctx.block(), state.finishTick() - ctx.now());
                return DeviceResult.pass();
            }
            DeviceRecord<QuernState> settled = settle(ctx, services);
            if (settled.equals(ctx.record())) {
                return DeviceResult.pass();
            }
            services.effects().show(ctx.block(), view(ctx, settled));
            return DeviceResult.update(settled);
        }

        @Override
        public DeviceResult<QuernState> placed(DeviceContext<QuernState> ctx) {
            services.effects().show(ctx.block(), view(ctx, ctx.record()));
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<QuernState> ctx) {
            services.effects().hide(ctx.block());
        }

        /**
         * TFC {@code attemptGrind}: {@code startGrinding} (input with a recipe, a handstone) or FAIL. FAIL does not consume
         * the click, so vanilla goes on to the held item's own use ({@code stack.useOn}, e.g. placing a held block); that is
         * a pass here.
         */
        private DeviceResult<QuernState> attemptGrind(DeviceContext<QuernState> ctx, DeviceInventory<QuernState> inv) {
            ItemStack input = inv.get(SLOT_INPUT);
            if (StackCodec.isEmpty(input) || StackCodec.isEmpty(inv.get(SLOT_HANDSTONE))
                || services.recipe().apply(input).isEmpty()) {
                return DeviceResult.pass();
            }
            DeviceRecord<QuernState> next = inv.record().withState(inv.record().state().start(ctx.now()));
            services.effects().started(ctx.block(), input);
            services.effects().spin(ctx.block());
            return DeviceResult.consume(next);
        }

        /** TFC {@code insertOrExtract(..., stack, slot)}. */
        private DeviceResult<QuernState> insertOrExtract(DeviceContext<QuernState> ctx, DeviceInventory<QuernState> inv,
                ItemStack stack, int slot) {
            if (StackCodec.isEmpty(stack)) {
                return extract(ctx, inv, slot);
            }
            DeviceInventory.Insert<QuernState> r = inv.insert(slot, stack);
            ctx.player().getInventory().setItem(ctx.hand(), StackCodec.isEmpty(r.remainder()) ? null : r.remainder());
            return DeviceResult.consume(r.inventory().record());
        }

        /** TFC {@code insertOrExtract(..., ItemStack.EMPTY, slot)}: PASS on an empty slot; the handstone takes the input too. */
        private DeviceResult<QuernState> extract(DeviceContext<QuernState> ctx, DeviceInventory<QuernState> inv, int slot) {
            ItemStack content = inv.get(slot);
            if (StackCodec.isEmpty(content)) {
                return DeviceResult.pass();
            }
            DeviceInventory.Extract<QuernState> ex = inv.extract(slot, content.getAmount());
            give(ctx.player(), ex.extracted());
            DeviceInventory<QuernState> after = ex.inventory();
            if (slot == SLOT_HANDSTONE) {
                ItemStack input = after.get(SLOT_INPUT);
                if (!StackCodec.isEmpty(input)) {
                    DeviceInventory.Extract<QuernState> in = after.extract(SLOT_INPUT, input.getAmount());
                    give(ctx.player(), in.extracted());
                    after = in.inventory();
                }
            }
            return DeviceResult.consume(after.record());
        }

        private QuernEffects.View view(DeviceContext<QuernState> ctx, DeviceRecord<QuernState> record) {
            DeviceInventory<QuernState> inv = new DeviceInventory<>(ctx.kind(), record, ctx.stacks());
            return new QuernEffects.View(inv.get(SLOT_HANDSTONE), inv.get(SLOT_INPUT), inv.get(SLOT_OUTPUT),
                record.state().grinding(ctx.now()));
        }
    }

    /** NeoForge {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest dropped at the player. */
    static void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest, e -> e.setPickupDelay(0));
        }
    }

    /** The block state of {@code hasHandstone} (TFC {@code updateHandstone}). */
    public static dev.tfp.paper.integration.BlockStateRef state(boolean hasHandstone) {
        return dev.tfp.paper.integration.BlockStateRef.of(ID).with(HAS_HANDSTONE, Boolean.toString(hasHandstone));
    }
}
