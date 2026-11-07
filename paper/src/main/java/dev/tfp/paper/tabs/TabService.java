/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/network/SwitchInventoryTabPacket.java @ c3b4d9536 (the server closes the current
 *       screen and opens the tab's),
 *       src/main/java/net/dries007/tfc/client/ClimateRenderCache.java @ c3b4d9536 (the climate values the page shows),
 *       src/main/java/net/dries007/tfc/client/ClientHelpers.java @ c3b4d9536 (inNorthernHemisphere)
 */
package dev.tfp.paper.tabs;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.climate.Solar;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tabs.TabLayout.Page;
import dev.tfp.paper.tabs.TabLayout.Tab;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-SURV-07: opens tab pages, "decide async, apply sync": the player's thread captures plain values (position, seed,
 * food level, stored nutrition, the clock), a worker builds the {@link TabPage} (the climate model reads the seed's noise),
 * and the player's thread opens it, only if the player is still looking at their own inventory or at a tab page. The page
 * is a snapshot: it is rebuilt when a tab button is pressed, not live (TFC redraws every frame; see the ledger).
 */
public final class TabService {
    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Supplier<ClockSnapshot> clock;
    private final Function<UUID, Optional<PlayerData>> playerData;
    private final Supplier<ClimateTemperatureStyle> style;
    private final boolean glyphs;

    /**
     * @param glyphs true when CraftEngine runs (its pack has the art and the line fonts)
     */
    public TabService(Server server, Scheduler scheduler, Messages messages, Supplier<ClockSnapshot> clock,
            Function<UUID, Optional<PlayerData>> playerData, Supplier<ClimateTemperatureStyle> style, boolean glyphs) {
        this.server = server;
        this.scheduler = scheduler;
        this.messages = messages;
        this.clock = clock;
        this.playerData = playerData;
        this.style = style;
        this.glyphs = glyphs;
    }

    public boolean glyphs() {
        return glyphs;
    }

    /** What the player's thread reads. */
    record Capture(UUID player, String locale, long worldSeed, boolean overworld, BlockPos pos, int foodLevel,
            Optional<PlayerData> data, ClockSnapshot clock, ClimateTemperatureStyle style) {
    }

    Capture capture(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        return new Capture(player.getUniqueId(), Messages.code(player.locale()), world.getSeed(),
            world.getEnvironment() == World.Environment.NORMAL, new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()),
            player.getFoodLevel(), playerData.apply(player.getUniqueId()), clock.get(), style.get());
    }

    /** The pure part: the page for a capture. */
    TabPage build(Page page, Capture c) {
        TabPage.Lang lang = (key, args) -> messages.get(c.locale(), key, args);
        return switch (page) {
            case CALENDAR -> {
                boolean north = !c.overworld() || Solar.inNorthernHemisphere(c.pos().z(), climate(c).hemisphereScale());
                yield TabPage.calendar(new TabPage.CalendarInput(c.clock().isSet(), c.clock().calendarTicks(),
                    c.clock().daysInMonth(), north), lang);
            }
            case NUTRITION -> TabPage.nutrition(nutrients(c.data(), c.foodLevel()), lang);
            case CLIMATE -> TabPage.climate(climateInput(c), lang);
        };
    }

    private static OverworldClimate climate(Capture c) {
        return OverworldClimate.forWorldSeed(c.worldSeed(), OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
    }

    static TabPage.ClimateInput climateInput(Capture c) {
        if (!c.overworld() || !c.clock().isSet()) {
            return new TabPage.ClimateInput(false, Float.NaN, Float.NaN, 0, Float.NaN, Float.NaN, true, c.style());
        }
        OverworldClimate climate = climate(c);
        ChunkClimate chunk = ChunkClimateSource.climateAt(c.worldSeed(), c.pos().x(), c.pos().z());
        long ticks = c.clock().calendarTicks();
        int dim = c.clock().daysInMonth();
        return new TabPage.ClimateInput(true, climate.averageTemperature(c.pos(), chunk), chunk.averageRainfall(),
            chunk.rainfallVariance(), climate.instantTemperature(c.pos(), chunk, ticks, dim),
            climate.instantRainfall(chunk, ticks, dim),
            Solar.inNorthernHemisphere(c.pos().z(), climate.hemisphereScale()), c.style());
    }

    /**
     * TFC {@code NutritionData.getNutrient} for each nutrient at the vanilla food level; without stored data (not loaded
     * yet) TFC's defaults.
     */
    static float[] nutrients(Optional<PlayerData> data, int foodLevel) {
        List<FoodData> foods = new ArrayList<>();
        data.ifPresent(d -> {
            for (PlayerData.FoodRecord r : d.nutrition()) {
                PlayerData.Nutrients n = r.nutrients();
                foods.add(new FoodData(r.hunger(), r.water(), r.saturation(), r.intoxication(), n.grain(), n.fruit(),
                    n.vegetables(), n.protein(), n.dairy(), r.decayModifier()));
            }
        });
        int hunger = Math.clamp(foodLevel, 0, NutritionState.MAX_HUNGER);
        return new NutritionState(foods).levels(hunger, NutritionConfig.DEFAULT).values();
    }

    /**
     * Opens {@code page} for {@code player}: capture on the player's thread, build on a worker, open on the player's
     * thread. Any thread.
     *
     * @return true once the page is open; false when the player left, or opened another screen meanwhile
     */
    public CompletableFuture<Boolean> open(Player player, Page page) {
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        boolean scheduled = scheduler.entity(player, () -> {
            Capture c = capture(player);
            CompletableFuture.supplyAsync(() -> build(page, c), scheduler.cpuExecutor()).whenComplete((built, err) -> {
                if (err != null) {
                    out.completeExceptionally(err);
                    return;
                }
                boolean again = scheduler.entity(player, () -> {
                    try {
                        out.complete(show(player, built));
                    } catch (RuntimeException e) {
                        out.completeExceptionally(e);
                        throw e;
                    }
                }, () -> out.complete(false));
                if (!again) {
                    out.complete(false);
                }
            });
        }, () -> out.complete(false));
        if (!scheduled) {
            out.complete(false);
        }
        return out;
    }

    /** True while {@code player} looks at their own inventory, a tab page, or nothing. Player's thread. */
    static boolean mayReplace(Player player) {
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        return top == null || top.getType() == InventoryType.CRAFTING || TabSession.of(top) != null;
    }

    /** Player's thread: opens the built page (the apply step). */
    boolean show(Player player, TabPage page) {
        if (!player.isOnline() || !mayReplace(player)) {
            return false;
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.isEmpty()) {
            return false; // TFC switches tabs only with an empty hand; we would drop or return the stack
        }
        TabSession session = new TabSession(player, page);
        Inventory inv = server.createInventory(session, TabLayout.CHEST.size(), TabTitles.title(glyphs, page));
        session.inventory(inv);
        for (Tab t : Tab.values()) {
            inv.setItem(t.slot(), button(player, t, page));
        }
        return player.openInventory(inv) != null;
    }

    /** A tab button: the blank model over the button art; its tooltip is the tab name, and the page text when active. */
    ItemStack button(Player viewer, Tab tab, TabPage page) {
        ItemStack stack = new ItemStack(org.bukkit.Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        GuiButtons.style(meta, glyphs, messages.get(viewer, "tabs.title." + tab.key()));
        if (tab == page.page().tab) {
            List<Component> lore = new ArrayList<>();
            for (String line : page.plainLines()) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
