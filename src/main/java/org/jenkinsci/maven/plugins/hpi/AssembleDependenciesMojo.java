package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Used to assemble transitive dependencies of plugins into one location.
 *
 * <p>
 * Unlike other similar mojos in this plugin, this one traverses dependencies
 * through its graph.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "assemble-dependencies", requiresProject = true, threadSafe = true)
public class AssembleDependenciesMojo extends AbstractDependencyGraphTraversingMojo {
    /**
     * Where to copy plugins into.
     */
    @Parameter(defaultValue = "${project.build.directory}/plugins/")
    private File outputDirectory;

    /**
     * Do we include optional dependencies?
     */
    @Parameter
    private boolean includesOptional;

    /**
     * Copy files as .jpi instead of .hpi
     */
    @Parameter
    private boolean useJpiExtension;

    /**
     * Scopes to include.
     */
    // skip test scope as that's not meant to be bundled
    // "provided" indicates the plugin assumes that scope is available, so skip that as well
    // "system" is not used for plugins
    @Parameter
    private String scopes = "compile,runtime";

    private List<String> parsedScopes;

    private final Map<String, MavenArtifact> hpis = new HashMap<>();

    @Override
    protected boolean accept(DependencyNode g, boolean isRoot) {
        Artifact artifact = g.getArtifact();
        Dependency dep = g.getDependency();

        if (dep == null) {
            return false;
        }

        String scope = dep.getScope();
        if (!parsedScopes.contains(scope)) {
            return false;
        }

        if (!includesOptional && dep.isOptional()) {
            return false;
        }

        MavenArtifact a = wrap(RepositoryUtils.toArtifact(artifact));
        if (!a.isPlugin(getLog())) {
            return isRoot;
        }

        MavenArtifact existing = hpis.get(a.getArtifactId());
        if (existing == null || a.isNewerThan(existing)) {
            hpis.put(a.getArtifactId(), a);
        }

        return true;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            hpis.clear();

            parsedScopes = new ArrayList<>();
            parsedScopes.add(null); // this is needed to traverse the root node
            for (String s : scopes.split(",")) {
                parsedScopes.add(s.trim());
            }

            traverseProject();
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to list up dependencies", e);
        }

        for (MavenArtifact a : hpis.values()) {
            try {
                MavenArtifact hpi = a.getHpi();
                getLog().debug("Copying " + hpi.getFile());

                FileUtils.copyFile(
                        hpi.getFile(), new File(outputDirectory, hpi.getArtifactId() + "." + getExtension()));
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to copy dependency: " + a, e);
            }
        }
    }

    private String getExtension() {
        return useJpiExtension ? "jpi" : "hpi";
    }
}
