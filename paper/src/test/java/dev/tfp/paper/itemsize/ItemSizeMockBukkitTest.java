/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemsize;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.item.Burden;
import dev.tfp.core.item.BurdenEffects;
import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-SURV-06: the chest and bundle limits, exhausted / overburdened and the tooltip line, on MockBukkit, with TFC's
 * anvil definition ({@code item_size/anvils.json}: {@code #tfc:anvils} huge, very heavy) and its chest definition.
 */
class ItemSizeMockBukkitTest extends MockServerTest {
    private static final NamespacedKey TEST_ID = new NamespacedKey("test", "id");
    private static final String ANVIL = "tfp:metal/anvil/copper";

    private ItemSizes sizes;
    private ContainerSizeListener listener;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("world");
        ItemSizeDefinitions defs = new ItemSizeDefinitions(List.of(
                new ItemSizeDefinitions.Definition("tfp:anvils", new Ingredient.Tag("tfp:anvils"),
                        new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY)),
                new ItemSizeDefinitions.Definition("tfp:logs", new Ingredient.Tag("minecraft:logs"),
                        new ItemSizing(ItemSize.VERY_LARGE, ItemWeight.MEDIUM))));
        TagLookup tags = new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return type == TagType.ITEM && (tag.equals("tfp:anvils") && id.startsWith("tfp:metal/anvil/")
                        || tag.equals("minecraft:logs") && id.equals("minecraft:oak_log"));
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return true;
            }
        };
        sizes = new ItemSizes(() -> defs, () -> tags,
                s -> s.hasItemMeta() ? Optional.ofNullable(s.getItemMeta().getPersistentDataContainer()
                        .get(TEST_ID, PersistentDataType.STRING)) : Optional.empty(),
                id -> id.equals("tfp:rock/raw/granite"), ItemSizeLimits.DEFAULT);
        listener = new ContainerSizeListener(sizes, Inventory::getHolder);
    }

    private static ItemStack custom(String id) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(TEST_ID, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory chestAt(int x) {
        Block block = world.getBlockAt(x, 64, 0);
        block.setType(Material.CHEST);
        return ((Chest) block.getState()).getInventory();
    }

    @Test
    void sizingFollowsDefinitionsThenTfcClassDefaults() {
        assertThat(sizes.sizing(custom(ANVIL))).isEqualTo(new ItemSizing(ItemSize.HUGE, ItemWeight.VERY_HEAVY));
        assertThat(sizes.sizing(new ItemStack(Material.OAK_LOG)))
                .isEqualTo(new ItemSizing(ItemSize.VERY_LARGE, ItemWeight.MEDIUM));
        assertThat(sizes.sizing(new ItemStack(Material.IRON_PICKAXE))).isEqualTo(ItemSizing.TOOL);
        assertThat(sizes.sizing(new ItemStack(Material.WATER_BUCKET))).isEqualTo(ItemSizing.TOOL);
        assertThat(sizes.sizing(new ItemStack(Material.DIAMOND_HELMET))).isEqualTo(ItemSizing.ARMOR);
        assertThat(sizes.sizing(new ItemStack(Material.STONE))).isEqualTo(ItemSizing.BLOCK);
        assertThat(sizes.sizing(new ItemStack(Material.STICK))).isEqualTo(ItemSizing.DEFAULT);
        assertThat(sizes.sizing(custom("tfp:rock/raw/granite"))).as("a custom block item").isEqualTo(ItemSizing.BLOCK);
        assertThat(sizes.sizing(custom("tfp:food/beef"))).isEqualTo(ItemSizing.DEFAULT);
        ItemStack tool = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = tool.getItemMeta();
        meta.getPersistentDataContainer().set(TEST_ID, PersistentDataType.STRING, "tfp:metal/hoe/copper");
        tool.setItemMeta(meta);
        assertThat(sizes.sizing(tool)).as("a custom tool on a vanilla hoe").isEqualTo(ItemSizing.TOOL);
        assertThat(sizes.sizing((ItemStack) null)).isEqualTo(ItemSizing.DEFAULT);
    }

    @Test
    void chestRefusesAHugeItemPlacedFromTheCursor() {
        PlayerMock player = server.addPlayer();
        InventoryView view = player.openInventory(chestAt(0));
        player.setItemOnCursor(custom(ANVIL));
        InventoryClickEvent anvil = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        listener.onClick(anvil);
        assertThat(anvil.isCancelled()).isTrue();

        player.setItemOnCursor(new ItemStack(Material.IRON_PICKAXE));
        InventoryClickEvent pick = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        listener.onClick(pick);
        assertThat(pick.isCancelled()).as("large fits a chest").isFalse();

        player.setItemOnCursor(new ItemStack(Material.OAK_LOG));
        InventoryClickEvent log = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        listener.onClick(log);
        assertThat(log.isCancelled()).as("very large does not").isTrue();
    }

    @Test
    void chestRefusesShiftClickNumberKeyAndDragOfAHugeItem() {
        PlayerMock player = server.addPlayer();
        InventoryView view = player.openInventory(chestAt(1));
        int bottomSlot = view.getTopInventory().getSize(); // raw slot of the player's first storage slot
        view.getBottomInventory().setItem(9, custom(ANVIL));
        InventoryClickEvent shift = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, bottomSlot,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        shift.setCurrentItem(custom(ANVIL));
        listener.onClick(shift);
        assertThat(shift.isCancelled()).isTrue();

        player.getInventory().setItem(2, custom(ANVIL));
        InventoryClickEvent key = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 3,
                ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 2);
        listener.onClick(key);
        assertThat(key.isCancelled()).isTrue();

        InventoryDragEvent drag = new InventoryDragEvent(view, null, custom(ANVIL), false,
                Map.of(4, custom(ANVIL)));
        listener.onDrag(drag);
        assertThat(drag.isCancelled()).isTrue();
        InventoryDragEvent dragBottom = new InventoryDragEvent(view, null, custom(ANVIL), false,
                Map.of(bottomSlot + 1, custom(ANVIL)));
        listener.onDrag(dragBottom);
        assertThat(dragBottom.isCancelled()).as("dragging within the player's own inventory").isFalse();
    }

    @Test
    void otherInventoriesAreNotRestricted() {
        PlayerMock player = server.addPlayer();
        Inventory plain = server.createInventory(null, 27);
        InventoryView view = player.openInventory(plain);
        player.setItemOnCursor(custom(ANVIL));
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                InventoryAction.PLACE_ALL);
        listener.onClick(e);
        assertThat(e.isCancelled()).as("a plugin GUI or other container keeps its own rules").isFalse();
    }

    @Test
    void bundleInASlotRefusesHeavyOrLargeCursorItems() {
        PlayerMock player = server.addPlayer();
        InventoryView view = player.openInventory(server.createInventory(null, 27));
        view.getTopInventory().setItem(0, new ItemStack(Material.BUNDLE));
        player.setItemOnCursor(custom(ANVIL));
        InventoryClickEvent anvil = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT,
                InventoryAction.SWAP_WITH_CURSOR);
        listener.onClick(anvil);
        assertThat(anvil.isCancelled()).isTrue();

        player.setItemOnCursor(new ItemStack(Material.STICK));
        InventoryClickEvent stick = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT,
                InventoryAction.SWAP_WITH_CURSOR);
        listener.onClick(stick);
        assertThat(stick.isCancelled()).as("very small, very light fits").isFalse();

        player.setItemOnCursor(new ItemStack(Material.STONE));
        InventoryClickEvent stone = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT,
                InventoryAction.SWAP_WITH_CURSOR);
        listener.onClick(stone);
        assertThat(stone.isCancelled()).as("a block (small, light) fits").isFalse();
    }

    @Test
    void twoAnvilsOverburdenOneExhausts() {
        PlayerMock player = server.addPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        List<Burden> notices = new ArrayList<>();
        BurdenApplier applier = new BurdenApplier(sizes, BurdenEffects.DEFAULT_PASSIVE_EXHAUSTION_MODIFIER,
                (p, b) -> notices.add(b));
        player.getInventory().setItem(0, custom(ANVIL));
        player.getInventory().setItem(5, custom("tfp:metal/anvil/bronze"));

        assertThat(applier.apply(player)).isEqualTo(Burden.MORE_THAN_ONE);
        assertThat(BurdenApplier.isImmobilised(player)).isTrue();
        assertThat(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()).as("the server clamps it at 0")
                .isLessThanOrEqualTo(0);
        assertThat(applier.apply(player)).isEqualTo(Burden.MORE_THAN_ONE);
        assertThat(notices).as("told once per change").containsExactly(Burden.MORE_THAN_ONE);

        player.getInventory().setItem(5, null);
        player.setExhaustion(0f);
        assertThat(applier.apply(player)).isEqualTo(Burden.ONE);
        assertThat(BurdenApplier.isImmobilised(player)).isFalse();
        assertThat(player.getAttribute(Attribute.MOVEMENT_SPEED).getValue()).isPositive();
        assertThat(player.getExhaustion()).isCloseTo(BurdenEffects.exhaustedFoodExhaustion(1f),
                org.assertj.core.data.Offset.offset(1e-7f));
        assertThat(notices).containsExactly(Burden.MORE_THAN_ONE, Burden.ONE);
    }

    @Test
    void creativeAndSpectatorCarryNoBurdenAdventureDoes() {
        PlayerMock player = server.addPlayer();
        BurdenApplier applier = new BurdenApplier(sizes, 1f, (p, b) -> { });
        player.getInventory().setItem(0, custom(ANVIL));
        player.getInventory().setItemInOffHand(custom(ANVIL));

        player.setGameMode(GameMode.ADVENTURE);
        assertThat(applier.apply(player)).as("offhand counts").isEqualTo(Burden.MORE_THAN_ONE);
        player.setGameMode(GameMode.CREATIVE);
        assertThat(applier.apply(player)).isEqualTo(Burden.NONE);
        assertThat(BurdenApplier.isImmobilised(player)).as("switching mode lifts it at once").isFalse();
        player.setGameMode(GameMode.SPECTATOR);
        assertThat(applier.apply(player)).isEqualTo(Burden.NONE);
        player.setGameMode(GameMode.SURVIVAL);
        assertThat(applier.apply(player)).isEqualTo(Burden.MORE_THAN_ONE);
        applier.clear(player);
        assertThat(BurdenApplier.isImmobilised(player)).isFalse();
    }

    @Test
    void tooltipLineIsWeightThenSizeInGrey() {
        ItemSizeTooltipContributor c = new ItemSizeTooltipContributor(sizes,
                (locale, key, args) -> Map.of("item_size.weight.very_heavy", "Very Heavy", "item_size.size.huge", "Huge",
                        "item_size.weight.very_light", "Very Light", "item_size.size.very_small", "Very Small")
                        .getOrDefault(key, key));
        List<Component> lines = new ArrayList<>();
        c.contribute(new TooltipItem(ANVIL, "minecraft:paper", 1, TooltipData.EMPTY),
                new TooltipContext("en_us", ClockSnapshot.UNSET), lines::add);
        c.contribute(new TooltipItem(null, "minecraft:stick", 1, TooltipData.EMPTY),
                new TooltipContext("en_us", ClockSnapshot.UNSET), lines::add);
        assertThat(lines).extracting(l -> PlainTextComponentSerializer.plainText().serialize(l))
                .containsExactly("⚖ Very Heavy ⇲ Huge", "⚖ Very Light ⇲ Very Small");
        assertThat(lines.get(0).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(c.order()).isEqualTo(dev.tfp.paper.tooltip.TooltipContributor.ORDER_SIZE);
    }

    @Test
    void chestHelperRecognisesChestBlocksOnly() {
        assertThat(listener.isChest(chestAt(2))).isTrue();
        assertThat(listener.isChest(server.createInventory(null, 27))).isFalse();
        assertThat(listener.isChest(null)).isFalse();
    }
}
