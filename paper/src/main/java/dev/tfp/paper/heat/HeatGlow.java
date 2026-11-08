/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The heat glow of a hot item on vanilla clients (PAR-HEAT-01). On the packet copy of a stack whose lazy temperature has
 * reached a glowing heat level, the stack's {@code item_model} becomes the item's heat glow definition (datagen
 * {@code HeatGlowModels}: a {@code range_dispatch} on {@code custom_model_data} floats[0]) and floats[0] becomes the
 * level index ({@link HeatLevel#ordinal()} + 1). The level is the one at the time the packet is built: the stack shows it
 * until it is sent again (the tooltip refresher resends a view whose heat line changed).
 *
 * <p>What it changed is recorded in the copy's {@code custom_data} under {@value #MARKER}: the original
 * {@code item_model} (empty when the stack had none of its own), so {@link #strip} restores exactly that when a
 * creative-mode client sends the stack back. A stack that already carries {@code custom_model_data} is left alone (that
 * component belongs to another feature). The logic works on {@link Access}, so it is testable without a running
 * PacketEvents; {@link HeatGlowHandler} adapts PacketEvents stacks. Thread-safe: reads one clock value and immutable
 * snapshots.
 */
public final class HeatGlow {
    /** The {@code custom_data} marker: the stack's original {@code item_model} ("" for none). */
    public static final String MARKER = "tfp:heat_glow";

    private static final String CE_ID = "craftengine:id";

    /** The parts of a stack the glow reads and changes. */
    public interface Access {
        /** The live {@code custom_data} compound, or null without the component. */
        NBTCompound customData();

        /** Sets ({@code data} non-null) or removes ({@code null}) the {@code custom_data} component. */
        void customData(NBTCompound data);

        /** Whether the stack carries a {@code custom_model_data} component. */
        boolean hasModelData();

        /** Sets {@code custom_model_data} to floats [{@code value}], or removes the component ({@code null}). */
        void modelData(Float value);

        /** The stack's own {@code item_model} (its component patch), or null when it has none. */
        String itemModel();

        /** Sets the stack's own {@code item_model}, or drops it back to the item type's default ({@code null}). */
        void itemModel(String model);
    }

    private final HeatGlowData data;
    private final HeatTooltipDecoration levels;

    /** @param now the current player tick */
    public HeatGlow(HeatGlowData data, LongSupplier now, HeatConfig config, Supplier<ItemHeatDefinitions> definitions) {
        this.data = Objects.requireNonNull(data, "data");
        this.levels = new HeatTooltipDecoration(now, config, definitions, (level, locale) -> "");
    }

    /** The {@code custom_model_data} floats[0] value of a heat level. */
    public static int index(HeatLevel level) {
        return level.ordinal() + 1;
    }

    /** The glowing heat level of a stack's {@code custom_data} now, or empty when it does not glow. */
    Optional<HeatLevel> glowingLevel(NBTCompound customData) {
        if (customData == null || data.model(customData.getStringTagValueOrNull(CE_ID)) == null) {
            return Optional.empty();
        }
        return levels.level(customData).filter(l -> index(l) >= data.firstGlowingIndex());
    }

    /**
     * Makes the packet copy of a hot stack glow.
     *
     * @return true if the stack was changed
     */
    public boolean decorate(Access stack) {
        NBTCompound customData = stack.customData();
        if (customData == null || customData.getTagOrNull(MARKER) != null || stack.hasModelData()) {
            return false;
        }
        Optional<HeatLevel> level = glowingLevel(customData);
        if (level.isEmpty()) {
            return false;
        }
        String original = stack.itemModel();
        customData.setTag(MARKER, new NBTString(original == null ? "" : original));
        stack.customData(customData);
        stack.itemModel(data.model(customData.getStringTagValueOrNull(CE_ID)));
        stack.modelData((float) index(level.get()));
        return true;
    }

    /**
     * Removes what {@link #decorate} added: the model data, the swapped {@code item_model} and the marker.
     *
     * @return true if the stack carried the marker
     */
    public static boolean strip(Access stack) {
        NBTCompound customData = stack.customData();
        String original = customData == null ? null : customData.getStringTagValueOrNull(MARKER);
        if (original == null) {
            return false;
        }
        customData.removeTag(MARKER);
        stack.customData(customData.isEmpty() ? null : customData);
        stack.itemModel(original.isEmpty() ? null : original);
        stack.modelData(null);
        return true;
    }
}
