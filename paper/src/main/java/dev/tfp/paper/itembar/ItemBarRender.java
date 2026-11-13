/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import dev.tfp.core.itembar.ItemBar;
import dev.tfp.core.itembar.ItemBarRegistry;
import dev.tfp.core.itembar.ShownBar;
import dev.tfp.paper.packet.ItemDecorationHandler;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Draws an {@link ItemBar} on a vanilla client (PAR-HEAT-14). A vanilla client draws the durability bar from
 * {@code minecraft:damage} and {@code minecraft:max_damage} only, so on the packet copy of a stack that shows one of our
 * bars both components are set: {@code max_damage = }{@value #MAX_DAMAGE} and {@code damage = (13 - width) * 100}, at
 * least 1. The client computes {@code round(13 - 13 * damage / max_damage)}, which gives the width back exactly; the
 * minimum damage of 1 keeps a full bar (width 13) visible, since the client only draws the bar of a damaged item. The
 * client colours it by the damage fraction (green when full, red when short), not with TFC's heat colour.
 *
 * <p>The stack's original {@code max_damage} and {@code damage} patch entries are recorded in its {@code custom_data}
 * under {@value #MARKER}, so {@link #strip} restores exactly them when a creative-mode client sends the stack back.
 * The server's stack is never changed. The logic runs on {@link Access}, so it is testable without a running
 * PacketEvents; {@link ItemBarHandler} adapts PacketEvents stacks.
 */
public final class ItemBarRender {
    /** The {@code custom_data} marker: a compound with the original patch entries. */
    public static final String MARKER = "tfp:item_bar";
    /** The {@code max_damage} of a stack drawing one of our bars. */
    public static final int MAX_DAMAGE = 1300;
    /** {@link Access#patch} value of a patch entry that removes the component ({@code !minecraft:damage}). */
    public static final int REMOVED = -1;

    private static final String VALUES = "PublicBukkitValues";
    private static final String CE_ID = "craftengine:id";

    /** The two components the bar is drawn with. */
    public enum Part {
        MAX_DAMAGE("max_damage"),
        DAMAGE("damage");

        private final String key;

        Part(String key) {
            this.key = key;
        }

        /** The key in the {@value #MARKER} compound. */
        public String key() {
            return key;
        }
    }

    /** The parts of a stack the bar reads and changes. */
    public interface Access {
        /** The live {@code custom_data} compound, or null without the component. */
        NBTCompound customData();

        /** Sets ({@code data} non-null) or removes ({@code null}) the {@code custom_data} component. */
        void customData(NBTCompound data);

        /** The vanilla item type id ({@code minecraft:paper}). */
        String material();

        /** The stack size. */
        int amount();

        /** Whether the client draws a bar of its own for the stack as it is (vanilla {@code isBarVisible}: damaged). */
        boolean hasOwnBar();

        /** The stack's own patch entry for {@code part}: null for none, {@link #REMOVED} for a removal, else the value. */
        Integer patch(Part part);

        /** Sets the patch entry for {@code part}: null drops it, {@link #REMOVED} writes a removal, else the value. */
        void patch(Part part, Integer value);
    }

    private final Supplier<ItemBarRegistry<BarStack>> registry;

    /** @param registry the bars, read once per stack */
    public ItemBarRender(Supplier<ItemBarRegistry<BarStack>> registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** The damage that makes a vanilla client draw a bar {@code width} pixels wide out of {@value #MAX_DAMAGE}. */
    public static int damage(int width) {
        return Math.max(1, (ItemBar.WIDTH - width) * (MAX_DAMAGE / ItemBar.WIDTH));
    }

    /** The bar width a vanilla client draws for {@code damage} out of {@code maxDamage} ({@code ItemStack.getBarWidth}). */
    public static int clientWidth(int damage, int maxDamage) {
        int w = Math.round(13.0F - damage * 13.0F / maxDamage);
        return Math.max(0, Math.min(13, w));
    }

    /** The view the bars see of a stack. */
    static BarStack view(Access stack) {
        NBTCompound data = stack.customData();
        if (data == null) {
            return new BarStack(new TooltipItem(null, stack.material(), stack.amount(), TooltipData.EMPTY));
        }
        NBTCompound values = data.getCompoundTagOrNull(VALUES);
        return new BarStack(new TooltipItem(data.getStringTagValueOrNull(CE_ID), stack.material(), stack.amount(),
                values == null ? TooltipData.EMPTY : new ItemDecorationHandler.NbtData(values)));
    }

    /** The bar the stack shows now, if any. */
    Optional<ShownBar> shown(Access stack) {
        return registry.get().shown(view(stack), stack.hasOwnBar());
    }

    /**
     * Draws the bar on the packet copy of a stack that shows one.
     *
     * @return true if the stack was changed
     */
    public boolean decorate(Access stack) {
        NBTCompound data = stack.customData();
        if (data != null && data.getTagOrNull(MARKER) != null) {
            return false; // a copy built from a decorated one
        }
        if (registry.get().bars().isEmpty()) {
            return false;
        }
        Optional<ShownBar> bar = shown(stack);
        if (bar.isEmpty()) {
            return false;
        }
        NBTCompound marker = new NBTCompound();
        for (Part part : Part.values()) {
            Integer original = stack.patch(part);
            if (original != null) {
                marker.setTag(part.key(), new NBTInt(original));
            }
        }
        if (data == null) {
            data = new NBTCompound();
        }
        data.setTag(MARKER, marker);
        stack.customData(data);
        stack.patch(Part.MAX_DAMAGE, MAX_DAMAGE);
        stack.patch(Part.DAMAGE, damage(bar.get().width()));
        return true;
    }

    /**
     * Removes what {@link #decorate} added: restores the original {@code max_damage} and {@code damage} patch entries
     * and drops the marker.
     *
     * @return true if the stack carried the marker
     */
    public static boolean strip(Access stack) {
        NBTCompound data = stack.customData();
        NBTCompound marker = data == null ? null : data.getCompoundTagOrNull(MARKER);
        if (marker == null) {
            return false;
        }
        for (Part part : Part.values()) {
            Number original = marker.getNumberTagValueOrNull(part.key());
            stack.patch(part, original == null ? null : original.intValue());
        }
        data.removeTag(MARKER);
        stack.customData(data.isEmpty() ? null : data);
        return true;
    }
}
