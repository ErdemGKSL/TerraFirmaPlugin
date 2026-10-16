package dev.tfp.spike.jar01;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;

/** Brigadier command registered through LifecycleEvents.COMMANDS. */
final class Jar01Command implements BasicCommand {
    static final AtomicInteger RUNS = new AtomicInteger();

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        RUNS.incrementAndGet();
        stack.getSender().sendMessage(Component.text("jar01 ok args=" + String.join(",", args)));
    }
}
