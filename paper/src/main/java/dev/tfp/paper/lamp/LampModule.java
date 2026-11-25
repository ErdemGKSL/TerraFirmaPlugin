/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LampBlock.java @ c3b4d9536 (canSurvive)
 */
package dev.tfp.paper.lamp;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.lamp.LampData;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;

/**
 * PAR-METAL-19: the nine metal lamps. Registers their {@link LampDevice} kinds, the placement guard, the fire lightable
 * (a fire starter lights a lamp with fuel), the run-dry watch and the chunk-load update, which turns a lamp that ran dry
 * while its chunk was unloaded. Needs the device, tags and (for lighting) fire modules before it in the service file.
 */
public final class LampModule implements FeatureModule {
    public static final String ID = "lamp";
    /** TFC's lamp metals ({@code Metal.PartType.ALL}). */
    public static final List<String> METALS = List.of("bismuth_bronze", "black_bronze", "black_steel", "blue_steel", "bronze",
        "copper", "red_steel", "steel", "wrought_iron");

    private volatile Scheduler.TaskHandle sweep;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devices = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        CustomItems items = ctx.integrations().customItems();
        Scheduler scheduler = ctx.scheduler();
        BarrelItemTanks tanks = new BarrelItemTanks(new BarrelItemTanks.Services(tags::itemId,
            (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
            id -> items.create(id, 1), items::exists));
        PaperLampEffects effects = new PaperLampEffects(scheduler, blocks, () -> ctx.integrations().blockVisuals(),
            () -> ctx.clock().get().playerTicks(), devices::neighbourChanged);
        sweep = effects.start();
        LampDevice lamp = new LampDevice(new LampDevice.Services(tanks, effects, LampModule::support), blocks);
        List<DeviceKind<LampData>> kinds = lamp.kinds(METALS);
        kinds.forEach(devices::register);
        ctx.listen(lamp.new Placement());
        ctx.services().get(FireStarting.class).ifPresent(fire -> fire.register(c -> lamp.light(c.block(), devices, kinds)));
        Optional<CatchUpService> catchUp = ctx.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devices, scheduler)));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle sw = sweep;
        sweep = null;
        if (sw != null) {
            sw.cancel();
        }
    }

    /** TFC {@code canSurvive}: {@code Block.canSupportCenter} on the block above (hanging) or below (standing). */
    static boolean support(Block block, boolean hanging) {
        Block neighbour = block.getRelative(hanging ? BlockFace.UP : BlockFace.DOWN);
        return neighbour.getBlockData().isFaceSturdy(hanging ? BlockFace.DOWN : BlockFace.UP, BlockSupport.CENTER);
    }

    /** Lamps of a loaded chunk are called once: one that ran dry while the chunk was unloaded turns unlit. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices, Scheduler scheduler) {
        Optional<LazyBlockIndex> index = catchUp.index(chunk);
        if (index.isEmpty()) {
            return;
        }
        World world = chunk.getWorld();
        for (Map.Entry<BlockPos, String> e : index.get().entries().entrySet()) {
            if (!CatchUpModule.DEVICE_KIND.equals(e.getValue())) {
                continue;
            }
            BlockPos p = e.getKey();
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (blocks.stateAt(block).map(s -> LampDevice.metal(s.id()) != null).orElse(false)) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }
}
