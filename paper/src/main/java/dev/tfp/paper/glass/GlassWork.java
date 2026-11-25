/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/GlassBlowpipeItem.java @ c3b4d9536 (use, onUseTick, finishUsingItem,
 *       consumeBlowpipe),
 * file: src/main/java/net/dries007/tfc/common/items/BlowpipeItem.java @ c3b4d9536 (the cooldown),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onItemStacked: a batch put on a blowpipe),
 * file: src/main/java/net/dries007/tfc/client/GlassblowingOverlays.java @ c3b4d9536 (the steps, shown on the action bar)
 */
package dev.tfp.paper.glass;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.glass.GlassBatches;
import dev.tfp.core.glass.GlassBlowpipes;
import dev.tfp.core.glass.GlassBlowpipes.Outcome;
import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.glass.GlassRecipe;
import dev.tfp.core.glass.GlassRules;
import dev.tfp.core.glass.GlassState;
import dev.tfp.paper.i18n.Messages;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-PROC-16: the blowpipe's operations. One use of a glass blowpipe with a glassworking tool (or an empty hand: blow and
 * stretch) in the other hand applies the operation; a matching recipe gives its result and empties the blowpipe. TFC holds
 * the use button for {@value GlassBlowpipes#OPERATION_USE_TICKS} ticks; a vanilla client cannot hold an item without a use
 * animation, so the operation is applied on the click and the {@value GlassBlowpipes#COOLDOWN_TICKS}-tick cooldown stays
 * (ledger). The steps so far show on the action bar (NP-UI-01 stands in for TFC's overlay).
 *
 * <p>Thread: the player's entity thread (interaction events already run there).
 */
public final class GlassWork {
    /** What the operations need from the plugin. */
    public record Services(Function<ItemStack, String> ids, ToDoubleFunction<ItemStack> temperature,
            Function<String, ItemStack> create, Function<CraftOutput, ItemStack> results, Collection<GlassRecipe> recipes,
            DoubleSupplier random, Messages messages) {
        public Services {
            Objects.requireNonNull(ids, "ids");
            Objects.requireNonNull(temperature, "temperature");
            Objects.requireNonNull(create, "create");
            Objects.requireNonNull(results, "results");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(messages, "messages");
        }
    }

    /** The result of {@link #use}. */
    public enum Result {
        /** Not a glass blowpipe in that hand, or no operation in the other hand: the click is left alone. */
        PASS,
        /** The blowpipe is not hot enough for the operation (TFC {@code InteractionResultHolder.fail}). */
        TOO_COLD,
        /** The blowpipe is cooling down from the last operation. */
        COOLING_DOWN,
        /** The operation was applied. */
        DONE
    }

    private static final String BATCH_TAG = "tfc:glass_batches";

    private final Services services;

    public GlassWork(Services services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The TFC-namespace id of {@code stack} ({@code tfp:} mapped to {@code tfc:}), {@code ""} for none. */
    String tfcId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String id = services.ids().apply(stack);
        return id == null ? "" : GlassPdc.toTfc(id);
    }

    /** Whether {@code stack} is a blowpipe that holds glass. */
    public boolean isGlassBlowpipe(ItemStack stack) {
        return GlassBlowpipes.isGlassBlowpipe(tfcId(stack));
    }

    /** TFC {@code GlassBlowpipeItem.use} and {@code finishUsingItem} for the blowpipe in {@code hand}. */
    public Result use(Player player, EquipmentSlot hand) {
        PlayerInventory inv = player.getInventory();
        ItemStack held = inv.getItem(hand);
        String pipe = tfcId(held);
        if (!GlassBlowpipes.isGlassBlowpipe(pipe)) {
            return Result.PASS;
        }
        EquipmentSlot otherSlot = hand == EquipmentSlot.HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack other = inv.getItem(otherSlot);
        double lookY = player.getLocation().getDirection().getY();
        Optional<GlassOperation> found = GlassBlowpipes.operationOf(tfcId(other), lookY);
        if (found.isEmpty()) {
            return Result.PASS;
        }
        GlassOperation op = found.get();
        if (!GlassRules.isHotEnough(op, (float) services.temperature().applyAsDouble(held))) {
            player.sendActionBar(Component.text(services.messages().get(player, "glass.not_hot_enough"), NamedTextColor.RED));
            return Result.TOO_COLD;
        }
        if (player.hasCooldown(held)) {
            return Result.COOLING_DOWN;
        }
        GlassState state = GlassPdc.read(held).orElse(GlassState.EMPTY);
        if (state.isEmpty()) {
            return Result.PASS; // a blowpipe item without data holds no glass (TFC: GlassWorking.apply is a no-op)
        }
        play(player, op, lookY);
        Outcome outcome = GlassBlowpipes.finish(services.recipes(), pipe, state, op, services.random());
        switch (outcome) {
            case Outcome.Stepped s -> {
                ItemStack next = held.clone();
                GlassPdc.write(next, s.state());
                inv.setItem(hand, next);
                showSteps(player, s.state());
                cooldown(player, next);
            }
            case Outcome.Finished f -> {
                ItemStack replacement = f.broke() ? null : services.create().apply(GlassPdc.toOurs(f.blowpipe()));
                inv.setItem(hand, replacement);
                give(player, services.results().apply(f.result()));
                Location at = player.getLocation();
                player.getWorld().playSound(at, f.broke() ? Sound.ENTITY_ITEM_BREAK : Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                    SoundCategory.PLAYERS, 1f, 1f);
                player.sendActionBar(Component.empty());
                if (replacement != null) {
                    cooldown(player, replacement);
                } else {
                    cooldown(player, held);
                }
            }
        }
        return Result.DONE;
    }

    private void cooldown(Player player, ItemStack stack) {
        player.setCooldown(stack, GlassBlowpipes.COOLDOWN_TICKS);
    }

    /** TFC {@code onUseTick}: the operation's sound, higher when looking up. */
    private void play(Player player, GlassOperation op, double lookY) {
        float pitch = 0.8f + (float) (lookY / 2f);
        String sound = op == GlassOperation.BLOW ? "minecraft:block.fire.extinguish" : "minecraft:block.anvil.use";
        player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 1f, Math.max(0.5f, Math.min(2f, pitch)));
    }

    /** The steps so far on the action bar. */
    void showSteps(Player player, GlassState state) {
        List<Component> names = new ArrayList<>();
        for (GlassOperation op : state.steps()) {
            String path = op.id().substring("tfc:".length());
            names.add(Component.translatable("glass_operation.tfc." + path, humanize(path)));
        }
        Component line = Component.text(services.messages().get(player, "glass.steps", state.steps().size(), GlassState.LIMIT) + " ",
            NamedTextColor.AQUA).append(Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(", ")), names)
            .color(NamedTextColor.WHITE));
        player.sendActionBar(line);
    }

    static String humanize(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private static void give(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    // ----- a batch put on a blowpipe -----

    /**
     * TFC {@code onItemStacked}: a single empty blowpipe clicked with the right button while a glass batch is on the
     * cursor becomes the blowpipe with that batch (one batch is used up). Returns the new blowpipe, or empty when the
     * stacks are not that pair.
     */
    public Optional<ItemStack> loadBatch(ItemStack blowpipe, ItemStack batch, boolean secondary) {
        if (!secondary || blowpipe == null || blowpipe.isEmpty() || blowpipe.getAmount() != 1 || batch == null || batch.isEmpty()
            || !GlassBlowpipes.isEmptyBlowpipe(tfcId(blowpipe)) || !isBatch(batch)) {
            return Optional.empty();
        }
        ItemStack loaded = services.create().apply(GlassPdc.toOurs(GlassBlowpipes.transform(tfcId(blowpipe))));
        if (loaded == null) {
            return Optional.empty();
        }
        GlassPdc.write(loaded, GlassRules.withBatch(GlassState.EMPTY, tfcId(batch)));
        return Optional.of(loaded);
    }

    private boolean isBatch(ItemStack batch) {
        return GlassBatches.accepts("#" + BATCH_TAG, tfcId(batch));
    }
}
