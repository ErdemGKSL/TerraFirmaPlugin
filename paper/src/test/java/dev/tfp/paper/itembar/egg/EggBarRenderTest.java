/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itembar.egg;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.itembar.ItemBar;
import dev.tfp.paper.itembar.HeatBars;
import dev.tfp.paper.itembar.ItemBars;
import dev.tfp.paper.packet.itembar.ItemBarRender;
import dev.tfp.paper.packet.itembar.ItemBarRender.Part;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-23: TFC's egg bar on the packet copy of a fertilized egg ({@code tfp:egg} record: {@code tfp:fertilized},
 * {@code tfp:hatch_day}), through the shared item-bar layer. The server stack is never touched: the bar is drawn on the copy
 * the packet carries, and the creative return strip restores it.
 */
class EggBarRenderTest {
    private static final String EGG = "tfp:food/egg";
    private static final long TODAY = 500;

    /** A packet stack as the plain parts the bar touches. */
    static final class FakeStack implements ItemBarRender.Access {
        NBTCompound customData;
        final Map<Part, Integer> patches = new EnumMap<>(Part.class);
        boolean damaged;

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
            return "minecraft:egg";
        }

        @Override
        public int amount() {
            return 1;
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

        int clientWidth() {
            Integer max = patches.get(Part.MAX_DAMAGE);
            Integer damage = patches.get(Part.DAMAGE);
            return max == null || damage == null || max <= 0 || damage <= 0 ? -1 : ItemBarRender.clientWidth(damage, max);
        }
    }

    static NBTCompound egg(boolean fertilized, long hatchDay) {
        NBTCompound record = new NBTCompound();
        record.setTag("tfp:v", new NBTInt(1));
        record.setTag("tfp:fertilized", new NBTByte((byte) (fertilized ? 1 : 0)));
        record.setTag("tfp:hatch_day", new NBTLong(hatchDay));
        NBTCompound values = new NBTCompound();
        values.setTag("tfp:egg", record);
        NBTCompound data = new NBTCompound();
        data.setTag("PublicBukkitValues", values);
        data.setTag("craftengine:id", new NBTString(EGG));
        return data;
    }

    private static ItemBarRender render(AtomicLong today) {
        ItemBars bars = new ItemBars();
        bars.register(EggBars.create(today::get));
        return new ItemBarRender(bars::registry);
    }

    @Test
    void aFertilizedEggPacketCopyCarriesTheBarThatGrowsOverEightDays() {
        AtomicLong today = new AtomicLong(TODAY);
        ItemBarRender render = render(today);
        int[] expected = {0, 2, 3, 5, 7, 8, 10, 11, 13};
        for (int day = 0; day <= 8; day++) {
            today.set(TODAY + day);
            FakeStack stack = new FakeStack(egg(true, TODAY + 8)); // laid on TODAY: hatches 8 days later
            assertThat(render.decorate(stack)).as("day %d", day).isTrue();
            assertThat(stack.patches).containsEntry(Part.MAX_DAMAGE, ItemBarRender.MAX_DAMAGE);
            // width 0 draws the empty bar: the vanilla client draws no bar for an undamaged stack, so 0 is drawn as damage = max
            assertThat(Math.max(0, stack.clientWidth())).as("day %d", day).isEqualTo(expected[day]);
        }
    }

    @Test
    void anUnfertilizedEggAndAStackWithNoEggShowNoBar() {
        ItemBarRender render = render(new AtomicLong(TODAY));
        FakeStack unfertilized = new FakeStack(egg(false, TODAY + 4));
        assertThat(render.decorate(unfertilized)).isFalse();
        assertThat(unfertilized.patches).isEmpty();
        NBTCompound plain = new NBTCompound();
        plain.setTag("craftengine:id", new NBTString(EGG));
        assertThat(render.decorate(new FakeStack(plain))).isFalse();
        assertThat(render.decorate(new FakeStack(null))).isFalse();
    }

    @Test
    void theServerStackIsNeverChangedAndTheCreativeStripRestoresTheCopy() {
        NBTCompound server = egg(true, TODAY + 4);
        NBTCompound copy = egg(true, TODAY + 4);
        FakeStack packet = new FakeStack(copy);
        assertThat(render(new AtomicLong(TODAY)).decorate(packet)).isTrue();
        assertThat(packet.clientWidth()).isEqualTo(7);
        assertThat(server).as("the server's own data carries no marker").isEqualTo(egg(true, TODAY + 4));
        assertThat(ItemBarRender.strip(packet)).isTrue();
        assertThat(packet.customData).isEqualTo(server);
        assertThat(packet.patches).isEmpty();
    }

    @Test
    void aDamagedStackKeepsItsOwnBar() {
        FakeStack stack = new FakeStack(egg(true, TODAY + 4));
        stack.damaged = true;
        stack.patches.put(Part.DAMAGE, 3);
        assertThat(render(new AtomicLong(TODAY)).decorate(stack)).isFalse();
        assertThat(stack.patches).containsOnly(Map.entry(Part.DAMAGE, 3));
    }

    @Test
    void theEggBarRegistersAfterTheHeatBar() throws IOException {
        ItemBars bars = new ItemBars();
        bars.register(HeatBars.create(() -> true, () -> TODAY, HeatConfig.DEFAULT,
                () -> new ItemHeatDefinitions(Map.of()), (stack, t) -> Optional.empty()));
        bars.register(EggBars.create(() -> TODAY));
        assertThat(bars.registry().bars().stream().map(ItemBar::id)).containsExactly("heat", "egg");
        try (var in = EggBarRenderTest.class.getResourceAsStream("/META-INF/services/dev.tfp.paper.module.FeatureModule")) {
            List<String> modules = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            assertThat(modules.indexOf("dev.tfp.paper.itembar.egg.EggBarModule"))
                .isGreaterThan(modules.indexOf("dev.tfp.paper.itembar.ItemBarModule"));
            assertThat(modules).contains("dev.tfp.paper.itembar.egg.EggBarModule");
        }
    }
}
