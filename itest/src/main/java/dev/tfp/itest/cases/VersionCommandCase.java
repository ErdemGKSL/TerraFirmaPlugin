/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * M1-BOOT-01: {@code /tfp version} is registered (Brigadier, from the bootstrapper) and answers with the
 * enabled plugin's report. Dispatched on the global region thread with a feedback-capturing sender.
 */
public final class VersionCommandCase implements SelfTestCase {
    @Override
    public String name() {
        return "/tfp version responds (M1-BOOT-01)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(15);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        CompletableFuture<String> out = new CompletableFuture<>();
        List<String> lines = new CopyOnWriteArrayList<>();
        tfp.scheduler().global(() -> {
            try {
                if (Bukkit.getCommandMap().getCommand("tfp") == null) {
                    throw new AssertionError("no 'tfp' command registered");
                }
                CommandSender sender = Bukkit.createCommandSender(c -> {
                    String text = PlainTextComponentSerializer.plainText().serialize(c);
                    lines.add(text);
                    if (text.contains("TerraFirmaPlugin") && text.contains("status:")) {
                        out.complete(text.lines().findFirst().orElse(text) + " | " + text.lines()
                                .filter(l -> l.contains("status:")).findFirst().orElse("").trim());
                    }
                });
                if (!Bukkit.dispatchCommand(sender, "tfp version")) {
                    throw new AssertionError("dispatchCommand(\"tfp version\") returned false");
                }
                if (!out.isDone()) {
                    throw new AssertionError("unexpected reply: " + lines);
                }
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }
}
