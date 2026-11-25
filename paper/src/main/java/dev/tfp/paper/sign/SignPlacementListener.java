/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCStandingSignBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/wood/TFCCeilingHangingSignBlock.java @ c3b4d9536
 */
package dev.tfp.paper.sign;

import dev.tfp.core.sign.SignPlacement;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-DECO-25: the rotation of a placed standing sign and the rotation and {@code attached} of a placed ceiling hanging
 * sign (vanilla {@code getStateForPlacement}). CraftEngine places the default state; the place event hands the facts to
 * the block's region, which decides with {@link SignPlacement} and writes the state (no physics, waterlogging kept).
 */
public final class SignPlacementListener implements Listener {
    public static final String STANDING = "tfp:wood/sign/";
    public static final String HANGING = "tfp:wood/hanging_sign/";
    public static final String WALL_HANGING = "tfp:wood/wall_hanging_sign/";

    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final BiConsumer<Block, BlockStateRef> writer;
    private final Predicate<Block> fullBottom;

    public SignPlacementListener(CustomBlocks blocks, Scheduler scheduler, BiConsumer<Block, BlockStateRef> writer) {
        this(blocks, scheduler, writer, b -> b.getBlockData().isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL));
    }

    /** @param fullBottom whether a block has a full down face (the block above a ceiling sign) */
    public SignPlacementListener(CustomBlocks blocks, Scheduler scheduler, BiConsumer<Block, BlockStateRef> writer,
            Predicate<Block> fullBottom) {
        this.fullBottom = Objects.requireNonNull(fullBottom, "fullBottom");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        float yaw = player.getLocation().getYaw();
        boolean sneaking = player.isSneaking();
        scheduler.region(block.getLocation(), () -> placed(block, yaw, sneaking));
    }

    /** Region thread. */
    void placed(Block block, float yaw, boolean sneaking) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty()) {
            return;
        }
        BlockStateRef state = now.get();
        BlockStateRef next;
        if (state.id().startsWith(STANDING)) {
            next = state.with("rotation", Integer.toString(SignPlacement.standingRotation(yaw)));
        } else if (state.id().startsWith(HANGING)) {
            Block above = block.getRelative(0, 1, 0);
            boolean full = fullBottom.test(above);
            SignPlacement.Hanging h = SignPlacement.ceilingHanging(yaw, sneaking, full, aboveAxis(above));
            next = state.with("rotation", Integer.toString(h.rotation())).with("attached", Boolean.toString(h.attached()));
        } else {
            return;
        }
        if (!next.equals(state)) {
            writer.accept(block, next);
        }
    }

    /** The axis of a hanging sign above, or null. */
    private String aboveAxis(Block above) {
        if (!scheduler.isOwner(above.getWorld(), above.getX() >> 4, above.getZ() >> 4)) {
            return null;
        }
        Optional<BlockStateRef> s = blocks.stateAt(above);
        if (s.isEmpty()) {
            return null;
        }
        String id = s.get().id();
        if (id.startsWith(WALL_HANGING)) {
            String facing = s.get().properties().get("facing");
            return facing == null ? null : SignPlacement.axis(facing);
        }
        if (id.startsWith(HANGING)) {
            String r = s.get().properties().get("rotation");
            return r == null ? null : SignPlacement.rotationAxis(Integer.parseInt(r));
        }
        return null;
    }
}
