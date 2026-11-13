/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.packet.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.tfp.core.visuals.OverlayFace;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Golden values of the overlay display packets per face, built under the 1.21.11 and the 26.3 PacketEvents mappings
 * (the field values the wrappers encode: entity type and position, metadata indices, translation, rotation, view range,
 * item model and the face tint colour). Bytes are not compared, and the PacketEvents item stack itself is not built:
 * both need a live Netty allocator, which the unit tests do not have (the in-game steps check the encoded result).
 */
class OverlayDisplayPacketsTest {
    private PacketEventsAPI<?> previous;

    private void install(ServerVersion version) {
        previous = PacketEvents.getAPI();
        ServerManager server = () -> version;
        PacketEvents.setAPI(new PacketEventsAPI<Object>() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public void init() {
            }

            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public Object getPlugin() {
                return null;
            }

            @Override
            public ServerManager getServerManager() {
                return server;
            }

            @Override
            public ProtocolManager getProtocolManager() {
                return null;
            }

            @Override
            public PlayerManager getPlayerManager() {
                return null;
            }

            @Override
            public NettyManager getNettyManager() {
                return null;
            }

            @Override
            public ChannelInjector getInjector() {
                return null;
            }
        });
    }

    @AfterEach
    void restore() {
        PacketEvents.setAPI(previous);
    }

    enum Version {
        V1_21_11(ServerVersion.V_1_21_11), V26_3(ServerVersion.V_26_3);

        final ServerVersion server;

        Version(ServerVersion server) {
            this.server = server;
        }
    }

    /**
     * {face: position offset of the neighbour cell, translation, rotation, tint, scale z} for the ore at (10, 64, -3);
     * the right rotation (the untilt) is {@link #UNTILT} on every face (PAR-INFRA-41).
     */
    private static final Map<OverlayFace, float[]> GOLDEN = Map.of(
            OverlayFace.DOWN, new float[] {10.5f, 63.5f, -2.5f, 0, 0.49f, 0, 0.70710677f, 0, 0, 0.70710677f, 0x808080, -1},
            OverlayFace.UP, new float[] {10.5f, 65.5f, -2.5f, 0, -0.49f, 0, -0.70710677f, 0, 0, 0.70710677f, 0xFFFFFF, 1},
            OverlayFace.NORTH, new float[] {10.5f, 64.5f, -3.5f, 0, 0, 0.49f, 0, 1, 0, 0, 0xE3E3E3, 1},
            OverlayFace.SOUTH, new float[] {10.5f, 64.5f, -1.5f, 0, 0, -0.49f, 0, 0, 0, 1, 0xE3E3E3, 1},
            OverlayFace.WEST, new float[] {9.5f, 64.5f, -2.5f, 0.49f, 0, 0, 0, -0.70710677f, 0, 0.70710677f, 0xC6C6C6, 1},
            OverlayFace.EAST, new float[] {11.5f, 64.5f, -2.5f, -0.49f, 0, 0, 0, 0.70710677f, 0, 0.70710677f, 0xC6C6C6, 1});
    /** -22.5 degrees about x: (sin -11.25, 0, 0, cos 11.25). */
    private static final float[] UNTILT = {-0.19509032f, 0, 0, 0.98078528f};

    @ParameterizedTest
    @EnumSource(Version.class)
    void spawnAndDataPerFace(Version version) {
        install(version.server);
        for (OverlayFace face : OverlayFace.values()) {
            float[] g = GOLDEN.get(face);
            Vector3d pos = OverlayDisplayPackets.position(10, 64, -3, face);
            assertThat(new double[] {pos.getX(), pos.getY(), pos.getZ()}).as(face.name())
                    .containsExactly(new double[] {g[0], g[1], g[2]}, within(1e-6));
            WrapperPlayServerSpawnEntity spawn = OverlayDisplayPackets.spawn(77, pos);
            assertThat(spawn.getEntityId()).isEqualTo(77);
            assertThat(spawn.getEntityType()).isEqualTo(EntityTypes.ITEM_DISPLAY);
            assertThat(spawn.getUUID()).isPresent();
            assertThat(spawn.getUUID().get().version()).isEqualTo(4);

            List<EntityData<?>> data = OverlayDisplayPackets.transform(face, OverlayDisplayPackets.cellTranslation(face),
                    0.75f);
            Map<Integer, Object> byIndex = data.stream().collect(Collectors.toMap(EntityData::getIndex, EntityData::getValue));
            assertThat(byIndex.keySet()).containsExactlyInAnyOrder(11, 12, 13, 14, 17, 24);
            Vector3f t = (Vector3f) byIndex.get(11);
            assertThat(new float[] {t.getX(), t.getY(), t.getZ()}).as(face + " translation")
                    .containsExactly(new float[] {g[3], g[4], g[5]}, within(1e-6f));
            Quaternion4f q = (Quaternion4f) byIndex.get(13);
            assertThat(new float[] {q.getX(), q.getY(), q.getZ(), q.getW()}).as(face + " rotation")
                    .containsExactly(new float[] {g[6], g[7], g[8], g[9]}, within(1e-6f));
            Quaternion4f r = (Quaternion4f) byIndex.get(14);
            assertThat(new float[] {r.getX(), r.getY(), r.getZ(), r.getW()}).as(face + " right rotation")
                    .containsExactly(UNTILT, within(1e-6f));
            Vector3f s = (Vector3f) byIndex.get(12);
            assertThat(new float[] {s.getX(), s.getY(), s.getZ()}).as(face + " scale")
                    .containsExactly(new float[] {1, 1, g[11]}, within(0f));
            assertThat(byIndex.get(17)).isEqualTo(0.75f);
            assertThat(byIndex.get(24)).isEqualTo((byte) 0);
            OverlayDisplayPackets.ItemSpec item = OverlayDisplayPackets.spec("tfp:ore_overlay/native_gold", face.tint());
            assertThat(item.model().toString()).isEqualTo("tfp:ore_overlay/native_gold");
            assertThat(item.shade()).as(face + " tint").isEqualTo((int) g[10]);
        }
    }

    @ParameterizedTest
    @EnumSource(Version.class)
    void crackItemsRidersAndRemovals(Version version) {
        install(version.server);
        assertThat(OverlayDisplayPackets.spec("tfp:ore_overlay/native_gold/crack_3", OverlayFace.EAST.tint()))
                .isEqualTo(new OverlayDisplayPackets.ItemSpec(
                        new com.github.retrooper.packetevents.resources.ResourceLocation("tfp", "ore_overlay/native_gold/crack_3"),
                        0xC6C6C6));
        Vector3f up = OverlayDisplayPackets.riderTranslation(OverlayFace.UP);
        // seat at 0.98 above the base; the top face lies 0.01 above the block top, i.e. 1.01 above the base
        assertThat(up.getY()).isCloseTo(0.5f - 0.98f + 0.51f, within(1e-6f));
        Vector3f west = OverlayDisplayPackets.riderTranslation(OverlayFace.WEST);
        assertThat(new float[] {west.getX(), west.getY(), west.getZ()})
                .containsExactly(new float[] {-0.51f, -0.48f, 0}, within(1e-6f));
        assertThat(OverlayDisplayPackets.passengers(9, new int[] {1, 2}).getPassengers()).containsExactly(1, 2);
        assertThat(OverlayDisplayPackets.remove(new int[] {3, 4}).getEntityIds()).containsExactly(3, 4);
        assertThat(List.of(OverlayDisplayPackets.TRANSLATION, OverlayDisplayPackets.SCALE,
                OverlayDisplayPackets.LEFT_ROTATION, OverlayDisplayPackets.RIGHT_ROTATION, OverlayDisplayPackets.VIEW_RANGE,
                OverlayDisplayPackets.ITEM, OverlayDisplayPackets.DISPLAY_CONTEXT))
                .containsExactly(11, 12, 13, 14, 17, 23, 24);
    }
}
