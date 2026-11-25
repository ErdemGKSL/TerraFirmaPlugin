/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TripHammerBlock.java @ c3b4d9536 (useItemOn: one item of the trip hammers
 *       tag into the empty slot, shrunk unless creative; DeviceBlock InventoryRemoveBehavior.DROP; getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/TripHammerBlockEntity.java @ c3b4d9536 (one slot, stack
 *       limit 1, isItemValid: TFCTags.Items.TRIP_HAMMERS)
 */
package dev.tfp.paper.triphammer;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The trip hammer's device kind: one slot holding one hammer (TFC's {@code trip_hammers} tag), filled by a right click with
 * one; the hammer drops with the block. Placing it starts its {@link TripHammerService} task and lists it in its chunk; removing
 * it stops both. Its facing is TFC's placement facing (toward the player), written one tick after the place event
 * ({@link Placement}). Callbacks run on the block's region thread.
 */
public final class TripHammerDevice {
    /** The hammer holds no state of its own besides its slot (TFC's cooldown and angle live in the service). */
    public record Nothing() {
        public static final Nothing INSTANCE = new Nothing();
        public static final VersionedCodec<Nothing> CODEC = VersionedCodec.<Nothing>builder("trip_hammer", 1)
            .build((v, out) -> { }, in -> INSTANCE);
    }

    private final TripHammerService service;
    private final TripHammerChunkIndex index;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Predicate<ItemStack> tripHammers;
    private final BiConsumer<Block, BlockStateRef> writer;

    /**
     * @param tripHammers TFC {@code isItemValid}: the item is in {@link TripHammerBlocks#TRIP_HAMMERS}
     * @param writer      writes the hammer's own new state in place, firing no callback (region thread)
     */
    public TripHammerDevice(TripHammerService service, TripHammerChunkIndex index, CustomBlocks blocks, Scheduler scheduler,
            Predicate<ItemStack> tripHammers, BiConsumer<Block, BlockStateRef> writer) {
        this.service = Objects.requireNonNull(service, "service");
        this.index = Objects.requireNonNull(index, "index");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tripHammers = Objects.requireNonNull(tripHammers, "tripHammers");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public DeviceKind<Nothing> kind() {
        return DeviceKind.builder(TripHammerBlocks.ID, Nothing.CODEC, Nothing.INSTANCE)
            .slots(1)
            .slotLimit(1)
            .filter((slot, stack) -> !StackCodec.isEmpty(stack) && tripHammers.test(stack))
            .removal(DeviceKind.RemoveBehaviour.DROP)
            .handler(new Handler())
            .build();
    }

    private final class Handler implements DeviceHandler<Nothing> {
        @Override
        public DeviceResult<Nothing> placed(DeviceContext<Nothing> ctx) {
            index.track(ctx.block());
            service.start(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<Nothing> useWithItem(DeviceContext<Nothing> ctx) {
            Player player = ctx.player();
            if (player == null || ctx.hand() == null) {
                return DeviceResult.pass();
            }
            // a hammer placed without a player (or before a restart of its chunk) starts at its first use
            index.track(ctx.block());
            service.start(ctx.block());
            ItemStack held = player.getInventory().getItem(ctx.hand());
            DeviceInventory<Nothing> inv = ctx.inventory();
            if (!StackCodec.isEmpty(inv.get(0)) || StackCodec.isEmpty(held) || !inv.canInsert(0, held)) {
                return DeviceResult.pass(); // TFC PASS_TO_DEFAULT_BLOCK_INTERACTION
            }
            ItemStack one = held.clone();
            one.setAmount(1);
            DeviceInventory<Nothing> next = inv.set(0, one);
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().setItem(ctx.hand(), held.getAmount() > 1 ? held.subtract(1) : null);
            }
            return DeviceResult.consume(next.record());
        }

        @Override
        public void removed(DeviceContext<Nothing> ctx) {
            index.untrack(ctx.block());
            service.stop(ctx.block().getWorld().getUID(), TripHammerService.pos(ctx.block()));
        }
    }

    /**
     * TFC {@code getStateForPlacement}: {@code facing = context.getHorizontalDirection().getOpposite()}. CraftEngine places the
     * default state; one tick after the place event a trip hammer takes the facing.
     */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block block = event.getBlockPlaced();
            Direction look = TripHammerBlocks.horizontal(event.getPlayer().getLocation().getYaw());
            scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> placed(block, look));
        }
    }

    /** Region thread of the block. */
    void placed(Block block, Direction look) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || TripHammerBlocks.facing(state.get()).isEmpty()) {
            return;
        }
        String facing = TripHammerBlocks.name(TripHammerBlocks.placementFacing(look));
        if (!facing.equals(state.get().properties().get(TripHammerBlocks.FACING))) {
            writer.accept(block, state.get().with(TripHammerBlocks.FACING, facing));
        }
    }
}
