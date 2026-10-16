package dev.tfp.spike.jar01;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/** Paper plugin loader: adds Caffeine from the PaperMC Maven proxy to the plugin's classpath. */
public final class Jar01Loader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("papermc", "default",
                "https://repo.papermc.io/repository/maven-public/").build());
        resolver.addDependency(new Dependency(new DefaultArtifact("com.github.ben-manes.caffeine:caffeine:3.2.2"), null));
        classpathBuilder.addLibrary(resolver);
    }
}
