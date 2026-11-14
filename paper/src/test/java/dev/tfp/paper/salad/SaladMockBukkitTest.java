/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.salad;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.salad.SaladMix;
import dev.tfp.core.salad.SaladScreen;
import dev.tfp.paper.gui.GuiListener;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-PROC-14: opening, the salad output, taking it, closing and the bowl after eating, on MockBukkit. */
class SaladMockBukkitTest extends MockServerTest {
    // TFC's data/tfc/tfc/food: carrot, red apple (an apple here), cooked beef
    private static final FoodData CARROT = new FoodData(4, 0, 2.0f, 0, 0, 0, 1.5f, 0, 0, 0.5f);
    private static final FoodData APPLE = new FoodData(4, 0, 0.2f, 0, 0, 0.9f, 0, 0, 0, 1.7f);
    private static final FoodData BEEF = new FoodData(4, 0, 2.0f, 0, 0, 0, 0, 2.5f, 0, 1.5f);
    private static final Map<Material, FoodData> FOODS = Map.of(Material.CARROT, CARROT, Material.APPLE, APPLE,
        Material.COOKED_BEEF, BEEF, Material.POTATO, new FoodData(4, 0, 1f, 0, 0, 0, 0, 0, 0, 1f));
    /** The salad items, stand-ins for tfp:food/<nutrient>_salad (stack size 64 like the bowls). */
    private static final Map<String, Material> SALADS = Map.of(SaladMix.itemId(Nutrient.VEGETABLES), Material.GREEN_DYE,
        SaladMix.itemId(Nutrient.FRUIT), Material.RED_DYE, SaladMix.itemId(Nutrient.PROTEIN), Material.BROWN_DYE);

    private final AtomicLong now = new AtomicLong(100_000);
    private final List<ItemStack> stamped = new ArrayList<>();
    private SaladScheduler scheduler;
    private PlayerMock player;
    private SaladMenu.Services services;

    @BeforeEach
    void setUp() {
        scheduler = new SaladScheduler();
        GuiService gui = new GuiService(server, scheduler, now::get, false);
        services = new SaladMenu.Services(gui, scheduler, Messages.fromJar(),
            s -> s.getType() == Material.BOWL,
            s -> FOODS.containsKey(s.getType()) || s.getType() == Material.ROTTEN_FLESH || s.getType() == Material.STICK,
            s -> s.getType() == Material.ROTTEN_FLESH ? Optional.of(CARROT) : Optional.ofNullable(FOODS.get(s.getType())),
            s -> s.getType() == Material.ROTTEN_FLESH,
            s -> s == null || s.getType().isAir() ? Optional.empty() : Optional.of(s.getType().getKey().toString()),
            (id, amount) -> Optional.ofNullable(SALADS.get(id)).or(() -> Optional.ofNullable(Material.matchMaterial(id)))
                .map(m -> new ItemStack(m, amount)),
            stamped::add);
        server.getPluginManager().registerEvents(new GuiListener(gui), plugin);
        server.getPluginManager().registerEvents(new SaladListener(services,
            id -> Optional.ofNullable(Material.matchMaterial(id)).map(ItemStack::new)), plugin);
        player = server.addPlayer("Bot");
        player.getInventory().setHeldItemSlot(0);
    }

    private boolean rightClick(EquipmentSlot hand) {
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.getInventory().getItem(hand),
            null, BlockFace.SELF, hand);
        server.getPluginManager().callEvent(e);
        return session() != null;
    }

    private GuiSession session() {
        return GuiService.session(player.getOpenInventory().getTopInventory());
    }

    private Inventory top() {
        return session().getInventory();
    }

    private InventoryClickEvent click(int raw, ClickType type, InventoryAction action) {
        InventoryType.SlotType slotType = raw < 27 ? InventoryType.SlotType.CONTAINER : InventoryType.SlotType.QUICKBAR;
        InventoryClickEvent e = new InventoryClickEvent(player.getOpenInventory(), slotType, raw, type, action);
        server.getPluginManager().callEvent(e);
        return e;
    }

    private ItemStack output() {
        return top().getItem(SaladScreen.OUTPUT_CHEST_SLOT);
    }

    private void put(int tfcSlot, Material m, int count) {
        top().setItem(SaladScreen.chestSlot(tfcSlot), new ItemStack(m, count));
        click(SaladScreen.chestSlot(tfcSlot), ClickType.LEFT, InventoryAction.PICKUP_ALL); // the change a click makes
        scheduler.runDelayed();
    }

    private int amount(int tfcSlot) {
        ItemStack s = top().getItem(SaladScreen.chestSlot(tfcSlot));
        return s == null ? 0 : s.getAmount();
    }

    private void openWithBowls(int bowls) {
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(Material.BOWL, bowls));
        assertThat(rightClick(EquipmentSlot.HAND)).isTrue();
    }

    @Test
    void sneakingRightClickWithABowlOpensAndMovesTheBowlsIn() {
        player.getInventory().setItem(0, new ItemStack(Material.BOWL, 10));
        assertThat(rightClick(EquipmentSlot.HAND)).as("not sneaking: no screen (TFC places the wooden bowl)").isFalse();
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(Material.CARROT, 3));
        assertThat(rightClick(EquipmentSlot.HAND)).as("not a bowl").isFalse();
        player.getInventory().setItem(0, new ItemStack(Material.BOWL, 10));
        assertThat(rightClick(EquipmentSlot.HAND)).isTrue();
        assertThat(top().getSize()).isEqualTo(27);
        assertThat(player.getOpenInventory().getTitle()).isEqualTo("Salad");
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(10);
        assertThat(player.getInventory().getItem(0)).isNull();
        assertThat(output()).isNull();
        // the other slots are refused filler
        assertThat(top().getItem(0).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled()).isTrue();
    }

    @Test
    void anOffHandBowlIsUsedWhenTheMainHandHasNone() {
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(Material.CARROT, 3));
        player.getInventory().setItemInOffHand(new ItemStack(Material.BOWL, 4));
        assertThat(rightClick(EquipmentSlot.OFF_HAND)).isTrue();
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(4);
        assertThat(player.getInventory().getItemInOffHand().isEmpty()).isTrue();
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(3);
    }

    @Test
    void spectatorsOpenNothing() {
        player.setGameMode(GameMode.SPECTATOR);
        player.setSneaking(true);
        player.getInventory().setItem(0, new ItemStack(Material.BOWL, 1));
        assertThat(rightClick(EquipmentSlot.HAND)).isFalse();
    }

    @Test
    void ingredientsMakeASaladWithTheMixedFoodData() {
        openWithBowls(10);
        put(0, Material.CARROT, 5);
        put(2, Material.APPLE, 3);
        ItemStack out = output();
        assertThat(out.getType()).isEqualTo(Material.GREEN_DYE); // vegetables 1.125 > fruit 0.675
        assertThat(out.getAmount()).isEqualTo(3);
        FoodData expected = SaladMix.mix(List.of(new SaladMix.Ingredient(0, CARROT, false, 5),
            new SaladMix.Ingredient(2, APPLE, false, 3)), 10).orElseThrow().food();
        assertThat(SaladPdc.food(out)).contains(expected);
        assertThat(expected.saturation()).isEqualTo((2.0f + 0.2f) * 0.75f);
        assertThat(SaladPdc.ingredients(out)).containsExactly("minecraft:carrot", "minecraft:apple");
        assertThat(SaladPdc.bowl(out)).contains("minecraft:bowl");
        assertThat(stamped).isNotEmpty();
        assertThat(stamped.getLast()).isEqualTo(out);
    }

    @Test
    void takingTheSaladShrinksEveryInputAndTheBowls() {
        openWithBowls(10);
        put(0, Material.CARROT, 5);
        put(1, Material.APPLE, 3);
        InventoryClickEvent take = click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(take.isCancelled()).isTrue();
        assertThat(player.getItemOnCursor().getType()).isEqualTo(Material.GREEN_DYE);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(3);
        assertThat(amount(0)).isEqualTo(2);
        assertThat(amount(1)).isZero();
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(7);
        // the carrots left make a vegetable salad of 2
        assertThat(output().getAmount()).isEqualTo(2);
        // a different salad on the cursor takes nothing
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(3);
        assertThat(amount(0)).isEqualTo(2);
    }

    @Test
    void aRightClickTakesHalfAndShiftClickTakesNothing() {
        openWithBowls(10);
        put(0, Material.COOKED_BEEF, 5);
        assertThat(output().getType()).isEqualTo(Material.BROWN_DYE);
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(amount(0)).isEqualTo(5);
        assertThat(output().getAmount()).isEqualTo(5);
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(3);
        assertThat(amount(0)).isEqualTo(2);
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(7);
        // the same salad on the cursor takes as many as fit
        assertThat(output().getAmount()).isEqualTo(2);
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.LEFT, InventoryAction.PLACE_ALL);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(5);
        assertThat(amount(0)).isZero();
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(5);
        assertThat(output()).isNull();
        // a dropped salad is taken too
        put(0, Material.COOKED_BEEF, 4);
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.DROP, InventoryAction.DROP_ONE_SLOT);
        assertThat(amount(0)).isEqualTo(3);
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(4);
    }

    @Test
    void aTakeInTheSameTickAsAChangeTakesWhatTheSlotsMakeNow() {
        openWithBowls(10);
        put(0, Material.CARROT, 5);
        top().setItem(SaladScreen.chestSlot(0), new ItemStack(Material.CARROT, 1)); // no update queued yet
        click(SaladScreen.OUTPUT_CHEST_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertThat(player.getItemOnCursor().getAmount()).isEqualTo(1);
        assertThat(amount(SaladScreen.SLOT_BOWLS)).isEqualTo(9);
    }

    @Test
    void rottenFoodNoBowlOrNoNutrientsMakeNothing() {
        openWithBowls(10);
        put(0, Material.CARROT, 5);
        put(1, Material.ROTTEN_FLESH, 5);
        assertThat(output()).isNull();
        put(1, Material.POTATO, 5); // water and saturation only, no nutrient... with the carrot still there
        assertThat(output()).isNotNull();
        top().setItem(SaladScreen.chestSlot(0), null);
        put(4, Material.STICK, 1); // usable but not food: skipped
        assertThat(output()).isNull();
        put(0, Material.CARROT, 2);
        assertThat(output().getAmount()).isEqualTo(2);
        top().setItem(SaladScreen.BOWL_CHEST_SLOT, null);
        put(0, Material.CARROT, 2);
        assertThat(output()).isNull();
    }

    @Test
    void slotsAcceptOnlyTheirItems() {
        openWithBowls(1);
        player.setItemOnCursor(new ItemStack(Material.DIRT, 1));
        assertThat(click(SaladScreen.chestSlot(0), ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        player.setItemOnCursor(new ItemStack(Material.CARROT, 1));
        assertThat(click(SaladScreen.chestSlot(0), ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isFalse();
        assertThat(click(SaladScreen.BOWL_CHEST_SLOT, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR).isCancelled()).isTrue();
        player.setItemOnCursor(new ItemStack(Material.BOWL, 1));
        assertThat(click(SaladScreen.chestSlot(1), ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled()).isTrue();
        // shift-click from the player inventory fills the inputs, then the bowl slot
        player.getOpenInventory().setItem(27, new ItemStack(Material.CARROT, 4));
        click(27, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertThat(amount(0)).isEqualTo(4);
    }

    @Test
    void closingGivesBackTheInputsAndBowlsButNotTheSalad() {
        openWithBowls(3);
        put(0, Material.CARROT, 5);
        assertThat(output()).isNotNull();
        GuiSession s = session();
        player.closeInventory();
        assertThat(s.closed()).isTrue();
        assertThat(player.getInventory().contains(Material.CARROT, 5)).isTrue();
        assertThat(player.getInventory().contains(Material.BOWL, 3)).isTrue();
        assertThat(player.getInventory().contains(Material.GREEN_DYE)).isFalse();
        assertThat(player.getInventory().contains(Material.GRAY_STAINED_GLASS_PANE)).isFalse();
    }

    private ItemStack salad(int count) {
        ItemStack salad = new ItemStack(Material.GREEN_DYE, count);
        SaladPdc.write(salad, CARROT, List.of("minecraft:carrot"), "minecraft:bowl");
        return salad;
    }

    @Test
    void eatingTheLastSaladLeavesTheBowlInTheHand() {
        player.getInventory().setItem(0, salad(1));
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, salad(1), EquipmentSlot.HAND));
        player.getInventory().setItem(0, null); // vanilla uses the stack up after the event
        scheduler.runDelayed();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.BOWL);
    }

    @Test
    void eatingOneOfSeveralGivesTheBowlToTheInventoryAndCreativeGetsNone() {
        player.getInventory().setItem(0, salad(3));
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, salad(3), EquipmentSlot.HAND));
        scheduler.runDelayed();
        assertThat(player.getInventory().contains(Material.BOWL, 1)).isTrue();
        player.getInventory().remove(Material.BOWL);
        player.setGameMode(GameMode.CREATIVE);
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, salad(3), EquipmentSlot.HAND));
        scheduler.runDelayed();
        assertThat(player.getInventory().contains(Material.BOWL)).isFalse();
        // a food without a stored bowl gives nothing
        player.setGameMode(GameMode.SURVIVAL);
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(player, new ItemStack(Material.CARROT), EquipmentSlot.HAND));
        scheduler.runDelayed();
        assertThat(player.getInventory().contains(Material.BOWL)).isFalse();
    }

    @Test
    void pdcRoundTripsAndIgnoresNewerVersions() {
        ItemStack s = salad(1);
        assertThat(SaladPdc.food(s)).contains(CARROT);
        assertThat(SaladPdc.food(new ItemStack(Material.CARROT))).isEmpty();
        assertThat(SaladPdc.bowl(new ItemStack(Material.CARROT))).isEmpty();
        assertThat(SaladPdc.ingredients(new ItemStack(Material.CARROT))).isEmpty();
        ItemMeta meta = s.getItemMeta();
        var food = meta.getPersistentDataContainer().get(SaladPdc.FOOD, org.bukkit.persistence.PersistentDataType.TAG_CONTAINER);
        food.set(new org.bukkit.NamespacedKey("tfp", "v"), org.bukkit.persistence.PersistentDataType.INTEGER, SaladPdc.VERSION + 1);
        meta.getPersistentDataContainer().set(SaladPdc.FOOD, org.bukkit.persistence.PersistentDataType.TAG_CONTAINER, food);
        s.setItemMeta(meta);
        assertThat(SaladPdc.food(s)).isEmpty();
    }

    @Test
    void dynamicFoodDataWinsOverTheDefinition() {
        ItemStack s = salad(1);
        assertThat(SaladModule.food(s, st -> Optional.of("minecraft:green_dye"),
            () -> new dev.tfp.core.food.FoodDefinitions(Map.of("minecraft:green_dye", APPLE)))).contains(CARROT);
        assertThat(SaladModule.food(new ItemStack(Material.APPLE), st -> Optional.of("minecraft:apple"),
            () -> new dev.tfp.core.food.FoodDefinitions(Map.of("minecraft:apple", APPLE)))).contains(APPLE);
    }
}
