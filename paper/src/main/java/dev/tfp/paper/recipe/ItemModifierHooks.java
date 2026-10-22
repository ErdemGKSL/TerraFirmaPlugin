/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs @ c3b4d9536 (every ItemStackModifier.apply) and
 * src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (updateFoodFromPrevious,
 * updateFoodFromAllPrevious, setCreatedNow, roundCreationDate, applyTrait, removeTrait)
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.json.Ids;
import dev.tfp.core.recipe.hooks.ModifierHooks;
import dev.tfp.core.recipe.output.ItemStackModifier;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * TFC's output modifiers ({@code tfc:copy_food}, {@code tfc:copy_heat}, {@code tfc:add_trait},
 * {@code tfc:damage_crafting_remainder}, ...) applied to real {@link ItemStack}s, with food and heat stored as PDC
 * records (lazy, calendar-based). Modifiers whose data belongs to another feature go through {@link ModifierExtensions}.
 *
 * <p>Immutable. One instance per operation for crafting (see {@link #forCrafting}), so no thread-local state is needed
 * where TFC uses {@code RecipeHelpers.getCraftingInput()} and {@code getCraftingPlayer()}. Runs on the thread that owns
 * the stacks (the player's or the device's region thread); it never touches the world.
 */
public final class ItemModifierHooks implements ModifierHooks<ItemStack> {
    /** The services the modifiers read. */
    public record Services(CustomItems items, Function<ItemStack, Optional<String>> itemIds, ItemFoodHooks food,
            Supplier<FoodDefinitions> foodDefinitions, ItemHeatHooks heat, ItemHeatHooks.Capacities heatCapacities,
            LongSupplier heatNow) {
        public Services {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(itemIds, "itemIds");
            Objects.requireNonNull(food, "food");
            Objects.requireNonNull(foodDefinitions, "foodDefinitions");
            Objects.requireNonNull(heat, "heat");
            Objects.requireNonNull(heatCapacities, "heatCapacities");
            Objects.requireNonNull(heatNow, "heatNow");
        }
    }

    /** TFC's lang key of the display-only chance line ({@code ChanceModifier}). */
    public static final String CHANCE_KEY = "tfc.tooltip.chance";

    private final Services services;
    private final TagLookup tags;
    private final ModifierExtensions extensions;
    private final DoubleSupplier random;
    private final List<ItemStack> craftingInputs;
    private final Consumer<ItemStack> crafter;

    public ItemModifierHooks(Services services, TagLookup tags, ModifierExtensions extensions, DoubleSupplier random) {
        this(services, tags, extensions, random, List.of(), stack -> {
        });
    }

    private ItemModifierHooks(Services services, TagLookup tags, ModifierExtensions extensions, DoubleSupplier random,
            List<ItemStack> craftingInputs, Consumer<ItemStack> crafter) {
        this.services = Objects.requireNonNull(services, "services");
        this.tags = Objects.requireNonNull(tags, "tags");
        this.extensions = Objects.requireNonNull(extensions, "extensions");
        this.random = Objects.requireNonNull(random, "random");
        this.craftingInputs = craftingInputs;
        this.crafter = crafter;
    }

    /**
     * A copy for one crafting operation: {@code inputs} are the grid's stacks (TFC {@code RecipeHelpers.getCraftingInput},
     * read only) and {@code giveToCrafter} receives extra products ({@code tfc:extra_products}).
     */
    public ItemModifierHooks forCrafting(List<ItemStack> inputs, Consumer<ItemStack> giveToCrafter) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : inputs) {
            if (s != null && !s.isEmpty()) {
                copy.add(s);
            }
        }
        return new ItemModifierHooks(services, tags, extensions, random, List.copyOf(copy),
                Objects.requireNonNull(giveToCrafter, "giveToCrafter"));
    }

    // ---- stack basics ----

    @Override
    public ItemStack empty() {
        return ItemStack.empty();
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    public int count(ItemStack stack) {
        return isEmpty(stack) ? 0 : stack.getAmount();
    }

    /** TFC {@code copyWithCount}: a copy with {@code count} items (empty for 0 or less). */
    @Override
    public ItemStack withCount(ItemStack stack, int count) {
        if (isEmpty(stack) || count <= 0) {
            return empty();
        }
        ItemStack copy = stack.clone();
        copy.setAmount(count);
        return copy;
    }

    @Override
    public ItemStack copy(ItemStack stack) {
        return isEmpty(stack) ? empty() : stack.clone();
    }

    /**
     * A new stack from a template: a CraftEngine item for our ids, else the vanilla item with its data components.
     * A food gets a creation date now (TFC: transient non-decaying, captured as the rounded current date when the
     * stack is first used). An unknown id gives an empty stack.
     */
    @Override
    public ItemStack create(ItemStackTemplate template) {
        ItemStack stack = createRaw(template);
        if (stack.isEmpty()) {
            return stack;
        }
        if (services.foodDefinitions().get().get(template.id()).isPresent() && !FoodPdc.has(stack)) {
            ItemFoodHooks food = services.food();
            FoodPdc.write(stack, FoodState.createdAt(food.now(), food.config()));
        }
        return stack;
    }

    private ItemStack createRaw(ItemStackTemplate template) {
        Optional<ItemStack> custom = services.items().create(template.id(), template.count());
        if (custom.isPresent()) {
            return custom.get();
        }
        if (template.components().isPresent()) {
            try {
                ItemStack s = Bukkit.getItemFactory().createItemStack(
                        ItemSyntax.of(template.id(), template.components().get()));
                s.setAmount(template.count());
                return s;
            } catch (RuntimeException | LinkageError e) {
                // An unparseable component (or a test server without the parser) keeps the plain item.
            }
        }
        Material m = Material.matchMaterial(template.id());
        return m == null || !m.isItem() || m.isAir() ? empty() : new ItemStack(m, template.count());
    }

    @Override
    public double nextRandom() {
        return random.getAsDouble();
    }

    @Override
    public List<ItemStack> craftingInputs() {
        return craftingInputs;
    }

    @Override
    public void giveToCrafter(ItemStack stack) {
        if (!isEmpty(stack)) {
            crafter.accept(stack);
        }
    }

    // ---- food ----

    private FoodConfig foodConfig() {
        return services.food().config();
    }

    private float decayModifier(ItemStack stack) {
        return services.food().decayModifier(stack);
    }

    /** TFC {@code FoodCapability.updateFoodFromPrevious(input, stack)}. */
    @Override
    public ItemStack copyFood(ItemStack stack, ItemStack input) {
        if (isEmpty(stack) || isEmpty(input)) {
            return stack;
        }
        Optional<FoodState> oldFood = FoodPdc.read(input);
        Optional<FoodState> newFood = FoodPdc.read(stack);
        if (oldFood.isPresent() && newFood.isPresent()) {
            FoodConfig config = foodConfig();
            // Copy the traits first, so the decay modifier counts both (TFC: Helpers.immutableAddAll).
            List<FoodTrait> traits = new ArrayList<>(newFood.get().traits());
            traits.addAll(oldFood.get().traits());
            FoodState temp = new FoodState(newFood.get().creationDate(), traits);
            float decayDelta = temp.decayDateModifier(decayModifier(stack), config)
                    / oldFood.get().decayDateModifier(decayModifier(input), config);
            long date = FoodDecay.newRoundedCreationDate(oldFood.get().creationDate(), decayDelta,
                    services.food().now(), config.stackTicks());
            FoodPdc.write(stack, new FoodState(date, traits));
        }
        return stack;
    }

    /** TFC {@code FoodCapability.updateFoodFromAllPrevious(craftingInputs, stack)}. */
    @Override
    public ItemStack copyOldestFood(ItemStack stack) {
        Optional<FoodState> newFood = isEmpty(stack) ? Optional.empty() : FoodPdc.read(stack);
        if (newFood.isEmpty() || craftingInputs.isEmpty()) {
            return stack;
        }
        FoodConfig config = foodConfig();
        float decayDateModifier = 0;
        long oldCreationDate = Long.MAX_VALUE;
        int oldFoodCount = 0;
        for (ItemStack old : craftingInputs) {
            Optional<FoodState> oldFood = FoodPdc.read(old);
            if (oldFood.isPresent()) {
                decayDateModifier += oldFood.get().decayDateModifier(decayModifier(old), config);
                oldCreationDate = Math.min(oldCreationDate, oldFood.get().creationDate());
                oldFoodCount++;
            }
        }
        if (oldFoodCount > 0) {
            float decayDelta = oldFoodCount * newFood.get().decayDateModifier(decayModifier(stack), config)
                    / decayDateModifier;
            long date = FoodDecay.newCreationDate(oldCreationDate, decayDelta, services.food().now(),
                    config.stackTicks());
            FoodPdc.write(stack, new FoodState(date, newFood.get().traits()));
        }
        return stack;
    }

    /** TFC {@code FoodCapability.setCreatedNow}: the rounded current date. */
    @Override
    public ItemStack resetFood(ItemStack stack) {
        Optional<FoodState> food = isEmpty(stack) ? Optional.empty() : FoodPdc.read(stack);
        food.ifPresent(f -> FoodPdc.write(stack, new FoodState(
                FoodDecay.roundedCreationDate(services.food().now(), foodConfig().stackTicks()), f.traits())));
        return stack;
    }

    /** TFC {@code FoodCapability.roundCreationDate}: flags stay. */
    @Override
    public ItemStack roundCreationDate(ItemStack stack) {
        Optional<FoodState> food = isEmpty(stack) ? Optional.empty() : FoodPdc.read(stack);
        if (food.isPresent() && !FoodDecay.isFlag(food.get().creationDate())) {
            FoodPdc.write(stack, new FoodState(
                    FoodDecay.roundedCreationDate(food.get().creationDate(), foodConfig().stackTicks()),
                    food.get().traits()));
        }
        return stack;
    }

    /** TFC {@code FoodCapability.applyTrait}. */
    @Override
    public ItemStack addTrait(ItemStack stack, String trait) {
        FoodTrait t = trait(trait);
        Optional<FoodState> food = isEmpty(stack) || t == null ? Optional.empty() : FoodPdc.read(stack);
        food.ifPresent(f -> FoodPdc.write(stack,
                f.withTraitApplied(t, decayModifier(stack), services.food().now(), foodConfig())));
        return stack;
    }

    /** TFC {@code FoodCapability.removeTrait}. */
    @Override
    public ItemStack removeTrait(ItemStack stack, String trait) {
        FoodTrait t = trait(trait);
        Optional<FoodState> food = isEmpty(stack) || t == null ? Optional.empty() : FoodPdc.read(stack);
        food.ifPresent(f -> FoodPdc.write(stack,
                f.withTraitRemoved(t, decayModifier(stack), services.food().now(), foodConfig())));
        return stack;
    }

    private static FoodTrait trait(String id) {
        String ours = Ids.namespace(id).equals(Ids.TFC) ? "tfp:" + Ids.path(id) : id;
        return FoodTrait.byId(ours);
    }

    @Override
    public ItemStack applyMeal(ItemStack stack, ItemStackModifier.Meal meal) {
        return extension(ModifierExtensions.Kind.MEAL, stack, empty(), meal).orElse(stack);
    }

    // ---- heat ----

    private boolean heatable(ItemStack stack) {
        return !isEmpty(stack) && services.heatCapacities().of(stack) > 0f;
    }

    /** TFC {@code CopyHeatModifier}: a heatable input's temperature onto a heatable output. */
    @Override
    public ItemStack copyHeat(ItemStack stack, ItemStack input) {
        if (heatable(input) && heatable(stack)) {
            setTemperature(stack, services.heat().temperature(input));
        }
        return stack;
    }

    /** TFC {@code AddHeatModifier}: adds to the output's current temperature (not below 0). */
    @Override
    public ItemStack addHeat(ItemStack stack, float temperature) {
        if (heatable(stack)) {
            setTemperature(stack, services.heat().temperature(stack) + temperature);
        }
        return stack;
    }

    private void setTemperature(ItemStack stack, float temperature) {
        long now = services.heatNow().getAsLong();
        HeatPdc.write(stack, HeatState.cold(now).set(temperature, now));
    }

    // ---- forging ----

    @Override
    public ItemStack copyForgingBonus(ItemStack stack, ItemStack input) {
        return extension(ModifierExtensions.Kind.COPY_FORGING_BONUS, stack, input, null).orElse(stack);
    }

    // ---- crafting remainders ----

    /** TFC {@code CraftingRemainderModifier}: the input's crafting remainder, or empty. */
    @Override
    public ItemStack craftingRemainder(ItemStack input) {
        if (isEmpty(input)) {
            return empty();
        }
        if (services.items().idOf(input).isPresent()) {
            return extension(ModifierExtensions.Kind.CRAFTING_REMAINDER, empty(), input, null).orElse(empty());
        }
        Material remainder = input.getType().getCraftingRemainingItem();
        return remainder == null || remainder.isAir() ? empty() : new ItemStack(remainder);
    }

    /**
     * TFC {@code DamageCraftingRemainderModifier}: the input damaged by one (vanilla Unbreaking may spare it, as for the
     * crafting player in TFC), empty when that breaks it, a plain copy of an unbreakable damageable item, else empty.
     */
    @Override
    public ItemStack damageCraftingRemainder(ItemStack stack, ItemStack input) {
        if (isEmpty(input)) {
            return empty();
        }
        ItemStack copy = input.clone();
        ItemMeta meta = copy.getItemMeta();
        if (!(meta instanceof Damageable d)) {
            return empty();
        }
        int max = d.hasMaxDamage() ? d.getMaxDamage() : copy.getType().getMaxDurability();
        if (max <= 0) {
            return empty();
        }
        if (!meta.isUnbreakable()) {
            int unbreaking = copy.getEnchantmentLevel(Enchantment.UNBREAKING);
            boolean spared = unbreaking > 0 && nextRandom() < (double) unbreaking / (unbreaking + 1);
            if (!spared) {
                int damage = d.getDamage() + 1;
                if (damage >= max) {
                    return empty();
                }
                d.setDamage(damage);
                copy.setItemMeta(d);
            }
        }
        return copy;
    }

    /** TFC {@code EmptyBowlModifier}: the bowl stored on the input, else a bowl ({@code Bowl.DISPLAY}). */
    @Override
    public ItemStack emptyBowl(ItemStack stack, ItemStack input) {
        return extension(ModifierExtensions.Kind.EMPTY_BOWL, stack, input, null)
                .orElseGet(() -> new ItemStack(Material.BOWL));
    }

    // ---- other devices ----

    @Override
    public ItemStack flowerCutting(ItemStack stack, ItemStack input) {
        return extension(ModifierExtensions.Kind.FLOWER_CUTTING, stack, input, null).orElse(empty());
    }

    @Override
    public ItemStack addBaitToRod(ItemStack stack) {
        return extension(ModifierExtensions.Kind.ADD_BAIT_TO_ROD, stack, empty(), null).orElse(stack);
    }

    @Override
    public ItemStack addGlass(ItemStack stack, ItemStack input) {
        return extension(ModifierExtensions.Kind.ADD_GLASS, stack, input, null).orElse(stack);
    }

    @Override
    public ItemStack addPowder(ItemStack stack) {
        return extension(ModifierExtensions.Kind.ADD_POWDER, stack, empty(), null).orElse(stack);
    }

    /**
     * TFC {@code DyeLeatherModifier} ({@code DyedItemColor.applyDyes}): one dyed copy of a {@code #minecraft:dyeable}
     * item, empty for any other item.
     */
    @Override
    public ItemStack dyeLeather(ItemStack stack, String color) {
        if (isEmpty(stack)) {
            return empty();
        }
        Optional<String> id = services.itemIds().apply(stack);
        if (id.isEmpty() || !tags.contains(TagType.ITEM, "minecraft:dyeable", id.get())) {
            return empty();
        }
        ItemStack copy = withCount(stack, 1);
        if (copy.getItemMeta() instanceof LeatherArmorMeta meta) {
            OptionalInt existing = meta.isDyed() ? OptionalInt.of(meta.getColor().asRGB()) : OptionalInt.empty();
            int dye = DyeColor.valueOf(color.toUpperCase(Locale.ROOT)).getColor().asRGB();
            meta.setColor(Color.fromRGB(DyeMixing.mix(existing, List.of(dye))));
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** TFC {@code RemoveDyeModifier}: the input (whole stack) without its dye colour. */
    @Override
    public ItemStack removeDye(ItemStack stack, ItemStack input) {
        if (isEmpty(input)) {
            return empty();
        }
        ItemStack copy = input.clone();
        if (copy.getItemMeta() instanceof LeatherArmorMeta meta && meta.isDyed()) {
            meta.setColor(null);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** TFC {@code ChanceModifier} in display context: a {@code "<chance * 100>% chance"} lore line. */
    @Override
    public ItemStack appendChanceLore(ItemStack stack, float chance) {
        if (isEmpty(stack)) {
            return stack;
        }
        List<Component> lore = new ArrayList<>();
        List<Component> current = stack.lore();
        if (current != null) {
            lore.addAll(current);
        }
        lore.add(Component.translatable(CHANCE_KEY, Component.text(String.valueOf(chance * 100f))));
        stack.lore(lore);
        return stack;
    }

    private Optional<ItemStack> extension(ModifierExtensions.Kind kind, ItemStack stack, ItemStack input,
            Object modifier) {
        Optional<ModifierExtensions.Handler> h = extensions.get(kind);
        if (h.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(h.get().apply(stack, input, craftingInputs, modifier).orElseGet(this::empty));
    }
}
