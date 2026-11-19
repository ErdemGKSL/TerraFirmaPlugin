/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.logging;

import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** PAR-GATHER-03: tree felling with an axe. Needs {@code TagsModule} before it in the service file. */
public final class FellingModule implements FeatureModule {
    public static final String ID = "felling";

    public static final String AXES = "minecraft:axes";
    public static final String INEFFICIENT_AXES = "tfc:inefficient_logging_axes";
    public static final String LOGS_THAT_LOG = "tfc:logs_that_log";

    /**
     * {@code #tfc:logs_that_log} is {@code #minecraft:logs}, which is {@code #minecraft:logs_that_burn} plus the nether stems.
     * The tag runtime only knows the data we ship, and that holds TFC's additions to {@code logs_that_burn} (every wood's
     * logs, wood and stripped forms) but not vanilla's {@code logs}, so the felling asks both.
     */
    public static final String LOGS_THAT_BURN = "minecraft:logs_that_burn";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        Scheduler scheduler = context.scheduler();
        context.listen(new TreeFelling(context.integrations().customBlocks(), new TreeFelling.Env() {
            @Override
            public boolean isAxe(ItemStack stack) {
                return tags.is(stack, AXES);
            }

            @Override
            public boolean isInefficient(ItemStack stack) {
                return tags.is(stack, INEFFICIENT_AXES);
            }

            @Override
            public boolean isLoggingBlock(BlockStateRef state) {
                return tags.is(TagType.BLOCK, state.id(), LOGS_THAT_LOG) || tags.is(TagType.BLOCK, state.id(), LOGS_THAT_BURN);
            }

            @Override
            public void region(World world, int chunkX, int chunkZ, Runnable task) {
                scheduler.region(world, chunkX, chunkZ, task);
            }

            @Override
            public void regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                scheduler.regionDelayed(world, chunkX, chunkZ, delayTicks, task);
            }

            @Override
            public boolean wear(Player player) {
                if (Bukkit.isOwnedByCurrentRegion(player)) {
                    player.damageItemStack(EquipmentSlot.HAND, 1);
                    return !player.getInventory().getItemInMainHand().getType().isAir();
                }
                // another region owns the player (Folia): wear on the player's own thread, the fall goes on
                scheduler.entity(player, () -> player.damageItemStack(EquipmentSlot.HAND, 1), () -> { });
                return true;
            }

            @Override
            public float nextFloat() {
                return ThreadLocalRandom.current().nextFloat();
            }
        }));
    }
}
