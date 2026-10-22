/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.recipe;

import dev.tfp.core.json.SchemaRecord;
import java.util.Objects;

/**
 * One loaded recipe: its id ({@code tfp:heating/...}), its TFC type name ({@code tfc:heating}) and the decoded fields.
 * Immutable; shared across threads.
 */
public record RecipeEntry(String id, String type, SchemaRecord recipe) {
    public RecipeEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(recipe, "recipe");
    }
}
