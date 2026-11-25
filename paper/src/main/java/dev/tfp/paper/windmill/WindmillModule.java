/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.windmill;

import dev.tfp.core.windmill.WindmillSample;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-MECH-04: the windmills and their blades. Registers the windmill's device kind (five blade slots), the blade item's use
 * on an axle, checks every placed or loaded windmill every 40 ticks ({@link WindmillService}) and reports its speed to the
 * {@link WindmillNetwork} service (provided by the rotation module, PAR-MECH-17). Needs the device and tags modules before it
 * in the service file.
 */
public final class WindmillModule implements FeatureModule {
    public static final String ID = "windmill";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService deviceService = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Scheduler scheduler = context.scheduler();
        CustomBlocks blocks = context.integrations().customBlocks();
        WindmillChunkIndex index = new WindmillChunkIndex(context.chunkData());
        AtomicReference<DeviceKind<WindmillSample>> kind = new AtomicReference<>();
        WindmillService.Records records = new WindmillService.Records() {
            @Override
            public WindmillSample load(Block block) {
                return deviceService.inspect(block).flatMap(DeviceService.Inspection::record)
                    .map(r -> r.state() instanceof WindmillSample s ? s : null).orElse(WindmillSample.FRESH);
            }

            @Override
            public void save(Block block, WindmillSample next) {
                deviceService.edit(block, kind.get(), (r, now) -> r.withState(next));
            }
        };
        WindmillService.Solids solids = block -> {
            int cx = block.getX() >> 4;
            int cz = block.getZ() >> 4;
            if (!block.getWorld().isChunkLoaded(cx, cz) || !scheduler.isOwner(block.getWorld(), cx, cz)) {
                return Optional.empty();
            }
            // TFC: !state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()
            return Optional.of(!block.getType().isAir() && !block.isPassable());
        };
        WindmillService.Wind wind = block -> {
            WindmillWind.Capture capture = WindmillWind.capture(block, context.clock().get());
            return () -> WindmillWind.wind(capture);
        };
        WindmillService service = new WindmillService(scheduler, blocks, solids, wind, records,
            () -> context.services().get(WindmillNetwork.class).orElse(WindmillNetwork.NONE));
        WindmillDevices devices = new WindmillDevices(service, index, blocks, stack -> tags.is(stack, WindmillBlocks.BLADES),
            new WindmillDevices.World() {
                @Override
                public void setState(Block block, BlockStateRef expected, BlockStateRef next) {
                    scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> {
                        if (blocks.stateAt(block).filter(expected::equals).isPresent()) {
                            context.integrations().blockVisuals().blockData(next).ifPresent(d -> block.setBlockData(d, false));
                        }
                    });
                }

                @Override
                public void replace(Block block, BlockStateRef expected, BlockStateRef next) {
                    scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> {
                        if (blocks.stateAt(block).filter(expected::equals).isPresent()) {
                            blocks.place(block, next, true);
                        }
                    });
                }

                @Override
                public boolean place(Block block, BlockStateRef state) {
                    return blocks.place(block, state, true);
                }

                @Override
                public void insertFirstBlade(Block block, ItemStack blade) {
                    deviceService.edit(block, kind.get(), (r, now) -> r.withSlot(0, deviceService.stacks().encode(blade)));
                }

                @Override
                public void tell(Player player, String key) {
                    player.sendActionBar(Component.text(context.messages().get(player, key)));
                }
            });
        kind.set(devices.kind());
        deviceService.register(devices.kind());
        context.services().provide(WindmillService.class, service);
        context.listen(devices.new BladeUse());
        blocks.onChunkLoad(chunk -> chunkLoaded(chunk, blocks, index, service));
        blocks.onChunkUnload(chunk -> index.positions(chunk).forEach(pos -> service.stop(chunk.getWorld().getUID(), pos)));
        // Chunks loaded before this module enabled get no chunk load event (as PAR-MECH-11 for the rotation blocks).
        scheduler.global(() -> context.plugin().getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                int cx = chunk.getX();
                int cz = chunk.getZ();
                scheduler.region(world, cx, cz, () -> {
                    if (world.isChunkLoaded(cx, cz)) {
                        chunkLoaded(world.getChunkAt(cx, cz), blocks, index, service);
                    }
                });
            }
        }));
    }

    /** Starts the listed windmills of a loaded chunk and forgets stale entries. Region thread of the chunk. */
    static void chunkLoaded(Chunk chunk, CustomBlocks blocks, WindmillChunkIndex index, WindmillService service) {
        for (BlockPos pos : index.positions(chunk)) {
            Block block = chunk.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isPresent() && WindmillBlocks.isWindmill(state.get().id())) {
                service.start(block);
            } else {
                index.untrack(block);
            }
        }
    }
}
