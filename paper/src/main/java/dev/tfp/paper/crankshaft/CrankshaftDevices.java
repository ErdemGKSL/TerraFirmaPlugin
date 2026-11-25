/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/CrankshaftBlock.java @ c3b4d9536 (useItemOn: a steel rod on the
 *       base places the shaft; updateShape: the shaft turns to air without its base),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/CrankshaftBlockEntity.java @ c3b4d9536 (only the base is a
 *       network node: onLoadAdditional / onUnloadAdditional)
 */
package dev.tfp.paper.crankshaft;

import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.crankshaft.CrankshaftRules.Part;
import dev.tfp.core.crankshaft.CrankshaftRules.Piece;
import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The crankshaft's device kind: the base starts its powered bellows task when placed and stops it when removed; a steel rod
 * used on the base grows the shaft in front of it; a shaft whose base is gone is removed (no drop, as TFC's
 * {@code updateShape} to air). Callbacks run on the block's region thread.
 */
public final class CrankshaftDevices {
    /** The crankshaft holds no state of its own. */
    public record Nothing() {
        public static final Nothing INSTANCE = new Nothing();
        public static final VersionedCodec<Nothing> CODEC = VersionedCodec.<Nothing>builder("crankshaft", 1)
            .build((v, out) -> { }, in -> INSTANCE);
    }

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final CrankshaftService service;
    private final CrankshaftChunkIndex index;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Predicate<ItemStack> steelRod;

    public CrankshaftDevices(CrankshaftService service, CrankshaftChunkIndex index, CustomBlocks blocks, Scheduler scheduler,
            Predicate<ItemStack> steelRod) {
        this.service = Objects.requireNonNull(service, "service");
        this.index = Objects.requireNonNull(index, "index");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.steelRod = Objects.requireNonNull(steelRod, "steelRod");
    }

    public DeviceKind<Nothing> kind() {
        return DeviceKind.builder(CrankshaftBlocks.ID, Nothing.CODEC, Nothing.INSTANCE)
            .removal(DeviceKind.RemoveBehaviour.NOOP)
            .handler(new Handler())
            .build();
    }

    /** TFC {@code stateAt.canBeReplaced() && stateAt.getFluidState().isEmpty()}, for a block this thread owns. */
    boolean replaceable(Block block) {
        if (!scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return false;
        }
        if (block.isLiquid() || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
            return false;
        }
        return block.isReplaceable() && blocks.stateAt(block).isEmpty();
    }

    /** The shaft at {@code shaft} turns to air when its base is gone (TFC updateShape). Region thread. */
    void checkShaft(Block shaft) {
        Optional<Piece> piece = blocks.stateAt(shaft).flatMap(CrankshaftBlocks::piece);
        if (piece.isEmpty() || piece.get().part() != Part.SHAFT) {
            return;
        }
        BlockPos behind = CrankshaftRules.partner(CrankshaftBlocks.pos(shaft), piece.get());
        Optional<Piece> base = CrankshaftBlocks.pieceAt(shaft.getWorld(), behind, blocks, scheduler);
        if (!CrankshaftRules.shaftSurvives(piece.get(), base)) {
            blocks.remove(shaft);
        }
    }

    private final class Handler implements DeviceHandler<Nothing> {
        @Override
        public DeviceResult<Nothing> placed(DeviceContext<Nothing> ctx) {
            Optional<Piece> piece = CrankshaftBlocks.piece(ctx.state());
            if (piece.isPresent() && piece.get().part() == Part.BASE) {
                index.track(ctx.block());
                service.start(ctx.block());
            }
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<Nothing> useWithItem(DeviceContext<Nothing> ctx) {
            Player player = ctx.player();
            Optional<Piece> piece = CrankshaftBlocks.piece(ctx.state());
            if (player == null || ctx.hand() == null || piece.isEmpty()) {
                return DeviceResult.pass();
            }
            ItemStack held = player.getInventory().getItem(ctx.hand());
            boolean rod = !StackCodec.isEmpty(held) && steelRod.test(held);
            BlockPos at = CrankshaftRules.partner(CrankshaftBlocks.pos(ctx.block()), piece.get());
            Block partner = ctx.block().getWorld().getBlockAt(at.x(), at.y(), at.z());
            Optional<Piece> shaft = CrankshaftRules.rodUse(piece.get(), rod, rod && replaceable(partner));
            if (shaft.isEmpty() || !blocks.place(partner, CrankshaftBlocks.state(shaft.get()), true)) {
                return DeviceResult.pass(); // TFC PASS_TO_DEFAULT_BLOCK_INTERACTION
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().setItem(ctx.hand(), held.getAmount() > 1 ? held.subtract(1) : null);
            }
            return DeviceResult.consume();
        }

        @Override
        public DeviceResult<Nothing> neighbourChanged(DeviceContext<Nothing> ctx) {
            checkShaft(ctx.block());
            return DeviceResult.pass();
        }

        @Override
        public void removed(DeviceContext<Nothing> ctx) {
            // the removal context names only the block id (no properties): stop whatever ran here, and look at the four
            // horizontal neighbours for a shaft whose base this was, once the base is gone
            Block block = ctx.block();
            index.untrack(block);
            service.stop(block.getWorld().getUID(), CrankshaftBlocks.pos(block));
            for (Direction d : HORIZONTAL) {
                Block shaft = block.getRelative(d.dx(), 0, d.dz());
                scheduler.regionDelayed(shaft.getWorld(), shaft.getX() >> 4, shaft.getZ() >> 4, 1, () -> checkShaft(shaft));
            }
        }
    }
}
