/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-DATA-05: tr_tr covers every en_us key and adds none of its own. */
class MessagesCoverageTest {
    @Test
    void turkishCoversEveryEnglishKey() {
        Messages messages = Messages.fromJar();
        Set<String> en = messages.bundleOf("en_us").keySet();
        Set<String> tr = messages.bundleOf("tr_tr").keySet();
        Set<String> missing = new HashSet<>(en);
        missing.removeAll(tr);
        Set<String> extra = new HashSet<>(tr);
        extra.removeAll(en);
        assertThat(missing).as("en_us keys missing from tr_tr").isEmpty();
        assertThat(extra).as("tr_tr keys unknown to en_us").isEmpty();
    }
}
