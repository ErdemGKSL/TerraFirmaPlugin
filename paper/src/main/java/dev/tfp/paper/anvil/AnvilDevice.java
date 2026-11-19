/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (interactWithAnvil: the sneaking
 * branches, InventoryRemoveBehavior.DROP),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockAnvilBlock.java @ c3b4d9536 (useItemOn, DROP),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (SLOT_*, isItemValid,
 * getSlotStackLimit)
 */
package dev.tfp.paper.anvil;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.AnvilTiers;
import dev.tfp.core.anvil.AnvilVisuals;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The anvils as a device kind (PAR-METAL-03): TFC's {@code AnvilBlock} and {@code RockAnvilBlock} with the
 * {@code AnvilBlockEntity} inventory on the device framework (PAR-INFRA-02). Four slots (the two inputs and the hammer hold
 * 1, the flux 64; the hammer slot takes only {@code c:tools/hammer}, the flux slot only {@code tfc:welding_flux}), dropped
 * with the block ({@code DROP}). One kind per block family ({@link #METAL_ID}, {@link #ROCK_ID}); both share this handler.
 *
 * <p>{@link DeviceHandler#useWithItem} is TFC's {@code interactWithAnvil}, sneaking branch (TFC allows the block's use while
 * sneaking, so {@link AnvilListener} routes the clicks CraftEngine skips): an empty hand takes the first non-empty input into
 * the hand; a hammer would weld (PAR-METAL-06, not here: the click passes); any other stack goes into the first of the flux,
 * main input and second input that takes some of it. The click without sneaking opens the anvil screen (PAR-METAL-04/05) and
 * passes here. Every change redraws the displays.
 */
public final class AnvilDevice {
    public static final String METAL_ID = "tfp:metal/anvil";
    public static final String ROCK_ID = "tfp:rock/anvil";
    /** TFC {@code TFCTags.Items.TOOLS_HAMMER} ({@code c:tools/hammer}). */
    public static final String HAMMERS = "c:tools/hammer";
    /** TFC {@code TFCTags.Items.WELDING_FLUX}. */
    public static final String FLUXES = "tfc:welding_flux";
    /** The block state property of a metal anvil's facing. */
    public static final String FACING = "facing";

    /**
     * What the device reads from the rest of the plugin.
     *
     * @param hammer whether an item is a hammer (TFC {@code TOOLS_HAMMER})
     * @param flux   whether an item is welding flux (TFC {@code WELDING_FLUX})
     * @param effects the world effects
     */
    public record Services(Predicate<ItemStack> hammer, Predicate<ItemStack> flux, AnvilEffects effects) {
        public Services {
            Objects.requireNonNull(hammer, "hammer");
            Objects.requireNonNull(flux, "flux");
            Objects.requireNonNull(effects, "effects");
        }
    }

    private AnvilDevice() {
    }

    /** The device kind of {@code id} ({@link #METAL_ID} or {@link #ROCK_ID}). */
    public static DeviceKind<AnvilState> kind(String id, Services services) {
        return DeviceKind.builder(id, AnvilState.CODEC, AnvilState.FRESH)
            .slots(AnvilSlots.COUNT)
            .slotLimit(AnvilSlots::limit) // TFC getSlotStackLimit
            .filter((slot, stack) -> switch (slot) { // TFC isItemValid
                case AnvilSlots.INPUT_MAIN, AnvilSlots.INPUT_SECOND -> true;
                case AnvilSlots.HAMMER -> services.hammer().test(stack);
                case AnvilSlots.CATALYST -> services.flux().test(stack);
                default -> false;
            })
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler(services))
            .build();
    }

    /** What the displays of {@code ctx}'s anvil show for {@code record}'s slots. */
    static AnvilEffects.View view(DeviceContext<AnvilState> ctx, DeviceRecord<AnvilState> record) {
        DeviceInventory<AnvilState> inv = new DeviceInventory<>(ctx.kind(), record, ctx.stacks());
        int tier = AnvilTiers.tier(ctx.state().id()).orElse(0);
        int rotation = AnvilVisuals.rotation(ctx.state().properties().getOrDefault(FACING, ""));
        return new AnvilEffects.View(tier, rotation, inv.get(AnvilSlots.HAMMER), inv.get(AnvilSlots.INPUT_MAIN),
            inv.get(AnvilSlots.INPUT_SECOND), inv.get(AnvilSlots.CATALYST));
    }

    private record Handler(Services services) implements DeviceHandler<AnvilState> {
        @Override
        public DeviceResult<AnvilState> useWithItem(DeviceContext<AnvilState> ctx) {
            Player player = ctx.player();
            EquipmentSlot hand = ctx.hand();
            if (player == null || hand == null || !player.isSneaking()) {
                return DeviceResult.pass(); // the screen (PAR-METAL-04, PAR-METAL-05)
            }
            ItemStack held = ctx.item();
            DeviceInventory<AnvilState> inv = ctx.inventory();
            if (StackCodec.isEmpty(held)) {
                // TFC: extraction requires the held item to be empty; one item of the first non-empty input
                for (int slot : AnvilSlots.BY_HAND_EXTRACT) {
                    if (!StackCodec.isEmpty(inv.get(slot))) {
                        DeviceInventory.Extract<AnvilState> taken = inv.extract(slot, 1);
                        player.getInventory().setItem(hand, taken.extracted());
                        return finish(ctx, taken.inventory().record());
                    }
                }
                return DeviceResult.pass();
            }
            if (services.hammer().test(held)) {
                return DeviceResult.pass(); // welding is PAR-METAL-06
            }
            for (int slot : AnvilSlots.BY_HAND_INSERT) {
                DeviceInventory.Insert<AnvilState> in = inv.insert(slot, held);
                if (held.getAmount() > (StackCodec.isEmpty(in.remainder()) ? 0 : in.remainder().getAmount())) {
                    player.getInventory().setItem(hand, StackCodec.isEmpty(in.remainder()) ? null : in.remainder());
                    return finish(ctx, in.inventory().record());
                }
            }
            return DeviceResult.pass();
        }

        private DeviceResult<AnvilState> finish(DeviceContext<AnvilState> ctx, DeviceRecord<AnvilState> next) {
            services.effects().show(ctx.block(), view(ctx, next));
            return DeviceResult.consume(next);
        }

        @Override
        public void removed(DeviceContext<AnvilState> ctx) {
            services.effects().hide(ctx.block());
        }
    }
}
