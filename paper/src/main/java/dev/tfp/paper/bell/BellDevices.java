/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCBellBlock.java @ c3b4d9536 (attemptToRing: pitch, hammer, volume 2)
 *       src/main/java/net/dries007/tfc/common/blockentities/TFCBellBlockEntity.java @ c3b4d9536 (no data of its own),
 *       vanilla BellBlock (useWithoutItem, onProjectileHit, getStateForPlacement)
 */
package dev.tfp.paper.bell;

import dev.tfp.core.persist.VersionedCodec;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.pump.BlockWriter;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockSupport;

/**
 * PAR-METAL-18: the bronze and brass bells as device kinds ({@code tfp:bronze_bell}, {@code tfp:brass_bell}). A right click or
 * a projectile that hits the block rings it (TFC {@code attemptToRing}): the bell sound at the metal's pitch, a lower one
 * ({@link BellRules#HARD_PITCH_DROP}) when the player holds a hammer. The attachment and facing of a placed bell come from
 * vanilla's placement rule ({@link BellRules#place}); CraftEngine places the default state and one tick later the plugin
 * writes the real one. The bell keeps no data (TFC's block entity only holds vanilla's swing state, which is client animation).
 */
public final class BellDevices {
    static final VersionedCodec<Boolean> CODEC = VersionedCodec.<Boolean>builder("bell", 1)
        .build((v, out) -> out.putInt("n", 0), in -> Boolean.TRUE);
    public static final String ATTACHMENT = "attachment";
    public static final String FACING = "facing";

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final BlockWriter writer;
    private final BellEffects effects;
    private final Predicate<ItemStack> hammer;
    private final BiPredicate<Block, Direction> sturdy;

    public BellDevices(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer, BellEffects effects,
            Predicate<ItemStack> hammer) {
        this(scheduler, blocks, writer, effects, hammer, null);
    }

    /** {@code sturdy}: whether the neighbour of a block in a direction has a sturdy face toward it (null: the server's answer). */
    public BellDevices(Scheduler scheduler, CustomBlocks blocks, BlockWriter writer, BellEffects effects,
            Predicate<ItemStack> hammer, BiPredicate<Block, Direction> sturdy) {
        this.sturdy = sturdy != null ? sturdy : this::serverSturdy;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.hammer = Objects.requireNonNull(hammer, "hammer");
    }

    public List<DeviceKind<Boolean>> kinds() {
        return BellRules.PITCH.keySet().stream().sorted().map(metal -> DeviceKind.builder(BellRules.id(metal), CODEC, Boolean.TRUE)
            .removal(DeviceKind.RemoveBehaviour.NOOP).handler(new Handler(metal)).build()).toList();
    }

    /** Rings the bell {@code block} of {@code metal}; {@code by} holds the hammer or not. Region thread. */
    void ring(Block block, String metal, Player by) {
        boolean hard = by != null && hammer.test(by.getInventory().getItemInMainHand());
        effects.ring(block, by, BellRules.pitch(metal, hard));
    }

    private final class Handler implements DeviceHandler<Boolean> {
        private final String metal;

        Handler(String metal) {
            this.metal = metal;
        }

        @Override
        public DeviceResult<Boolean> use(DeviceContext<Boolean> ctx) {
            if (ctx.player() == null) {
                return DeviceResult.pass();
            }
            ring(ctx.block(), metal, ctx.player());
            return DeviceResult.consume();
        }
    }

    /** Vanilla {@code BellBlock.onProjectileHit}: an arrow, trident or other projectile that hits the bell rings it. */
    public final class Projectiles implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(ProjectileHitEvent event) {
            Block hit = event.getHitBlock();
            if (hit == null) {
                return;
            }
            Optional<BlockStateRef> state = blocks.stateAt(hit);
            String metal = state.map(s -> BellRules.metal(s.id())).orElse(null);
            if (metal == null) {
                return;
            }
            Projectile p = event.getEntity();
            effects.ring(hit, p.getShooter() instanceof Player shooter ? shooter : p, BellRules.pitch(metal, false));
        }
    }

    /**
     * Vanilla {@code getStateForPlacement}: one tick after the placement event the bell takes its attachment and facing from
     * the clicked face and the player's direction.
     */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block placed = event.getBlockPlaced();
            Block against = event.getBlockAgainst();
            Direction clicked = clickedFace(placed, against);
            Direction horizontal = horizontal(event.getPlayer().getLocation().getYaw());
            scheduler.region(placed.getLocation(), () -> placed(placed, clicked, horizontal));
        }
    }

    /** The face of {@code against} that was clicked: the one {@code placed} lies on (up when it replaced the block itself). */
    static Direction clickedFace(Block placed, Block against) {
        int dx = placed.getX() - against.getX();
        int dy = placed.getY() - against.getY();
        int dz = placed.getZ() - against.getZ();
        for (Direction d : Direction.values()) {
            if (d.dx() == dx && d.dy() == dy && d.dz() == dz) {
                return d;
            }
        }
        return Direction.UP;
    }

    /** Vanilla {@code Direction.fromYRot}: {@code floor(yaw / 90 + 0.5) & 3} is south, west, north, east. */
    static Direction horizontal(float yaw) {
        return switch (((int) Math.floor(yaw / 90.0 + 0.5)) & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    void placed(Block block, Direction clicked, Direction horizontal) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || BellRules.metal(state.get().id()) == null) {
            return;
        }
        BellRules.Placement p = BellRules.place(clicked, horizontal, d -> sturdy.test(block, d));
        BlockStateRef next = state.get().with(ATTACHMENT, p.attachment()).with(FACING, p.facing());
        if (!next.equals(state.get())) {
            writer.set(block, next);
        }
    }

    /** {@code isFaceSturdy(level, pos, d.getOpposite())} of the neighbour of {@code block} in direction {@code d}. */
    private boolean serverSturdy(Block block, Direction d) {
        Block n = block.getRelative(d.dx(), d.dy(), d.dz());
        if (!scheduler.isOwner(n.getWorld(), n.getX() >> 4, n.getZ() >> 4) || !n.getWorld().isChunkLoaded(n.getX() >> 4, n.getZ() >> 4)) {
            return false;
        }
        BlockFace toward = switch (d.opposite()) {
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
            case UP -> BlockFace.UP;
            case DOWN -> BlockFace.DOWN;
        };
        return n.getBlockData().isFaceSturdy(toward, BlockSupport.FULL);
    }
}
