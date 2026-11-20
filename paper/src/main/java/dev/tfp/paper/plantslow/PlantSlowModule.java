/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.plantslow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.leaves.SlowBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * PAR-GATHER-10: registers the plant, berry bush and thatch slow factors with the {@link SlowBlocks} service of
 * PAR-GATHER-04. The speed table loads on the CPU pool; until then plants do not slow. Needs {@code LeavesModule} before it.
 */
public final class PlantSlowModule implements FeatureModule {
    public static final String ID = "plantslow";
    static final String FILE = "tfp/data_maps/plant_speed_factors.json";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        PlantSlowProvider provider = new PlantSlowProvider();
        context.services().require(SlowBlocks.class).register(provider);
        ClassLoader loader = context.plugin().getClass().getClassLoader();
        CompletableFuture.supplyAsync(() -> read(RegistryService.jarDataRoot(loader).resolve(FILE)),
                context.scheduler().cpuExecutor()).whenComplete((table, err) -> {
                    if (err != null) {
                        context.logger().log(Level.WARNING, "Cannot load the plant speed factors; plants do not slow", err);
                        return;
                    }
                    provider.load(table);
                });
    }

    static Map<String, Float> read(Path file) {
        try (Reader in = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
            Map<String, Float> table = new HashMap<>();
            root.getAsJsonObject("plants").entrySet().forEach(e -> table.put(e.getKey(), e.getValue().getAsFloat()));
            return table;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
