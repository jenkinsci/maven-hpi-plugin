package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.ProjectBuilder;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.graph.DependencyNode;

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

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ProjectBuilder projectBuilder;

    private List<String> parsedScopes;

    private final Map<String, MavenArtifact> hpis = new HashMap<>();

    @Override
    protected boolean accept(DependencyNode g) {
        org.eclipse.aether.artifact.Artifact a = g.getArtifact();

        // Convert Aether artifact to Maven artifact using the artifactFactory
        Artifact mavenArtifact = artifactFactory.createArtifactWithClassifier(
                a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getExtension(), null);
        mavenArtifact.setScope(getNodeScope(g));

        // Create a MavenArtifact wrapper
        MavenArtifact ma =
                new MavenArtifact(mavenArtifact, repositorySystem, artifactFactory, projectBuilder, session, project);

        String scope = getNodeScope(g);
        if (!parsedScopes.contains(scope)) {
            return false;
        }

        if (!includesOptional && isOptional(g)) {
            return false; // cut off optional dependencies
        }

        if (!ma.isPlugin(getLog())) {
            // only traverse chains of direct plugin dependencies, unless it's from the root
            return g.getChildren().isEmpty(); // If it has no children, it might be the root
        }

        MavenArtifact v = hpis.get(ma.getArtifactId());
        if (v == null || ma.isNewerThan(v)) {
            hpis.put(ma.getArtifactId(), ma);
        }

        return true;
    }

    /**
     * Gets the scope of a DependencyNode
     */
    private String getNodeScope(DependencyNode node) {
        if (node.getDependency() != null) {
            return node.getDependency().getScope();
        }
        return null;
    }

    /**
     * Checks if a DependencyNode is optional
     */
    private boolean isOptional(DependencyNode node) {
        if (node.getDependency() != null) {
            return node.getDependency().isOptional();
        }
        return false;
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
        } catch (Exception e) {
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
