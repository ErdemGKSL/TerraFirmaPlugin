/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import dev.tfp.core.install.InstallManifest.Entry;

/**
 * What the installer does with one shipped file ({@code dependency-bootstrap} §5). Pure: decided from
 * three hashes, so it is unit-tested without a file system.
 */
public enum FileDecision {
    /** Write the shipped bytes (new file, untouched old version, or {@code restore-deleted}). Record the shipped hash. */
    WRITE,
    /** The file already has the shipped bytes. Record the shipped hash, write nothing. */
    UNCHANGED,
    /** The owner edited it and we ship a new version: keep theirs, write ours as {@code <name>.tfp-new}. */
    WRITE_SIDE_FILE,
    /** The owner edited it and our version did not change since: leave it, nothing to offer. */
    KEEP_EDITED,
    /** The owner deleted it (now or earlier): do not recreate; record a tombstone. */
    RESPECT_DELETED;

    /** Suffix of the side file holding our version next to an owner-edited file. */
    public static final String SIDE_SUFFIX = ".tfp-new";

    /**
     * @param shipped        sha256 of the file in our jar
     * @param current        sha256 of the file on disk, or null if absent
     * @param recorded       the manifest entry from our last install, or null if we never wrote this path
     * @param restoreDeleted {@code auto-install.restore-deleted}
     */
    public static FileDecision decide(String shipped, String current, Entry recorded, boolean restoreDeleted) {
        if (current == null) {
            if (recorded == null) {
                return WRITE;
            }
            return restoreDeleted ? WRITE : RESPECT_DELETED;
        }
        if (current.equals(shipped)) {
            return UNCHANGED;
        }
        if (recorded == null || recorded.deleted()) {
            // A file we never wrote (or the owner recreated after deleting ours): theirs.
            return WRITE_SIDE_FILE;
        }
        if (current.equals(recorded.sha256())) {
            return WRITE; // untouched by the owner: update
        }
        return shipped.equals(recorded.sha256()) ? KEEP_EDITED : WRITE_SIDE_FILE;
    }

    /** The manifest entry to store after acting on this decision. */
    public Entry entryAfter(String shipped, Entry recorded) {
        return switch (this) {
            case WRITE, UNCHANGED, WRITE_SIDE_FILE -> Entry.written(shipped);
            case KEEP_EDITED -> recorded;
            case RESPECT_DELETED -> Entry.tombstone(recorded != null ? recorded.sha256() : shipped);
        };
    }
}
