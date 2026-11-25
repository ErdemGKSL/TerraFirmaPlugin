/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rotation.spin;

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
import com.github.retrooper.packetevents.util.Quaternion4f;
import dev.tfp.core.rotation.RotationAxis;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * PAR-MECH-12: the spinning display's metadata under the 1.21.11 and 26.3 PacketEvents mappings (indices and values; the
 * item stack and the bytes need a live Netty allocator, the in-game check covers them), as {@code OverlayDisplayPacketsTest}.
 */
class SpinPacketsTest {
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

    @ParameterizedTest
    @EnumSource(Version.class)
    void theSegmentCarriesDelayZeroTheDurationAndBothRotations(Version version) {
        install(version.server);
        List<EntityData<?>> data = PacketSpinWire.segment(RotationAxis.Z, 1.2, 37);
        Map<Integer, Object> byIndex = data.stream().collect(Collectors.toMap(EntityData::getIndex, EntityData::getValue));
        assertThat(byIndex).containsEntry(PacketSpinWire.INTERPOLATION_DELAY, 0).containsEntry(PacketSpinWire.INTERPOLATION_DURATION, 37);
        Quaternion4f left = (Quaternion4f) byIndex.get(PacketSpinWire.LEFT_ROTATION);
        assertThat(left.getZ()).isCloseTo((float) Math.sin(0.6), within(1e-6f));
        assertThat(left.getW()).isCloseTo((float) Math.cos(0.6), within(1e-6f));
        Quaternion4f right = (Quaternion4f) byIndex.get(PacketSpinWire.RIGHT_ROTATION);
        float[] r = SpinQuaternions.right(RotationAxis.Z, 1.2);
        assertThat(new float[] {right.getX(), right.getY(), right.getZ(), right.getW()}).containsExactly(r);
        List<Integer> spawn = PacketSpinWire.full(RotationAxis.Z, 0).stream().map(EntityData::getIndex).toList();
        assertThat(spawn).contains(8, 9, 11, 12, 13, 14, 17, 24);
        assertThat(PacketSpinWire.full(RotationAxis.Z, 0).stream().filter(d -> d.getIndex() == 9).findFirst().orElseThrow()
            .getValue()).isEqualTo(0);
    }

}
