/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * X-THR-01 threading and platform-isolation gate. {@code core/} is platform-free and thread-safe
 * (CLAUDE.md, ARCHITECTURE.md section 2): no Bukkit, Paper, Folia, NeoForge, Minecraft, CraftEngine
 * or BetterModel types, and no thread or lock primitives that bypass the snapshot / async / region
 * model. Every violation found by the survey is listed in an exception map below with its reason, so
 * a new violation fails the build until it is either fixed or documented here in review.
 */
class PlatformIsolationTest {
    /** Package prefixes that must never be imported from {@code core/src/main} or {@code core/src/test}. */
    static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "org.bukkit.",
            "io.papermc.",
            "org.spigotmc.",
            "net.minecraft.",
            "com.mojang.",
            "net.neoforged.",
            "net.momirealms.craftengine.",
            "kr.toxicity.",
            "com.nexomc.",
            "dev.lone.",
            "net.kyori.",
            "com.comphenix.");

    /**
     * Known platform imports in core: {@code relative path -> import line}. Empty: the survey found none.
     * Add an entry here only with a reason in review; the default answer is to move the code out of core.
     */
    static final Map<String, String> PLATFORM_IMPORT_EXCEPTIONS = Map.of();

    /** Thread, lock and process primitives that core main code must not use. */
    static final List<Pattern> FORBIDDEN_USAGE_PATTERNS = List.of(
            Pattern.compile("\\bThread\\s*\\."),
            Pattern.compile("\\bsynchronized\\b"),
            Pattern.compile("\\bBukkit\\s*\\."),
            Pattern.compile("\\bRuntime\\s*\\.\\s*getRuntime\\b"),
            Pattern.compile("\\bSystem\\s*\\.\\s*exit\\b"));

    /**
     * Known usages in {@code core/src/main}: {@code "relative path :: token" -> occurrences}. Each entry
     * is a deliberate exception with its reason:
     * <ul>
     *   <li>{@code GlobalClockHolder.publish} is {@code synchronized} on purpose. It is called only from
     *       the global scheduler, and the lock keeps {@code sequence} strictly increasing and the
     *       snapshot fields consistent for the lock-free readers. Its callers are single-threaded, so
     *       the lock is never contended. Replacing it with a lock-free compare-and-set would be a
     *       design change, recorded in ARCHITECTURE.md first.</li>
     * </ul>
     */
    static final Map<String, Integer> USAGE_EXCEPTIONS = Map.of(
            "src/main/java/dev/tfp/core/clock/GlobalClockHolder.java :: synchronized", 1);

    private static final Pattern IMPORT_LINE = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void coreHasNoPlatformImports() throws IOException {
        Path root = coreRoot();
        Map<String, String> violations = new TreeMap<>();
        for (Path file : javaFiles(root.resolve("src/main/java"), root.resolve("src/test/java"))) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = IMPORT_LINE.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                for (String prefix : FORBIDDEN_IMPORT_PREFIXES) {
                    if (name.startsWith(prefix)) {
                        violations.put(relative(root, file), "import " + name);
                    }
                }
            }
        }
        assertThat(violations)
                .as("platform imports in core/ outside the documented exceptions (move the code out of core or document it)")
                .isEqualTo(PLATFORM_IMPORT_EXCEPTIONS);
    }

    @Test
    void coreMainHasNoThreadOrProcessPrimitives() throws IOException {
        Path root = coreRoot();
        Map<String, Integer> usages = new TreeMap<>();
        for (Path file : javaFiles(root.resolve("src/main/java"))) {
            String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            for (Pattern pattern : FORBIDDEN_USAGE_PATTERNS) {
                Matcher m = pattern.matcher(code);
                int count = 0;
                String token = null;
                while (m.find()) {
                    count++;
                    token = m.group().replaceAll("\\s", "");
                }
                if (count > 0) {
                    String key = relative(root, file) + " :: " + (token.equals("synchronized") ? "synchronized" : token);
                    usages.merge(key, count, Integer::sum);
                }
            }
        }
        assertThat(usages)
                .as("thread/lock/process primitives in core/src/main outside the documented exceptions")
                .isEqualTo(new TreeMap<>(USAGE_EXCEPTIONS));
    }

    /** Gradle runs the test with the subproject as the working directory; the repo root is also accepted. */
    static Path coreRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("src/main/java"))) {
            return cwd;
        }
        Path core = cwd.resolve("core");
        if (Files.isDirectory(core.resolve("src/main/java"))) {
            return core;
        }
        throw new IllegalStateException("cannot find core/src/main/java from " + cwd);
    }

    static List<Path> javaFiles(Path... dirs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java") && Files.isRegularFile(p)).forEach(files::add);
            }
        }
        files.sort(null);
        return files;
    }

    static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** Removes block and line comments so Javadoc prose does not count as code usage. */
    static String stripComments(String source) {
        String noBlocks = BLOCK_COMMENT.matcher(source).replaceAll(" ");
        return LINE_COMMENT.matcher(noBlocks).replaceAll("");
    }
}
