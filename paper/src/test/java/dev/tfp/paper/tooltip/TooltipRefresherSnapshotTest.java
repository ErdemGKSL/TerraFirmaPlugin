/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip;

import static dev.tfp.paper.tooltip.NbtStacks.clock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-INFRA-05 regression (in-game rejection 2026-10-22: "Index 46 out of bounds for length 46" on every inventory
 * open). On Paper 1.21.11 the player's own crafting view reports {@code countSlots()} = 5 + the player inventory's
 * size, which is more than the 46 raw slots of the crafting menu, so reading raw slots up to {@code countSlots()}
 * threw and left the watch's in-flight flag set for good. The snapshot now reads the top and bottom inventories'
 * contents, and a failing snapshot releases the flag and logs once.
 */
class TooltipRefresherSnapshotTest extends MockServerTest {
    private static final Function<ItemStack, Optional<String>> IDS = s -> Optional.of("tfp:metal/ingot/copper");
    /** The raw slots of InventoryMenu: 0-4 crafting, 5-8 armour, 9-44 main, 45 off-hand. */
    private static final int MENU_SLOTS = 46;

    private static ItemStack heated(float temperature) {
        ItemStack stack = new ItemStack(Material.COPPER_INGOT);
        assertThat(HeatPdc.write(stack, new HeatState(temperature, 0L))).isTrue();
        return stack;
    }

    /** A 5-slot crafting grid (result + 2x2) holding only {@code contents}. */
    private static Inventory top(ItemStack[] contents) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getContents" -> contents.clone();
                    case "getSize" -> contents.length;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * The player's own crafting view as the 1.21.11 server reports it: {@code countSlots()} is top + bottom size, but
     * {@code getItem} only accepts the 46 menu slots, like CraftInventoryView over InventoryMenu.
     */
    private static InventoryView craftingView(Inventory top, PlayerInventory bottom, ItemStack cursor) {
        return (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
                new Class<?>[] {InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> bottom;
                    case "getCursor" -> cursor;
                    case "countSlots" -> top.getSize() + bottom.getSize();
                    case "getItem" -> {
                        int slot = (Integer) args[0];
                        if (slot < 0 || slot >= MENU_SLOTS) {
                            throw new IndexOutOfBoundsException(
                                    "Index " + slot + " out of bounds for length " + MENU_SLOTS);
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void thePlayersCraftingViewIsReadByInventoryNotByRawSlot() {
        PlayerMock player = server.addPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(3, heated(900f));
        inv.setItemInOffHand(heated(800f));
        ItemStack[] grid = new ItemStack[5];
        grid[2] = heated(700f);
        InventoryView view = craftingView(top(grid), inv, heated(600f));
        assertThat(view.countSlots()).as("the stub reproduces the server's oversized count")
                .isGreaterThan(MENU_SLOTS);

        Map<Integer, TooltipItem> items = assertNoThrow(view);

        int offHand = indexOf(inv.getContents(), inv.getItemInOffHand());
        assertThat(offHand).isGreaterThanOrEqualTo(36);
        assertThat(items).containsOnlyKeys(2, TooltipRefresher.BOTTOM + 3, TooltipRefresher.BOTTOM + offHand,
                TooltipRefresher.CURSOR);
        assertThat(temperature(items.get(2))).isEqualTo(700f);
        assertThat(temperature(items.get(TooltipRefresher.BOTTOM + 3))).isEqualTo(900f);
        assertThat(temperature(items.get(TooltipRefresher.BOTTOM + offHand))).as("off-hand").isEqualTo(800f);
        assertThat(temperature(items.get(TooltipRefresher.CURSOR))).as("cursor").isEqualTo(600f);
    }

    @Test
    void aFailingSnapshotReleasesTheFlagAndLogsOnce() {
        List<LogRecord> records = new ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        TooltipRefresher refresher = new TooltipRefresher(noScheduler(),
                new TooltipPipeline(() -> clock(0L), null), () -> clock(0L), IDS, FoodPdc.DEFAULT_TRAITS, log);
        TooltipRefresher.Watch w = new TooltipRefresher.Watch();

        for (int run = 0; run < 3; run++) {
            assertThat(w.busy.compareAndSet(false, true)).as("run " + run + " can take the flag").isTrue();
            assertThat(refresher.capture(w, "Bot", () -> {
                throw new IndexOutOfBoundsException("Index 46 out of bounds for length 46");
            })).isNull();
            assertThat(w.busy).as("released after run " + run).isFalse();
        }
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMessage()).startsWith("Tooltip refresh failed for Bot");

        assertThat(w.busy.compareAndSet(false, true)).isTrue();
        assertThat(refresher.capture(w, "Bot", Map::of)).isEmpty();
        assertThat(w.busy).as("a good snapshot keeps the flag for the decision").isTrue();
    }

    private static Map<Integer, TooltipItem> assertNoThrow(InventoryView view) {
        List<Map<Integer, TooltipItem>> out = new ArrayList<>();
        assertThatCode(() -> out.add(TooltipRefresher.snapshot(view, IDS, FoodPdc.DEFAULT_TRAITS)))
                .doesNotThrowAnyException();
        return out.get(0);
    }

    private static int indexOf(ItemStack[] contents, ItemStack stack) {
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].equals(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static float temperature(TooltipItem item) {
        return item.values().compound(HeatPdc.KEY).number("tfp:temperature").floatValue();
    }

    private static Scheduler noScheduler() {
        return (Scheduler) Proxy.newProxyInstance(Scheduler.class.getClassLoader(), new Class<?>[] {Scheduler.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
