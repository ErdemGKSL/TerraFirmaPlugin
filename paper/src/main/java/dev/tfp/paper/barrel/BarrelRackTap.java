/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (tickPouring: the faucet at
 * pos.relative(facing) must be air, the fluid handler of the block below it on its UP side; the barrel's sided fluid
 * handler: an upright barrel takes fluid from above only; inventory.fill only while unsealed)
 */
package dev.tfp.paper.barrel;

import dev.tfp.core.barrel.BarrelData;
import dev.tfp.core.barrel.BarrelTap;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * PAR-PROC-38: TFC's barrel tap on the device records, lazily ({@link BarrelTap}). Before an access reads a barrel's record,
 * {@link #settleAround} settles its own tap (if it lies on its side) and the taps of the barrels pouring into it (a barrel
 * on its side one block up and one to the side, facing it), so the fluid both records hold is the one TFC's ticking would
 * have moved by now. Both records are edited through {@link DeviceService#edit}: the target first (what it takes), then the
 * source (what left it); the instant recipes run on both, the fluid surface of the target follows, and a pour that moved
 * fluid plays TFC's drip once (TFC: every 12 ticks while it pours; here only at an access, so it is tied to an action).
 *
 * <p>The fluid handlers below a tap are barrels only: an upright, unsealed barrel (TFC's barrel takes fluid on its up side
 * only when upright); TFC's other fluid block entities are not ported yet (ledger). Region thread of the accessed barrel;
 * a barrel on a region this thread does not own is left alone (its clock is not moved, so nothing is lost).
 */
public final class BarrelRackTap {
    private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private final DeviceService devices;
    private final DeviceKind<BarrelData> kind;
    private final BarrelDevice.Services services;
    private final StackCodec stacks;
    private final Scheduler scheduler;

    public BarrelRackTap(DeviceService devices, DeviceKind<BarrelData> kind, BarrelDevice.Services services,
            StackCodec stacks, Scheduler scheduler) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.services = Objects.requireNonNull(services, "services");
        this.stacks = Objects.requireNonNull(stacks, "stacks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Settles the tap of {@code block} and of the barrels pouring into it. */
    public void settleAround(Block block) {
        if (!isBarrel(block)) {
            return;
        }
        if (services.racks().facing(block) != BlockFace.UP) {
            pour(block);
            return;
        }
        Block above = block.getRelative(BlockFace.UP);
        for (BlockFace side : SIDES) {
            Block source = above.getRelative(side);
            if (owned(source) && isBarrel(source) && services.racks().facing(source) == side.getOppositeFace()) {
                pour(source);
            }
        }
    }

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    private boolean isBarrel(Block block) {
        return devices.inspect(block).filter(i -> i.kindId().equals(BarrelDevice.ID)).isPresent();
    }

    /** The target below the tap of {@code source}, if the tap can pour into it now. */
    Optional<Block> target(Block source, BlockFace facing) {
        Block faucet = source.getRelative(facing);
        if (!faucet.getType().isAir()) {
            return Optional.empty();
        }
        Block below = faucet.getRelative(BlockFace.DOWN);
        if (!owned(below) || !isBarrel(below) || services.racks().facing(below) != BlockFace.UP) {
            return Optional.empty();
        }
        return Optional.of(below);
    }

    /**
     * Settles the tap of the barrel on its side at {@code source}.
     *
     * @return the mB that went through the tap
     */
    int pour(Block source) {
        BlockFace facing = services.racks().facing(source);
        if (facing == BlockFace.UP) {
            return 0;
        }
        Optional<Block> target = target(source, facing);
        int capacity = services.book().capacity();
        int[] moved = new int[1];
        BarrelData[] targetBefore = new BarrelData[1];
        BarrelData[] targetAfter = new BarrelData[1];
        devices.edit(source, kind, (record, now) -> {
            BarrelData s = record.state();
            if (target.isEmpty() || !BarrelTap.canPour(s, true)) {
                BarrelTap.Result r = BarrelTap.settle(s, now, target.isPresent(), FluidAmount.EMPTY, 0);
                return r.source() == s ? record : record.withState(r.source());
            }
            BarrelTap.Result[] result = new BarrelTap.Result[1];
            devices.edit(target.get(), kind, (t, tNow) -> {
                BarrelData td = t.state();
                targetBefore[0] = td;
                boolean open = !td.sealed();
                int room = open ? BarrelTap.room(td.fluid(), s.fluid().fluid(), capacity,
                    services.book().session().usable(s.fluid().fluid())) : 0;
                BarrelTap.Result r = BarrelTap.settle(s, now, open, td.fluid(), room);
                result[0] = r;
                if (r.moved() == 0) {
                    targetAfter[0] = td;
                    return t;
                }
                BarrelContents c = BarrelDevice.contents(t, stacks).withFluid(r.target());
                DeviceRecord<BarrelData> next = BarrelDevice.write(t, td, BarrelDevice.instant(target.get(), c, services),
                    stacks);
                targetAfter[0] = next.state();
                return next;
            });
            if (result[0] == null) {
                // the target could not be read: the tap is closed for this settle
                BarrelTap.Result r = BarrelTap.settle(s, now, false, FluidAmount.EMPTY, 0);
                return r.source() == s ? record : record.withState(r.source());
            }
            moved[0] = result[0].moved();
            if (moved[0] == 0) {
                return result[0].source() == s ? record : record.withState(result[0].source());
            }
            BarrelContents c = BarrelDevice.contents(record, stacks).withFluid(result[0].source().fluid());
            return BarrelDevice.write(record, result[0].source(), BarrelDevice.instant(source, c, services), stacks);
        });
        if (moved[0] > 0) {
            services.effects().drip(source);
            BarrelDevice.surface(target.get(), targetBefore[0], targetAfter[0], services);
        }
        return moved[0];
    }
}
