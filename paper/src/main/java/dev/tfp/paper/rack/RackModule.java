/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/ToolRackBlock.java @ c3b4d9536
 */
package dev.tfp.paper.rack;

import dev.tfp.core.rack.RackState;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.bukkit.block.Block;

/**
 * PAR-DECO-05: the tool racks, shelves, bookshelves and lecterns of the 20 woods (blocks written by datagen
 * {@code rack/RackBlocks}): the tool rack and bookshelf device kinds with their clicks ({@link RackInteractions}), the
 * placement ({@link RackPlacement}), the support rule of racks and shelves ({@link RackSupport}) and the tools drawn on a
 * rack ({@link ToolRackDisplays}). The shelf's placed items and the lectern's book are PAR-DECO-27. Needs the device, tags and
 * neighbour modules before it in the service file.
 */
public final class RackModule implements FeatureModule {
    public static final String ID = "rack";
    /** TFC's {@code TFCTags.Items.TOOL_RACK_TOOLS}. */
    public static final String TOOL_TAG = "tfc:usable_on_tool_rack";
    /** Vanilla's {@code ItemTags.BOOKSHELF_BOOKS}. */
    public static final String BOOK_TAG = "minecraft:bookshelf_books";

    private final List<NeighbourService.Registration> registrations = new ArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        BiConsumer<Block, BlockStateRef> writer = (block, next) -> {
            BlockVisuals visuals = context.integrations().blockVisuals();
            visuals.blockData(next).ifPresent(d -> block.setBlockData(d, false));
        };
        DeviceKind<RackState> toolRack = devices.register(RackDevices.toolRack(stack -> tags.is(stack, TOOL_TAG), ToolRackDisplays::clear));
        DeviceKind<RackState> bookshelf = devices.register(RackDevices.bookshelf(stack -> tags.is(stack, BOOK_TAG)));
        context.listen(new RackInteractions(devices, toolRack, bookshelf, blocks, context.scheduler(),
            stack -> tags.is(stack, TOOL_TAG), stack -> tags.is(stack, BOOK_TAG), writer));
        context.listen(new RackPlacement(blocks, context.scheduler(), writer,
            (n, face) -> n.getBlockData().isFaceSturdy(face, org.bukkit.block.BlockSupport.FULL)));
        context.services().get(NeighbourService.class)
            .ifPresent(n -> registrations.add(n.register(new RackSupport(RackSupport::solidId))));
    }

    @Override
    public void disable() {
        registrations.forEach(NeighbourService.Registration::unregister);
        registrations.clear();
    }
}
