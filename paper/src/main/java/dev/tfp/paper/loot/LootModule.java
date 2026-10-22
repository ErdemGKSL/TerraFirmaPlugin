/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import dev.tfp.core.tag.TagType;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * PAR-INFRA-09: provides {@link LootService} ({@code services().require(LootService.class)}) and the break/explosion
 * listener. Tables load on the CPU pool from the jar's data root ({@code content/data/<ns>/loot_table}); until the load
 * finishes the set is empty, so a block whose drops are delegated to this runtime (no CraftEngine {@code loot:}) drops
 * nothing in that startup window (recorded divergence; PAR-INFRA-21 decides whether to hold breaks until loaded). Must come
 * after {@code TagsModule} in the service file.
 */
public final class LootModule implements FeatureModule {
    public static final String ID = "loot";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        AtomicReference<LootTables> tables = new AtomicReference<>(LootTables.EMPTY);
        ClassLoader loader = context.plugin().getClass().getClassLoader();
        LootService service = new LootService(tables::get, context.integrations().customItems(), tags::itemId,
                (item, tag) -> tags.is(TagType.ITEM, item, tag), context.scheduler(),
                () -> context.services().get(LootStackHooks.class).orElse(LootStackHooks.NONE),
                ThreadLocalRandom::current, context.logger());
        context.services().provide(LootService.class, service);
        context.listen(new LootBreakListener(service, context.integrations().customBlocks()));
        CompletableFuture.supplyAsync(() -> LootTables.load(RegistryService.jarDataRoot(loader)),
                context.scheduler().cpuExecutor()).whenComplete((loaded, err) -> {
                    if (err != null) {
                        context.logger().log(Level.WARNING, "Cannot load the loot tables; blocks keep their own drops", err);
                        return;
                    }
                    tables.set(loaded);
                    for (String error : loaded.errors()) {
                        context.logger().warning("Loot table skipped: " + error);
                    }
                    context.logger().fine("Loot tables: " + loaded.size());
                });
    }
}
