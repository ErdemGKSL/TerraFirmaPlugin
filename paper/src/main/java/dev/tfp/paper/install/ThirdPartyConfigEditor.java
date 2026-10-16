/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.install;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.tfp.core.install.ConfigEdit;
import dev.tfp.core.install.ConfigEdit.Outcome;
import dev.tfp.core.install.ConfigEdit.Plan;
import dev.tfp.core.install.ConfigEdit.Record;
import dev.tfp.core.install.InstallStatus.EditResult;
import dev.tfp.paper.config.Yamls;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Applies {@link ConfigEdit}s to the files below {@code plugins/} (M1-INST-02). Each file is read once,
 * all its edits are planned on the text in order, the result is checked to still be valid YAML, and it is
 * written atomically only if something changed. What we changed is remembered in {@code edits.json} in our
 * data folder ({@link ConfigEdit.Record}), so an owner who later sets a third value wins.
 *
 * <p>Blocking file I/O: {@code onLoad} or the io executor only.
 */
public final class ThirdPartyConfigEditor {
    public static final String RECORD_FILE = "edits.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private final Path pluginsDir;
    private final Path recordFile;

    /**
     * @param pluginsDir the server's {@code plugins/} folder
     * @param stateDir   our folder for installer state ({@code plugins/TerraFirmaPlugin/install})
     */
    public ThirdPartyConfigEditor(Path pluginsDir, Path stateDir) {
        this.pluginsDir = pluginsDir;
        this.recordFile = stateDir.resolve(RECORD_FILE);
    }

    /** Applies {@code edits} for the plugins in {@code installed} (others are skipped silently). */
    public List<EditResult> apply(List<ConfigEdit> edits, java.util.Set<String> installed) throws IOException {
        return run(edits, installed, true);
    }

    /**
     * Checks without writing ({@code auto-install.config-edits: false}): an edit that would be applied is
     * reported as {@link Outcome#OWNER_KEPT}.
     */
    public List<EditResult> check(List<ConfigEdit> edits, java.util.Set<String> installed) throws IOException {
        return run(edits, installed, false);
    }

    private List<EditResult> run(List<ConfigEdit> edits, java.util.Set<String> installed, boolean write) throws IOException {
        Map<String, Record> records = readRecords();
        Map<String, Record> nextRecords = new TreeMap<>(records);
        Map<ConfigEdit.Target, List<ConfigEdit>> byFile = new LinkedHashMap<>();
        for (ConfigEdit e : edits) {
            if (installed.contains(e.target().plugin())) {
                byFile.computeIfAbsent(e.target(), t -> new ArrayList<>()).add(e);
            }
        }
        List<EditResult> results = new ArrayList<>();
        for (Map.Entry<ConfigEdit.Target, List<ConfigEdit>> f : byFile.entrySet()) {
            Path file = pluginsDir.resolve(f.getKey().path());
            String original;
            try {
                original = Files.readString(file, StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                for (ConfigEdit edit : f.getValue()) {
                    results.add(new EditResult(edit, Outcome.FILE_MISSING, null, null, file.toString()));
                }
                continue;
            }
            String text = original;
            List<Plan> plans = new ArrayList<>();
            for (ConfigEdit edit : f.getValue()) {
                Plan p = edit.plan(text, records.get(edit.id()));
                plans.add(p);
                text = p.text();
            }
            boolean writeOk = true;
            String failure = "";
            if (!write) {
                for (int i = 0; i < plans.size(); i++) {
                    Plan p = plans.get(i);
                    results.add(new EditResult(f.getValue().get(i),
                            p.outcome() == Outcome.APPLIED ? Outcome.OWNER_KEPT : p.outcome(), p.before(), p.before(),
                            p.outcome() == Outcome.APPLIED ? "auto-install.config-edits is off" : p.detail()));
                }
                continue;
            }
            if (!text.equals(original)) {
                try {
                    Yamls.load(text); // never leave another plugin with a file it cannot parse
                    AtomicFiles.write(file, text.getBytes(StandardCharsets.UTF_8));
                } catch (RuntimeException e) {
                    writeOk = false;
                    failure = "edited file would not parse: " + e.getMessage();
                }
            }
            for (int i = 0; i < plans.size(); i++) {
                ConfigEdit edit = f.getValue().get(i);
                Plan p = plans.get(i);
                if (p.outcome() == Outcome.APPLIED && !writeOk) {
                    results.add(new EditResult(edit, Outcome.FAILED, p.before(), p.before(), failure));
                    continue;
                }
                if (p.record() != null) {
                    nextRecords.put(edit.id(), p.record());
                }
                results.add(new EditResult(edit, p.outcome(), p.before(), p.after(), p.detail()));
            }
        }
        if (!nextRecords.equals(records)) {
            writeRecords(nextRecords);
        }
        return results;
    }

    Map<String, Record> readRecords() throws IOException {
        Map<String, Record> out = new TreeMap<>();
        String json;
        try {
            json = Files.readString(recordFile, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return out;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                out.put(e.getKey(), new Record(str(o, "original"), str(o, "ours")));
            }
        } catch (JsonParseException | IllegalStateException e) {
            return new TreeMap<>(); // unreadable: behave as on a first install
        }
        return out;
    }

    private void writeRecords(Map<String, Record> records) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Record> e : records.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("original", e.getValue().original());
            o.addProperty("ours", e.getValue().ours());
            root.add(e.getKey(), o);
        }
        AtomicFiles.write(recordFile, (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    public Path recordFile() {
        return Objects.requireNonNull(recordFile);
    }
}
