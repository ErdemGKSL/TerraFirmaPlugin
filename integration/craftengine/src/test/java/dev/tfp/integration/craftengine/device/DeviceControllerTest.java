/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine.device;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.persist.DataMap;
import dev.tfp.paper.device.DeviceBridge;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceRouter;
import dev.tfp.paper.device.ProbeDevice;
import java.util.Optional;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.NBT;
import net.momirealms.craftengine.libraries.nbt.StringTag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-02: the {@code tfp:device} block-entity controller round trip for a device kind, without a server. "Restart"
 * = save into a block-entity tag, CraftEngine's NBT writer and reader, load into a new controller, decode with the
 * kind's codec. The in-server place/restart/break check is the self-test {@code device.roundtrip} (PAR-INFRA-18).
 */
class DeviceControllerTest {
    private static final DeviceKind<ProbeDevice.State> KIND = ProbeDevice.kind();

    static CompoundTag restart(CompoundTag tag) {
        return NBT.readCompressed(NBT.writeCompressed(tag, false), false);
    }

    @Test
    void recordSurvivesSaveAndRestart() {
        DeviceRecord<ProbeDevice.State> rec = KIND.fresh()
                .withState(new ProbeDevice.State(24_000, 3))
                .withSlot(0, ItemBlob.of(new byte[] {10, 20, 30}))
                .withSlot(3, ItemBlob.of(new byte[] {7}))
                .withLastUpdateTick(123_456_789L);
        DeviceController a = new DeviceController(null);
        a.write(KIND.codec().encode(rec));

        CompoundTag be = new CompoundTag();
        be.putString("id", "tfp:device/probe"); // CraftEngine's own keys share the tag
        a.saveCustomData(be);
        assertThat(be.keySet()).contains("tfp:device", "id");

        DeviceController b = new DeviceController(null);
        b.loadCustomData(restart(be));
        assertThat(b.hasUnreadableData()).isFalse();
        assertThat(KIND.codec().decode(b.read())).isEqualTo(rec);
    }

    @Test
    void neverWrittenDeviceReadsEmpty() {
        DeviceController c = new DeviceController(null);
        c.loadCustomData(new CompoundTag());
        assertThat(c.read().size()).isZero();

        CompoundTag be = new CompoundTag();
        c.saveCustomData(be);
        DeviceController d = new DeviceController(null);
        d.loadCustomData(restart(be));
        assertThat(d.read().size()).isZero();
    }

    @Test
    void readAndWriteAreCopies() {
        DeviceController c = new DeviceController(null);
        DataMap map = new DataMap().putInt("x", 1);
        c.write(map);
        map.putInt("x", 2);
        assertThat(c.read().requireInt("x")).isEqualTo(1);
        c.read().putInt("x", 3);
        assertThat(c.read().requireInt("x")).isEqualTo(1);
    }

    /** A router that only answers {@link DeviceRouter#created}: the probe kind stamped at {@code tick}. */
    private static DeviceRouter creating(long tick) {
        return new DeviceRouter() {
            @Override
            public boolean use(Block block, Player player, EquipmentSlot hand, ItemStack item) {
                return false;
            }

            @Override
            public void neighbourChanged(Block block) {
            }

            @Override
            public void removed(World world, int x, int y, int z, String blockId, DataMap data) {
            }

            @Override
            public Optional<DataMap> created(String blockId) {
                return KIND.covers(blockId)
                        ? Optional.of(KIND.codec().encode(KIND.fresh().withLastUpdateTick(tick)))
                        : Optional.empty();
            }
        };
    }

    @Test
    void creationStampsOnlyADeviceWithoutARecord() {
        DeviceRouter router = creating(777);
        DeviceBridge.installRouter(router);
        try {
            DeviceController fresh = new DeviceController(null);
            fresh.stampIfFresh(ProbeDevice.ID);
            assertThat(KIND.codec().decode(fresh.read()).lastUpdateTick()).isEqualTo(777);

            DeviceRecord<ProbeDevice.State> old = KIND.fresh().withLastUpdateTick(5);
            DeviceController stored = new DeviceController(null);
            stored.write(KIND.codec().encode(old));
            stored.stampIfFresh(ProbeDevice.ID); // a chunk load of a written device changes nothing
            assertThat(KIND.codec().decode(stored.read())).isEqualTo(old);

            DeviceController other = new DeviceController(null);
            other.stampIfFresh("tfp:not_a_device");
            assertThat(other.read().size()).isZero();
        } finally {
            DeviceBridge.clearRouter(router);
        }
        DeviceController noRouter = new DeviceController(null);
        noRouter.stampIfFresh(ProbeDevice.ID); // module not enabled yet: first access stamps instead
        assertThat(noRouter.read().size()).isZero();
    }

    @Test
    void unreadableEnvelopeIsKeptVerbatim() {
        CompoundTag be = new CompoundTag();
        be.put("tfp:device", new StringTag("written by something else"));
        DeviceController c = new DeviceController(null);
        c.loadCustomData(be);
        assertThat(c.hasUnreadableData()).isTrue();
        CompoundTag saved = new CompoundTag();
        c.saveCustomData(saved);
        assertThat(saved.get("tfp:device")).isEqualTo(new StringTag("written by something else"));
    }
}
