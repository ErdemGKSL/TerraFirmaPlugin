/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/AnvilContainer.java @ c3b4d9536 (onButtonPress, addContainerSlots,
 *       moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/AnvilScreen.java @ c3b4d9536 (renderBg, renderTooltip),
 *       src/main/java/net/dries007/tfc/client/screen/button/AnvilStepButton.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/client/screen/button/AnvilPlanButton.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (createForgingEffects)
 */
package dev.tfp.paper.anvil.forge;

import dev.tfp.core.anvil.AnvilRecipe;
import dev.tfp.core.anvil.AnvilRecipes;
import dev.tfp.core.anvil.AnvilSlots;
import dev.tfp.core.anvil.AnvilState;
import dev.tfp.core.anvil.AnvilTiers;
import dev.tfp.core.anvil.AnvilVisuals;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.forge.AnvilScreenLayout;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;
import dev.tfp.core.tool.ForgingQuality;
import dev.tfp.paper.advancement.criteria.AdvancementTriggerEvent;
import dev.tfp.paper.advancement.criteria.CriteriaMap;
import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.anvil.AnvilEffects;
import dev.tfp.paper.anvil.plan.AnvilPlanScreens;
import dev.tfp.paper.anvil.plan.ForgingPdc;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.particle.TfcParticle;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-METAL-05: the anvil's forging screen (TFC {@code AnvilContainer} and {@code AnvilScreen}) on the GUI framework, opened by
 * the plain click on an anvil (TFC always opens this screen; the plan screen of PAR-METAL-04 opens from its plan button and
 * returns here once a plan is chosen).
 *
 * <p>Slots as TFC: the second input, the flux, the main input and the hammer are chest slots (see {@link AnvilScreenLayout});
 * the eight step buttons, the plan button (showing the plan's result) and the weld button are buttons. The art layers draw the
 * progress marker, the target pointer, the rule icons (green when the history matches, red when not) and the last three steps.
 * A step button runs {@link AnvilForger#work}. The chest's slots are a copy of the anvil's: the anvil's record stays the
 * authority, committed one tick after a click, before a step, and when the screen closes; another writer of the record (a
 * sneaking hand click) commits the copy first and reloads it after, through {@link #edit} and {@link #reload}. One viewer per anvil.
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}), which must own the anvil's block (the screen
 * refuses to work on a block it does not own); the busy map is thread-safe.
 */
public final class AnvilForgeScreens {
    static final String STATE = "anvil.forge";
    private static final int[] DEVICE_SLOTS = {AnvilSlots.INPUT_MAIN, AnvilSlots.INPUT_SECOND, AnvilSlots.HAMMER, AnvilSlots.CATALYST};

    /** The welding part of the screen (PAR-METAL-06 plugs in): whether the weld button is lit and what pressing it does. */
    public interface Welding {
        Welding NONE = new Welding() {
        };

        /** Whether the anvil at {@code block} can weld its two inputs now (TFC {@code canWeldNow}). */
        default boolean canWeld(Block block, ItemStack main, ItemStack second) {
            return false;
        }

        /** The weld button was pressed by {@code viewer}; the record is committed. Change the anvil with {@code DeviceService.edit}. */
        default void weld(Player viewer, Block block) {
        }
    }

    record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    static final class Screen {
        final Block block;
        final Key key;
        final DeviceKind<AnvilState> kind;
        final int tier;
        DeviceRecord<AnvilState> shown;
        boolean pending;
        boolean gone;

        Screen(Block block, DeviceKind<AnvilState> kind, int tier, DeviceRecord<AnvilState> shown) {
            this.block = block;
            this.key = Key.of(block);
            this.kind = kind;
            this.tier = tier;
            this.shown = shown;
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final DeviceService devices;
    private final AnvilForger forger;
    private final AnvilForger.Env env;
    private final AnvilPlanScreens plans;
    private final AnvilDevice.Plans rules;
    private final CustomBlocks blocks;
    private final AnvilEffects effects;
    private final Messages messages;
    private final Function<Player, String> title;
    private final Predicate<ItemStack> flux;
    private final Map<Key, GuiSession> open = new ConcurrentHashMap<>();
    private volatile Welding welding = Welding.NONE;

    public AnvilForgeScreens(GuiService gui, Scheduler scheduler, DeviceService devices, AnvilForger.Env env, AnvilPlanScreens plans,
            CustomBlocks blocks, AnvilEffects effects, Messages messages, Function<Player, String> title, Predicate<ItemStack> flux) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.env = Objects.requireNonNull(env, "env");
        this.forger = new AnvilForger(env);
        this.plans = Objects.requireNonNull(plans, "plans");
        this.rules = plans;
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.title = Objects.requireNonNull(title, "title");
        this.flux = Objects.requireNonNull(flux, "flux");
    }

    /** PAR-METAL-06 sets the welding part. */
    public void welding(Welding welding) {
        this.welding = Objects.requireNonNull(welding, "welding");
    }

    // ---- opening --------------------------------------------------------------------------------------------

    /** Opens the screen of the anvil at {@code block} for {@code viewer} one tick later (off the click). Returns true. */
    public boolean openLater(Player viewer, Block block) {
        scheduler.entityDelayed(viewer, 1, () -> {
            if (viewer.isOnline()) {
                open(viewer, block);
            }
        }, () -> { });
        return true;
    }

    /**
     * Opens the screen now.
     *
     * @return the session, or empty when the block is no anvil, this thread does not own it, someone else has it open, or the
     *         screen was refused
     */
    public Optional<GuiSession> open(Player viewer, Block block) {
        if (!owned(block)) {
            return Optional.empty();
        }
        Optional<DeviceKind<AnvilState>> kind = kind(block);
        int tier = blocks.stateAt(block).map(BlockStateRef::id).flatMap(id -> {
            var t = AnvilTiers.tier(id);
            return t.isPresent() ? Optional.of(t.getAsInt()) : Optional.<Integer>empty();
        }).orElse(-1);
        Key key = Key.of(block);
        GuiSession other = open.get(key);
        if (kind.isEmpty() || tier < 0 || (other != null && !other.closed())) {
            return Optional.empty();
        }
        Optional<DeviceRecord<AnvilState>> record = devices.edit(block, kind.get(), (r, now) -> r);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(block, kind.get(), tier, record.get());
        GuiSession session = gui.open(viewer, screen(screen));
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, record.get());
        return Optional.of(session);
    }

    /** The open screen of the anvil at {@code block}, if any. */
    public Optional<GuiSession> viewer(Block block) {
        GuiSession s = open.get(Key.of(block));
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before another writer changes the anvil at {@code block}: commits the open copy. Viewer thread. */
    public void edit(Block block) {
        viewer(block).ifPresent(this::flush);
    }

    /** After another writer changed the anvil at {@code block}: shows the stored record. Viewer thread. */
    public void reload(Block block) {
        viewer(block).ifPresent(this::reload);
    }

    public static boolean isForge(GuiSession session) {
        return state(session) != null;
    }

    /** The record last shown in {@code session}. */
    public static Optional<DeviceRecord<AnvilState>> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.shown);
    }

    // ---- the screen -------------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen) {
        TfpGui.Builder b = TfpGui.builder(AnvilScreenLayout.CHEST, AnvilScreenLayout.LAYERS).label(title);
        // shift-click order (AnvilContainer.moveStack): the hammer and the flux, then the inputs
        for (int slot : new int[] {AnvilSlots.HAMMER, AnvilSlots.CATALYST, AnvilSlots.INPUT_MAIN, AnvilSlots.INPUT_SECOND}) {
            b.input(chestSlot(slot), (s, chestSlot, stack) -> !GuiButtons.isButton(stack) && accepts(slot, stack)
                && roomFor(s, slot));
        }
        for (ForgeStep step : ForgeStep.VALUES) {
            b.button(AnvilScreenLayout.stepSlot(step), s -> stepIcon(s.viewer(), step), (s, click) -> press(s, step));
        }
        b.button(AnvilScreenLayout.PLAN, s -> planIcon(s.viewer(), screen), (s, click) -> plan(s));
        b.button(AnvilScreenLayout.WELD, s -> labelled(s.viewer(), "gui.anvil.weld"), (s, click) -> weld(s));
        return b.frames(s -> frames(screen)).lockedWhen(s -> screen.gone).onClose(this::closed).build();
    }

    private boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case AnvilSlots.HAMMER -> env.hammer().test(stack);
            case AnvilSlots.CATALYST -> flux.test(stack);
            default -> true;
        };
    }

    /** A one-item slot already holding its item takes nothing more. */
    private static boolean roomFor(GuiSession session, int slot) {
        ItemStack there = session.getInventory().getItem(chestSlot(slot));
        return there == null || there.isEmpty() || there.getAmount() < AnvilSlots.limit(slot);
    }

    /** The chest slot of the anvil slot {@code slot}. */
    static int chestSlot(int slot) {
        return switch (slot) {
            case AnvilSlots.INPUT_MAIN -> AnvilScreenLayout.MAIN;
            case AnvilSlots.INPUT_SECOND -> AnvilScreenLayout.SECOND;
            case AnvilSlots.HAMMER -> AnvilScreenLayout.HAMMER;
            case AnvilSlots.CATALYST -> AnvilScreenLayout.CATALYST;
            default -> throw new IllegalArgumentException("anvil slot " + slot);
        };
    }

    /** The anvil slot of chest slot {@code chest}, or -1. */
    static int deviceSlot(int chest) {
        for (int slot : DEVICE_SLOTS) {
            if (chestSlot(slot) == chest) {
                return slot;
            }
        }
        return -1;
    }

    /** The overlay frames of what {@code screen} shows. */
    Map<String, Integer> frames(Screen screen) {
        DeviceRecord<AnvilState> record = screen.shown;
        ItemStack main = record == null ? ItemStack.empty() : devices.stacks().decode(record.slot(AnvilSlots.INPUT_MAIN));
        ItemStack second = record == null ? ItemStack.empty() : devices.stacks().decode(record.slot(AnvilSlots.INPUT_SECOND));
        ForgingPdc.Forging forging = StackCodec.isEmpty(main) ? null : ForgingPdc.read(main);
        AnvilRecipes book = env.recipes().get();
        Optional<AnvilRecipe> recipe = forging == null ? Optional.empty() : book.byId(forging.recipe());
        boolean workable = recipe.isEmpty() && !StackCodec.isEmpty(main)
            && book.hasAny(main, screen.tier, env.ingredients().get());
        List<ForgeRule> rules = recipe.map(AnvilRecipe::rules).orElse(List.of());
        ForgeSteps steps = forging == null ? ForgeSteps.EMPTY : forging.steps();
        return AnvilScreenLayout.frames(recipe.isPresent(), forging == null ? 0 : forging.work(), forging == null ? 0 : forging.target(),
            env.range().getAsInt(), rules, steps, welding.canWeld(screen.block, main, second), workable);
    }

    private ItemStack stepIcon(Player viewer, ForgeStep step) {
        return labelled(viewer, "gui.anvil.step." + step.name().toLowerCase(Locale.ROOT));
    }

    private ItemStack labelled(Player viewer, String key) {
        return GuiButtons.icon(gui.glyphs(), messages.get(viewer, key));
    }

    /** TFC {@code AnvilPlanButton}: the plan's result item when the main input has one, else the "Plans" glyph on the frame. */
    private ItemStack planIcon(Player viewer, Screen screen) {
        DeviceRecord<AnvilState> record = screen.shown;
        ItemStack main = record == null ? ItemStack.empty() : devices.stacks().decode(record.slot(AnvilSlots.INPUT_MAIN));
        ForgingPdc.Forging forging = StackCodec.isEmpty(main) ? null : ForgingPdc.read(main);
        Optional<AnvilRecipe> recipe = forging == null ? Optional.empty() : env.recipes().get().byId(forging.recipe());
        String label = messages.get(viewer, "gui.anvil.plans");
        if (recipe.isPresent()) {
            ItemStack result = forger.result(recipe.get());
            if (result != null) {
                result.editMeta(meta -> {
                    meta.customName(Component.text(label).decoration(TextDecoration.ITALIC, false));
                    meta.getPersistentDataContainer().set(GuiButtons.MARKER, PersistentDataType.BYTE, (byte) 1);
                });
                return result;
            }
        }
        return GuiButtons.icon(gui.glyphs(), label);
    }

    // ---- buttons ----------------------------------------------------------------------------------------------

    /** TFC {@code AnvilContainer.onButtonPress} for a step: {@code blockEntity.work(player, step)}. */
    void press(GuiSession session, ForgeStep step) {
        Screen screen = state(session);
        Player viewer = session.viewer();
        if (screen == null || session.closed() || screen.gone) {
            return;
        }
        if (!owned(screen.block)) {
            viewer.closeInventory();
            return;
        }
        flush(session);
        if (screen.gone || session.closed()) {
            return;
        }
        AnvilForger.Outcome[] outcome = new AnvilForger.Outcome[1];
        Optional<DeviceRecord<AnvilState>> after = devices.edit(screen.block, screen.kind, (record, now) -> {
            outcome[0] = forger.work(viewer, record, screen.tier, step);
            return outcome[0].record();
        });
        if (after.isEmpty() || outcome[0] == null) {
            gone(session, screen);
            return;
        }
        AnvilForger.Outcome o = outcome[0];
        apply(session, screen, after.get());
        if (o.message() != null) {
            viewer.sendMessage(Component.text(messages.get(viewer, o.message())));
        }
        switch (o.result()) {
            case WORKED, COMPLETED -> forgingEffects(screen.block);
            case DESTROYED -> screen.block.getWorld().playSound(center(screen.block), Sound.BLOCK_ANVIL_DESTROY, SoundCategory.PLAYERS,
                0.4f, 1.0f);
            case REFUSED -> {
            }
        }
        if (o.result() == AnvilForger.Result.COMPLETED && o.bonus() == ForgingQuality.PERFECT) {
            AdvancementTriggerEvent.publish(viewer, CriteriaMap.PERFECTLY_FORGED); // TFC PERFECTLY_FORGED
        }
        if (o.result() != AnvilForger.Result.REFUSED) {
            effects.show(screen.block, view(screen, after.get()));
        }
    }

    /**
     * TFC {@code AnvilBlockEntity.workRemotely}: works the anvil at {@code block} without a player (the trip hammer). The record is
     * edited through {@link DeviceService#edit}; the caller keeps an open screen in step (the module's access hook). Region thread
     * of {@code block}.
     *
     * @return true when the anvil was worked (TFC's return value)
     */
    public boolean workRemotely(Block block, ForgeStep step, int movement, boolean forceCompletion) {
        if (!owned(block)) {
            return false;
        }
        Optional<DeviceKind<AnvilState>> kind = kind(block);
        int tier = blocks.stateAt(block).map(BlockStateRef::id).flatMap(id -> {
            var t = AnvilTiers.tier(id);
            return t.isPresent() ? Optional.of(t.getAsInt()) : Optional.<Integer>empty();
        }).orElse(-1);
        if (kind.isEmpty() || tier < 0) {
            return false;
        }
        AnvilForger.Outcome[] outcome = new AnvilForger.Outcome[1];
        Optional<DeviceRecord<AnvilState>> after = devices.edit(block, kind.get(), (record, now) -> {
            outcome[0] = forger.workRemotely(record, tier, step, movement, forceCompletion);
            return outcome[0].record();
        });
        AnvilForger.Outcome o = outcome[0];
        if (after.isEmpty() || o == null || o.result() == AnvilForger.Result.REFUSED) {
            return false;
        }
        if (o.result() == AnvilForger.Result.DESTROYED) {
            block.getWorld().playSound(center(block), Sound.BLOCK_ANVIL_DESTROY, SoundCategory.PLAYERS, 0.4f, 1.0f);
        } else {
            forgingEffects(block);
        }
        String facing = blocks.stateAt(block).map(s -> s.properties().getOrDefault(AnvilDevice.FACING, "")).orElse("");
        DeviceRecord<AnvilState> r = after.get();
        effects.show(block, new AnvilEffects.View(tier, AnvilVisuals.rotation(facing), devices.stacks().decode(r.slot(AnvilSlots.HAMMER)),
            devices.stacks().decode(r.slot(AnvilSlots.INPUT_MAIN)), devices.stacks().decode(r.slot(AnvilSlots.INPUT_SECOND)),
            devices.stacks().decode(r.slot(AnvilSlots.CATALYST))));
        return true;
    }

    /** TFC {@code createForgingEffects}: the hit sound and sparks above the anvil (an explicit action: particles are fine). */
    private void forgingEffects(Block block) {
        block.getWorld().playSound(center(block), dev.tfp.paper.sound.Sounds.ANVIL_HIT, SoundCategory.PLAYERS, 0.4f, 1.0f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        TfcParticle.SPARK.play(new Location(block.getWorld(), block.getX() + r.nextDouble(0.2, 0.8), block.getY() + r.nextDouble(0.8, 1.0),
            block.getZ() + r.nextDouble(0.2, 0.8)));
    }

    private static Location center(Block block) {
        return new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }

    /** TFC {@code PLAN_ID}: the plan screen, when the main input has any plan. */
    private void plan(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.gone) {
            return;
        }
        flush(session);
        plans.openNow(session.viewer(), screen.block);
    }

    /** TFC {@code WELD_ID}. */
    private void weld(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.gone || !owned(screen.block)) {
            return;
        }
        flush(session);
        welding.weld(session.viewer(), screen.block);
        reload(session);
    }

    // ---- sync -------------------------------------------------------------------------------------------------

    /** A click or drag happened in {@code session}'s window: commit the copy next tick (once per tick). */
    void touched(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.pending) {
            return;
        }
        screen.pending = true;
        boolean queued = scheduler.entityDelayed(session.viewer(), 1, () -> {
            screen.pending = false;
            flush(session);
        }, () -> screen.pending = false);
        if (!queued) {
            screen.pending = false;
        }
    }

    /** Gives back what a shift-click, drag or swap put above a slot's limit. Returns whether anything moved. */
    boolean clamp(GuiSession session) {
        Screen screen = state(session);
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        boolean moved = false;
        for (int slot : DEVICE_SLOTS) {
            ItemStack there = inv.getItem(chestSlot(slot));
            int limit = AnvilSlots.limit(slot);
            if (there == null || there.getAmount() <= limit) {
                continue;
            }
            if (screen != null && screen.shown != null && there.equals(devices.stacks().decode(screen.shown.slot(slot)))) {
                continue; // what the anvil itself holds (a finished stack of more than one) is not an overflow
            }
            ItemStack excess = there.clone();
            excess.setAmount(there.getAmount() - limit);
            there.setAmount(limit);
            inv.setItem(chestSlot(slot), there);
            give(viewer, excess);
            moved = true;
        }
        return moved;
    }

    /** Commits the chest's copy when it differs from the record last shown. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.gone) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (sameSlots(now, screen)) {
            return;
        }
        if (!owned(screen.block)) {
            session.viewer().closeInventory(); // closed() gives the changes back
            return;
        }
        Optional<DeviceRecord<AnvilState>> after = devices.edit(screen.block, screen.kind, (record, tick) -> {
            DeviceRecord<AnvilState> next = record;
            for (int i = 0; i < DEVICE_SLOTS.length; i++) {
                next = next.withSlot(DEVICE_SLOTS[i], devices.stacks().encode(now.get(i)));
            }
            return rules.slotsChanged(screen.block, screen.tier, next, devices.stacks());
        });
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
            effects.show(screen.block, view(screen, after.get()));
        }
    }

    private void reload(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.gone || !owned(screen.block)) {
            return;
        }
        Optional<DeviceRecord<AnvilState>> now = devices.edit(screen.block, screen.kind, (r, t) -> r);
        if (now.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, now.get());
        }
    }

    /** Shows {@code record}: the four slots, then the title frames and the button tooltips. */
    private void apply(GuiSession session, Screen screen, DeviceRecord<AnvilState> record) {
        screen.shown = record;
        Inventory inv = session.getInventory();
        for (int slot : DEVICE_SLOTS) {
            ItemStack s = devices.stacks().decode(record.slot(slot));
            inv.setItem(chestSlot(slot), StackCodec.isEmpty(s) ? null : s);
        }
        session.refresh();
    }

    /** The anvil is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private void gone(GuiSession session, Screen screen) {
        DeviceRecord<AnvilState> last = screen.shown;
        screen.gone = true; // locks every slot until the screen is closed
        screen.shown = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int slot : DEVICE_SLOTS) {
            ItemStack there = inv.getItem(chestSlot(slot));
            ItemStack known = last == null ? ItemStack.empty() : devices.stacks().decode(last.slot(slot));
            inv.setItem(chestSlot(slot), null);
            if (there != null && !there.isEmpty() && !there.equals(known)) {
                give(viewer, there);
            }
        }
        if (!session.closed()) {
            viewer.closeInventory();
        }
    }

    private void closed(GuiSession session, Player viewer) {
        Screen screen = state(session);
        if (screen == null) {
            return;
        }
        if (!screen.gone && screen.shown != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!sameSlots(now, screen)) {
                boolean done = false;
                if (owned(screen.block)) {
                    done = devices.edit(screen.block, screen.kind, (record, tick) -> {
                        DeviceRecord<AnvilState> next = record;
                        for (int i = 0; i < DEVICE_SLOTS.length; i++) {
                            next = next.withSlot(DEVICE_SLOTS[i], devices.stacks().encode(now.get(i)));
                        }
                        return rules.slotsChanged(screen.block, screen.tier, next, devices.stacks());
                    }).map(r -> {
                        effects.show(screen.block, view(screen, r));
                        return true;
                    }).orElse(false);
                }
                if (!done) {
                    giveChanges(screen.shown, now, viewer);
                    screen.shown = null;
                }
            }
        }
        for (int slot : DEVICE_SLOTS) {
            session.getInventory().setItem(chestSlot(slot), null); // the anvil holds them; the closed copy must not
        }
        open.remove(screen.key, session);
    }

    private void giveChanges(DeviceRecord<AnvilState> last, List<ItemStack> now, Player viewer) {
        // the anvil could not take the copy: what is not in the anvil as last shown goes back to the viewer
        for (int i = 0; i < DEVICE_SLOTS.length; i++) {
            ItemStack there = now.get(i);
            ItemStack known = last == null ? ItemStack.empty() : devices.stacks().decode(last.slot(DEVICE_SLOTS[i]));
            if (!StackCodec.isEmpty(there) && !there.equals(known)) {
                give(viewer, there);
            }
        }
    }

    private boolean sameSlots(List<ItemStack> now, Screen screen) {
        if (screen.shown == null) {
            return true;
        }
        for (int i = 0; i < DEVICE_SLOTS.length; i++) {
            ItemStack known = devices.stacks().decode(screen.shown.slot(DEVICE_SLOTS[i]));
            ItemStack here = now.get(i);
            if (StackCodec.isEmpty(known) != StackCodec.isEmpty(here) || (!StackCodec.isEmpty(known) && !known.equals(here))) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(DEVICE_SLOTS.length);
        for (int slot : DEVICE_SLOTS) {
            ItemStack s = inv.getItem(chestSlot(slot));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }

    private AnvilEffects.View view(Screen screen, DeviceRecord<AnvilState> record) {
        String facing = blocks.stateAt(screen.block).map(s -> s.properties().getOrDefault(AnvilDevice.FACING, "")).orElse("");
        return new AnvilEffects.View(screen.tier, AnvilVisuals.rotation(facing), devices.stacks().decode(record.slot(AnvilSlots.HAMMER)),
            devices.stacks().decode(record.slot(AnvilSlots.INPUT_MAIN)), devices.stacks().decode(record.slot(AnvilSlots.INPUT_SECOND)),
            devices.stacks().decode(record.slot(AnvilSlots.CATALYST)));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    static void give(Player viewer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : viewer.getInventory().addItem(stack.clone()).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), rest);
        }
    }

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<AnvilState>> kind(Block block) {
        return blocks.stateAt(block).map(BlockStateRef::id).flatMap(devices.kinds()::find).map(k -> (DeviceKind<AnvilState>) k);
    }
}
