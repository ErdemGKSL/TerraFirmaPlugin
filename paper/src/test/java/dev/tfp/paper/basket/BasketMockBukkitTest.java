/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.basket;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.item.ItemSize;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.item.ItemSizing;
import dev.tfp.core.item.ItemWeight;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.itemsize.ContainerSizeListener;
import dev.tfp.paper.itemsize.ItemSizes;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

/**
 * PAR-PROC-22: {@code tfp:basket} (a CraftEngine item on {@code minecraft:bundle}) takes only what TFC's
 * {@code TFCBundleItem.overrideOtherStackedOnMe} allows (lighter than medium and smaller than large), through
 * PAR-SURV-06's {@link ContainerSizeListener}. The definitions are TFC's {@code item_size/basket} (normal, very heavy),
 * {@code item_size/anvils} (huge, very heavy) and {@code item_size/logs} (very large, medium), plus two edge rows.
 */
class BasketMockBukkitTest extends MockServerTest {
    private static final NamespacedKey TEST_ID = new NamespacedKey("test", "id");
    private static final String BASKET = "tfp:basket";

    private ContainerSizeListener listener;
    private PlayerMock player;
    private InventoryView view;

    @BeforeEach
    void setUp() {
        ItemSizeDefinitions defs = new ItemSizeDefinitions(List.of(
                def("tfp:basket", new Ingredient.Item(BASKET), ItemSize.NORMAL, ItemWeight.VERY_HEAVY),
                def("tfp:anvils", new Ingredient.Tag("tfp:anvils"), ItemSize.HUGE, ItemWeight.VERY_HEAVY),
                def("tfp:logs", new Ingredient.Tag("minecraft:logs"), ItemSize.VERY_LARGE, ItemWeight.MEDIUM),
                def("test:normal_light", new Ingredient.Item("tfp:test/normal_light"), ItemSize.NORMAL, ItemWeight.LIGHT),
                def("test:large_very_light", new Ingredient.Item("tfp:test/large_very_light"), ItemSize.LARGE,
                        ItemWeight.VERY_LIGHT)));
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
        ItemSizes sizes = new ItemSizes(() -> defs, () -> tags,
                s -> s.hasItemMeta() ? Optional.ofNullable(s.getItemMeta().getPersistentDataContainer()
                        .get(TEST_ID, PersistentDataType.STRING)) : Optional.empty(),
                id -> false, ItemSizeLimits.DEFAULT);
        listener = new ContainerSizeListener(sizes);
        player = server.addPlayer();
        // Paper's snapshot-free holder lookup, which the listener uses, is not implemented by MockBukkit's inventory.
        view = player.openInventory(new InventoryMock(null, 27, InventoryType.CHEST) {
            @Override
            public InventoryHolder getHolder(boolean useSnapshot) {
                return getHolder();
            }
        });
        view.getTopInventory().setItem(0, custom(Material.BUNDLE, BASKET));
    }

    private static ItemSizeDefinitions.Definition def(String id, Ingredient ingredient, ItemSize size, ItemWeight weight) {
        return new ItemSizeDefinitions.Definition(id, ingredient, new ItemSizing(size, weight));
    }

    private static ItemStack custom(Material base, String id) {
        ItemStack stack = new ItemStack(base);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(TEST_ID, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Clicks the cursor stack onto the basket in slot 0 (the 1.21.4+ "place into bundle" action). */
    private boolean refused(ItemStack cursor, InventoryAction action) {
        player.setItemOnCursor(cursor);
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT,
                action);
        listener.onClick(click);
        return click.isCancelled();
    }

    @Test
    void tooLargeOrTooHeavyItemsAreRefused() {
        assertThat(refused(custom(Material.PAPER, "tfp:metal/anvil/copper"), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("anvil: huge, very heavy").isTrue();
        assertThat(refused(new ItemStack(Material.OAK_LOG), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("log: very large, medium").isTrue();
        assertThat(refused(custom(Material.PAPER, "tfp:test/large_very_light"), InventoryAction.PLACE_SOME_INTO_BUNDLE))
                .as("large is not smaller than large").isTrue();
        assertThat(refused(custom(Material.BUNDLE, BASKET), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("a basket (very heavy) into a basket").isTrue();
        assertThat(refused(new ItemStack(Material.IRON_PICKAXE), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("a tool: large, medium").isTrue();
    }

    @Test
    void smallLightItemsGoIn() {
        assertThat(refused(new ItemStack(Material.STICK), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("default: very small, very light").isFalse();
        assertThat(refused(new ItemStack(Material.STONE), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                .as("a block: small, light").isFalse();
        assertThat(refused(custom(Material.PAPER, "tfp:test/normal_light"), InventoryAction.PLACE_SOME_INTO_BUNDLE))
                .as("normal and light is the largest that fits").isFalse();
    }

    @Test
    void vanillaAndDyedBundlesFollowTheBasketRule() {
        // Ledger divergence: the size rule matches any bundle-material item, not only tfp:basket. Every one of the 17
        // vanilla bundles is listed by its own name (plain + one per dye colour), so a check that misses one fails.
        List<Material> bundles = new ArrayList<>(List.of(Material.BUNDLE));
        for (DyeColor colour : DyeColor.values()) {
            bundles.add(Material.valueOf(colour.name() + "_BUNDLE"));
        }
        assertThat(bundles).hasSize(17);
        for (Material bundle : bundles) {
            view.getTopInventory().setItem(0, new ItemStack(bundle));
            assertThat(refused(new ItemStack(Material.OAK_LOG), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                    .as("log into %s", bundle).isTrue();
            assertThat(refused(new ItemStack(Material.STICK), InventoryAction.PLACE_ALL_INTO_BUNDLE))
                    .as("stick into %s", bundle).isFalse();
            // The right-click path (servers without the bundle actions): here the slot's material decides.
            assertThat(rightClicked(new ItemStack(Material.OAK_LOG)))
                    .as("right-click a log onto %s", bundle).isTrue();
            assertThat(rightClicked(new ItemStack(Material.STICK)))
                    .as("right-click a stick onto %s", bundle).isFalse();
        }
    }

    @Test
    void rightClickOntoANonBundleIsLeftAlone() {
        // Control for the material check: the same right click onto a non-bundle is not a bundle insert.
        for (Material other : List.of(Material.CHEST, Material.DIRT, Material.SHULKER_BOX)) {
            view.getTopInventory().setItem(0, new ItemStack(other));
            assertThat(rightClicked(new ItemStack(Material.OAK_LOG))).as("log right-clicked onto %s", other).isFalse();
        }
    }

    /** Right-clicks the cursor stack onto slot 0 with the pre-1.21.4 action (no bundle actions in the API). */
    private boolean rightClicked(ItemStack cursor) {
        player.setItemOnCursor(cursor);
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT,
                InventoryAction.SWAP_WITH_CURSOR);
        listener.onClick(click);
        return click.isCancelled();
    }

    @Test
    void basketOnTheCursorPicksUpAnythingAsInTfc() {
        // TFC overrides only "other stacked on me"; the vanilla "stacked on other" path (basket on the cursor) is
        // unchecked there too.
        view.getTopInventory().setItem(1, custom(Material.PAPER, "tfp:metal/anvil/copper"));
        player.setItemOnCursor(custom(Material.BUNDLE, BASKET));
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 1, ClickType.RIGHT,
                InventoryAction.PICKUP_ALL_INTO_BUNDLE);
        listener.onClick(click);
        assertThat(click.isCancelled()).isFalse();
    }
}
