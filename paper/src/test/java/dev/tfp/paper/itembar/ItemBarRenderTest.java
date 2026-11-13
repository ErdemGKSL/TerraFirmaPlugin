/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.itembar.HeatBar;
import dev.tfp.core.itembar.ItemBar;
import dev.tfp.core.itembar.ShownBar;
import dev.tfp.paper.itembar.ItemBarRender.Part;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-14: the heat bar drawn as a vanilla durability bar on the packet copy of a stack ({@code max_damage},
 * {@code damage}), the creative-return strip that restores the stack exactly, the "own bar wins" rule and the setting.
 * Runs on plain NBT through {@link ItemBarRender.Access} (a PacketEvents item stack needs a running PacketEvents).
 */
class ItemBarRenderTest {
    private static final String COPPER = "tfp:metal/ingot/copper";
    private static final String BEEF = "tfp:food/beef";
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(
        Map.of(COPPER, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f),
            BEEF, new ItemHeatDefinitions.HeatData(1f, Float.NaN, Float.NaN)));

    /** A packet stack as the plain parts the bar touches. */
    static final class FakeStack implements ItemBarRender.Access {
        NBTCompound customData;
        boolean damaged;
        final Map<Part, Integer> patches = new EnumMap<>(Part.class);

        FakeStack(NBTCompound customData) {
            this.customData = customData;
        }

        @Override
        public NBTCompound customData() {
            return customData;
        }

        @Override
        public void customData(NBTCompound data) {
            customData = data;
        }

        @Override
        public String material() {
            return "minecraft:paper";
        }

        @Override
        public int amount() {
            return 4;
        }

        @Override
        public boolean hasOwnBar() {
            return damaged;
        }

        @Override
        public Integer patch(Part part) {
            return patches.get(part);
        }

        @Override
        public void patch(Part part, Integer value) {
            if (value == null) {
                patches.remove(part);
            } else {
                patches.put(part, value);
            }
        }

        /** The width a vanilla client draws, or -1 when it draws no bar (not damaged). */
        int clientWidth() {
            Integer max = patches.get(Part.MAX_DAMAGE);
            Integer damage = patches.get(Part.DAMAGE);
            if (max == null || damage == null || max <= 0 || damage <= 0) {
                return -1;
            }
            return ItemBarRender.clientWidth(damage, max);
        }
    }

    static NBTCompound heated(String id, float temperature, long tick) {
        NBTCompound heat = new NBTCompound();
        heat.setTag("tfp:v", new NBTInt(1));
        heat.setTag("tfp:temperature", new NBTFloat(temperature));
        heat.setTag("tfp:tick", new NBTLong(tick));
        NBTCompound values = new NBTCompound();
        values.setTag("tfp:heat", heat);
        NBTCompound data = new NBTCompound();
        data.setTag("PublicBukkitValues", values);
        data.setTag("craftengine:id", new NBTString(id));
        return data;
    }

    private static ItemBars bars(AtomicBoolean enabled, AtomicLong now, AtomicInteger recipeLookups) {
        ItemBars bars = new ItemBars();
        bars.register(HeatBars.create(enabled::get, now::get, HeatConfig.DEFAULT, () -> DEFS, (stack, t) -> {
            recipeLookups.incrementAndGet();
            return COPPER.equals(stack.item().customId()) ? Optional.of(1080f) : Optional.empty();
        }));
        return bars;
    }

    private static ItemBarRender render(AtomicBoolean enabled, AtomicLong now) {
        return new ItemBarRender(bars(enabled, now, new AtomicInteger())::registry);
    }

    private static ItemBarRender render() {
        return render(new AtomicBoolean(true), new AtomicLong(100L));
    }

    @Test
    void everyWidthRoundTripsThroughTheVanillaFormula() {
        for (int w = 1; w <= ItemBar.WIDTH; w++) {
            int damage = ItemBarRender.damage(w);
            assertThat(damage).as("damaged, so the client draws it").isPositive();
            assertThat(ItemBarRender.clientWidth(damage, ItemBarRender.MAX_DAMAGE)).as("width " + w).isEqualTo(w);
        }
    }

    @Test
    void aHotIngotGetsTheBarOnThePacketCopy() {
        FakeStack stack = new FakeStack(heated(COPPER, 540f, 100L));
        assertThat(render().decorate(stack)).isTrue();
        assertThat(stack.patches).containsEntry(Part.MAX_DAMAGE, ItemBarRender.MAX_DAMAGE);
        assertThat(stack.clientWidth()).as("13 * 540 / 1080 = 6.5 -> 7").isEqualTo(7);
        assertThat(stack.customData.getCompoundTagOrNull(ItemBarRender.MARKER)).as("nothing to restore").isNotNull()
            .matches(NBTCompound::isEmpty);
    }

    @Test
    void theWidthIsTheLazyOneWhenThePacketIsBuilt() {
        AtomicLong now = new AtomicLong(100L);
        ItemBarRender render = render(new AtomicBoolean(true), now);
        FakeStack hot = new FakeStack(heated(COPPER, 1_080f, 100L));
        render.decorate(hot);
        assertThat(hot.clientWidth()).isEqualTo(13);
        now.set(100L + 600L); // 1080 - 600 * 0.8 / 2.857143 = 912: 13 * 912 / 1080 = 10.98 -> 11
        FakeStack later = new FakeStack(heated(COPPER, 1_080f, 100L));
        render.decorate(later);
        assertThat(later.clientWidth()).isEqualTo(11);
        now.set(100L + 60_000L);
        FakeStack cold = new FakeStack(heated(COPPER, 1_080f, 100L));
        assertThat(render.decorate(cold)).as("cooled to 0: no bar").isFalse();
        assertThat(cold.patches).isEmpty();
        assertThat(cold.customData.getTagOrNull(ItemBarRender.MARKER)).isNull();
    }

    @Test
    void anItemWithoutARecipeUsesTheMaxVisibleTemperature() {
        FakeStack beef = new FakeStack(heated(BEEF, 800f, 100L));
        render().decorate(beef);
        assertThat(beef.clientWidth()).as("13 * 800 / 1600 = 6.5 -> 7").isEqualTo(7);
    }

    @Test
    void coldUnheatableAndPlainStacksAreLeftAlone() {
        ItemBarRender r = render();
        assertThat(r.decorate(new FakeStack(null))).as("no custom_data").isFalse();
        NBTCompound plain = new NBTCompound();
        plain.setTag("craftengine:id", new NBTString(COPPER));
        assertThat(r.decorate(new FakeStack(plain))).as("no heat record").isFalse();
        assertThat(r.decorate(new FakeStack(heated("tfp:rock/loose/granite", 500f, 100L))))
            .as("cannot be heated").isFalse();
        assertThat(r.decorate(new FakeStack(heated(COPPER, 0f, 100L)))).as("cold").isFalse();
    }

    @Test
    void aDamagedToolKeepsItsDurabilityBar() {
        FakeStack tool = new FakeStack(heated(COPPER, 540f, 100L));
        tool.damaged = true;
        tool.patches.put(Part.DAMAGE, 30);
        assertThat(render().decorate(tool)).isFalse();
        assertThat(tool.patches).containsOnly(Map.entry(Part.DAMAGE, 30));
    }

    @Test
    void theSettingOffShowsNothing() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        ItemBarRender r = render(enabled, new AtomicLong(100L));
        FakeStack stack = new FakeStack(heated(COPPER, 540f, 100L));
        assertThat(r.decorate(stack)).isFalse();
        assertThat(stack.patches).isEmpty();
        enabled.set(true);
        assertThat(r.decorate(stack)).isTrue();
    }

    @Test
    void theCreativeReturnStripRestoresTheStackExactly() {
        NBTCompound original = heated(COPPER, 540f, 100L);
        FakeStack stack = new FakeStack(heated(COPPER, 540f, 100L));
        stack.patches.put(Part.MAX_DAMAGE, 250); // an undamaged tool with its own max_damage
        stack.patches.put(Part.DAMAGE, ItemBarRender.REMOVED);
        assertThat(render().decorate(stack)).isTrue();
        assertThat(stack.clientWidth()).isEqualTo(7);
        assertThat(ItemBarRender.strip(stack)).isTrue();
        assertThat(stack.customData).isEqualTo(original);
        assertThat(stack.patches).containsOnly(Map.entry(Part.MAX_DAMAGE, 250), Map.entry(Part.DAMAGE,
            ItemBarRender.REMOVED));
        assertThat(ItemBarRender.strip(stack)).as("nothing left to strip").isFalse();

        FakeStack bare = new FakeStack(heated(COPPER, 540f, 100L));
        render().decorate(bare);
        ItemBarRender.strip(bare);
        assertThat(bare.patches).as("no patch entries of its own: none back").isEmpty();
        assertThat(bare.customData).isEqualTo(original);
    }

    @Test
    void aStripThatEmptiesCustomDataRemovesIt() {
        NBTCompound data = new NBTCompound();
        data.setTag(ItemBarRender.MARKER, new NBTCompound());
        FakeStack stack = new FakeStack(data);
        stack.patches.put(Part.MAX_DAMAGE, ItemBarRender.MAX_DAMAGE);
        stack.patches.put(Part.DAMAGE, 600);
        assertThat(ItemBarRender.strip(stack)).isTrue();
        assertThat(stack.customData).isNull();
        assertThat(stack.patches).isEmpty();
    }

    @Test
    void aDecoratedCopyIsNotDecoratedTwice() {
        FakeStack stack = new FakeStack(heated(COPPER, 540f, 100L));
        ItemBarRender r = render();
        r.decorate(stack);
        stack.damaged = false;
        assertThat(r.decorate(stack)).isFalse();
        ItemBarRender.strip(stack);
        assertThat(stack.patches).isEmpty();
    }

    @Test
    void theRecipeIsLookedUpOncePerStack() {
        AtomicInteger lookups = new AtomicInteger();
        ItemBars bars = bars(new AtomicBoolean(true), new AtomicLong(100L), lookups);
        new ItemBarRender(bars::registry).decorate(new FakeStack(heated(COPPER, 540f, 100L)));
        assertThat(lookups.get()).isEqualTo(1);
    }

    @Test
    void laterBarsRegisterAfterHeat() {
        ItemBars bars = bars(new AtomicBoolean(true), new AtomicLong(100L), new AtomicInteger());
        bars.register(new ItemBar<>() {
            @Override
            public String id() {
                return "egg";
            }

            @Override
            public boolean isBarVisible(BarStack stack) {
                return true;
            }

            @Override
            public int barWidth(BarStack stack) {
                return 4;
            }

            @Override
            public int barColor(BarStack stack) {
                return 0xFFAA00;
            }
        });
        assertThat(bars.registry().bars()).extracting(ItemBar::id).containsExactly(HeatBar.ID, "egg");
        FakeStack cold = new FakeStack(heated(COPPER, 0f, 100L));
        Optional<ShownBar> shown = new ItemBarRender(bars::registry).shown(cold);
        assertThat(shown).contains(new ShownBar("egg", 4, 0xFFAA00));
    }

    @Test
    void theHeatSettingIsReadFromTheConfigTree() {
        assertThat(ItemBarModule.heatEnabled(Map.of())).isTrue();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", Map.of("heat", false)))).isFalse();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", Map.of("heat", true)))).isTrue();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", Map.of("heat", "no")))).as("bad value").isTrue();
        assertThat(ItemBarModule.heatEnabled(Map.of("item-bars", List.of()))).isTrue();
        assertThat(ItemBarModule.heatEnabled((java.nio.file.Path) null)).isTrue();
    }
}
