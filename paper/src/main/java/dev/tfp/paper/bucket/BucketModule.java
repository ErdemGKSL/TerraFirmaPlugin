/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bucket;

import dev.tfp.core.bucket.BucketConfig;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.block.Block;

/**
 * PAR-FLUID-02: the wooden, red steel and blue steel buckets and TFC's fluid buckets used on the world
 * ({@link BucketListener}). The source-placement switches come from the {@code buckets:} section of {@code config.yml}
 * (PAR-FLUID-12), read on every use so {@code /tfp reload config} applies them. Listed after the {@code tags}
 * module, whose service it needs, and after {@code worldwater} (PAR-FLUID-03), whose water kinds it uses when present.
 */
public final class BucketModule implements FeatureModule {
    public static final String ID = "bucket";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        CustomItems items = context.integrations().customItems();
        Function<Block, String> water = context.services().get(WorldWaterService.class)
                .<Function<Block, String>>map(w -> block -> w.kindOf(block).containerFluid())
                .orElse(BucketListener::biomeWaterFluid);
        context.listen(new BucketListener(tags::itemId,
                (kind, fluid) -> kind.usableTag().map(tag -> tags.is(TagType.FLUID, fluid, tag)).orElse(true),
                bucketConfig(context.config()), id -> items.create(id, 1), water));
    }

    /** The listener's bucket config: the {@code buckets:} section of the config currently applied by {@code config}. */
    static Supplier<BucketConfig> bucketConfig(ConfigService config) {
        return () -> config.current().buckets();
    }
}
