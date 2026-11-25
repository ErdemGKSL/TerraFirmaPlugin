/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFluidCreateSource)
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.worldrules.WorldRules;
import dev.tfp.core.worldrules.WorldRules.Liquid;
import dev.tfp.core.worldrules.WorldRules.Neighbour;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;

/**
 * TFC's {@code onFluidCreateSource}: a waterlogged aqueduct is no source when water decides whether to form a new
 * source, and neither is a source a sluice's outlet feeds. Paper has no event for that count, so the listener re-runs
 * vanilla's {@code getNewLiquid} on a snapshot of the block's neighbours ({@link WorldRules#deniedSource}, pure) when
 * water is about to become a source: a flowing water block turning into a source ({@link FluidLevelChangeEvent}: the
 * event's new data is replaced by TFC's answer) or water spreading into an empty block as a source ({@link
 * BlockFromToEvent}: cancelled, and the region thread then places TFC's answer if the block is still empty).
 */
final class FluidSourceListener implements Listener {
    /** A neighbour's fluid: whether it is water, its amount (1..8) and whether it is a source. */
    record FluidSample(boolean water, int amount, boolean source) {
        static final FluidSample NONE = new FluidSample(false, 0, false);
    }

    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final Scheduler scheduler;
    private final Function<Block, FluidSample> fluid;
    private final Function<Block, Optional<BlockStateRef>> custom;

    FluidSourceListener(Scheduler scheduler, Function<Block, FluidSample> fluid,
            Function<Block, Optional<BlockStateRef>> custom) {
        this.scheduler = scheduler;
        this.fluid = fluid;
        this.custom = custom;
    }

    /** Paper's fluid data of a block. */
    static FluidSample sample(Block block) {
        var data = block.getWorld().getFluidData(block.getX(), block.getY(), block.getZ());
        org.bukkit.Fluid type = data.getFluidType();
        boolean water = type == org.bukkit.Fluid.WATER || type == org.bukkit.Fluid.FLOWING_WATER;
        return water ? new FluidSample(true, data.getLevel(), data.isSource()) : FluidSample.NONE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLevelChange(FluidLevelChangeEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.WATER || !(event.getNewData() instanceof Levelled next) || next.getLevel() != 0
                || block.getBlockData() instanceof Levelled now && now.getLevel() == 0) {
            return;
        }
        denied(block).ifPresent(liquid -> {
            if (liquid.kind() == Liquid.Kind.EMPTY) {
                event.setCancelled(true); // cannot happen with two sources beside it; keep the old state
            } else {
                Levelled ours = (Levelled) next.clone();
                ours.setLevel(liquid.level());
                event.setNewData(ours);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (event.getBlock().getType() != Material.WATER || !to.getType().isAir()) {
            return;
        }
        denied(to).ifPresent(liquid -> {
            event.setCancelled(true);
            if (liquid.kind() == Liquid.Kind.EMPTY) {
                return;
            }
            BlockData water = Material.WATER.createBlockData(d -> ((Levelled) d).setLevel(liquid.level()));
            scheduler.region(to.getLocation(), () -> {
                if (to.getType().isAir()) {
                    to.setBlockData(water, true);
                }
            });
        });
    }

    /** TFC's answer at {@code block} where it differs from vanilla's new source. Owning region thread. */
    Optional<Liquid> denied(Block block) {
        List<Neighbour> around = new ArrayList<>(4);
        boolean anyCounted = false;
        int sources = 0;
        for (BlockFace face : HORIZONTAL) {
            Block n = block.getRelative(face);
            FluidSample s = fluid.apply(n);
            if (s.water() && s.source()) {
                sources++;
            }
            around.add(new Neighbour(s.water(), s.water() ? s.amount() : 0, s.source(), false, false));
        }
        if (sources < 2) {
            return Optional.empty(); // vanilla makes no source: nothing to deny
        }
        for (int i = 0; i < HORIZONTAL.length; i++) {
            Neighbour n = around.get(i);
            if (!n.source()) {
                continue;
            }
            Block at = block.getRelative(HORIZONTAL[i]);
            boolean aqueduct = at.getType() != Material.WATER && custom.apply(at).map(r -> isAqueduct(r.id())).orElse(false);
            boolean sluiceFed = sluiceFeeds(at);
            if (aqueduct || sluiceFed) {
                around.set(i, new Neighbour(true, n.amount(), true, aqueduct, sluiceFed));
                anyCounted = true;
            }
        }
        if (!anyCounted) {
            return Optional.empty();
        }
        Block below = block.getRelative(BlockFace.DOWN);
        FluidSample belowFluid = fluid.apply(below);
        boolean belowHolds = below.getType().isSolid() || belowFluid.water() && belowFluid.source();
        boolean above = fluid.apply(block.getRelative(BlockFace.UP)).water();
        return WorldRules.deniedSource(around, belowHolds, above);
    }

    /** A sluice's lower half one block above {@code source}, on a horizontal side, facing back towards it. */
    private boolean sluiceFeeds(Block source) {
        for (BlockFace face : HORIZONTAL) {
            Block rel = source.getRelative(face).getRelative(BlockFace.UP);
            Optional<BlockStateRef> ref = custom.apply(rel);
            if (ref.isPresent() && ref.get().id().startsWith("tfp:wood/sluice/")
                    && "false".equals(ref.get().properties().get("upper"))
                    && face.getOppositeFace().name().equalsIgnoreCase(ref.get().properties().get("facing"))) {
                return true;
            }
        }
        return false;
    }

    /** TFC's {@code AqueductBlock}: {@code tfp:rock/aqueduct/<rock>} (PAR-DECO-22) or {@code tfp:aqueduct...}. */
    static boolean isAqueduct(String id) {
        return id.startsWith("tfp:aqueduct") || id.contains("/aqueduct/");
    }
}
