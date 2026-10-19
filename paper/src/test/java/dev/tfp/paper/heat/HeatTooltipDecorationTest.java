/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-01: the heat level the tooltip decoration derives from an item's {@code custom_data}. A PacketEvents item stack
 * cannot be built without a running PacketEvents API, so the lore add and strip are checked in-game; the NBT logic is here.
 */
class HeatTooltipDecorationTest {
    private static final String ID = "tfp:metal/ingot/copper";
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(
        Map.of(ID, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f)));

    private static NBTCompound heated(String id, float temperature, long tick) {
        NBTCompound heat = new NBTCompound();
        heat.setTag("tfp:v", new NBTInt(1));
        heat.setTag("tfp:temperature", new NBTFloat(temperature));
        heat.setTag("tfp:tick", new NBTLong(tick));
        NBTCompound values = new NBTCompound();
        values.setTag("tfp:heat", heat);
        NBTCompound data = new NBTCompound();
        data.setTag("PublicBukkitValues", values);
        if (id != null) {
            data.setTag("craftengine:id", new NBTString(id));
        }
        return data;
    }

    private static HeatTooltipDecoration deco(AtomicLong now) {
        return new HeatTooltipDecoration(now::get, HeatConfig.DEFAULT, () -> DEFS, (level, locale) -> "Heat " + level.name() + " " + locale);
    }

    @Test
    void theLevelOfAHeatedItemFollowsTheLazyCooling() {
        AtomicLong now = new AtomicLong(100L);
        HeatTooltipDecoration d = deco(now);
        assertThat(d.level(heated(ID, 1_000f, 100L))).contains(HeatLevel.ORANGE);
        now.set(100L + 600L); // 600 ticks: 1000 - 600 * 0.8 / 2.857143 = 832
        assertThat(d.level(heated(ID, 1_000f, 100L))).contains(HeatLevel.BRIGHT_RED);
        now.set(100L + 6_000L); // cooled to 0: no level
        assertThat(d.level(heated(ID, 1_000f, 100L))).isEmpty();
    }

    @Test
    void anItemThatCannotBeHeatedHasNoCapacityOrIdAndGetsNoLevel() {
        HeatTooltipDecoration d = deco(new AtomicLong(100L));
        assertThat(d.level(heated("tfp:not_heatable", 1_000f, 100L))).as("no item_heat data").isEmpty();
        assertThat(d.level(heated(null, 1_000f, 100L))).as("no CraftEngine id").isEmpty();
        assertThat(d.level(heated(ID, 0f, 100L))).as("cold").isEmpty();
        assertThat(d.level(new NBTCompound())).as("no heat record").isEmpty();
    }

    @Test
    void aNewerVersionRecordIsNotRead() {
        NBTCompound data = heated(ID, 1_000f, 100L);
        data.getCompoundTagOrNull("PublicBukkitValues").getCompoundTagOrNull("tfp:heat")
            .setTag("tfp:v", new NBTInt(HeatPdc.VERSION + 1));
        assertThat(deco(new AtomicLong(100L)).level(data)).isEmpty();
    }
}
