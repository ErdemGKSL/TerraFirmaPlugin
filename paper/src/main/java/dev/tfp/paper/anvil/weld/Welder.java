/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (weld, getWeldStatus, canWeldNow),
 * file: src/main/java/net/dries007/tfc/common/recipes/WeldingRecipe.java @ c3b4d9536 (assemble),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (the hammer branch of interactWithAnvil)
 */
package dev.tfp.paper.anvil.weld;

import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.WeldingRecipe;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.forge.ForgingBonus;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tool.ForgingConfig;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.anvil.forge.ForgeHeat;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.tool.ForgingBonusPdc;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-06: welding the anvil's two inputs (TFC {@code AnvilBlockEntity.weld} and {@code getWeldStatus}). Pure on the record
 * and the stacks; the world effects are the caller's. Thread of the anvil's owner.
 *
 * <p>The first recipe that accepts the two inputs (either order) decides; a tier, heat or flux problem is reported, never a
 * different recipe tried. The result takes the heat of the warmer input and the forging bonus by the recipe's rule.
 */
public final class Welder {
    /** TFC {@code AnvilBlockEntity.WeldStatus}. */
    public enum Status {
        PROBLEM_TIER, PROBLEM_HEAT, PROBLEM_FLUX, PROBLEM_INGOTS, PROBLEM_UNKNOWN, WELDABLE
    }

    /** What a weld attempt did, TFC's {@code InteractionResult}: SUCCESS, FAIL (a message was due) or PASS (not attempted). */
    public enum Result {
        PASS, FAIL, SUCCESS
    }

    public static final String MSG_TIER = "anvil.weld.tier_too_low";
    public static final String MSG_HEAT = "anvil.weld.not_hot_enough";
    public static final String MSG_FLUX = "anvil.weld.no_flux";

    /**
     * @param stacks   the stack codec of the device
     * @param book     the welding recipes
     * @param ingredients the ingredient context
     * @param canWeld  whether an item is hot enough to weld ({@code IHeatView.canWeld}); true for an item without heat
     * @param heat     passes the heat of an input to the result
     * @param output   makes the result stack of a recipe from the main input ({@code ItemStackProvider.getSingleStack})
     * @param config   the forging configuration (the bonus's efficiency)
     */
    public record Env(StackCodec stacks, WeldBook book, Supplier<IngredientContext<ItemStack>> ingredients, Predicate<ItemStack> canWeld,
            ForgeHeat heat, BiFunction<WeldBook.Entry, ItemStack, ItemStack> output, Supplier<ForgingConfig> config) {
        public Env {
            Objects.requireNonNull(stacks, "stacks");
            Objects.requireNonNull(book, "book");
            Objects.requireNonNull(ingredients, "ingredients");
            Objects.requireNonNull(canWeld, "canWeld");
            Objects.requireNonNull(heat, "heat");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(config, "config");
        }
    }

    /** @param record the record to store; @param result what happened; @param message the message key for the player, or null */
    public record Outcome(DeviceRecord<AnvilState> record, Result result, String message) {
    }

    private final Env env;

    public Welder(Env env) {
        this.env = Objects.requireNonNull(env, "env");
    }

    private Optional<WeldBook.Entry> find(ItemStack main, ItemStack second) {
        WeldBook.Compiled c = env.book().get();
        return c.recipes().find(main, second, env.ingredients().get()).flatMap(c::entry);
    }

    /** TFC {@code getWeldStatus} of the two inputs and the flux. */
    public Status status(int tier, ItemStack main, ItemStack second, ItemStack flux) {
        if (StackCodec.isEmpty(main) && StackCodec.isEmpty(second)) {
            return Status.PROBLEM_INGOTS;
        }
        Optional<WeldBook.Entry> found = find(main, second);
        if (found.isEmpty()) {
            return Status.PROBLEM_UNKNOWN;
        }
        if (!found.get().recipe().isCorrectTier(tier)) {
            return Status.PROBLEM_TIER;
        }
        if (!hotEnough(main) || !hotEnough(second)) {
            return Status.PROBLEM_HEAT;
        }
        return StackCodec.isEmpty(flux) ? Status.PROBLEM_FLUX : Status.WELDABLE;
    }

    private boolean hotEnough(ItemStack stack) {
        return StackCodec.isEmpty(stack) || env.canWeld().test(stack);
    }

    /** TFC {@code canWeldNow} of the anvil's {@code record}. */
    public boolean canWeldNow(int tier, DeviceRecord<AnvilState> record) {
        StackCodec s = env.stacks();
        return status(tier, s.decode(record.slot(AnvilSlots.INPUT_MAIN)), s.decode(record.slot(AnvilSlots.INPUT_SECOND)),
            s.decode(record.slot(AnvilSlots.CATALYST))) == Status.WELDABLE;
    }

    /** TFC {@code AnvilBlockEntity.weld}. */
    public Outcome weld(int tier, DeviceRecord<AnvilState> record) {
        StackCodec s = env.stacks();
        ItemStack main = s.decode(record.slot(AnvilSlots.INPUT_MAIN));
        ItemStack second = s.decode(record.slot(AnvilSlots.INPUT_SECOND));
        ItemStack flux = s.decode(record.slot(AnvilSlots.CATALYST));
        Status status = status(tier, main, second, flux);
        switch (status) {
            case PROBLEM_INGOTS, PROBLEM_UNKNOWN -> {
                return new Outcome(record, Result.PASS, null);
            }
            case PROBLEM_TIER -> {
                return new Outcome(record, Result.FAIL, MSG_TIER);
            }
            case PROBLEM_HEAT -> {
                return new Outcome(record, Result.FAIL, MSG_HEAT);
            }
            case PROBLEM_FLUX -> {
                return new Outcome(record, Result.FAIL, MSG_FLUX);
            }
            default -> {
            }
        }
        WeldBook.Entry entry = find(main, second).orElseThrow();
        ItemStack result = env.output().apply(entry, main);
        if (StackCodec.isEmpty(result)) {
            return new Outcome(record, Result.PASS, null); // the result item does not exist (yet): nothing is spent
        }
        WeldingRecipe recipe = entry.recipe();
        if (recipe.bonus() != WeldingRecipe.Bonus.IGNORE) {
            ForgingBonus mainBonus = ForgingBonus.values()[ForgingBonusPdc.read(main).ordinal()];
            ForgingBonus secondBonus = ForgingBonus.values()[ForgingBonusPdc.read(second).ordinal()];
            ForgingQuality quality = ForgingQuality.values()[recipe.resultBonus(mainBonus, secondBonus).ordinal()];
            ForgingBonusPdc.write(result, quality, env.config().get());
        }
        // always copy heat from inputs since we have two
        env.heat().keep(main, result);
        env.heat().keep(second, result);
        ItemStack rest = flux.clone();
        rest.setAmount(flux.getAmount() - 1);
        DeviceRecord<AnvilState> next = record.withSlot(AnvilSlots.INPUT_MAIN, s.encode(result))
            .withSlot(AnvilSlots.INPUT_SECOND, s.encode(ItemStack.empty()))
            .withSlot(AnvilSlots.CATALYST, s.encode(rest.getAmount() <= 0 ? ItemStack.empty() : rest));
        return new Outcome(next, Result.SUCCESS, null);
    }
}
