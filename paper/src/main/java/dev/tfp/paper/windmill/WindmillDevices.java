/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java @ c3b4d9536 (useItemOn, InventoryRemoveBehavior.DROP),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (updateState,
 *       isItemValid, getSlotStackLimit), src/main/java/net/dries007/tfc/common/items/WindmillBladeItem.java @ c3b4d9536 (useOn)
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.power.ShaftAxis;
import dev.tfp.core.power.Windmill;
import dev.tfp.core.windmill.WindmillBlades;
import dev.tfp.core.windmill.WindmillSample;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The windmill's device kind (five one-blade slots, dropped when it breaks) and the blade item's use on an axle. Callbacks run
 * on the block's region thread; a change of the windmill's own block (the {@code count}, or back to its axle) is written on
 * the next tick through {@link World}.
 */
public final class WindmillDevices {
    /** The world side. Region thread of the block. */
    public interface World {
        /** Writes {@code next} over the windmill's own state on the next tick if it still holds {@code expected}. */
        void setState(Block block, BlockStateRef expected, BlockStateRef next);

        /** Replaces the block on the next tick (the windmill with its axle) if it still holds {@code expected}. */
        void replace(Block block, BlockStateRef expected, BlockStateRef next);

        /** Places {@code state} now (the axle becomes a windmill); false when it could not. */
        boolean place(Block block, BlockStateRef state);

        /** Puts {@code blade} into slot 0 of the windmill just placed at {@code block}. */
        void insertFirstBlade(Block block, ItemStack blade);

        /** TFC {@code displayClientMessage(..., true)}: the action bar line of a message key. */
        void tell(Player player, String key);
    }

    private final WindmillService service;
    private final WindmillChunkIndex index;
    private final CustomBlocks blocks;
    private final Predicate<ItemStack> blades;
    private final World world;
    private final DeviceKind<WindmillSample> kind;

    public WindmillDevices(WindmillService service, WindmillChunkIndex index, CustomBlocks blocks, Predicate<ItemStack> blades,
            World world) {
        this.service = Objects.requireNonNull(service, "service");
        this.index = Objects.requireNonNull(index, "index");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.blades = Objects.requireNonNull(blades, "blades");
        this.world = Objects.requireNonNull(world, "world");
        this.kind = DeviceKind.builder(WindmillBlocks.KIND, WindmillBlocks.CODEC, WindmillSample.FRESH)
            .slots(Windmill.SLOTS)
            .slotLimit(1) // TFC getSlotStackLimit
            .filter((slot, stack) -> blades.test(stack)) // TFC isItemValid: #tfc:windmill_blades
            .removal(DeviceKind.RemoveBehaviour.DROP) // TFC InventoryRemoveBehavior.DROP
            .handler(new Handler())
            .build();
    }

    public DeviceKind<WindmillSample> kind() {
        return kind;
    }

    /** TFC {@code player.isCreative()} / {@code getAbilities().instabuild}. */
    static boolean creative(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    /** TFC {@code updateState}: the number of filled slots. */
    static int filled(DeviceRecord<?> record) {
        List<Boolean> filled = new ArrayList<>();
        for (int i = 0; i < Windmill.SLOTS; i++) {
            filled.add(!record.slot(i).isEmpty());
        }
        return WindmillBlades.count(filled);
    }

    private final class Handler implements DeviceHandler<WindmillSample> {
        @Override
        public DeviceResult<WindmillSample> placed(DeviceContext<WindmillSample> ctx) {
            index.track(ctx.block());
            service.start(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<WindmillSample> useWithItem(DeviceContext<WindmillSample> ctx) {
            Player player = ctx.player();
            EquipmentSlot hand = ctx.hand();
            if (player == null || hand == null) {
                return DeviceResult.pass();
            }
            ItemStack held = player.getInventory().getItem(hand);
            boolean empty = StackCodec.isEmpty(held);
            int count = WindmillBlocks.count(ctx.state());
            WindmillBlades.Click click = WindmillBlades.click(count, empty, !empty && blades.test(held));
            DeviceInventory<WindmillSample> inventory = ctx.inventory();
            switch (click) {
                case INSERT -> {
                    ItemStack one = held.clone();
                    one.setAmount(1);
                    DeviceInventory.Insert<WindmillSample> insert = inventory.insert(one);
                    if (!StackCodec.isEmpty(insert.remainder())) {
                        return DeviceResult.pass(); // TFC: the leftover goes back, PASS_TO_DEFAULT_BLOCK_INTERACTION
                    }
                    if (!creative(player)) {
                        player.getInventory().setItem(hand, held.getAmount() > 1 ? held.subtract(1) : null);
                    }
                    inventory = insert.inventory();
                }
                case EXTRACT -> {
                    DeviceInventory.Extract<WindmillSample> extract = inventory.extract(WindmillBlades.extractSlot(count), 1);
                    if (!creative(player) && !StackCodec.isEmpty(extract.extracted())) {
                        give(player, extract.extracted());
                    }
                    inventory = extract.inventory();
                }
                default -> {
                    return DeviceResult.pass();
                }
            }
            DeviceRecord<WindmillSample> next = inventory.record();
            update(ctx.block(), ctx.state(), filled(next));
            return DeviceResult.consume(next);
        }

        @Override
        public void removed(DeviceContext<WindmillSample> ctx) {
            index.untrack(ctx.block());
            service.stop(ctx.block().getWorld().getUID(), WindmillService.pos(ctx.block()));
        }
    }

    /** TFC {@code updateState}: no blade turns the windmill back into its axle, otherwise the count follows the blades. */
    void update(Block block, BlockStateRef state, int count) {
        if (count == 0) {
            world.replace(block, state, WindmillBlocks.axleOf(state));
        } else if (count != WindmillBlocks.count(state)) {
            world.setState(block, state, state.with(WindmillBlocks.COUNT, Integer.toString(count)));
        }
    }

    /** TFC {@code ItemHandlerHelper.giveItemToPlayer}: into the inventory, the rest at the player's feet. */
    static void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack.clone()).values()
            .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /**
     * TFC {@code WindmillBladeItem.useOn}: a blade used on an axle that is not vertical turns it into a windmill of the axle's
     * wood with that blade, unless the disk around it holds a solid block. Region thread of the block (the clicking player's).
     *
     * @return whether the click was used (the event is then cancelled)
     */
    public boolean useBladeOn(Block block, Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (StackCodec.isEmpty(held) || !blades.test(held)) {
            return false;
        }
        Optional<BlockStateRef> state = blocks.stateAt(block).filter(s -> WindmillBlocks.isAxle(s.id()));
        if (state.isEmpty()) {
            return false;
        }
        String axis = state.get().properties().get(WindmillBlocks.AXIS);
        boolean obstructed = false;
        if ("x".equals(axis) || "z".equals(axis)) {
            // an unreadable cell counts as solid here: the answer is needed now
            ShaftAxis a = "x".equals(axis) ? ShaftAxis.X : ShaftAxis.Z;
            boolean[] disk = WindmillService.disk(block, a, b -> Optional.of(service.solid(b).orElse(true)));
            obstructed = Boolean.TRUE.equals(WindmillService.obstructed(disk));
        }
        switch (WindmillBlades.convert(axis, obstructed)) {
            case NOT_ENOUGH_SPACE -> {
                world.tell(player, WindmillBlades.NOT_ENOUGH_SPACE);
                return true; // TFC InteractionResult.FAIL
            }
            case CONVERT -> {
                if (!world.place(block, WindmillBlocks.windmillOf(state.get()))) {
                    return false;
                }
                ItemStack one = held.clone();
                one.setAmount(1);
                world.insertFirstBlade(block, one);
                if (!creative(player)) {
                    player.getInventory().setItem(hand, held.getAmount() > 1 ? held.subtract(1) : null);
                }
                index.track(block);
                service.start(block);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** The blade item's right click on an axle. */
    public final class BladeUse implements Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent event) {
            if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
                return;
            }
            if (useBladeOn(event.getClickedBlock(), event.getPlayer(), event.getHand())) {
                event.setCancelled(true);
            }
        }
    }
}
