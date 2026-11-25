/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/AddTrimCommand.java @ c3b4d9536
 */
package dev.tfp.paper.command;

import dev.tfp.core.command.AddTrimCommand;
import dev.tfp.core.command.AddTrimCommand.ArmorPiece;
import dev.tfp.core.command.AddTrimCommand.Failure;
import dev.tfp.core.command.AddTrimCommand.Lookup;
import dev.tfp.core.metal.Metal;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.concurrent.CompletableFuture;
import org.bukkit.NamespacedKey;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * {@code /tfp trim <material> <pattern> [metal]} (TFC {@code AddTrimCommand}). Without a metal the trim goes onto the
 * armour in the main hand; with one, the four armour pieces of that metal are given, trimmed. The rules (failure order,
 * the untrimmed set that still reports the failure once per piece) are {@link AddTrimCommand}.
 *
 * <p>A material item is a trim material when it is one of the vanilla ingredients ({@link #VANILLA_MATERIALS}); a pattern
 * item is one when it is a vanilla {@code <pattern>_armor_trim_smithing_template}. TFC's own trim materials (gems and
 * metals) are PAR-DATA-11 and answer "not recognised" until then.
 */
public final class TrimCommands {
    /** Vanilla trim material ingredients (TFC: {@code TrimMaterials.getFromIngredient}) to the material's key. */
    static final Map<String, String> VANILLA_MATERIALS = Map.ofEntries(
            Map.entry("minecraft:quartz", "quartz"), Map.entry("minecraft:iron_ingot", "iron"),
            Map.entry("minecraft:netherite_ingot", "netherite"), Map.entry("minecraft:redstone", "redstone"),
            Map.entry("minecraft:copper_ingot", "copper"), Map.entry("minecraft:gold_ingot", "gold"),
            Map.entry("minecraft:emerald", "emerald"), Map.entry("minecraft:diamond", "diamond"),
            Map.entry("minecraft:lapis_lazuli", "lapis"), Map.entry("minecraft:amethyst_shard", "amethyst"),
            Map.entry("minecraft:resin_brick", "resin"));
    private static final String TEMPLATE_SUFFIX = "_armor_trim_smithing_template";

    /** What a trim command did: the failure to report (if any) and how many times, the pieces given, and whether the held item
     * cannot carry a trim. */
    public record Outcome(Optional<Failure> failure, int reports, int given, boolean notApplied) {
        Outcome(Optional<Failure> failure, int reports, int given) {
            this(failure, reports, given, false);
        }
    }

    private final Scheduler scheduler;
    private final CustomItems items;

    public TrimCommands(Scheduler scheduler, CustomItems items) {
        this.scheduler = scheduler;
        this.items = items;
    }

    /** The trim material of an ingredient item id, or empty. */
    static Optional<TrimMaterial> material(String itemId) {
        String key = VANILLA_MATERIALS.get(itemId);
        return key == null ? Optional.empty() : Optional.ofNullable(lookup(RegistryKey.TRIM_MATERIAL, key));
    }

    /** The trim pattern of a smithing template item id, or empty. */
    static Optional<TrimPattern> pattern(String itemId) {
        if (!itemId.startsWith("minecraft:") || !itemId.endsWith(TEMPLATE_SUFFIX)) {
            return Optional.empty();
        }
        String name = itemId.substring("minecraft:".length(), itemId.length() - TEMPLATE_SUFFIX.length());
        return Optional.ofNullable(lookup(RegistryKey.TRIM_PATTERN, name));
    }

    /** {@code minecraft:name} in the server's registry; null when there is none (or no registry access, in tests). */
    private static <T extends Keyed> T lookup(RegistryKey<T> key, String name) {
        try {
            return RegistryAccess.registryAccess().getRegistry(key).get(NamespacedKey.minecraft(name));
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Adds the trim to {@code stack}; false when it is not armour (TFC: the item component is set anyway). */
    private static boolean trim(ItemStack stack, TrimMaterial material, TrimPattern pattern) {
        if (!(stack.getItemMeta() instanceof ArmorMeta meta)) {
            return false;
        }
        meta.setTrim(new ArmorTrim(material, pattern));
        stack.setItemMeta(meta);
        return true;
    }

    /** {@code /tfp trim <material> <pattern>}: the item in {@code player}'s main hand. Any thread. */
    public CompletableFuture<Outcome> held(Player player, String materialId, String patternId) {
        return onPlayer(player, () -> {
            Optional<TrimMaterial> material = material(materialId);
            Optional<TrimPattern> pattern = pattern(patternId);
            Optional<Failure> failure = AddTrimCommand.trimFailure(new Lookup(material.isPresent(), pattern.isPresent()));
            if (failure.isPresent()) {
                return new Outcome(failure, 1, 0);
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!trim(hand, material.get(), pattern.get())) {
                return new Outcome(Optional.empty(), 0, 0, true); // TFC's tfc.commands.trim.not_applied
            }
            player.getInventory().setItemInMainHand(hand);
            return new Outcome(Optional.empty(), 0, 0);
        });
    }

    /** {@code /tfp trim <material> <pattern> <metal>}: the four pieces of {@code metal}, trimmed when both lookups hold. */
    public CompletableFuture<Outcome> set(Player player, String materialId, String patternId, Metal metal) {
        return onPlayer(player, () -> {
            String name = metal.name().toLowerCase(Locale.ROOT);
            List<ItemStack> pieces = new ArrayList<>();
            for (ArmorPiece piece : ArmorPiece.values()) {
                items.create("tfp:metal/" + piece.name().toLowerCase(Locale.ROOT) + "/" + name, 1).ifPresent(pieces::add);
            }
            Optional<TrimMaterial> material = material(materialId);
            Optional<TrimPattern> pattern = pattern(patternId);
            AddTrimCommand.ArmorSet set = AddTrimCommand.armorSet(pieces.size() == ArmorPiece.values().length,
                    new Lookup(material.isPresent(), pattern.isPresent()));
            if (set.pieces().isEmpty()) {
                return new Outcome(set.failure(), 1, 0);
            }
            for (ItemStack piece : pieces) {
                if (set.failure().isEmpty()) {
                    trim(piece, material.get(), pattern.get());
                }
                player.getInventory().addItem(piece).values()
                        .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            }
            return new Outcome(set.failure(), set.failure().isPresent() ? set.pieces().size() : 0, set.pieces().size());
        });
    }

    private <T> CompletableFuture<T> onPlayer(Player player, java.util.function.Supplier<T> body) {
        CompletableFuture<T> out = new CompletableFuture<>();
        Runnable gone = () -> out.completeExceptionally(new IllegalStateException(player.getName() + " is gone"));
        boolean scheduled = scheduler.entity(player, () -> {
            try {
                out.complete(body.get());
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, gone);
        if (!scheduled) {
            gone.run();
        }
        return out;
    }
}
