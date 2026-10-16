/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.boot;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import java.util.List;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Paper plugin loader: libraries that are resolved at startup instead of bundled.
 *
 * <p>Today the list is empty: Caffeine and JCTools are bundled into the jar by {@code :dist} and
 * relocated to {@code dev.tfp.lib.*}, so they never clash with another plugin's copy and need no
 * network at startup. A library added to {@link #LIBRARIES} is fetched from the PaperMC Maven proxy
 * (the path proven by M0-JAR-01; Maven Central rate-limits servers).
 */
public final class TfpLoader implements PluginLoader {
    static final String PAPER_REPOSITORY = "https://repo.papermc.io/repository/maven-public/";

    /** Maven coordinates ({@code group:artifact:version}) resolved at startup. */
    static final List<String> LIBRARIES = List.of();

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        resolver(LIBRARIES).ifPresent(classpathBuilder::addLibrary);
    }

    /** A resolver for {@code coordinates}, or empty if there is nothing to resolve. */
    static java.util.Optional<MavenLibraryResolver> resolver(List<String> coordinates) {
        if (coordinates.isEmpty()) {
            return java.util.Optional.empty();
        }
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("papermc", "default", PAPER_REPOSITORY).build());
        for (String c : coordinates) {
            resolver.addDependency(new Dependency(new DefaultArtifact(c), null));
        }
        return java.util.Optional.of(resolver);
    }
}
