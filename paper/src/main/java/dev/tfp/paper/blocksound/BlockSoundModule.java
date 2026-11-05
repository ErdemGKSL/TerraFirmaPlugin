/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.blocksound;

import dev.tfp.paper.install.Installer;
import dev.tfp.paper.integration.craftengine.CeBlockSoundTable;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.packet.PacketLayers;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * PAR-INFRA-33: CraftEngine block hit and level-event break sounds at the TFC {@code SoundType}'s volume and pitch.
 * Loads {@link CeBlockSoundTable} from CraftEngine's resources folder on the I/O pool, registers
 * {@link BlockSoundTracker} and, when PacketEvents is installed, {@link BlockSoundPacketHandler}.
 */
public final class BlockSoundModule implements FeatureModule {
    public static final String ID = "block_sound";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        BlockSoundRewrite rewrite = new BlockSoundRewrite(new TrackedBlocks());
        context.services().provide(BlockSoundRewrite.class, rewrite);
        context.listen(new BlockSoundTracker(rewrite, context.integrations().customBlocks(), System::nanoTime));
        if (context.plugin().getServer().getPluginManager().getPlugin(PacketLayers.PACKETEVENTS) != null) {
            // Reflective, so that this module still loads when PacketEvents is absent.
            context.packets().register((PacketHandler) Class.forName(getClass().getPackageName() + ".BlockSoundPacketHandler")
                    .getConstructor(BlockSoundRewrite.class, java.util.logging.Logger.class)
                    .newInstance(rewrite, context.logger()));
        } else {
            context.logger().info("PacketEvents not installed: CraftEngine block hit/break sounds keep its fixed volume and pitch");
        }
        Path resources = Installer.craftEngineTarget(context.plugin().getDataFolder().toPath().getParent()).getParent();
        context.scheduler().io(() -> {
            List<String> problems = new ArrayList<>();
            try {
                CeBlockSoundTable table = CeBlockSoundTable.load(resources, problems);
                rewrite.table(table);
                context.logger().info("Block sound table: " + table.size() + " CraftEngine blocks with TFC hit/break volume and pitch");
            } catch (Exception e) {
                context.logger().log(Level.WARNING, "Block sound table could not be read from " + resources, e);
            }
            problems.forEach(p -> context.logger().warning("Block sound table: skipped " + p));
        });
    }
}
