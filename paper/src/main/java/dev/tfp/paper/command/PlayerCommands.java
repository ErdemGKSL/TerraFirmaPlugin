/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/PlayerCommand.java @ c3b4d9536
 */
package dev.tfp.paper.command;

import dev.tfp.core.command.PlayerCommand;
import dev.tfp.core.food.HungerState;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.food.NutritionConfig;
import dev.tfp.core.food.NutritionState;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.thirst.ThirstConfig;
import dev.tfp.core.thirst.ThirstState;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.player.GameModeNeeds;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import org.bukkit.entity.Player;

/**
 * {@code /tfp player <target> query|set|reset|add} (TFC {@code PlayerCommand}). The arithmetic is
 * {@link PlayerCommand}; this reads and writes the player's state on the player's own thread. Hunger and saturation are
 * the vanilla food bar (TFC's hunger is the vanilla food level); thirst is the stored lazy clock, brought to now first
 * ({@link GameModeNeeds#evaluate}) so a set or an add starts from the real value and stamps the clock at now.
 *
 * <p>The HUD shows the change at its next reading (at most a second): nothing here republishes it.
 */
public final class PlayerCommands {
    private final Scheduler scheduler;
    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final ToDoubleFunction<UUID> ambient;

    /**
     * @param playerTicks the calendar's player tick
     * @param ambient     the player's ambient temperature in degrees, as the HUD last read it
     */
    public PlayerCommands(Scheduler scheduler, PlayerDataStore data, LongSupplier playerTicks,
            ToDoubleFunction<UUID> ambient) {
        this.scheduler = scheduler;
        this.data = data;
        this.playerTicks = playerTicks;
        this.ambient = ambient;
    }

    /** Runs {@code request} on {@code target}; the future completes on the player's thread. Any thread. */
    public CompletableFuture<Reply> run(Player target, PlayerCommand.Request request) {
        CompletableFuture<Reply> out = new CompletableFuture<>();
        Runnable gone = () -> out.complete(Reply.error("command.player_not_found", target.getName()));
        boolean scheduled = scheduler.entity(target, () -> {
            try {
                out.complete(apply(target, request));
            } catch (RuntimeException e) {
                out.completeExceptionally(e);
            }
        }, gone);
        if (!scheduled) {
            gone.run();
        }
        return out;
    }

    /** The player's thread. */
    Reply apply(Player target, PlayerCommand.Request request) {
        UUID id = target.getUniqueId();
        if (data.get(id).isEmpty()) {
            return Reply.error("command.data.player.no_data"); // TFC fail_invalid_food_stats
        }
        long now = playerTicks.getAsLong();
        boolean active = GameModeNeeds.needsActive(target.getGameMode());
        float temperature = (float) ambient.applyAsDouble(id);
        PlayerData stored = data.update(id, d -> GameModeNeeds.evaluate(d, active, now, temperature));
        int food = Math.clamp(target.getFoodLevel(), 0, NutritionState.MAX_HUNGER);
        float saturation = Math.max(0f, target.getSaturation());
        HungerState hunger = new HungerState(food, saturation, 0L, now);
        ThirstConfig config = ThirstConfig.DEFAULT;
        ThirstState thirst = new ThirstState(Math.clamp(stored.thirst(), 0f, config.maxThirst()), now);
        if (request.verb() == PlayerCommand.Verb.QUERY) {
            return query(request.stat(), hunger, thirst, stored, food);
        }
        PlayerCommand.Outcome outcome = PlayerCommand.apply(request, hunger, thirst, config);
        switch (request.stat()) {
            case HUNGER -> target.setFoodLevel(outcome.hunger().hunger());
            case SATURATION -> target.setSaturation(outcome.hunger().saturation());
            case WATER -> {
                float water = outcome.thirst().thirst();
                data.update(id, d -> d.withThirst(water));
            }
            case NUTRITION -> throw new IllegalStateException("nutrition has no change");
        }
        return Reply.ok("command.data.player.changed", target.getName(), request.stat().name().toLowerCase(java.util.Locale.ROOT),
                changed(request.stat(), outcome));
    }

    private static String changed(PlayerCommand.Stat stat, PlayerCommand.Outcome outcome) {
        return switch (stat) {
            case HUNGER -> Integer.toString(outcome.hunger().hunger());
            case SATURATION -> Float.toString(outcome.hunger().saturation());
            case WATER -> Float.toString(outcome.thirst().thirst());
            case NUTRITION -> "";
        };
    }

    private static Reply query(PlayerCommand.Stat stat, HungerState hunger, ThirstState thirst, PlayerData stored,
            int food) {
        return switch (stat) {
            case HUNGER -> Reply.ok("command.data.player.query_hunger", hunger.hunger());
            case SATURATION -> Reply.ok("command.data.player.query_saturation", Float.toString(hunger.saturation()));
            case WATER -> Reply.ok("command.data.player.query_water", Float.toString(thirst.thirst()));
            case NUTRITION -> {
                float[] values = nutritionOf(stored).levels(food, NutritionConfig.DEFAULT).values();
                int[] p = PlayerCommand.nutritionPercents(values);
                yield Reply.ok("command.data.player.query_nutrition", p[Nutrient.GRAIN.ordinal()],
                        p[Nutrient.FRUIT.ordinal()], p[Nutrient.VEGETABLES.ordinal()], p[Nutrient.PROTEIN.ordinal()],
                        p[Nutrient.DAIRY.ordinal()]);
            }
        };
    }

    /** The stored nutrition records as the core state (the hunger it is read at is the food level). */
    static NutritionState nutritionOf(PlayerData stored) {
        List<FoodData> foods = new ArrayList<>(stored.nutrition().size());
        for (PlayerData.FoodRecord r : stored.nutrition()) {
            PlayerData.Nutrients n = r.nutrients();
            foods.add(new FoodData(r.hunger(), r.water(), r.saturation(), r.intoxication(), n.grain(), n.fruit(),
                    n.vegetables(), n.protein(), n.dairy(), r.decayModifier()));
        }
        return new NutritionState(foods);
    }

    /** Online player lookup helper for the tree: {@code Optional.empty()} when {@code name} is not online. */
    static Optional<Player> online(org.bukkit.Server server, String name) {
        return Optional.ofNullable(server.getPlayerExact(name));
    }
}
