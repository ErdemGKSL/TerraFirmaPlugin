/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.persist;

/** Stored data cannot be read: wrong type, missing key, corrupt bytes, or an unknown version. */
public class PersistException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PersistException(String message) {
        super(message);
    }

    public PersistException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The data was written by a newer plugin version; it must be kept as-is, never overwritten with defaults. */
    public static final class NewerVersion extends PersistException {
        private static final long serialVersionUID = 1L;
        private final int found;
        private final int supported;

        public NewerVersion(String codec, int found, int supported) {
            super(codec + ": data version " + found + " is newer than this plugin supports (" + supported
                    + "); downgrading is not supported");
            this.found = found;
            this.supported = supported;
        }

        public int found() {
            return found;
        }

        public int supported() {
            return supported;
        }
    }
}
