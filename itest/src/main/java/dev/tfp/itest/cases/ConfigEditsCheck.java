/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.install.ConfigEdit;
import dev.tfp.core.install.YamlKeyEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Read-only check that the third-party config edits of M1-INST-02 are present in the files (pure; the case
 * reads the files). Unlike {@code ThirdPartyConfigEditor#check} it never writes the edit record.
 */
public final class ConfigEditsCheck {
    /** @param problems lines that fail the case; {@code notes} lines for the report only */
    public record Result(List<String> problems, List<String> notes, int checked) {
        public Result {
            problems = List.copyOf(problems);
            notes = List.copyOf(notes);
        }
    }

    private ConfigEditsCheck() {
    }

    /**
     * @param edits     the edit table ({@code ThirdPartyEdits.all})
     * @param installed plugin names present on the server; edits for others are ignored
     * @param read      file text by {@link ConfigEdit.Target#path()} (below {@code plugins/}), empty if missing
     */
    public static Result check(List<ConfigEdit> edits, Set<String> installed, Function<String, Optional<String>> read) {
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        int checked = 0;
        for (ConfigEdit e : edits) {
            if (!installed.contains(e.target().plugin())) {
                continue;
            }
            Optional<String> text = read.apply(e.target().path());
            if (text.isEmpty()) {
                (e.mandatory() ? problems : notes).add(e.target().path() + " is missing (" + e.key() + ")");
                continue;
            }
            checked++;
            try {
                YamlKeyEditor ed = YamlKeyEditor.of(text.get());
                if (!e.rule().satisfied(ed, e.key())) {
                    problems.add(e.id() + " is " + e.rule().current(ed, e.key()) + ", expected " + e.rule() + " (" + e.why() + ")");
                }
            } catch (IllegalArgumentException ex) {
                problems.add(e.target().path() + ": cannot read " + e.key() + ": " + ex.getMessage());
            }
        }
        return new Result(problems, notes, checked);
    }
}
