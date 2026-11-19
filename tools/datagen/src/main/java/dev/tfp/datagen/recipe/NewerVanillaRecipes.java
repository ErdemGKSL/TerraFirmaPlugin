/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.recipe;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PAR-PROC-43: vanilla tool, weapon and armour recipes newer than TFC's 1.21.1 are disabled like their 1.21.1 siblings.
 * The ids come from the pinned list {@code newer_vanilla_recipes.txt}; each becomes a {@code data/minecraft/recipe/<id>.json}
 * holding TFC's own false condition, which the runtime loader records in {@code RegistrySnapshot.disabledRecipes}.
 * The CraftListener already blocks disabled ids. An id TFC itself overrides or disables is left to TFC's file.
 */
public final class NewerVanillaRecipes {
    static final String RESOURCE = "newer_vanilla_recipes.txt";
    /** The recipe folder of the vanilla namespace in the output. */
    public static final String DIR = "data/minecraft/recipe/";
    /** TFC's disabling file body, byte for byte (see TFC's {@code data/minecraft/recipe/iron_pickaxe.json}). */
    static final String DISABLED = "{\n  \"neoforge:conditions\": [\n    {\n      \"type\": \"neoforge:false\"\n    }\n  ]\n}";

    private NewerVanillaRecipes() {
    }

    /** The pinned ids (bare names, in file order, all sections). */
    public static List<String> ids() {
        try (InputStream in = NewerVanillaRecipes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new DatagenException("missing resource " + RESOURCE);
            }
            List<String> ids = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String t = line.strip();
                if (!t.isEmpty() && !t.startsWith("#") && !t.startsWith("[")) {
                    ids.add(t);
                }
            }
            return List.copyOf(ids);
        } catch (IOException e) {
            throw new DatagenException("cannot read " + RESOURCE + ": " + e);
        }
    }

    /**
     * Writes the disabling files into {@code out}, skipping ids whose file already exists (TFC overrides it).
     *
     * @return how many files were written
     */
    public static int run(OutputTree out) {
        Set<String> present = out.files().keySet();
        int n = 0;
        for (String id : ids()) {
            String path = DIR + id + ".json";
            if (!present.contains(path)) {
                out.put(path, DISABLED);
                n++;
            }
        }
        return n;
    }
}
