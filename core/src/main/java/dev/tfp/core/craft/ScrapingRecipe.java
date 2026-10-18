/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/ScrapingRecipe.java @ c3b4d9536 (the fields and getters)
 */
package dev.tfp.core.craft;

/**
 * One scraping recipe: the item the knife scrapes, the result when the whole grid is scraped, and an optional extra drop
 * spawned into the world at the same moment (the wool from raw hides).
 *
 * @param id            the recipe id, the TFC file path under {@code tfc:recipe/}
 * @param ingredient    the item id that is scraped
 * @param inputTexture  the texture shown while the item is unscraped (presentation only)
 * @param outputTexture the texture shown while the item is scraped (presentation only)
 * @param result        the result stack
 * @param extraDrop     the extra drop, or {@code null} when the recipe has none
 */
public record ScrapingRecipe(
    String id, String ingredient, String inputTexture, String outputTexture, CraftOutput result, CraftOutput extraDrop) {
    public ScrapingRecipe {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty scraping recipe id");
        if (ingredient == null || ingredient.isEmpty()) throw new IllegalArgumentException("scraping recipe " + id + " has no ingredient");
        if (result == null) throw new IllegalArgumentException("scraping recipe " + id + " has no result");
    }
}
