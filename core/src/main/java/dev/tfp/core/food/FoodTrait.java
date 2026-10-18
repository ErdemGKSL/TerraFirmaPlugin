/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodTraits.java @ c3b4d9536
 */
package dev.tfp.core.food;

import java.util.List;
import java.util.Objects;

/**
 * A food trait (TFC {@code FoodTrait}): an id and a decay modifier that multiplies the food's decay
 * modifier. Below 1 the food lasts longer, above 1 it rots sooner, 0 stops decay.
 *
 * <p>TFC reads each modifier from a {@code Double} server config and uses {@code floatValue()}; the
 * constants below are those defaults ({@code ServerConfig}, {@code trait*Modifier}) as floats. A server
 * that configures other values builds its own instances with the same ids.
 *
 * @param id            the trait id, as the data names it, e.g. {@code tfp:salted} (TFC {@code tfc:salted})
 * @param decayModifier the multiplier on the food's decay modifier, {@code >= 0}
 */
public record FoodTrait(String id, float decayModifier) {
    public static final FoodTrait SALTED = new FoodTrait("tfp:salted", 0.5f);
    /** No decay change; required to pickle foods. */
    public static final FoodTrait BRINED = new FoodTrait("tfp:brined", 1f);
    public static final FoodTrait PICKLED = new FoodTrait("tfp:pickled", 0.5f);
    /** Large and small vessels. */
    public static final FoodTrait PRESERVED = new FoodTrait("tfp:preserved", 0.5f);
    /** Sealed in vinegar. */
    public static final FoodTrait VINEGAR = new FoodTrait("tfp:vinegar", 0.1f);
    public static final FoodTrait CHARCOAL_GRILLED = new FoodTrait("tfp:charcoal_grilled", 1.25f);
    public static final FoodTrait WOOD_GRILLED = new FoodTrait("tfp:wood_grilled", 0.8f);
    public static final FoodTrait BURNT_TO_A_CRISP = new FoodTrait("tfp:burnt_to_a_crisp", 2.5f);
    public static final FoodTrait WILD = new FoodTrait("tfp:wild", 0.5f);
    /** Sealed by boiling: never decays. */
    public static final FoodTrait CANNED = new FoodTrait("tfp:canned", 0f);

    /** All TFC traits with their default modifiers, in TFC registration order. */
    public static final List<FoodTrait> DEFAULTS = List.of(SALTED, BRINED, PICKLED, PRESERVED, VINEGAR,
            CHARCOAL_GRILLED, WOOD_GRILLED, BURNT_TO_A_CRISP, WILD, CANNED);

    public FoodTrait {
        Objects.requireNonNull(id, "id");
        if (!(decayModifier >= 0) || Float.isInfinite(decayModifier)) {
            throw new IllegalArgumentException("decayModifier must be finite and >= 0: " + decayModifier);
        }
    }

    /** The default trait with this id, or {@code null}. */
    public static FoodTrait byId(String id) {
        for (FoodTrait t : DEFAULTS) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
