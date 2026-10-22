/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tags;

import dev.tfp.core.tag.TagType;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * PAR-INFRA-07: provides the {@link Tags} service to later modules ({@code services().require(Tags.class)}). Listed
 * early in the service file so recipe, device and food modules can use it.
 */
public final class TagsModule implements FeatureModule {
    public static final String ID = "tags";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        ClassLoader loader = context.plugin().getClass().getClassLoader();
        Supplier<TagRules> rules = () -> {
            Path root = RegistryService.jarDataRoot(loader);
            return TagRules.load(root);
        };
        TagRules initial = rules.get();
        Tags tags = new Tags(context.registryService().registries(), context.integrations().customItems(),
                context.integrations().customBlocks(), ServerTags.BUKKIT, initial, rules,
                context.scheduler().cpuExecutor(),
                err -> context.logger().log(Level.WARNING, "Cannot reload the tag rules; keeping the previous ones", err));
        context.services().provide(Tags.class, tags);
        context.logger().fine("Tag rules: " + initial.size(TagType.ITEM) + " item, " + initial.size(TagType.BLOCK)
                + " block tags");
    }
}
