/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (the firepit tags),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (fireStarterChance, flintAndPyriteChance)
 */
package dev.tfp.paper.fire;

import dev.tfp.core.fuel.FireStartPlan;
import dev.tfp.core.fuel.FirepitPile;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-05: fire starting. Provides {@link FireStarting} (TFC's {@code StartFireEvent.startFire}; devices register
 * {@link FireLightable}s, the firepit its {@link FirepitFactory}), the built-in TNT lightable and the firestarter and
 * flint and pyrite listener. Needs the tags module before it in the service file.
 */
public final class FireModule implements FeatureModule {
    public static final String ID = "fire";
    public static final String FIREPIT_STICKS = "tfc:firepit_sticks";
    public static final String FIREPIT_KINDLING = "tfc:firepit_kindling";
    public static final String FIREPIT_LOGS = "tfc:firepit_logs";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        FireStarting fire = new FireStarting(runner(context.scheduler()), rain(context.services()), new PaperFireWorld(),
            kinds(tags));
        fire.register(new TntLightable());
        context.services().provide(FireStarting.class, fire);
        context.listen(new FireStarterListener(stack -> context.integrations().customItems().idOf(stack), fire,
            new PaperFireTools(), FireStartPlan.FIRESTARTER_CHANCE, FireStartPlan.FLINT_AND_PYRITE_CHANCE, r -> { }));
    }

    /** TFC's tag order: sticks, then kindling, then logs. Any thread. */
    static Function<ItemStack, FirepitPile.Kind> kinds(Tags tags) {
        return stack -> {
            if (tags.is(stack, FIREPIT_STICKS)) {
                return FirepitPile.Kind.STICK;
            }
            if (tags.is(stack, FIREPIT_KINDLING)) {
                return FirepitPile.Kind.KINDLING;
            }
            return tags.is(stack, FIREPIT_LOGS) ? FirepitPile.Kind.LOG : FirepitPile.Kind.OTHER;
        };
    }

    static FireStarting.Runner runner(Scheduler scheduler) {
        return new FireStarting.Runner() {
            @Override
            public void region(Block block, Runnable task) {
                World w = block.getWorld();
                int cx = block.getX() >> 4;
                int cz = block.getZ() >> 4;
                if (scheduler.isOwner(w, cx, cz)) {
                    task.run();
                } else {
                    scheduler.region(w, cx, cz, task);
                }
            }

            @Override
            public void cpu(Runnable task) {
                scheduler.cpu(task);
            }
        };
    }

    /** The climate rain of PAR-CLIM-02 behind vanilla's gates; never raining without the service. */
    static FireStarting.Rain rain(ModuleServices services) {
        return new FireStarting.Rain() {
            @Override
            public boolean reaches(Block pos) {
                return PrecipitationService.rainReaches(pos.getWorld(), pos.getX(), pos.getY(), pos.getZ());
            }

            @Override
            public boolean raining(World world, int x, int y, int z, boolean reaches) {
                return services.get(PrecipitationService.class)
                    .map(p -> p.isRainingAt(world, new BlockPos(x, y, z), reaches)).orElse(false);
            }
        };
    }
}
