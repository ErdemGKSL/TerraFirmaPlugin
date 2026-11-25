/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgingComponent.java @ c3b4d9536 (steps, work, target, recipe;
 * withRecipe keeps the steps and the work),
 * src/main/java/net/dries007/tfc/common/component/forge/ForgingCapability.java @ c3b4d9536 (clearRecipeIfNotWorked)
 */
package dev.tfp.paper.anvil.plan;

import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The forging state of an item on an anvil (TFC {@code ForgingComponent}, PAR-METAL-04) as item PDC {@code tfp:forging}: the
 * selected plan's recipe id, the work target seeded when it was selected, the work offset and the step history. TFC keeps the
 * same four values in a data component on the main input; the PDC string is
 * {@code recipe;target;work;total;STEP,STEP,STEP} (the last three steps oldest first). The anvil forging screen (PAR-METAL-05)
 * adds steps with {@link #withStep}.
 *
 * <p>Threads: an ItemStack is plain data; call this on the thread that owns the stack.
 */
public final class ForgingPdc {
    public static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("tfp:forging"));

    /**
     * @param recipe the plan's recipe id, for example {@code tfc:anvil/metal/pickaxe_head/copper}
     * @param target the work target of the plan in this world
     * @param work   the work offset so far
     * @param steps  the step history
     */
    public record Forging(String recipe, int target, int work, ForgeSteps steps) {
        public Forging {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(steps, "steps");
            if (recipe.isEmpty() || recipe.indexOf(';') >= 0) {
                throw new IllegalArgumentException("bad recipe id: " + recipe);
            }
        }

        /** TFC {@code withRecipe}: another plan on the same work: new recipe and target, the steps and the work stay. */
        public Forging withPlan(String newRecipe, int newTarget) {
            return new Forging(newRecipe, newTarget, work, steps);
        }

        /** TFC {@code withStep(step, amount)}. */
        public Forging withStep(ForgeStep step, int amount) {
            return new Forging(recipe, target, work + amount, steps.withStep(step));
        }

        /** TFC {@code ForgeSteps.isWorked}. */
        public boolean worked() {
            return steps.isWorked();
        }
    }

    private ForgingPdc() {
    }

    /** The stack's forging state, or null for none (or an unreadable value). */
    public static Forging read(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasItemMeta()) {
            return null;
        }
        String s = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return s == null ? null : parse(s);
    }

    /** Stores {@code forging} on {@code stack}, or removes the entry for null. */
    public static void write(ItemStack stack, Forging forging) {
        stack.editMeta(meta -> {
            if (forging == null) {
                meta.getPersistentDataContainer().remove(KEY);
            } else {
                meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, format(forging));
            }
        });
    }

    /**
     * TFC {@code clearRecipeIfNotWorked}: removes the forging state of {@code stack} unless a step was taken.
     *
     * @return {@code true} if the state was removed
     */
    public static boolean clearIfNotWorked(ItemStack stack) {
        Forging f = read(stack);
        if (f != null && !f.worked()) {
            write(stack, null);
            return true;
        }
        return false;
    }

    static String format(Forging f) {
        StringBuilder steps = new StringBuilder();
        for (ForgeStep step : f.steps().steps()) {
            if (steps.length() > 0) {
                steps.append(',');
            }
            steps.append(step.name());
        }
        return f.recipe() + ";" + f.target() + ";" + f.work() + ";" + f.steps().total() + ";" + steps;
    }

    static Forging parse(String s) {
        String[] p = s.split(";", -1);
        if (p.length != 5) {
            return null;
        }
        try {
            List<ForgeStep> steps = new ArrayList<>(3);
            if (!p[4].isEmpty()) {
                for (String name : p[4].split(",")) {
                    steps.add(ForgeStep.valueOf(name));
                }
            }
            if (steps.size() > 3) {
                return null;
            }
            return new Forging(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                new ForgeSteps(steps, Integer.parseInt(p[3])));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
