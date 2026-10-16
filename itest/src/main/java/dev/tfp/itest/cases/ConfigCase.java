/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.config.ConfigCodec;
import dev.tfp.core.install.InstallStatus;
import dev.tfp.core.install.ThirdPartyEdits;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.config.Yamls;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.install.Installer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Our {@code config.yml} parses without warnings (M1-BOOT-03) and the third-party config edits of
 * M1-INST-02 are present in the CraftEngine/BetterHud/BetterModel files. Read-only. A pending restart
 * (first start of a fresh server: RESTART-effect keys were just written) is reported, not failed; blocking
 * install problems (degraded without a pending restart) fail.
 */
public final class ConfigCase implements SelfTestCase {
    @Override
    public String name() {
        return "config.yml valid and CraftEngine/BetterHud/BetterModel config edits present (M1-BOOT-03, M1-INST-02)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(20);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        return CompletableFuture.supplyAsync(() -> check(tfp), tfp.scheduler().ioExecutor());
    }

    private static String check(TfpPlugin tfp) {
        if (tfp.config() == null) {
            throw new AssertionError("no config service (onLoad did not pass the compatibility gate)");
        }
        String text = read(tfp.config().file()).orElseThrow(() -> new AssertionError(tfp.config().file() + " is missing"));
        ConfigCodec.Parsed parsed = ConfigCodec.parse(Yamls.load(text));
        if (!parsed.warnings().isEmpty()) {
            throw new AssertionError("config.yml warnings: " + String.join("; ", parsed.warnings()));
        }

        Path plugins = tfp.getDataPath().toAbsolutePath().getParent();
        Set<String> installed = new HashSet<>();
        for (String name : new String[] {Installer.CRAFTENGINE, "BetterModel", Installer.BETTERHUD}) {
            if (tfp.getServer().getPluginManager().getPlugin(name) != null) {
                installed.add(name);
            }
        }
        Path contentRoot = RegistryService.jarDataRoot(TfpPlugin.class.getClassLoader()).getParent();
        ConfigEditsCheck.Result edits = ConfigEditsCheck.check(ThirdPartyEdits.all(Installer.ceStates(contentRoot)),
                installed, path -> read(plugins.resolve(path)));
        if (!edits.problems().isEmpty()) {
            throw new AssertionError("config edits not in effect: " + String.join("; ", edits.problems()));
        }

        Installer.Result install = tfp.install();
        if (install == null) {
            throw new AssertionError("the installer did not run");
        }
        InstallStatus status = install.status();
        if (status.degraded() && !status.restartRequired()) {
            throw new AssertionError("install degraded: " + String.join(" / ", status.banner()));
        }
        List<String> notes = edits.notes();
        return "config.yml ok; " + edits.checked() + " edit(s) present for " + installed
                + (status.restartRequired() ? "; restart required (first start: RESTART keys take effect next start)" : "")
                + (notes.isEmpty() ? "" : "; notes: " + String.join("; ", notes));
    }

    private static Optional<String> read(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
