/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/DairyAnimal.java (mobInteract, hasProduct),
 * src/main/java/net/dries007/tfc/common/entities/livestock/WoolyAnimal.java (isShearable, isReadyForAnimalProduct,
 * onSheared, hasProduct, getWoolItem), src/main/java/net/dries007/tfc/common/entities/livestock/ProducingAnimal.java and
 * src/main/java/net/dries007/tfc/common/entities/livestock/ProducingMammal.java (isReadyForAnimalProduct,
 * setProductsCooldown, getProductsCooldown) @ c3b4d9536
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.core.livestock.LivestockRules;
import dev.tfp.core.livestock.LivestockSpecies;

import java.util.Optional;

/**
 * TFC's milk and wool rules as pure functions of the stored animal, the stored product tick and the player tick. Nothing
 * ticks: the cooldown is {@code produceTicks + producedTick - now}, clamped at zero, and readiness is read when asked.
 *
 * <p><b>Cooldown.</b> {@link #cooldownRemaining} is {@code ProducingAnimal.getProductsCooldown}. A harvest sets the
 * product tick to the current player tick, which starts the cooldown.
 *
 * <p><b>Milk and eggs</b> use {@code cooldown == 0} ({@code DairyAnimal.hasProduct}, and {@code OviparousAnimal.hasProduct}
 * with a {@code producedTick <= 0} shortcut). <b>Shearing</b> uses {@code producedTick <= 0 || cooldown <= 0}
 * ({@code WoolyAnimal.hasProduct}), so an animal that has never been sheared always has wool.
 * The milk rule has no {@code producedTick <= 0} shortcut, which is upstream behaviour: a cow that was never milked can
 * only be milked once {@code produceTicks} of world time have passed. This port keeps it on purpose.
 *
 * <p><b>Familiarity threshold.</b> TFC compares the float familiarity with the double {@code produceFamiliarity}, so
 * the comparison is done in double. A familiarity of {@code 0.15f} therefore passes the default {@code 0.15}, as it
 * does in TFC.
 */
public final class ProductRules {
    /** TFC {@code WoolyAnimal.getWoolItem}: a familiarity above this gives two wool, otherwise one. */
    public static final float DOUBLE_WOOL_FAMILIARITY = 0.99f;
    /** TFC {@code addUses(event.getUses())} after a milking or a shearing; {@code AnimalProductEvent} defaults to 1. */
    public static final int USES_PER_HARVEST = 1;

    private ProductRules() {
    }

    /**
     * A harvest that happened: the animal after it (caught up, uses increased) and the product state with its new
     * product tick. The glue then gives the player the item, a bucket of milk, or the wool.
     */
    public record Harvest(AnimalState animal, ProductState product) {
    }

    /** A shearing that happened: the harvest state and the number of wool items (1 or 2). */
    public record Shearing(AnimalState animal, ProductState product, int wool) {
    }

    /**
     * {@code ProducingAnimal.getProductsCooldown}: {@code max(0, produceTicks + producedTick - now)}.
     */
    public static long cooldownRemaining(ProductSpec spec, ProductState product, long playerTicks) {
        return Math.max(0L, (long) spec.produceTicks() + product.producedTick() - playerTicks);
    }

    /**
     * {@code hasProduct} for the spec's kind, read at {@code playerTicks}. The animal's familiarity is not checked here,
     * only in {@link #isReady}, as in TFC.
     *
     * @param species the species, for the adulthood days
     */
    public static boolean hasProduct(ProductSpec spec, LivestockSpecies species, AnimalState animal, ProductState product,
                                     long playerTicks) {
        final long cooldown = cooldownRemaining(spec, product, playerTicks);
        final boolean adult = LivestockRules.ageAt(animal, species, playerTicks) == LivestockAge.ADULT;
        return switch (spec.kind()) {
            case MILK -> !animal.male() && adult && cooldown == 0L;
            case EGG -> !animal.male() && adult && (product.producedTick() <= 0L || cooldown <= 0L);
            case SHEAR -> product.producedTick() <= 0L || cooldown <= 0L;
        };
    }

    /**
     * TFC {@code isReadyForAnimalProduct}, the check the player and the nest box use. Milk and eggs are ready when
     * familiarity is above the threshold and {@link #hasProduct}. Shearing is also limited to adults.
     *
     * <p>Familiarity is read lazily with {@link LivestockRules#familiarityAt}, so it includes every decay up to
     * {@code playerTicks}.
     */
    public static boolean isReady(ProductSpec spec, LivestockSpecies species, AnimalState animal, ProductState product,
                                  long playerTicks) {
        final double familiarity = LivestockRules.familiarityAt(animal, playerTicks);
        if (!(familiarity > spec.produceFamiliarity())) {
            return false;
        }
        if (!hasProduct(spec, species, animal, product, playerTicks)) {
            return false;
        }
        return spec.kind() != ProductKind.SHEAR
            || LivestockRules.ageAt(animal, species, playerTicks) == LivestockAge.ADULT;
    }

    /**
     * TFC {@code DairyAnimal.mobInteract} after the fluid transfer: the animal gets the cooldown and one use.
     * The caller gives the bucket of milk.
     *
     * @return the harvest, or empty if the animal is not ready or the spec is not a milk product
     */
    public static Optional<Harvest> milk(ProductSpec spec, LivestockSpecies species, AnimalState animal, ProductState product,
                                         long playerTicks) {
        if (spec.kind() != ProductKind.MILK || !isReady(spec, species, animal, product, playerTicks)) {
            return Optional.empty();
        }
        return Optional.of(harvest(animal, playerTicks));
    }

    /**
     * TFC {@code WoolyAnimal.onSheared}: the cooldown starts at the current tick, the animal gets one use, and the wool
     * count is taken from the familiarity at that tick (not from any familiarity change made by the shearing, which
     * there is none).
     *
     * @return the shearing, or empty if the animal is not shearable ({@code isShearable == isReadyForAnimalProduct})
     */
    public static Optional<Shearing> shear(ProductSpec spec, LivestockSpecies species, AnimalState animal, ProductState product,
                                           long playerTicks) {
        if (spec.kind() != ProductKind.SHEAR || !isReady(spec, species, animal, product, playerTicks)) {
            return Optional.empty();
        }
        final float familiarity = (float) LivestockRules.familiarityAt(animal, playerTicks);
        final Harvest harvest = harvest(animal, playerTicks);
        return Optional.of(new Shearing(harvest.animal(), harvest.product(), woolAmount(familiarity)));
    }

    /** TFC {@code WoolyAnimal.getWoolItem}: two wool above {@link #DOUBLE_WOOL_FAMILIARITY}, otherwise one. */
    public static int woolAmount(float familiarity) {
        return familiarity > DOUBLE_WOOL_FAMILIARITY ? 2 : 1;
    }

    /**
     * Catches the animal up to {@code playerTicks}, adds the harvest use and starts the cooldown at {@code playerTicks}.
     * Shared by milking, shearing and laying, since TFC does the same two things in each.
     */
    static Harvest harvest(AnimalState animal, long playerTicks) {
        final AnimalState caught = LivestockRules.catchUp(animal, playerTicks);
        return new Harvest(caught.withUses(caught.uses() + USES_PER_HARVEST), new ProductState(playerTicks));
    }
}
