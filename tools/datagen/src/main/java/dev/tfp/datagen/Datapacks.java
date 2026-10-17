/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import com.google.gson.JsonObject;

/** The {@code pack.mcmeta} of every datapack datagen writes (the server's supported range, pack format 61 and up). */
public final class Datapacks {
    private Datapacks() {
    }

    public static String packMcmeta(String description) {
        JsonObject pack = new JsonObject();
        JsonObject p = new JsonObject();
        p.addProperty("description", description);
        p.addProperty("pack_format", 61);
        JsonObject sf = new JsonObject();
        sf.addProperty("min_inclusive", 61);
        sf.addProperty("max_inclusive", 1000);
        p.add("supported_formats", sf);
        p.addProperty("min_format", 61);
        p.addProperty("max_format", 1000);
        pack.add("pack", p);
        return Json.write(pack);
    }
}
