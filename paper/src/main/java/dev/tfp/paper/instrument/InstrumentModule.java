/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/ThermometerBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/AnemometerBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/VaneBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/devices/CalendarClockBlock.java @ c3b4d9536
 */
package dev.tfp.paper.instrument;

import dev.tfp.core.instrument.WindField;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import dev.tfp.paper.tags.Tags;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

/**
 * PAR-CLIM-09: registers the four instrument device kinds ({@link InstrumentDevices}) and provides the
 * {@link InstrumentSources.Registry} that device modules use to expose their heat and recipe timer to a thermometer or clock
 * mounted on them (PAR-CLIM-28: the firebox and crucible sources are wired here through {@link DeviceSources}). The
 * CraftEngine blocks come from the instrument datagen (PAR-CLIM-27).
 */
public final class InstrumentModule implements FeatureModule {
    public static final String ID = "instrument";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        InstrumentSources.Registry sources = new InstrumentSources.Registry();
        Tags tags = context.services().require(Tags.class);
        BarrelDurations barrels = new BarrelDurations(context.services().require(dev.tfp.paper.recipe.Recipes.class), tags,
            context.integrations().customItems(), devices.stacks());
        sources.register(new DeviceSources(devices, barrels));
        context.services().provide(InstrumentSources.Registry.class, sources);
        InstrumentRules.Support support = support(context, tags);
        Scheduler scheduler = context.scheduler();
        InstrumentDevices.Computer compute = new InstrumentDevices.Computer() {
            @Override
            public <T> void run(Supplier<T> work, Consumer<T> then) {
                scheduler.cpu(() -> then.accept(work.get()));
            }
        };
        InstrumentDevices.Services services = new InstrumentDevices.Services(sources, context.clock()::get,
            WindField.DEFAULT_OCEAN_WIND_SCALE, compute,
            (player, task) -> scheduler.entity(player, task, () -> { }),
            (block, task) -> scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task),
            (block, state) -> show(context, block, state),
            (player, readout) -> player.sendActionBar(Component.text(format(context, player, readout))), support,
            block -> context.integrations().customBlocks().remove(block));
        for (var kind : InstrumentDevices.kinds(services)) {
            devices.register(kind);
        }
    }

    /** The world questions of the support rules: the tags, vanilla block support and the custom block ids. */
    static InstrumentRules.Support support(ModuleContext context, Tags tags) {
        return new InstrumentRules.Support() {
            @Override
            public boolean readable(Block block, InstrumentReadout.Kind kind) {
                return tags.is(block, kind == InstrumentReadout.Kind.THERMOMETER ? InstrumentRules.THERMOMETER_READABLE
                    : InstrumentRules.CLOCK_READABLE);
            }

            @Override
            public boolean sturdyFace(Block block, BlockFace face) {
                return block.getBlockData().isFaceSturdy(face, BlockSupport.FULL);
            }

            @Override
            public boolean centerSupportUp(Block block) {
                return block.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.CENTER);
            }

            @Override
            public Optional<String> id(Block block) {
                return context.integrations().customBlocks().stateAt(block).map(BlockStateRef::id);
            }
        };
    }

    /** Writes {@code state} to the block when it is still an instrument of that id (region thread). */
    static void show(ModuleContext context, Block block, BlockStateRef state) {
        Optional<BlockData> data = context.integrations().blockVisuals().blockData(state);
        Optional<BlockStateRef> now = context.integrations().customBlocks().stateAt(block);
        if (data.isEmpty() || now.isEmpty() || !now.get().id().equals(state.id()) || now.get().equals(state)) {
            return;
        }
        block.setBlockData(data.get(), false);
    }

    static String format(ModuleContext context, org.bukkit.entity.Player player, InstrumentReadout.Readout readout) {
        Object[] args = readout.args().stream()
            .map(a -> a.startsWith("instrument.") ? context.messages().get(player, a) : a).toArray();
        return context.messages().get(player, readout.key(), args);
    }
}
