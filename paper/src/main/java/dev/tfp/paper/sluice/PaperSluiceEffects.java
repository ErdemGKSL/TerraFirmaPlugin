/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick, getRepresentativeState,
 * isFluidValid, getWaterOutputPos, getWaterInputPos)
 */
package dev.tfp.paper.sluice;

import dev.tfp.core.pan.PanDeposits;
import dev.tfp.core.pan.SluiceRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The sluice's world effects (PAR-PROC-13) on Paper: vanilla water as TFC's {@code any_fresh_water}, the loot of the
 * deposits (rolled off the region thread, spawned on it) and the one-shot wake-ups.
 *
 * <p>Water: TFC accepts a flowing input of fluid level 1 (the thinnest flow, Bukkit {@link Levelled} level 7, not falling)
 * and an output of the same fluid. A source in the output position is made from the input when the output block can be
 * replaced; with no input the output water the sluice made is removed again.
 */
public final class PaperSluiceEffects implements SluiceEffects {
    /** TFC: the flowing water level 1 is the thinnest flow, which is Bukkit's level 7. */
    static final int THIN_FLOW_LEVEL = 7;

    private record Key(java.util.UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final LootService loot;
    private final Supplier<PanDeposits> deposits;
    private final Supplier<BlockVisuals> visuals;
    private final Consumer<Block> waker;
    private final Set<Key> pending = ConcurrentHashMap.newKeySet();

    /**
     * @param waker called on the block's region thread when a wake-up is due (the device framework's neighbour callback)
     */
    public PaperSluiceEffects(Scheduler scheduler, CustomBlocks blocks, LootService loot, Supplier<PanDeposits> deposits,
            Supplier<BlockVisuals> visuals, Consumer<Block> waker) {
        this.visuals = visuals;
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.loot = loot;
        this.deposits = deposits;
        this.waker = waker;
    }

    private BlockFace facing(Block upper) {
        Optional<BlockStateRef> state = blocks.stateAt(upper);
        return state.map(SluiceDevice::facing).orElse(BlockFace.NORTH);
    }

    static Block at(Block upper, int[] offset) {
        return upper.getRelative(offset[0], offset[1], offset[2]);
    }

    private static boolean freshWater(Block block) {
        return block.getType() == Material.WATER;
    }

    @Override
    public SluiceRules.State flow(Block upper) {
        BlockFace facing = facing(upper);
        Block in = at(upper, SluiceDevice.inputOffset(facing));
        Block out = at(upper, SluiceDevice.outputOffset(facing));
        BlockData data = in.getBlockData();
        boolean inputValid = freshWater(in) && data instanceof Levelled l && l.getLevel() == THIN_FLOW_LEVEL;
        boolean outputValid = freshWater(out);
        return SluiceRules.representative(inputValid, outputValid, outputValid && freshWater(in));
    }

    @Override
    public void water(Block upper, SluiceRules.State state) {
        BlockFace facing = facing(upper);
        Location where = upper.getLocation();
        scheduler.region(where, () -> {
            // the commit: re-read, the world may have changed since the decision
            if (SluiceRules.State.INPUT_ONLY == state) {
                Block in = at(upper, SluiceDevice.inputOffset(facing));
                Block out = at(upper, SluiceDevice.outputOffset(facing));
                if (freshWater(in) && !freshWater(out) && (out.isEmpty() || out.isReplaceable())) {
                    out.setType(Material.WATER, true);
                }
            } else if (SluiceRules.State.OUTPUT_ONLY == state) {
                Block out = at(upper, SluiceDevice.outputOffset(facing));
                Block in = at(upper, SluiceDevice.inputOffset(facing));
                if (freshWater(out) && !freshWater(in)) {
                    out.setType(Material.AIR, true);
                }
            }
        });
    }

    @Override
    public void output(Block upper, List<String> ids) {
        BlockFace facing = facing(upper);
        PanDeposits table = deposits.get();
        for (String id : ids) {
            Optional<String> tableId = table.lootTable(id);
            if (tableId.isEmpty()) {
                continue;
            }
            loot.tables().get(tableId.get()).ifPresent(t -> {
                LootContext ctx = LootContext.builder().sluice(true).build();
                java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> loot.roll(t, ctx), scheduler.cpuExecutor())
                    .thenAccept(drops -> scheduler.region(upper.getLocation(), () -> spawn(upper, facing, loot.stacks(drops, null))));
            });
        }
    }

    /** TFC {@code Helpers.spawnItem(level, outputPos, item, 1.125, dx, 0, dz)}: above the output, pushed along the facing. */
    private void spawn(Block upper, BlockFace facing, List<ItemStack> stacks) {
        Block out = at(upper, SluiceDevice.outputOffset(facing));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (ItemStack stack : stacks) {
            Location at = out.getLocation().add(0.5, 1.125, 0.5);
            Vector push = new Vector(facing.getModX() * 0.12 * r.nextFloat(), 0, facing.getModZ() * 0.12 * r.nextFloat());
            upper.getWorld().dropItem(at, stack, e -> {
                e.setPickupDelay(10);
                e.setVelocity(push);
            });
        }
    }

    @Override
    public void wake(Block upper, long ticks) {
        Key key = Key.of(upper);
        if (!pending.add(key)) {
            return;
        }
        scheduler.regionDelayed(upper.getWorld(), upper.getX() >> 4, upper.getZ() >> 4, Math.max(1, ticks), () -> {
            pending.remove(key);
            waker.accept(upper);
        });
    }

    @Override
    public void used(Block upper) {
        upper.getWorld().playSound(upper.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f,
            1f);
    }

    @Override
    public void place(Block upper, BlockStateRef base, BlockFace facing) {
        scheduler.region(upper.getLocation(), () -> {
            Optional<BlockStateRef> now = blocks.stateAt(upper);
            if (now.isEmpty() || !now.get().id().equals(base.id())) {
                return; // broken or replaced meanwhile
            }
            Block lower = upper.getRelative(facing);
            if (!(lower.isEmpty() || lower.isReplaceable())) {
                blocks.remove(upper);
                return;
            }
            String dir = facing.name().toLowerCase(java.util.Locale.ROOT);
            BlockStateRef top = base.with(SluiceDevice.FACING, dir).with(SluiceDevice.UPPER, "true");
            visuals.get().blockData(top).ifPresent(d -> upper.setBlockData(d, false));
            blocks.place(lower, base.with(SluiceDevice.FACING, dir).with(SluiceDevice.UPPER, "false"), true);
        });
    }

    @Override
    public void verify(Block block, BlockStateRef state) {
        BlockFace facing = SluiceDevice.facing(state);
        boolean upper = SluiceDevice.isUpper(state);
        scheduler.region(block.getLocation(), () -> {
            Optional<BlockStateRef> now = blocks.stateAt(block);
            if (now.isEmpty() || !now.get().equals(state)) {
                return; // gone or changed meanwhile
            }
            Block partner = upper ? block.getRelative(facing) : block.getRelative(facing.getOppositeFace());
            boolean present = blocks.stateAt(partner)
                .filter(s -> s.id().equals(state.id()) && SluiceDevice.isUpper(s) != upper).isPresent();
            if (present) {
                return;
            }
            Block top = upper ? block : partner;
            Block out = at(top, SluiceDevice.outputOffset(facing));
            if (freshWater(out)) { // TFC onRemove: the output water the sluice made
                out.setType(Material.AIR, true);
            }
            blocks.remove(block);
        });
    }
}
