/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.visuals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.install.InstallManifest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * When the CraftEngine pack must be rebuilt (M1-INST-03, {@code dependency-bootstrap} §6/§6b: CraftEngine
 * never builds the pack at startup). We record the fingerprint of the content the last <b>successful</b>
 * rebuild delivered; a start whose fingerprint differs (first install, plugin update, profile switch, a
 * rebuild that never finished) asks for one rebuild. Pure.
 *
 * <p>A profile switch changes only client-side visuals (templates and {@code block_state_mappings}); CE
 * block ids and the data saved in worlds stay the same. So the plan never migrates world data.
 */
public final class PackBuild {
    /** File name of the record in our install state folder. */
    public static final String RECORD_FILE = "pack-build.json";

    private PackBuild() {
    }

    /**
     * What our CE pack holds.
     *
     * @param contentHash sha256 over every installed file of our CE pack (path + content hash)
     * @param selection   the visual allocation installed with it
     */
    public record Fingerprint(String contentHash, VisualSelection selection) {
        public Fingerprint {
            Objects.requireNonNull(contentHash);
        }

        /** From the files of our CE pack (path → sha256, e.g. the install manifest). */
        public static Fingerprint of(Map<String, String> files, VisualSelection selection) {
            StringBuilder sb = new StringBuilder();
            new TreeMap<>(files).forEach((k, v) -> sb.append(k).append('\0').append(v).append('\n'));
            return new Fingerprint(InstallManifest.sha256(sb.toString().getBytes(StandardCharsets.UTF_8)), selection);
        }

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("content", contentHash);
            if (selection != null) {
                o.addProperty("family", selection.family());
                o.addProperty("profile", selection.profile().id());
                o.addProperty("nether_end", selection.netherEnd());
            }
            return o.toString();
        }

        /** Empty for an unreadable record (then the next start rebuilds, which is safe). */
        public static Optional<Fingerprint> fromJson(String json) {
            try {
                JsonObject o = JsonParser.parseString(json).getAsJsonObject();
                VisualSelection s = null;
                if (o.has("profile")) {
                    s = new VisualSelection(o.get("family").getAsString(),
                            dev.tfp.core.config.TfpConfig.Profile.byId(o.get("profile").getAsString()).orElseThrow(),
                            o.get("nether_end").getAsBoolean());
                }
                return Optional.of(new Fingerprint(o.get("content").getAsString(), s));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * @param rebuild    trigger one CraftEngine pack rebuild once CraftEngine has loaded
     * @param migrateWorldData always false: no visual change touches saved worlds
     * @param reason     one line for the log (empty when nothing changed)
     */
    public record Plan(boolean rebuild, boolean migrateWorldData, String reason) {
        public static final Plan NOTHING = new Plan(false, false, "");
    }

    /**
     * @param lastBuilt what the last successful rebuild delivered (empty: never, or unknown)
     * @param current   what is installed now
     */
    public static Plan decide(Optional<Fingerprint> lastBuilt, Fingerprint current) {
        if (lastBuilt.isEmpty()) {
            return new Plan(true, false, "first install of our CraftEngine pack");
        }
        Fingerprint last = lastBuilt.get();
        if (!Objects.equals(last.selection(), current.selection())) {
            return new Plan(true, false, "visual profile changed: " + describe(last.selection()) + " -> "
                    + describe(current.selection()) + " (client-side visuals only; worlds are not changed)");
        }
        if (!last.contentHash().equals(current.contentHash())) {
            return new Plan(true, false, "our CraftEngine content changed");
        }
        return Plan.NOTHING;
    }

    private static String describe(VisualSelection s) {
        return s == null ? "none" : s.id();
    }
}
