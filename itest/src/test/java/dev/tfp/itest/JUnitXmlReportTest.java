/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** M1-TEST-01: the JUnit XML report parses, counts right and escapes log text. */
class JUnitXmlReportTest {
    static final List<CaseResult> RESULTS = List.of(
            CaseResult.passed("ready", "ready, 41.2 s after JVM start", Duration.ofMillis(1500)),
            CaseResult.passed("quiet", "", Duration.ZERO),
            CaseResult.failed("log <threads> & \"quotes\"", "1 violation", "[13:00:00] \u001b[31mnot region owner\u001b[0m",
                    Duration.ZERO),
            CaseResult.error("player data", "java.io.IOException: disk", "at x.y(Z.java:1)", Duration.ofMillis(3)),
            CaseResult.skipped("bovine", "adapter not verified"));

    static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void reportParsesWithCountsPropertiesAndOneElementPerOutcome() throws Exception {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("server", "Paper 1.21.11 & co");
        String xml = JUnitXmlReport.toXml("tfp.selftest", RESULTS, props, Instant.parse("2026-10-16T12:00:00.123Z"));
        Element suite = parse(xml).getDocumentElement();

        assertThat(suite.getTagName()).isEqualTo("testsuite");
        assertThat(suite.getAttribute("tests")).isEqualTo("5");
        assertThat(suite.getAttribute("failures")).isEqualTo("1");
        assertThat(suite.getAttribute("errors")).isEqualTo("1");
        assertThat(suite.getAttribute("skipped")).isEqualTo("1");
        assertThat(suite.getAttribute("timestamp")).isEqualTo("2026-10-16T12:00:00");
        assertThat(((Element) suite.getElementsByTagName("property").item(0)).getAttribute("value"))
                .isEqualTo("Paper 1.21.11 & co");

        NodeList cases = suite.getElementsByTagName("testcase");
        assertThat(cases.getLength()).isEqualTo(5);
        assertThat(((Element) cases.item(0)).getAttribute("time")).isEqualTo("1.500");
        assertThat(((Element) cases.item(0)).getElementsByTagName("system-out").item(0).getTextContent())
                .contains("41.2 s");
        assertThat(((Element) cases.item(1)).getChildNodes().getLength()).isZero();
        Element failed = (Element) cases.item(2);
        assertThat(failed.getAttribute("name")).isEqualTo("log <threads> & \"quotes\"");
        assertThat(failed.getElementsByTagName("failure").item(0).getTextContent())
                .contains("not region owner").doesNotContain("\u001b");
        assertThat(((Element) cases.item(3)).getElementsByTagName("error").item(0).getTextContent()).contains("Z.java:1");
        assertThat(((Element) ((Element) cases.item(4)).getElementsByTagName("skipped").item(0)).getAttribute("message"))
                .isEqualTo("adapter not verified");
    }

    /** The check in itest/build.gradle.kts (runSelfTest doLast) sums failures= and errors= attributes. */
    @Test
    void gradleGateSeesFailuresOnlyWhenThereAreSome() {
        Pattern gate = Pattern.compile("(failures|errors)=\"(\\d+)\"");
        String bad = JUnitXmlReport.toXml("s", RESULTS, Map.of(), Instant.EPOCH);
        String good = JUnitXmlReport.toXml("s", List.of(RESULTS.get(0), RESULTS.get(4),
                CaseResult.passed("message with failures=\"7\" in it", "errors=\"9\"", Duration.ZERO)), Map.of(), Instant.EPOCH);
        assertThat(sum(gate, bad)).isEqualTo(2);
        assertThat(sum(gate, good)).isZero();
    }

    static int sum(Pattern p, String xml) {
        return p.matcher(xml).results().mapToInt(m -> Integer.parseInt(m.group(2))).sum();
    }

    @Test
    void writeReplacesTheReportAtomically(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("run/selftest-report.xml");
        JUnitXmlReport.write(file, "old");
        JUnitXmlReport.write(file, JUnitXmlReport.toXml("s", RESULTS, Map.of(), Instant.EPOCH));
        assertThat(parse(Files.readString(file)).getDocumentElement().getAttribute("tests")).isEqualTo("5");
        try (var files = Files.list(file.getParent())) {
            assertThat(files).containsExactly(file);
        }
    }
}
