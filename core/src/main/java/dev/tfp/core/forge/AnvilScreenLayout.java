/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/AnvilScreen.java @ c3b4d9536 (renderBg: the progress and target markers,
 * the rule icons and overlays, the last steps),
 * src/main/java/net/dries007/tfc/common/container/AnvilContainer.java @ c3b4d9536 (the slots)
 */
package dev.tfp.core.forge;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PAR-METAL-05: where TFC's anvil forging screen (176 x 207) lands in the 5-row chest (176 x 204) the plugin opens, and the
 * overlay frames that show the forging state. Pure; any thread. The datagen ({@code AnvilForgeArt}) draws the art from these
 * numbers and its test checks them against TFC's sources.
 *
 * <p>The slots and buttons must sit on the chest's 18-pixel grid, so the elements of TFC's screen move as blocks (in screen
 * pixels): the left column (second input, flux, the weld button, main input) by +4, the first two by -2 and the main input by
 * -4; the display of the rules and last steps and the eight step buttons by (+9, -2); the hammer and the plan button by +14
 * (the hammer by -4 and the plan button by -3). The progress bar stays; the player inventory moves up by 4.
 *
 * <p>Chest slots: the second input {@value #SECOND}, flux {@value #CATALYST}, weld {@value #WELD}, main input {@value #MAIN},
 * the plan button {@value #PLAN}, the hammer {@value #HAMMER}, and the step buttons from {@link #stepSlot}.
 */
public final class AnvilScreenLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(5);
    /** TFC's screen height (166 + 41). */
    public static final int TFC_HEIGHT = 207;
    /** The player inventory's offset in TFC's screen ({@code init(playerInv, 41)}). */
    public static final int TFC_INVENTORY_OFFSET = 41;

    // ---- slots ------------------------------------------------------------------------------------------------
    public static final int SECOND = 1;
    public static final int CATALYST = 10;
    public static final int WELD = 19;
    public static final int MAIN = 28;
    public static final int PLAN = 26;
    public static final int HAMMER = 35;

    /** TFC's slot item positions (x, y), main, second, hammer, catalyst, and the chest slots that hold them. */
    public static final int[][] TFC_SLOTS = {{22, 76}, {22, 20}, {138, 76}, {22, 38}};
    public static final int[] CHEST_SLOTS = {MAIN, SECOND, HAMMER, CATALYST};

    /** TFC's step button grid: columns at x 53 + 18 c, rows at y 56 + 18 r; here chest columns 3 to 6, rows 2 and 3. */
    public static final int BUTTON_X0 = 53;
    public static final int BUTTON_Y0 = 56;
    public static final int BUTTON_PITCH = 18;
    public static final int BUTTON_COLUMN = 3;
    public static final int BUTTON_ROW = 2;
    /** TFC's plan button (18 x 18 at (137, 56)) and weld button (18 x 18 at (21, 56)). */
    public static final int TFC_PLAN_X = 137;
    public static final int TFC_WELD_X = 21;
    public static final int TFC_BUTTON_Y = 56;

    // ---- the moves of TFC's elements, in screen pixels ------------------------------------------------------------
    /** The left column's second input, flux and the arrows. */
    public static final int LEFT_DX = 4;
    public static final int LEFT_DY = -2;
    /** The main input and the arrow heads beside it. */
    public static final int MAIN_DY = -4;
    /** The display of the rules and last steps and the step buttons. */
    public static final int CENTRE_DX = 9;
    public static final int CENTRE_DY = -2;
    /** The hammer. */
    public static final int HAMMER_DX = 14;
    public static final int HAMMER_DY = -4;
    /** The player inventory. */
    public static final int PLAYER_DY = CHEST.playerRowY(0) - (84 + TFC_INVENTORY_OFFSET);

    /** Work offsets the bar shows: {@code [0, 150]}; the marker x is {@code 11 + offset}. */
    public static final int BAR_X = 11;
    public static final int BAR_FRAMES = ForgeStep.LIMIT + 1;
    public static final int BAR_WIDTH = 156;
    public static final int BAR_COLUMNS = 12;
    public static final int BAR_ROWS = (BAR_FRAMES + BAR_COLUMNS - 1) / BAR_COLUMNS;
    /** TFC's {@code anvilAcceptableWorkRange} bound: below 2 the target is a pointer, from 2 a bracket. */
    public static final int POINTER_RANGE_BELOW = 2;
    /** The largest right limit of the bracket ({@code min(145, target + range)}). */
    public static final int RIGHT_LIMIT_MAX = 145;

    public static final int RULE_SLOTS = 3;
    /** Frames of a rule image: none, then (rule, unmatched) and (rule, matched) for each of the 30 rules. */
    public static final int RULE_FRAMES = 1 + 2 * ForgeRule.VALUES.length;
    public static final int RULE_COLUMNS = 8;
    public static final int RULE_ROWS = (RULE_FRAMES + RULE_COLUMNS - 1) / RULE_COLUMNS;
    public static final int STEP_FRAMES = 1 + ForgeStep.VALUES.length;
    public static final int STEP_COLUMNS = 3;
    public static final int STEP_ROWS = 3;

    // ---- the images -------------------------------------------------------------------------------------------
    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/anvil", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage PROGRESS = bar("progress", BAR_X, 104, 5);
    public static final GuiImage POINTER = bar("target", BAR_X, 98, 5);
    public static final GuiImage LEFT = bar("target_left", BAR_X, 96, 7);
    public static final GuiImage RIGHT = bar("target_right", BAR_X, 96, 7);
    /** The centre mark of a bracket wider than 2: frame {@code f} is drawn at x {@code 13 + f}. */
    public static final GuiImage CENTRE = bar("target_centre", 13, 94, 5);
    public static final GuiImage WELD_BUTTON = new GuiImage("tfp:gui/anvil_weld", CHEST.slotX(WELD) - 1, CHEST.slotY(WELD) - 1, 18, 18, 1,
        2);
    public static final GuiImage PLAN_BUTTON = new GuiImage("tfp:gui/anvil_plan_button", CHEST.slotX(PLAN) - 1, CHEST.slotY(PLAN) - 1, 18,
        18, 1, 3);

    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_TARGET = "target";
    public static final String KEY_LEFT = "target_left";
    public static final String KEY_RIGHT = "target_right";
    public static final String KEY_CENTRE = "target_centre";
    public static final String KEY_WELD = "weld";
    public static final String KEY_PLAN = "plan";

    private AnvilScreenLayout() {
    }

    private static GuiImage bar(String name, int x, int y, int height) {
        return new GuiImage("tfp:gui/anvil_" + name, x, y, BAR_WIDTH, height, BAR_ROWS, BAR_COLUMNS);
    }

    /** The overlay of rule {@code i} (0 to 2): 20 x 22 at (68 + 19 i, 11), TFC's (59 + 19 i, 13) moved with the display. */
    public static GuiImage ruleImage(int i) {
        checkIndex(i);
        return new GuiImage("tfp:gui/anvil_rule_" + i, 59 + 19 * i + CENTRE_DX, 13 + CENTRE_DY, 20, 22, RULE_ROWS, RULE_COLUMNS);
    }

    /** The icon of the {@code i}-th last step (0 the latest): 16 x 16 at (108 - 19 i, 32), TFC's (99 - 19 i, 34) moved. */
    public static GuiImage stepImage(int i) {
        checkIndex(i);
        return new GuiImage("tfp:gui/anvil_step_" + i, 99 - 19 * i + CENTRE_DX, 34 + CENTRE_DY, 16, 16, STEP_ROWS, STEP_COLUMNS);
    }

    private static void checkIndex(int i) {
        if (i < 0 || i >= RULE_SLOTS) {
            throw new IndexOutOfBoundsException("rule or step index " + i);
        }
    }

    /** The chest slot of {@code step}'s button: TFC's button grid moved onto chest columns 3 to 6 and rows 2 and 3. */
    public static int stepSlot(ForgeStep step) {
        int column = BUTTON_COLUMN + (step.buttonX() - BUTTON_X0) / BUTTON_PITCH;
        int row = BUTTON_ROW + (step.buttonY() - BUTTON_Y0) / BUTTON_PITCH;
        return row * ChestGeometry.COLUMNS + column;
    }

    /** The step of the button on chest slot {@code slot}, or null. */
    public static ForgeStep stepAt(int slot) {
        for (ForgeStep step : ForgeStep.VALUES) {
            if (stepSlot(step) == slot) {
                return step;
            }
        }
        return null;
    }

    /** All the overlays, with their draw order (the bar and markers, the rules, the last steps, the two buttons). */
    public static final GuiLayers LAYERS = layers();

    private static GuiLayers layers() {
        java.util.ArrayList<GuiLayers.Layer> l = new java.util.ArrayList<>();
        l.add(new GuiLayers.Layer(KEY_PROGRESS, 10, PROGRESS));
        l.add(new GuiLayers.Layer(KEY_TARGET, 11, POINTER));
        l.add(new GuiLayers.Layer(KEY_LEFT, 12, LEFT));
        l.add(new GuiLayers.Layer(KEY_RIGHT, 13, RIGHT));
        l.add(new GuiLayers.Layer(KEY_CENTRE, 14, CENTRE));
        for (int i = 0; i < RULE_SLOTS; i++) {
            l.add(new GuiLayers.Layer(ruleKey(i), 20 + i, ruleImage(i)));
            l.add(new GuiLayers.Layer(stepKey(i), 30 + i, stepImage(i)));
        }
        l.add(new GuiLayers.Layer(KEY_WELD, 40, WELD_BUTTON));
        l.add(new GuiLayers.Layer(KEY_PLAN, 41, PLAN_BUTTON));
        return new GuiLayers(BACKGROUND, List.copyOf(l));
    }

    public static String ruleKey(int i) {
        return "rule_" + i;
    }

    public static String stepKey(int i) {
        return "step_" + i;
    }

    /** Frame of rule {@code rule} on a rule image: {@code 1 + 2 * ordinal + (matched ? 1 : 0)}. */
    public static int ruleFrame(ForgeRule rule, boolean matched) {
        return 1 + 2 * rule.ordinal() + (matched ? 1 : 0);
    }

    /** Frame of {@code step} on a step image: {@code 1 + ordinal}. */
    public static int stepFrame(ForgeStep step) {
        return 1 + step.ordinal();
    }

    /** The plan button's frames: unworkable, workable (both a question mark), the recipe's frame (the result item is the slot's). */
    public static final int PLAN_UNWORKABLE = 1;
    public static final int PLAN_WORKABLE = 0;
    public static final int PLAN_RECIPE = 2;

    /**
     * The overlay frames of the screen.
     *
     * @param recipe    whether the main input has a plan (TFC {@code forging.getRecipe() != null})
     * @param work      the work offset
     * @param target    the plan's work target
     * @param range     the acceptable work range ({@code anvilAcceptableWorkRange})
     * @param rules     the plan's rules
     * @param steps     the step history
     * @param canWeld   whether the weld button is lit
     * @param workable  whether the main input has any plan on this anvil (the plan button's question mark frame)
     */
    public static Map<String, Integer> frames(boolean recipe, int work, int target, int range, List<ForgeRule> rules,
            ForgeSteps steps, boolean canWeld, boolean workable) {
        Map<String, Integer> out = new HashMap<>();
        if (recipe) {
            out.put(KEY_PROGRESS, clampFrame(work));
            if (range < POINTER_RANGE_BELOW) {
                out.put(KEY_TARGET, clampFrame(target));
            } else {
                int left = Math.max(0, target - range);
                int right = Math.min(RIGHT_LIMIT_MAX, target + range);
                out.put(KEY_LEFT, clampFrame(left));
                out.put(KEY_RIGHT, clampFrame(right));
                if (range > 2) {
                    out.put(KEY_CENTRE, clampFrame((right + left) / 2));
                }
            }
            for (int i = 0; i < Math.min(rules.size(), RULE_SLOTS); i++) {
                ForgeRule rule = rules.get(i);
                if (rule != null) {
                    out.put(ruleKey(i), ruleFrame(rule, steps.matches(rule)));
                }
            }
        }
        List<ForgeStep> last = steps.steps();
        for (int i = 0; i < Math.min(last.size(), RULE_SLOTS); i++) {
            out.put(stepKey(i), stepFrame(last.get(last.size() - 1 - i)));
        }
        out.put(KEY_WELD, canWeld ? 1 : 0);
        out.put(KEY_PLAN, recipe ? PLAN_RECIPE : workable ? PLAN_WORKABLE : PLAN_UNWORKABLE);
        return Map.copyOf(out);
    }

    private static int clampFrame(int v) {
        return Math.max(0, Math.min(BAR_FRAMES - 1, v));
    }
}
