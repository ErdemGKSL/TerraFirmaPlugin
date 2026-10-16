/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** M1-SVC-04: server-side messages per locale, fallbacks, and completeness of every shipped bundle. */
class MessagesTest {
    final Messages messages = Messages.fromJar();

    @Test
    void translatedMessagePerShippedLocale() {
        assertThat(messages.locales()).contains("en_us", "tr_tr");
        assertThat(messages.get("en_us", "command.player_not_found", "Alex")).isEqualTo("No online player named Alex.");
        assertThat(messages.get("tr_tr", "command.player_not_found", "Alex")).isEqualTo("Alex adında çevrimiçi bir oyuncu yok.");
        // Every shipped locale returns its own text for a key, not the English fallback.
        for (String locale : messages.locales()) {
            String text = messages.get(locale, "reload.config.done");
            assertThat(text).as(locale).isEqualTo(messages.bundleOf(locale).get("reload.config.done"));
            if (!locale.equals(Messages.FALLBACK)) {
                assertThat(text).as(locale).isNotEqualTo(messages.get(Messages.FALLBACK, "reload.config.done"));
            }
        }
    }

    @Test
    void localeCodesAndFallbacks() {
        assertThat(Messages.code(Locale.forLanguageTag("tr-TR"))).isEqualTo("tr_tr");
        assertThat(messages.get(Locale.forLanguageTag("tr-TR"), "reload.data.started")).isEqualTo("Veriler yeniden yükleniyor...");
        // same language, other country → the shipped Turkish bundle
        assertThat(messages.get("tr_cy", "reload.data.started")).isEqualTo("Veriler yeniden yükleniyor...");
        // unknown locale → English; unknown key → the key
        assertThat(messages.get("xx_yy", "reload.data.started")).isEqualTo("Reloading data...");
        assertThat(messages.get("en_us", "no.such.key")).isEqualTo("no.such.key");
        // hostile locale strings never reach the class loader
        assertThat(messages.get("../../x", "reload.data.started")).isEqualTo("Reloading data...");
    }

    @Test
    void argumentsAreLiteralAndApostrophesSurvive() {
        assertThat(Messages.format("{1} ve {0}'in", "a", "b")).isEqualTo("b ve a'in");
        assertThat(Messages.format("{0} {5} {x}", "a")).isEqualTo("a {5} {x}");
        // Turkish word order differs from English: argument indexes are reordered in the bundle.
        assertThat(messages.get("tr_tr", "debug.chunk.header", 1, 2, "world")).isEqualTo("world dünyasındaki 1, 2 parçası:");
    }

    static final Pattern ARG = Pattern.compile("\\{(\\d+)}");

    static Set<String> args(String s) {
        Set<String> out = new TreeSet<>();
        Matcher m = ARG.matcher(s);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void everyBundleHasEveryKeyWithTheSameArguments() {
        Map<String, String> en = messages.bundleOf(Messages.FALLBACK);
        assertThat(en).isNotEmpty();
        for (String locale : messages.locales()) {
            Map<String, String> b = messages.bundleOf(locale);
            assertThat(b.keySet()).as(locale + " keys").isEqualTo(en.keySet());
            for (Map.Entry<String, String> e : en.entrySet()) {
                assertThat(args(b.get(e.getKey()))).as(locale + " " + e.getKey()).isEqualTo(args(e.getValue()));
            }
        }
    }
}
