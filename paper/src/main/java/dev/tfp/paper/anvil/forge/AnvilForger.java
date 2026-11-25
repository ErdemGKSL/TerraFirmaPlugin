/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (work)
 */
package dev.tfp.paper.anvil.forge;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.ForgingProgress;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgingBonus;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.anvil.plan.ForgingPdc;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.tool.ForgingBonusPdc;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-05: one hammer step on the anvil's main input (TFC {@code AnvilBlockEntity.work}): a hammer (in the anvil's
 * slot, else the player's main hand, else the off hand), the plan's recipe on this tier, an item hot enough, then the step
 * is added to the forging state, the hammer is damaged, and the item is destroyed (the offset left {@code [0, 150]}) or
 * finished (the recipe's rules and work target are met: the result replaces it with the heat of the input and the forging
 * bonus). Pure on the record and the stacks; the world effects are the caller's.
 *
 * <p>Threads: the viewer's thread, which owns the anvil's block (the record edit's thread).
 */
public final class AnvilForger {
    /** What the forger reads from the rest of the plugin. */
    public record Env(StackCodec stacks, Supplier<AnvilRecipes> recipes, Supplier<IngredientContext<ItemStack>> ingredients,
            Predicate<ItemStack> hammer, ForgeHeat heat, CustomItems items, Supplier<ForgingConfig> config, IntSupplier range,
            BiFunction<Player, ItemStack, ItemStack> damage) {
        public Env {
            Objects.requireNonNull(stacks, "stacks");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(ingredients, "ingredients");
            Objects.requireNonNull(hammer, "hammer");
            Objects.requireNonNull(heat, "heat");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(range, "range");
            Objects.requireNonNull(damage, "damage");
        }

        /** The hammer damage of the game: one point, through Paper (the durability events and TFC's forging bonus apply). */
        public static BiFunction<Player, ItemStack, ItemStack> realDamage() {
            return (player, stack) -> stack.damage(1, player);
        }
    }

    /** What a step did. */
    public enum Result {
        /** Nothing happened (no hammer, no plan, too cold, too low a tier, nothing to overwork). */
        REFUSED,
        /** The step was added. */
        WORKED,
        /** The offset left {@code [0, 150]}: the main input is gone. */
        DESTROYED,
        /** The recipe was met: the main input is the result. */
        COMPLETED
    }

    /**
     * @param record  the record to store
     * @param result  what happened
     * @param message the message key for the player (TFC's chat line), or null
     * @param bonus   the forging bonus of a completed item
     */
    public record Outcome(DeviceRecord<AnvilState> record, Result result, String message, ForgingQuality bonus) {
    }

    public static final String MSG_HAMMER = "anvil.forge.hammer_required";
    public static final String MSG_TIER = "anvil.forge.tier_too_low";
    public static final String MSG_COLD = "anvil.forge.not_hot_enough";

    private final Env env;

    public AnvilForger(Env env) {
        this.env = Objects.requireNonNull(env, "env");
    }

    /**
     * Applies {@code step} for {@code player} on the anvil's {@code record} of tier {@code tier}.
     *
     * <p>Any hammer taken by the step (damaged, or broken) is already stored: in the record for the anvil's slot, in the
     * player's inventory for a held one.
     */
    public Outcome work(Player player, DeviceRecord<AnvilState> record, int tier, ForgeStep step) {
        StackCodec stacks = env.stacks();
        ItemStack hammer = stacks.decode(record.slot(AnvilSlots.HAMMER));
        EquipmentSlot hand = null;
        if (!isHammer(hammer)) {
            hammer = null;
            for (EquipmentSlot h : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                ItemStack held = player.getInventory().getItem(h);
                if (hammer == null && isHammer(held)) {
                    hammer = held;
                    hand = h;
                }
            }
        }
        if (hammer == null) {
            return new Outcome(record, Result.REFUSED, MSG_HAMMER, ForgingQuality.NONE);
        }
        ItemStack main = stacks.decode(record.slot(AnvilSlots.INPUT_MAIN));
        ForgingPdc.Forging forging = StackCodec.isEmpty(main) ? null : ForgingPdc.read(main);
        if (forging == null) {
            return refused(record, null); // TFC: no recipe on the item, nothing to work
        }
        ForgingProgress before = new ForgingProgress(forging.steps(), forging.work());
        if (!before.canApply(step)) {
            return refused(record, null); // the first hit cannot overwork
        }
        Optional<AnvilRecipe> found = env.recipes().get().byId(forging.recipe());
        if (found.isEmpty()) {
            return refused(record, null);
        }
        AnvilRecipe recipe = found.get();
        if (!recipe.matches(main, tier, env.ingredients().get())) {
            return refused(record, MSG_TIER);
        }
        if (!env.heat().canWork(main)) {
            return refused(record, MSG_COLD);
        }
        ItemStack result = null;
        if (!StackCodec.isEmpty(main)) {
            result = result(recipe);
            if (result == null && finishes(recipe, forging, step)) {
                return refused(record, null); // the result item does not exist (yet): nothing is spent on it
            }
        }
        ForgingPdc.Forging next = forging.withStep(step, step.step());
        // damage the hammer (TFC Helpers.damageItem)
        DeviceRecord<AnvilState> out = record;
        ItemStack damaged = env.damage().apply(player, hammer);
        if (hand == null) {
            out = out.withSlot(AnvilSlots.HAMMER, stacks.encode(damaged == null || damaged.isEmpty() ? ItemStack.empty() : damaged));
        } else {
            player.getInventory().setItem(hand, damaged == null || damaged.isEmpty() ? null : damaged);
        }
        ForgingProgress progress = new ForgingProgress(next.steps(), next.work());
        if (progress.isDestroyed()) {
            return new Outcome(out.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(ItemStack.empty())), Result.DESTROYED, null,
                ForgingQuality.NONE);
        }
        if (recipe.checkComplete(progress, next.target(), env.range().getAsInt()) && result != null) {
            ForgingConfig config = env.config().get();
            ForgingBonus bonus = recipe.bonusFor(progress, next.target(), thresholds(config));
            ForgingQuality quality = ForgingQuality.values()[bonus.ordinal()];
            env.heat().keep(main, result); // always preserve the heat of the input
            if (quality != ForgingQuality.NONE) {
                ForgingBonusPdc.write(result, quality, config);
            }
            return new Outcome(out.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(result)), Result.COMPLETED, null, quality);
        }
        ItemStack worked = main.clone();
        ForgingPdc.write(worked, next);
        return new Outcome(out.withSlot(AnvilSlots.INPUT_MAIN, stacks.encode(worked)), Result.WORKED, null, ForgingQuality.NONE);
    }

    private boolean finishes(AnvilRecipe recipe, ForgingPdc.Forging forging, ForgeStep step) {
        ForgingPdc.Forging next = forging.withStep(step, step.step());
        ForgingProgress progress = new ForgingProgress(next.steps(), next.work());
        return !progress.isDestroyed() && recipe.checkComplete(progress, next.target(), env.range().getAsInt());
    }

    private Outcome refused(DeviceRecord<AnvilState> record, String message) {
        return new Outcome(record, Result.REFUSED, message, ForgingQuality.NONE);
    }

    private boolean isHammer(ItemStack stack) {
        return !StackCodec.isEmpty(stack) && env.hammer().test(stack);
    }

    /** {@code ForgingBonus.byRatio}'s thresholds by tier: the entry of NONE is ignored. */
    static double[] thresholds(ForgingConfig c) {
        return new double[] {0, c.modestThreshold(), c.wellThreshold(), c.expertThreshold(), c.perfectThreshold()};
    }

    /** The result of {@code recipe} as an item stack, or null when its item does not exist. */
    public ItemStack result(AnvilRecipe recipe) {
        String id = recipe.output().item();
        int count = Math.min(recipe.output().count(), 64);
        Optional<ItemStack> custom = env.items().create(id, count);
        if (custom.isPresent()) {
            return custom.get();
        }
        Material material = id.startsWith("minecraft:") ? Material.matchMaterial(id) : null;
        return material != null && material.isItem() ? new ItemStack(material, count) : null;
    }
}
