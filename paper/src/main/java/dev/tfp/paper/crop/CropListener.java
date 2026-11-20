/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropBlock.java @ c3b4d9536 (canSurvive: farmland below),
 *       src/main/java/net/dries007/tfc/common/blocks/crop/DoubleCropBlock.java @ c3b4d9536 (playerWillDestroy, updateShape),
 *       src/main/java/net/dries007/tfc/util/loot/CropYieldProvider.java @ c3b4d9536 (the yield loot parameter)
 */
package dev.tfp.paper.crop;

import dev.tfp.core.crop.CropPlant;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTable;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Planting and breaking living crops: a crop block is placed only on farmland (TFC {@code canSurvive}); a break rolls the crop's loot table
 * with the stored yield ({@code CropYieldProvider}) and removes the other half of a two-block crop. Runs at HIGH, before the generic loot
 * listener (HIGHEST), which then sees no drops to roll.
 */
public final class CropListener implements Listener {
    private final CropWorld world;
    private final Supplier<LootService> loot;
    private final Supplier<DeviceService> devices;
    private final Supplier<DeviceKind<CropPlant>> kind;

    public CropListener(CropWorld world, Supplier<LootService> loot, Supplier<DeviceService> devices, Supplier<DeviceKind<CropPlant>> kind) {
        this.world = world;
        this.loot = loot;
        this.devices = devices;
        this.kind = kind;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Optional<BlockStateRef> state = world.blocks().stateAt(block);
        if (state.isEmpty() || CropIds.type(state.get().id()).isEmpty() || CropIds.isTop(state.get())) {
            return;
        }
        if (!world.canSurviveBottom(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> state = world.blocks().stateAt(block);
        if (state.isEmpty() || CropIds.type(state.get().id()).isEmpty()) {
            return;
        }
        boolean top = CropIds.isTop(state.get());
        Block bottom = top ? block.getRelative(BlockFace.DOWN) : block;
        Block partner = top ? bottom : block.getRelative(BlockFace.UP);
        Optional<BlockStateRef> partnerState = world.blocks().stateAt(partner);
        if (partnerState.isPresent() && partnerState.get().id().equals(state.get().id())
                && CropIds.isTop(partnerState.get()) != top) {
            world.scheduler().region(partner.getLocation(), () -> world.blocks().remove(partner));
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return;
        }
        LootService service = loot.get();
        Optional<LootTable> table = service.tables().forBlock(state.get().id());
        if (table.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        float yield = devices.get().edit(bottom, kind.get(), (record, now) -> record).map(r -> r.state().state().yield()).orElse(0f);
        LootContext context = LootContext.builder()
            .block(state.get().id(), state.get().properties())
            .tool(service.tool(event.getPlayer().getInventory().getItemInMainHand()))
            .cropYield(yield)
            .build();
        service.dropBlock(block, table.get(), context);
    }
}
