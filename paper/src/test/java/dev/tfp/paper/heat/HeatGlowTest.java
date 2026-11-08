/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.ItemHeatDefinitions;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-01: the heat glow on the packet copy of a stack: the level index in {@code custom_model_data} floats[0], the
 * glow {@code item_model}, and the creative-return strip that restores the stack exactly. Runs on plain NBT through
 * {@link HeatGlow.Access} (a PacketEvents item stack needs a running PacketEvents).
 */
class HeatGlowTest {
    private static final String ID = "tfp:metal/ingot/copper";
    private static final String MODEL = "tfp:metal/ingot/copper";
    private static final String GLOW = "tfp:heat_glow/metal/ingot/copper";
    private static final ItemHeatDefinitions DEFS = new ItemHeatDefinitions(
        Map.of(ID, new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f),
            "tfp:food/beef", new ItemHeatDefinitions.HeatData(1f, Float.NaN, Float.NaN)));
    private static final HeatGlowData DATA = new HeatGlowData(Map.of(ID, GLOW), HeatGlow.index(HeatLevel.FAINT_RED));

    /** A stack as the plain parts the glow touches. */
    static final class FakeStack implements HeatGlow.Access {
        NBTCompound customData;
        Float modelData;
        boolean foreignModelData;
        String itemModel;

        FakeStack(NBTCompound customData, String itemModel) {
            this.customData = customData;
            this.itemModel = itemModel;
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
        public boolean hasModelData() {
            return modelData != null || foreignModelData;
        }

        @Override
        public void modelData(Float value) {
            modelData = value;
        }

        @Override
        public String itemModel() {
            return itemModel;
        }

        @Override
        public void itemModel(String model) {
            itemModel = model;
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

    private static HeatGlow glow(AtomicLong now) {
        return new HeatGlow(DATA, now::get, HeatConfig.DEFAULT, () -> DEFS);
    }

    @Test
    void aCopperIngotAt900GlowsBrightRed() {
        FakeStack stack = new FakeStack(heated(ID, 900f, 100L), MODEL);
        assertThat(glow(new AtomicLong(100L)).decorate(stack)).isTrue();
        assertThat(stack.modelData).as("floats[0] = BRIGHT_RED ordinal + 1").isEqualTo(6f);
        assertThat(stack.itemModel).isEqualTo(GLOW);
        assertThat(stack.customData.getStringTagValueOrNull(HeatGlow.MARKER)).as("the original model").isEqualTo(MODEL);
    }

    @Test
    void theLevelIndexFollowsEveryGlowingLevel() {
        for (HeatLevel level : HeatLevel.values()) {
            FakeStack stack = new FakeStack(heated(ID, (level.min() + level.max()) / 2f, 100L), MODEL);
            boolean changed = glow(new AtomicLong(100L)).decorate(stack);
            if (level.ordinal() < HeatLevel.FAINT_RED.ordinal()) {
                assertThat(changed).as(level + " does not glow").isFalse();
                assertThat(stack.modelData).isNull();
                assertThat(stack.itemModel).isEqualTo(MODEL);
            } else {
                assertThat(changed).as(level.name()).isTrue();
                assertThat(stack.modelData).as(level.name()).isEqualTo((float) (level.ordinal() + 1));
            }
        }
        FakeStack hotter = new FakeStack(heated(ID, 1590f, 100L), MODEL);
        glow(new AtomicLong(100L)).decorate(hotter);
        assertThat(hotter.modelData).isEqualTo((float) HeatGlow.index(HeatLevel.BRILLIANT_WHITE));
    }

    @Test
    void theLevelIsTheLazyOneWhenThePacketIsBuilt() {
        AtomicLong now = new AtomicLong(100L);
        FakeStack stack = new FakeStack(heated(ID, 1_000f, 100L), MODEL);
        glow(now).decorate(stack);
        assertThat(stack.modelData).isEqualTo((float) HeatGlow.index(HeatLevel.ORANGE));
        now.set(100L + 600L); // 1000 - 600 * 0.8 / 2.857143 = 832: bright red
        FakeStack later = new FakeStack(heated(ID, 1_000f, 100L), MODEL);
        glow(now).decorate(later);
        assertThat(later.modelData).isEqualTo((float) HeatGlow.index(HeatLevel.BRIGHT_RED));
        now.set(100L + 6_000L); // cold
        FakeStack cold = new FakeStack(heated(ID, 1_000f, 100L), MODEL);
        assertThat(glow(now).decorate(cold)).isFalse();
    }

    @Test
    void itemsWithoutAGlowModelOrHeatAreLeftAlone() {
        HeatGlow g = glow(new AtomicLong(100L));
        assertThat(g.decorate(new FakeStack(heated("tfp:food/beef", 900f, 100L), "tfp:food/beef")))
            .as("heatable but no glow definition: would be a missing model").isFalse();
        assertThat(g.decorate(new FakeStack(null, MODEL))).as("no custom_data").isFalse();
        NBTCompound plain = new NBTCompound();
        plain.setTag("craftengine:id", new NBTString(ID));
        assertThat(g.decorate(new FakeStack(plain, MODEL))).as("no heat record").isFalse();
        FakeStack foreign = new FakeStack(heated(ID, 900f, 100L), MODEL);
        foreign.foreignModelData = true;
        assertThat(g.decorate(foreign)).as("custom_model_data of another feature").isFalse();
        assertThat(foreign.itemModel).isEqualTo(MODEL);
    }

    @Test
    void aDecoratedCopyIsNotDecoratedTwice() {
        FakeStack stack = new FakeStack(heated(ID, 900f, 100L), MODEL);
        HeatGlow g = glow(new AtomicLong(100L));
        g.decorate(stack);
        stack.modelData = null; // even if the model data were gone, the marker stops a second swap
        assertThat(g.decorate(stack)).isFalse();
        assertThat(stack.customData.getStringTagValueOrNull(HeatGlow.MARKER)).isEqualTo(MODEL);
    }

    @Test
    void theCreativeReturnStripRestoresTheStackExactly() {
        NBTCompound original = heated(ID, 900f, 100L);
        FakeStack stack = new FakeStack(heated(ID, 900f, 100L), MODEL);
        glow(new AtomicLong(100L)).decorate(stack);
        assertThat(HeatGlow.strip(stack)).isTrue();
        assertThat(stack.customData).isEqualTo(original);
        assertThat(stack.modelData).isNull();
        assertThat(stack.itemModel).isEqualTo(MODEL);
        assertThat(HeatGlow.strip(stack)).as("nothing left to strip").isFalse();
    }

    @Test
    void aStackWithoutItsOwnItemModelGetsNoneBack() {
        FakeStack stack = new FakeStack(heated(ID, 900f, 100L), null);
        glow(new AtomicLong(100L)).decorate(stack);
        assertThat(stack.customData.getStringTagValueOrNull(HeatGlow.MARKER)).isEmpty();
        HeatGlow.strip(stack);
        assertThat(stack.itemModel).isNull();
    }

    @Test
    void theDatagenListIsRead() throws IOException {
        HeatGlowData data = HeatGlowData.read(new StringReader("""
            {"format": 1, "first_glowing_index": 4, "items": {"tfp:metal/ingot/copper": "tfp:heat_glow/metal/ingot/copper"}}
            """));
        assertThat(data.size()).isEqualTo(1);
        assertThat(data.model(ID)).isEqualTo(GLOW);
        assertThat(data.model("tfp:other")).isNull();
        assertThat(data.firstGlowingIndex()).isEqualTo(4);
        assertThatThrownBy(() -> HeatGlowData.read(new StringReader("{\"format\": 2}"))).isInstanceOf(IOException.class);
    }

    @Test
    void theGlowListIsInTheContentJarWhenItWasGenerated() throws IOException {
        // The paper test classpath holds the content jar's resources when content/generated exists (share-build).
        if (getClass().getClassLoader().getResource(HeatGlowData.FILE) == null) {
            return;
        }
        HeatGlowData data = HeatGlowData.load(getClass().getClassLoader());
        assertThat(data.model(ID)).isEqualTo(GLOW);
        assertThat(data.firstGlowingIndex()).isEqualTo(HeatGlow.index(HeatLevel.FAINT_RED));
    }
}
