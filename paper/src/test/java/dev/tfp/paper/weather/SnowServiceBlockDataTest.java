/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;
import org.junit.jupiter.api.Test;

/**
 * PAR-CLIM-10: SnowService reads and builds pointed dripstone (icicles) through state strings, never through a cast to
 * {@link PointedDripstone}. On Paper 26.x {@code CraftPointedDripstone} implements only {@code Speleothem}, so the cast in
 * the old field initialiser threw a ClassCastException and the plugin did not enable. The snow layer cast stays: 26.3's
 * {@code CraftSnowLayer} still implements {@code Snow} (checked with javap on paper-26.3 build 196).
 */
class SnowServiceBlockDataTest extends MockServerTest {

    /** A BlockData like Paper 26.x's: the right material and state string, but not a {@link PointedDripstone}. */
    private static BlockData speleothemOnly(String state) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getMaterial" -> Material.POINTED_DRIPSTONE;
                case "getAsString" -> state;
                case "toString" -> state;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test
    void anIcicleIsRecognisedWithoutTheInterface() {
        BlockData down = speleothemOnly("minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=false]");
        BlockData up = speleothemOnly("minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]");
        assertThat(down).isNotInstanceOf(PointedDripstone.class);
        assertThat(SnowService.isDownIcicle(down)).isTrue();
        assertThat(SnowService.isDownIcicle(up)).isFalse();
        assertThat(SnowService.isDownIcicle(Material.STONE.createBlockData())).isFalse();
    }

    @Test
    void theThicknessChangeKeepsTheOtherProperties() {
        String tip = "minecraft:pointed_dripstone[thickness=tip,vertical_direction=down,waterlogged=true]";
        assertThat(SnowService.withProperty(tip, "thickness", "frustum"))
            .isEqualTo("minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down,waterlogged=true]");
        assertThat(SnowService.withProperty("minecraft:stone", "a", "b")).isEqualTo("minecraft:stone[a=b]");
        assertThat(SnowService.withProperty("minecraft:snow[]", "layers", "2")).isEqualTo("minecraft:snow[layers=2]");
        assertThat(SnowService.withProperty("minecraft:snow[layers=1]", "x", "y")).isEqualTo("minecraft:snow[layers=1,x=y]");
        assertThat(SnowService.property(tip, "vertical_direction")).isEqualTo("down");
        assertThat(SnowService.property(tip, "missing")).isNull();
        assertThat(SnowService.property("minecraft:stone", "thickness")).isNull();
    }

    @Test
    void theIcicleTipStringIsADownwardTip() {
        // MockBukkit cannot parse pointed_dripstone properties; the real parse runs in the server enable smoke on
        // Paper 1.21.11 and 26.3 (the service builds this tip in its field initialiser).
        assertThat(SnowService.ICICLE_TIP).startsWith("minecraft:pointed_dripstone[");
        assertThat(SnowService.property(SnowService.ICICLE_TIP, "thickness")).isEqualTo("tip");
        assertThat(SnowService.property(SnowService.ICICLE_TIP, "vertical_direction")).isEqualTo("down");
        assertThat(SnowService.isDownIcicle(speleothemOnly(SnowService.ICICLE_TIP))).isTrue();
    }
}
