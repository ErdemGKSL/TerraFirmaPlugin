/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/BowlBlock.java @ c3b4d9536 (useItemOn, InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/common/blockentities/BowlBlockEntity.java @ c3b4d9536 (one slot of 16, powders only)
 */
package dev.tfp.paper.bowl;

import dev.tfp.core.bowl.BowlRules;
import dev.tfp.core.bowl.BowlRules.Action;
import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomItems;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-09: the wooden and the ceramic bowl as device kinds ({@code tfp:wooden_bowl}, {@code tfp:ceramic/bowl}), TFC's
 * {@code BowlBlock} + {@code BowlBlockEntity}: one slot of up to {@value BowlRules#MAX_POWDER} items of the
 * {@code tfc:bowl_powders} tag, dropped with the bowl ({@code InventoryRemoveBehavior.DROP}). A right click does what
 * {@link BowlRules#decide} says. The bowl keeps no state besides the slot, so the kind's state is a constant.
 */
public final class BowlDevices {
    public static final String CERAMIC = "tfp:ceramic/bowl";
    public static final String WOODEN = "tfp:wooden_bowl";
    /** TFC {@code TFCTags.Items.BOWL_POWDERS}. */
    public static final String POWDERS_TAG = "tfc:bowl_powders";
    /** TFC {@code TFCTags.Items.CAN_BE_SALTED}. */
    public static final String SALTABLE_TAG = "tfc:foods/can_be_salted";

    static final VersionedCodec<Boolean> CODEC = VersionedCodec.<Boolean>builder("bowl", 1)
        .build((v, out) -> out.putInt("n", 0), in -> Boolean.TRUE);

    private final BiPredicate<ItemStack, String> tags;
    private final CustomItems items;
    private final BowlEffects effects;
    private final BowlFood food;
    private final BowlGlass glass;

    /** @param tags whether a stack is in an item tag ({@code Tags::is}) */
    public BowlDevices(BiPredicate<ItemStack, String> tags, CustomItems items, BowlEffects effects, BowlFood food, BowlGlass glass) {
        this.tags = Objects.requireNonNull(tags, "tags");
        this.items = Objects.requireNonNull(items, "items");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.food = Objects.requireNonNull(food, "food");
        this.glass = Objects.requireNonNull(glass, "glass");
    }

    public List<DeviceKind<Boolean>> kinds() {
        return List.of(kind(CERAMIC), kind(WOODEN));
    }

    private DeviceKind<Boolean> kind(String id) {
        return DeviceKind.builder(id, CODEC, Boolean.TRUE)
            .slots(1)
            .slotLimit(BowlRules.MAX_POWDER)
            .filter((slot, stack) -> tags.test(stack, POWDERS_TAG))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    /** The item id of {@code stack}, {@code tfp:} for TFC's own items. */
    String idOf(ItemStack stack) {
        return items.idOf(stack).orElseGet(() -> stack.getType().getKey().toString());
    }

    private final class Handler implements DeviceHandler<Boolean> {
        @Override
        public DeviceResult<Boolean> useWithItem(DeviceContext<Boolean> ctx) {
            Player player = ctx.player();
            if (player == null) {
                return DeviceResult.pass();
            }
            ItemStack held = ctx.item();
            boolean heldEmpty = StackCodec.isEmpty(held);
            DeviceInventory<Boolean> inv = ctx.inventory();
            ItemStack current = inv.get(0);
            boolean contentEmpty = StackCodec.isEmpty(current);
            String contentId = contentEmpty ? "" : idOf(current);
            GlassOperation op = contentEmpty ? null
                : GlassOperation.byPowder(contentId.replaceFirst("^tfp:", "tfc:")).orElse(null);
            BowlRules.Situation s = new BowlRules.Situation(ctx.hand() == EquipmentSlot.HAND, player.isSneaking(), heldEmpty,
                !heldEmpty && glass.working(held), !heldEmpty && tags.test(held, POWDERS_TAG),
                !heldEmpty && tags.test(held, SALTABLE_TAG) && food.canSalt(held), contentEmpty,
                contentId.equals(BowlRules.SALT), op != null);
            Action action = BowlRules.decide(s);
            DeviceInventory<Boolean> next;
            switch (action) {
                case Action.ApplyGlass a -> {
                    if (!glass.apply(held, op.id())) {
                        return DeviceResult.pass();
                    }
                    next = inv.extract(0, 1).inventory();
                    player.setCooldown(held, BowlRules.GLASS_COOLDOWN_TICKS);
                }
                case Action.Take t -> {
                    DeviceInventory.Extract<Boolean> e = inv.extract(0, t.count());
                    give(player, e.extracted());
                    next = e.inventory();
                }
                case Action.Insert i -> {
                    DeviceInventory.Insert<Boolean> in = inv.insert(0, held);
                    next = in.inventory();
                    setHand(player, ctx.hand(), in.remainder());
                }
                case Action.Salt sa -> {
                    int n = BowlRules.toSalt(held.getAmount(), current.getAmount());
                    ItemStack salted = held.clone();
                    salted.setAmount(n);
                    food.salt(salted);
                    ItemStack rest = held.clone();
                    rest.setAmount(held.getAmount() - n);
                    setHand(player, ctx.hand(), rest);
                    give(player, salted);
                    next = inv.extract(0, n).inventory();
                }
                case Action.Pass p -> {
                    return DeviceResult.pass();
                }
            }
            effects.show(ctx.block(), next.get(0));
            effects.sand(ctx.block());
            return DeviceResult.consume(next.record());
        }

        @Override
        public void removed(DeviceContext<Boolean> ctx) {
            effects.hide(ctx.block());
        }
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest at the player's feet. */
    static void give(Player player, ItemStack stack) {
        if (StackCodec.isEmpty(stack)) {
            return;
        }
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private static void setHand(Player player, EquipmentSlot hand, ItemStack stack) {
        player.getInventory().setItem(hand, StackCodec.isEmpty(stack) || stack.getAmount() <= 0 ? null : stack);
    }

    /** Whether {@code id} is one of the two bowl blocks. */
    public static boolean isBowl(String id) {
        return id.equals(CERAMIC) || id.equals(WOODEN);
    }
}
