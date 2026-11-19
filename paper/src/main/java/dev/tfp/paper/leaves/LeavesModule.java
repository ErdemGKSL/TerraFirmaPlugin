/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.leaves;

import dev.tfp.core.leaves.LeafRules;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.World;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/**
 * PAR-GATHER-04: leaves. Provides {@link SlowBlocks} (the service plants and thatch register with), the movement slowdown, and
 * the decay of leaves that lost their logs ({@link LeafDecay}). Needs {@code TagsModule} before it in the service file.
 */
public final class LeavesModule implements FeatureModule {
    public static final String ID = "leaves";

    static final NamespacedKey SLOW_KEY = new NamespacedKey("tfp", "slow_blocks");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        SlowBlocks slow = new SlowBlocks();
        slow.register(SlowBlocks.leaves(LeafRules.LEAVES_MOVEMENT_MODIFIER));
        context.services().provide(SlowBlocks.class, slow);
        context.listen(new SlowdownListener(context.integrations().customBlocks(), slow, LeavesModule::setSlowdown));
        Tags tags = context.services().require(Tags.class);
        Scheduler scheduler = context.scheduler();
        context.listen(new LeafDecay(context.integrations().customBlocks(), new LeafDecay.Env() {
            @Override
            public boolean isLog(BlockStateRef state) {
                return tags.is(TagType.BLOCK, state.id(), "tfc:logs_that_log")
                        || tags.is(TagType.BLOCK, state.id(), "minecraft:logs_that_burn");
            }

            @Override
            public void regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task) {
                scheduler.regionDelayed(world, chunkX, chunkZ, delayTicks, task);
            }

            @Override
            public float nextFloat() {
                return ThreadLocalRandom.current().nextFloat();
            }
        }));
    }

    /** A transient multiplier on the movement speed attribute: the client applies it to its own prediction. */
    static void setSlowdown(Player player, double multiplier) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(SLOW_KEY);
        double amount = multiplier - 1.0;
        if (multiplier >= 1.0) {
            if (old != null) {
                speed.removeModifier(SLOW_KEY);
            }
            return;
        }
        if (old != null) {
            if (Math.abs(old.getAmount() - amount) < 1.0E-6) {
                return;
            }
            speed.removeModifier(SLOW_KEY);
        }
        speed.addTransientModifier(new AttributeModifier(SLOW_KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
}
