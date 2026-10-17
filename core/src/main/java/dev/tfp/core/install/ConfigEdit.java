/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import dev.tfp.core.compat.Version;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One targeted edit of a key in another plugin's config ({@code dependency-bootstrap} §6 table). Pure:
 * {@link #plan} maps the file text (and our record of earlier edits) to an outcome and the new text.
 *
 * @param target    which file
 * @param key       dot path of the key
 * @param rule      when the current value is acceptable, and what we set otherwise
 * @param effect    what it takes for the plugin to pick up a change
 * @param mandatory TFC worlds must not be generated while this edit is not in effect
 * @param why       one line for the log
 */
public record ConfigEdit(Target target, String key, Rule rule, Effect effect, boolean mandatory, String why) {
    public ConfigEdit {
        Objects.requireNonNull(target);
        Objects.requireNonNull(key);
        Objects.requireNonNull(rule);
        Objects.requireNonNull(effect);
    }

    /** The files we edit, relative to the server's {@code plugins/} folder. */
    public enum Target {
        CRAFTENGINE_CONFIG("CraftEngine", "CraftEngine/config.yml"),
        CRAFTENGINE_SHULKER_PACK("CraftEngine", "CraftEngine/resources/remove_shulker_head/pack.yml"),
        BETTERHUD_CONFIG("BetterHud", "BetterHud/config.yml"),
        BETTERMODEL_CONFIG("BetterModel", "BetterModel/config.yml");

        private final String plugin;
        private final String path;

        Target(String plugin, String path) {
            this.plugin = plugin;
            this.path = path;
        }

        /** The plugin name (as in its descriptor) that owns the file. */
        public String plugin() {
            return plugin;
        }

        /** {@code /}-separated path below {@code plugins/}. */
        public String path() {
            return path;
        }
    }

    /** What it takes for the owning plugin to apply a change. */
    public enum Effect {
        /** Read once at startup (before our {@code onLoad}): needs a server restart. */
        RESTART,
        /** Read at the next CraftEngine pack build. */
        PACK_REBUILD,
        /** Read at the plugin's next config parse (its first one happens after our {@code onLoad}). */
        RELOAD
    }

    /** Result of one edit. */
    public enum Outcome {
        /** The value is already acceptable. */
        UNCHANGED,
        /** We changed it. */
        APPLIED,
        /** The owner set a value different from both the original and ours after we edited it: left alone. */
        OWNER_KEPT,
        /** The file does not exist (plugin not installed or not started yet). */
        FILE_MISSING,
        /** The file could not be edited safely. */
        FAILED
    }

    /**
     * What we remember about an edit we made, so we don't fight an owner who changed it back on purpose.
     *
     * @param original the value before our first edit (null: absent)
     * @param ours     the value we wrote
     */
    public record Record(String original, String ours) {
    }

    /**
     * @param text   the new file text (equal to the input unless {@code outcome == APPLIED})
     * @param before the value before (null: absent)
     * @param after  the value after (null: absent)
     * @param record the record to keep (null: none)
     */
    public record Plan(Outcome outcome, String text, String before, String after, Record record, String detail) {
    }

    /** Decide and (if needed) apply this edit to {@code text}. Never throws for malformed input: returns FAILED. */
    public Plan plan(String text, Record previous) {
        try {
            YamlKeyEditor ed = YamlKeyEditor.of(text);
            String before = rule.current(ed, key);
            if (rule.satisfied(ed, key)) {
                return new Plan(Outcome.UNCHANGED, text, before, before, previous, "");
            }
            if (previous != null && !Objects.equals(before, previous.original()) && !Objects.equals(before, previous.ours())) {
                return new Plan(Outcome.OWNER_KEPT, text, before, before, previous,
                        "changed by the owner after our edit (" + previous.original() + " -> " + previous.ours() + ")");
            }
            rule.apply(ed, key);
            String after = rule.current(YamlKeyEditor.of(ed.text()), key);
            if (!rule.satisfied(YamlKeyEditor.of(ed.text()), key)) {
                return new Plan(Outcome.FAILED, text, before, before, previous, "edit did not take effect");
            }
            Record rec = new Record(previous != null ? previous.original() : before, after);
            return new Plan(Outcome.APPLIED, ed.text(), before, after, rec, "");
        } catch (IllegalArgumentException e) {
            return new Plan(Outcome.FAILED, text, null, null, previous, e.getMessage());
        }
    }

    /** Stable id for the edit record file: {@code CraftEngine/config.yml#misc.delay-configuration-load}. */
    public String id() {
        return target.path() + "#" + key;
    }

    /** When a value is acceptable and how to fix it. Values are compared as unquoted text. */
    public sealed interface Rule {
        /** The value as text (lists joined with {@code ", "}), or null if the key is absent. */
        String current(YamlKeyEditor ed, String key);

        boolean satisfied(YamlKeyEditor ed, String key);

        void apply(YamlKeyEditor ed, String key);

        /** The scalar must equal {@code value} (case-insensitive); an absent key is set. */
        record Equals(String value) implements Rule {
            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.scalar(key).orElse(null);
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                return ed.scalar(key).map(v -> v.equalsIgnoreCase(value)).orElse(false);
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.setScalar(key, YamlKeyEditor.literal(value));
            }
        }

        /** The scalar must not be {@code forbidden}; an absent key is fine (plugin default). */
        record NotEquals(String forbidden, String replacement) implements Rule {
            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.scalar(key).orElse(null);
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                return ed.scalar(key).map(v -> !v.equalsIgnoreCase(forbidden)).orElse(!ed.has(key));
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.setScalar(key, YamlKeyEditor.literal(replacement));
            }
        }

        /** An integer of at least {@code min}; anything else (absent, not a number, smaller) is set to {@code min}. */
        record AtLeast(int min) implements Rule {
            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.scalar(key).orElse(null);
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                try {
                    return ed.scalar(key).map(v -> Integer.parseInt(v.strip()) >= min).orElse(false);
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.setScalar(key, Integer.toString(min));
            }
        }

        /**
         * A version of at least {@code min}, or one of {@code words} that resolve to an acceptable version
         * (CraftEngine's {@code server}/{@code latest}). An absent key is set.
         */
        record MinVersion(String min, Set<String> words) implements Rule {
            public MinVersion {
                words = Set.copyOf(words);
            }

            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.scalar(key).orElse(null);
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                Optional<String> v = ed.scalar(key);
                if (v.isEmpty()) {
                    return false;
                }
                if (words.contains(v.get().strip().toLowerCase(Locale.ROOT))) {
                    return true;
                }
                return Version.tryParse(v.get().strip()).map(x -> !x.isOlderThan(Version.parse(min))).orElse(false);
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.setScalar(key, YamlKeyEditor.quote(min));
            }
        }

        /** The list must not contain {@code item}; an absent key is fine. */
        record ListWithout(String item) implements Rule {
            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.list(key).map(l -> String.join(", ", l)).orElse(ed.scalar(key).orElse(null));
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                if (!ed.has(key)) {
                    return true;
                }
                Optional<List<String>> l = ed.list(key);
                return l.isPresent() && !l.get().contains(item);
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.removeListItem(key, item);
            }
        }

        /**
         * Every rule must hold. {@link #apply} applies them in order, so one key can need two list changes
         * (e.g. remove the demo HUD and add ours) and still be one edit with one record.
         */
        record All(List<Rule> rules) implements Rule {
            public All {
                rules = List.copyOf(rules);
            }

            @Override
            public String current(YamlKeyEditor ed, String key) {
                return rules.getFirst().current(ed, key);
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                return rules.stream().allMatch(r -> r.satisfied(ed, key));
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                for (Rule r : rules) {
                    r.apply(ed, key);
                }
            }
        }

        /** The list must contain {@code item}; an absent key is fine (the plugin's default contains it). */
        record ListWith(String item) implements Rule {
            @Override
            public String current(YamlKeyEditor ed, String key) {
                return ed.list(key).map(l -> String.join(", ", l)).orElse(ed.scalar(key).orElse(null));
            }

            @Override
            public boolean satisfied(YamlKeyEditor ed, String key) {
                if (!ed.has(key)) {
                    return true;
                }
                return ed.list(key).map(l -> l.contains(item)).orElse(false);
            }

            @Override
            public void apply(YamlKeyEditor ed, String key) {
                ed.addListItem(key, item, YamlKeyEditor.quote(item));
            }
        }
    }
}
