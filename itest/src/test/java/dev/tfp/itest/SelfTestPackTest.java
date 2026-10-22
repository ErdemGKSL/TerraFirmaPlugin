/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.config.Yamls;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** M1-INT-02: the self-test CraftEngine pack and its install into plugins/CraftEngine/resources/tfpitest. */
class SelfTestPackTest {
    @Test
    void packDefinesTheMarkerBlockInItsNamespace() throws Exception {
        Path src = SelfTestPack.source(getClass().getClassLoader());
        Map<?, ?> pack = Yamls.load(Files.readString(src.resolve("pack.yml")));
        assertThat(pack.get("namespace")).isEqualTo(SelfTestPack.NAMESPACE);
        Map<?, ?> blocks = (Map<?, ?>) Yamls.load(Files.readString(src.resolve("configuration/blocks.yml"))).get("blocks");
        assertThat(new java.util.ArrayList<Object>(blocks.keySet())).containsExactly(SelfTestPack.MARKER);
    }

    @Test
    void installsOnlyWhenCraftEngineResourcesExist(@TempDir Path plugins) throws Exception {
        String first = SelfTestPack.install(plugins, getClass().getClassLoader(), "1.0");
        assertThat(first).startsWith("not installed");
        assertThat(plugins.resolve("CraftEngine")).doesNotExist();

        Files.createDirectories(plugins.resolve("CraftEngine/resources"));
        String second = SelfTestPack.install(plugins, getClass().getClassLoader(), "1.0");
        Path target = plugins.resolve("CraftEngine/resources/tfpitest");
        assertThat(second).contains("3 written"); // pack.yml, blocks.yml, devices.yml (PAR-INFRA-18)
        assertThat(target.resolve("pack.yml")).exists();
        assertThat(target.resolve("configuration/blocks.yml")).exists();
        assertThat(target.resolve("configuration/devices.yml")).exists();
        assertThat(SelfTestPack.install(plugins, getClass().getClassLoader(), "1.0")).contains("0 written", "3 unchanged");
    }
}
