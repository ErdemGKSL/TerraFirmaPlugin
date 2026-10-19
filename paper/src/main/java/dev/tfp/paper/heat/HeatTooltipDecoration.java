/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTooltipInfo)
 */
package dev.tfp.paper.heat;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.packet.ClientItemDecoration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * The heat line in the item tooltip of outgoing item packets (M3-HEAT-01), computed from the packet's own copy of the
 * stack: its {@code tfp:heat} record (see {@link HeatPdc}) in {@code custom_data.PublicBukkitValues}, its CraftEngine id
 * and the item heat capacity. Nothing is read from the server's stack and nothing ticks: the temperature is the lazy
 * one at the time the packet is built, so a stack shows the level it had when it was last sent.
 *
 * <p>The added line is recorded by a marker in the packet copy's {@code custom_data} ({@value #MARKER}, the number of
 * lines added), so {@link #strip} removes exactly that when a creative-mode client sends the stack back
 * ({@link dev.tfp.paper.packet.CreativeReturnHandler}). Netty thread: reads one volatile clock value and the
 * immutable definitions snapshot.
 */
public final class HeatTooltipDecoration implements ClientItemDecoration {
    public static final String NAME = "heat-tooltip";
    /** The {@code custom_data} marker: an int, the number of lore lines this decoration added. */
    public static final String MARKER = "tfp:heat_lore";

    private static final String VALUES = "PublicBukkitValues";
    private static final String CE_ID = "craftengine:id";

    private final LongSupplier now;
    private final HeatConfig config;
    private final Supplier<ItemHeatDefinitions> definitions;
    private final Function<HeatLevel, String> text;

    /**
     * @param now         the current player tick
     * @param text        the tooltip text of a heat level (translated by the caller)
     */
    public HeatTooltipDecoration(LongSupplier now, HeatConfig config, Supplier<ItemHeatDefinitions> definitions,
            Function<HeatLevel, String> text) {
        this.now = now;
        this.config = config;
        this.definitions = definitions;
        this.text = text;
    }

    @Override
    public String name() {
        return NAME;
    }

    /** The heat level of the packet copy of a stack now, or empty when it is not heated or cannot be heated. */
    Optional<HeatLevel> level(ItemStack item) {
        return item.getComponent(ComponentTypes.CUSTOM_DATA).flatMap(this::level);
    }

    /** As {@link #level(ItemStack)} from the stack's {@code custom_data} (the NBT logic, free of the item type). */
    Optional<HeatLevel> level(NBTCompound data) {
        String id = data.getStringTagValueOrNull(CE_ID);
        NBTCompound values = data.getCompoundTagOrNull(VALUES);
        NBTCompound heat = values == null ? null : values.getCompoundTagOrNull(HeatPdc.KEY);
        if (id == null || heat == null) {
            return Optional.empty();
        }
        Number version = heat.getNumberTagValueOrNull("tfp:v");
        Number temperature = heat.getNumberTagValueOrNull("tfp:temperature");
        Number tick = heat.getNumberTagValueOrNull("tfp:tick");
        if (version == null || version.intValue() > HeatPdc.VERSION || temperature == null || tick == null) {
            return Optional.empty();
        }
        float capacity = definitions.get().heatCapacity(id);
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        float now = new HeatState(temperature.floatValue(), tick.longValue())
                .temperatureAt(this.now.getAsLong(), capacity, config);
        return HeatLevel.of(now);
    }

    /**
     * Adds the heat line to the packet copy {@code item} when it is heated.
     *
     * @return true if the stack was changed
     */
    public boolean decorate(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Optional<HeatLevel> level = level(item);
        if (level.isEmpty()) {
            return false;
        }
        NBTCompound data = item.getComponent(ComponentTypes.CUSTOM_DATA).orElseThrow();
        if (data.getNumberTagValueOrNull(MARKER) != null) {
            return false; // already decorated (a packet copy that was built from a decorated one)
        }
        List<Component> lines = new ArrayList<>(item.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines());
        lines.add(Component.text(text.apply(level.get())));
        item.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        data.setTag(MARKER, new NBTInt(1));
        return true;
    }

    @Override
    public boolean strip(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Optional<NBTCompound> data = item.getComponent(ComponentTypes.CUSTOM_DATA);
        if (data.isEmpty()) {
            return false;
        }
        Number added = data.get().getNumberTagValueOrNull(MARKER);
        if (added == null) {
            return false;
        }
        data.get().removeTag(MARKER);
        List<Component> lines = new ArrayList<>(item.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines());
        int n = Math.min(added.intValue(), lines.size());
        lines.subList(lines.size() - n, lines.size()).clear();
        if (lines.isEmpty()) {
            item.unsetComponent(ComponentTypes.LORE);
        } else {
            item.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        }
        return true;
    }
}
