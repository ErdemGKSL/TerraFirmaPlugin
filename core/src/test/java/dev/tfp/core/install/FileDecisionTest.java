/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.install.InstallManifest.Entry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M1-INST-01: the per-file install rule (untouched → overwrite, edited → .tfp-new, deleted → respect). */
class FileDecisionTest {
    static final String OLD = "a".repeat(64);
    static final String NEW = "b".repeat(64);
    static final String OWNER = "c".repeat(64);

    @Test
    void newFileIsWritten() {
        assertThat(FileDecision.decide(NEW, null, null, false)).isEqualTo(FileDecision.WRITE);
    }

    @Test
    void untouchedFileIsUpdated() {
        FileDecision d = FileDecision.decide(NEW, OLD, Entry.written(OLD), false);
        assertThat(d).isEqualTo(FileDecision.WRITE);
        assertThat(d.entryAfter(NEW, Entry.written(OLD))).isEqualTo(Entry.written(NEW));
    }

    @Test
    void sameContentIsLeftAlone() {
        assertThat(FileDecision.decide(NEW, NEW, Entry.written(NEW), false)).isEqualTo(FileDecision.UNCHANGED);
        // A crash after writing files but before the manifest: the file already has the new bytes.
        assertThat(FileDecision.decide(NEW, NEW, Entry.written(OLD), false)).isEqualTo(FileDecision.UNCHANGED);
    }

    @Test
    void editedFileGetsSideFileOnlyWhenWeShipSomethingNew() {
        assertThat(FileDecision.decide(NEW, OWNER, Entry.written(OLD), false)).isEqualTo(FileDecision.WRITE_SIDE_FILE);
        assertThat(FileDecision.WRITE_SIDE_FILE.entryAfter(NEW, Entry.written(OLD))).isEqualTo(Entry.written(NEW));
        // Next start, same shipped version: nothing new to offer, keep theirs.
        assertThat(FileDecision.decide(NEW, OWNER, Entry.written(NEW), false)).isEqualTo(FileDecision.KEEP_EDITED);
        assertThat(FileDecision.KEEP_EDITED.entryAfter(NEW, Entry.written(NEW))).isEqualTo(Entry.written(NEW));
    }

    @Test
    void foreignFileAtOurPathIsTreatedAsEdited() {
        assertThat(FileDecision.decide(NEW, OWNER, null, false)).isEqualTo(FileDecision.WRITE_SIDE_FILE);
    }

    @Test
    void deletedFileIsRespectedUnlessRestoreDeleted() {
        assertThat(FileDecision.decide(NEW, null, Entry.written(OLD), false)).isEqualTo(FileDecision.RESPECT_DELETED);
        assertThat(FileDecision.RESPECT_DELETED.entryAfter(NEW, Entry.written(OLD))).isEqualTo(Entry.tombstone(OLD));
        assertThat(FileDecision.decide(NEW, null, Entry.tombstone(OLD), false)).isEqualTo(FileDecision.RESPECT_DELETED);
        assertThat(FileDecision.decide(NEW, null, Entry.tombstone(OLD), true)).isEqualTo(FileDecision.WRITE);
        assertThat(FileDecision.decide(NEW, null, Entry.written(OLD), true)).isEqualTo(FileDecision.WRITE);
    }

    @Test
    void ownerRecreatingADeletedFileIsTheirs() {
        assertThat(FileDecision.decide(NEW, OWNER, Entry.tombstone(OLD), false)).isEqualTo(FileDecision.WRITE_SIDE_FILE);
    }

    @Test
    void manifestRoundTrip() {
        InstallManifest m = InstallManifest.EMPTY.toBuilder("1.0")
                .put("pack.yml", Entry.written(OLD))
                .put("configuration/a.yml", Entry.tombstone(NEW))
                .build();
        InstallManifest back = InstallManifest.fromJson(m.toJson());
        assertThat(back).isEqualTo(m);
        assertThat(back.files()).isEqualTo(Map.of("configuration/a.yml", Entry.tombstone(NEW),
                "pack.yml", Entry.written(OLD)));
        assertThat(m.toJson()).doesNotContain("\"deleted\": false");
    }

    @Test
    void badManifestIsRejected() {
        assertThatThrownBy(() -> InstallManifest.fromJson("{\"format\": 99}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallManifest.fromJson("not json {")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sha256OfKnownInput() {
        assertThat(InstallManifest.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
