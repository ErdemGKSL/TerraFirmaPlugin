package dev.tfp.spike.bm01;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Appends observations to bm01-report.txt in the server's working directory (the run dir). */
final class Report {
    private static final Path FILE = Path.of("bm01-report.txt");

    private Report() {}

    static synchronized void line(String text) {
        String line = Instant.now() + " [" + Thread.currentThread().getName() + "] " + text + "\n";
        try {
            Files.writeString(FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[bm01] cannot write report: " + e);
        }
    }
}
