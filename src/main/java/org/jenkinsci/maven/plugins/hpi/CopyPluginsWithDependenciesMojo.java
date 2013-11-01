package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.dependency.fromConfiguration.ArtifactItem;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.maven.artifact.Artifact.*;

/**
 * Resolve plugin dependencies transitively and copy them all into a specific directory.
 *
 * @author Kohsuke Kawaguchi
 * @goal copy-plugins-with-dependencies
 */
public class CopyPluginsWithDependenciesMojo extends AbstractJenkinsMojo {
    /**
     * Collection of plugins to copy from
     *
     * @parameter
     */
    private List<ArtifactItem> artifactItems;

    /**
     * Where to copy plugins into.
     *
     * @parameter
     */
    private File outputDirectory;

    /**
     * @component
     */
    protected ArtifactResolver resolver;

    /**
     * @component
     */
    protected ArtifactCollector artifactCollector;


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Set<Artifact> all = new HashSet<Artifact>();

            for (ArtifactItem ai : artifactItems) {
                all.add(toArtifact(ai));
            }

            ArtifactResolutionResult r = resolver.resolveTransitively(all, project.getArtifact(), remoteRepos, localRepository, artifactMetadataSource);
            for (Artifact o : (Set<Artifact>)r.getArtifacts()) {
                if (wrap(o).isPlugin()) {
                    getLog().debug("Copying "+o.getFile());

                    Artifact hpi = artifactFactory.createArtifact(o.getGroupId(),o.getArtifactId(),o.getVersion(),SCOPE_COMPILE,"hpi");
                    resolver.resolve(hpi,remoteRepos,localRepository);

                    FileUtils.copyFile(hpi.getFile(), new File(outputDirectory,hpi.getArtifactId()+".hpi"));
                }
            }

        } catch (InvalidVersionSpecificationException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies",e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies",e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies",e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies",e);
        }
    }

    private Artifact toArtifact(ArtifactItem ai) throws InvalidVersionSpecificationException {
        VersionRange vr = VersionRange.createFromVersionSpec(ai.getVersion());

        return artifactFactory.createDependencyArtifact(ai.getGroupId(), ai.getArtifactId(), vr,
                "hpi", ai.getClassifier(), SCOPE_COMPILE);
    }
}
