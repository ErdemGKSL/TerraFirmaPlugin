/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.plantstage;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.stream.NetStreamOutput;
import com.github.retrooper.packetevents.protocol.world.chunk.BaseChunk;
import com.github.retrooper.packetevents.protocol.world.chunk.Column;
import com.github.retrooper.packetevents.protocol.world.chunk.TileEntity;
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.DataPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.GlobalPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.ListPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.MapPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.PaletteType;
import com.github.retrooper.packetevents.protocol.world.chunk.palette.SingletonPalette;
import com.github.retrooper.packetevents.protocol.world.chunk.storage.BitStorage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.google.gson.JsonParser;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.paper.packet.PacketHandler;
import dev.tfp.paper.plant.lifecycle.PlantStageRemap;
import dev.tfp.paper.plant.lifecycle.PlantStageSource;
import dev.tfp.paper.plant.lifecycle.PlantStageTable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-63: the plant stage handler rewrites plant ids by season and leaves every other byte as it was. */
class PlantStagePacketHandlerTest {
    private static final String JSON = """
        {"plants": {
           "allium": {"wet_season_blooming": false, "bloom_offset": -0.124, "blooming_end": 0.25, "seeding_end": 0.42,
                      "dying_end": 0.5, "dormant_end": 0.75, "sprouting_end": 0.88, "start_hour": 0, "end_hour": 0}},
         "dormant_states": {"tfp:plant/allium": {"plant": "allium", "state": "tfp:plant/dormant_look[look=0]"}}}
        """;
    private static final int PLANT = 100;
    private static final int DORMANT = 200;
    private static final int STONE = 1;
    private static final int AIR = 0;
    private static final int DAYS = 8;
    private static final PlantStageRemap REMAP = PlantStageRemap.resolve(
        PlantStageTable.parse(JsonParser.parseString(JSON).getAsJsonObject()),
        s -> Map.of("tfp:plant/allium", PLANT, "tfp:plant/dormant_look[look=0]", DORMANT).getOrDefault(s, -1));
    private static final PlantStageRemap.ColumnClimate COOL = new PlantStageRemap.ColumnClimate() {
        @Override
        public float rainfallVariance(int x, int z) {
            return 0f;
        }

        @Override
        public float averageTemperature(int x, int y, int z) {
            return 10f;
        }
    };
    private static final PlantStageSource.Viewer VIEWER = new PlantStageSource.Viewer(UUID.randomUUID(), -64, 20_000f, COOL);

    private static final class Source implements PlantStageSource {
        ClockSnapshot clock;

        Source(long calendarTicks) {
            clock = new ClockSnapshot(1, 0, calendarTicks, DAYS, true);
        }

        @Override
        public PlantStageRemap remap() {
            return REMAP;
        }

        @Override
        public Viewer viewer(UUID player) {
            return VIEWER;
        }

        @Override
        public ClockSnapshot clock() {
            return clock;
        }
    }

    /** The first calendar tick (in 1/96 year steps) at which block (x, z) is or is not dormant. */
    private static long tickWhere(boolean dormant, int x, int y, int z) {
        long year = CalendarTicks.ticksInYear(DAYS);
        for (int i = 0; i < 96; i++) {
            long tick = year * i / 96;
            PlantStageRemap.View view = VIEWER.view(new ClockSnapshot(1, 0, tick, DAYS, true), z);
            if ((REMAP.block(PLANT, x, y, z, COOL, view) == DORMANT) == dormant) {
                return tick;
            }
        }
        throw new AssertionError("no " + (dormant ? "dormant" : "blooming") + " tick");
    }

    private static PlantStagePacketHandler handler(long tick) {
        return new PlantStagePacketHandler(new Source(tick), Logger.getLogger("test"));
    }

    /** A list-palette section: stone below, the plant at (3, 1, 5), air above. */
    private static DataPalette plantSection() {
        DataPalette data = DataPalette.createForChunk();
        ListPalette palette = new ListPalette(4);
        BitStorage storage = new BitStorage(4, 4096);
        for (int i = 0; i < 4096; i++) {
            int y = i >> 8;
            int id = y == 0 ? STONE : AIR;
            if (i == index(3, 1, 5)) {
                id = PLANT;
            }
            storage.set(i, palette.stateToId(id));
        }
        data.palette = palette;
        data.storage = storage;
        return data;
    }

    private static DataPalette stoneSection() {
        DataPalette data = DataPalette.createForChunk();
        data.palette = new SingletonPalette(STONE);
        data.storage = null;
        return data;
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static byte[] bytes(DataPalette data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataPalette.write(new NetStreamOutput(out), data);
        return out.toByteArray();
    }

    private static Column column(DataPalette... sections) {
        BaseChunk[] chunks = new BaseChunk[sections.length];
        for (int i = 0; i < sections.length; i++) {
            chunks[i] = new Chunk_v1_18(ClientVersion.V_1_21_4, 4096, sections[i], DataPalette.createForBiome());
        }
        return new Column(2, -3, true, chunks, new TileEntity[0]);
    }

    @Test
    void chunkSectionWithAPlantShowsTheDormantLookInItsDormantWeeksOnly() throws IOException {
        // the plant at section 1 of chunk (2, -3): block (2*16+3, -64+16+1, -3*16+5)
        int x = 35;
        int y = -47;
        int z = -43;
        DataPalette stone = stoneSection();
        byte[] stoneBefore = bytes(stone);
        DataPalette plants = plantSection();
        Column dormant = column(stone, plants);
        assertThat(handler(tickWhere(true, x, y, z)).column(dormant, REMAP, VIEWER)).isTrue();
        assertThat(plants.get(3, 1, 5)).isEqualTo(DORMANT);
        assertThat(plants.get(3, 0, 5)).isEqualTo(STONE);
        assertThat(plants.get(3, 2, 5)).isEqualTo(AIR);
        assertThat(bytes(stone)).isEqualTo(stoneBefore);

        DataPalette blooming = plantSection();
        byte[] before = bytes(blooming);
        assertThat(handler(tickWhere(false, x, y, z)).column(column(stoneSection(), blooming), REMAP, VIEWER)).isFalse();
        assertThat(bytes(blooming)).isEqualTo(before);
    }

    @Test
    void chunkWithoutPlantsIsLeftByteIdentical() throws IOException {
        DataPalette a = stoneSection();
        DataPalette b = DataPalette.createForChunk();
        ListPalette palette = new ListPalette(4);
        BitStorage storage = new BitStorage(4, 4096);
        for (int i = 0; i < 4096; i++) {
            storage.set(i, palette.stateToId(i % 3 == 0 ? STONE : AIR));
        }
        b.palette = palette;
        b.storage = storage;
        byte[] beforeA = bytes(a);
        byte[] beforeB = bytes(b);
        for (int i = 0; i < 96; i += 7) {
            long tick = CalendarTicks.ticksInYear(DAYS) * i / 96;
            assertThat(handler(tick).column(column(a, b), REMAP, VIEWER)).isFalse();
        }
        assertThat(bytes(a)).isEqualTo(beforeA);
        assertThat(bytes(b)).isEqualTo(beforeB);
    }

    @Test
    void noCalendarReadingPassesEverything() {
        Source source = new Source(0);
        source.clock = ClockSnapshot.UNSET;
        PlantStagePacketHandler h = new PlantStagePacketHandler(source, Logger.getLogger("test"));
        DataPalette plants = plantSection();
        assertThat(h.column(column(plants), REMAP, VIEWER)).isFalse();
        assertThat(h.block(PLANT, 1, 2, 3, REMAP, VIEWER)).isEqualTo(PLANT);
    }

    @Test
    void blockChangesFollowTheSameRule() {
        int x = 10;
        int y = 70;
        int z = -20;
        assertThat(handler(tickWhere(true, x, y, z)).block(PLANT, x, y, z, REMAP, VIEWER)).isEqualTo(DORMANT);
        assertThat(handler(tickWhere(false, x, y, z)).block(PLANT, x, y, z, REMAP, VIEWER)).isEqualTo(PLANT);
        assertThat(handler(tickWhere(true, x, y, z)).block(STONE, x, y, z, REMAP, VIEWER)).isEqualTo(STONE);

        WrapperPlayServerMultiBlockChange.EncodedBlock plant = new WrapperPlayServerMultiBlockChange.EncodedBlock(PLANT, x, y, z);
        WrapperPlayServerMultiBlockChange.EncodedBlock stone = new WrapperPlayServerMultiBlockChange.EncodedBlock(STONE, x + 1, y, z);
        assertThat(handler(tickWhere(true, x, y, z)).blocks(
            new WrapperPlayServerMultiBlockChange.EncodedBlock[] {plant, stone}, REMAP, VIEWER)).isTrue();
        assertThat(plant.getBlockId()).isEqualTo(DORMANT);
        assertThat(stone.getBlockId()).isEqualTo(STONE);
        assertThat(handler(tickWhere(false, x, y, z)).blocks(
            new WrapperPlayServerMultiBlockChange.EncodedBlock[] {new WrapperPlayServerMultiBlockChange.EncodedBlock(PLANT, x, y, z)},
            REMAP, VIEWER)).isFalse();
    }

    @Test
    void encodeKeepsVanillaPaletteWidths() {
        int[] cells = new int[4096];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i % 20;
        }
        DataPalette data = DataPalette.createForChunk();
        assertThat(PlantSectionRewrite.encode(data, cells)).isTrue();
        assertThat(data.palette).isInstanceOf(MapPalette.class);
        assertThat(data.storage.getBitsPerEntry()).isEqualTo(5);
        assertThat(PlantSectionRewrite.decode(data)).isEqualTo(cells);

        int[] two = new int[4096];
        two[7] = 9;
        DataPalette small = DataPalette.createForChunk();
        assertThat(PlantSectionRewrite.encode(small, two)).isTrue();
        assertThat(small.palette).isInstanceOf(ListPalette.class);
        assertThat(small.storage.getBitsPerEntry()).isEqualTo(4);
        assertThat(PlantSectionRewrite.decode(small)).isEqualTo(two);

        int[] same = new int[4096];
        assertThat(PlantSectionRewrite.encode(small, same)).isTrue();
        assertThat(small.palette).isInstanceOf(SingletonPalette.class);

        DataPalette global = new DataPalette(GlobalPalette.INSTANCE, new BitStorage(15, 4096), PaletteType.CHUNK);
        int[] wide = new int[4096];
        for (int i = 0; i < wide.length; i++) {
            wide[i] = i;
        }
        assertThat(PlantSectionRewrite.encode(global, wide)).isTrue();
        assertThat(global.palette).isSameAs(GlobalPalette.INSTANCE);
        assertThat(global.storage.getBitsPerEntry()).isEqualTo(15);
        assertThat(PlantSectionRewrite.decode(global)).isEqualTo(wide);

        int[] tooMany = new int[4096];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = i % 300;
        }
        DataPalette list = plantSection();
        Object before = list.palette;
        assertThat(PlantSectionRewrite.encode(list, tooMany)).isFalse();
        assertThat(list.palette).isSameAs(before);
    }

    @Test
    void theReflectiveHandlerNameResolvesInThePacketLayer() throws Exception {
        Class<?> handler = Class.forName("dev.tfp.paper.packet.plantstage.PlantStagePacketHandler");
        Object created = handler.getConstructor(PlantStageSource.class, Logger.class)
            .newInstance(new Source(0), Logger.getLogger("test"));
        assertThat(((PacketHandler) created).sendTypes()).containsExactlyInAnyOrder("CHUNK_DATA", "BLOCK_CHANGE",
            "MULTI_BLOCK_CHANGE");
    }
}
