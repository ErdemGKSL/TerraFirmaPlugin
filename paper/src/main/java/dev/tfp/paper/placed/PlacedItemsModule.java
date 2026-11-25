/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enablePlacingItems)
 */
package dev.tfp.paper.placed;

import dev.tfp.core.item.ContainerRules;
import dev.tfp.core.placed.Gesture;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.core.placed.QuadrantSupport;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.neighbour.NeighbourService;
import dev.tfp.paper.tags.Tags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

/**
 * PAR-POT-01: placed items. Registers the {@link PlacedItemDevice} kind, the gesture listener
 * ({@link PlacedItemListener}) and the support rule ({@link PlacedSupport}). Needs the tags, device, neighbour and item
 * size modules before it in the service file.
 *
 * <p>Config, read from the plugin's {@code config.yml} when present (the keys are not in the shipped defaults yet,
 * follow-up PAR-POT-13): {@code placed-items.enabled} (TFC {@code enablePlacingItems}, default true) and
 * {@code placed-items.gesture} ({@code hold | sneak-double-tap | both}, default both). The size limits are TFC's
 * {@code maxPlacedItemSize} / {@code maxPlacedLargeItemSize} of {@link ItemSizes#limits()}.
 */
public final class PlacedItemsModule implements FeatureModule {
    public static final String ID = "placed_items";
    static final String ENABLED_KEY = "placed-items.enabled";
    static final String GESTURE_KEY = "placed-items.gesture";
    /** Item tags whose tap opens a screen: the tap waits for the release of a hold ({@link PlacedItemListener}). */
    static final List<String> DEFERRED_TAP_TAGS = List.of("tfc:vessels", "tfc:molds");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        YamlConfiguration yaml = config(context.config().file());
        if (!yaml.getBoolean(ENABLED_KEY, true)) {
            context.logger().info("Placed items are off (" + ENABLED_KEY + ": false)");
            return;
        }
        Gesture gesture = gesture(yaml.getString(GESTURE_KEY), context.logger());
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        ItemSizes sizes = context.services().require(ItemSizes.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        CustomItems items = context.integrations().customItems();
        PlacedItemEffects effects = new PaperPlacedItemEffects(stack -> items.idOf(stack).isEmpty() && stack.getType().isBlock());
        DeviceKind<PlacedState> kind = devices.register(PlacedItemDevice.kind(effects));
        PlacedItems placed = new PlacedItems(blocks, devices, kind,
            stack -> ContainerRules.placed(sizes.sizing(stack), sizes.limits()), PlacedItemsModule::supported, effects);
        UseClassifier classifier = new UseClassifier(items::idOf, blocks::exists, tags::is,
            block -> devices.inspect(block).isPresent());
        context.listen(new PlacedItemListener(placed, classifier,
            stack -> DEFERRED_TAP_TAGS.stream().anyMatch(t -> tags.is(stack, t)), context.scheduler(), gesture,
            () -> System.nanoTime() / 1_000_000L, org.bukkit.entity.Player::getTicksLived));
        context.services().get(NeighbourService.class).ifPresent(n -> n.register(new PlacedSupport(PlacedSupport::solidId)));
    }

    /** The plugin's config file as Bukkit YAML; empty when missing or unreadable (the defaults apply). */
    static YamlConfiguration config(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    static Gesture gesture(String value, Logger logger) {
        if (value == null) {
            return Gesture.DEFAULT;
        }
        Optional<Gesture> g = Gesture.byId(value);
        if (g.isEmpty()) {
            logger.warning(GESTURE_KEY + ": unknown value '" + value + "' (hold, sneak-double-tap, both); using "
                + Gesture.DEFAULT.id());
        }
        return g.orElse(Gesture.DEFAULT);
    }

    /** TFC {@code updateStateValues}: the quadrants the up face of {@code below} covers. Region thread. */
    static boolean[] supported(Block below) {
        List<QuadrantSupport.Box> boxes = new ArrayList<>();
        for (BoundingBox b : below.getCollisionShape().getBoundingBoxes()) {
            boxes.add(new QuadrantSupport.Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()));
        }
        return QuadrantSupport.supported(boxes);
    }
}
