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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

            List<Artifact> hpis = new ArrayList<Artifact>();
            int artifactIdLength=0; // how many chars does it take to print artifactId?
            int versionLength=0;

            ArtifactResolutionResult r = resolver.resolveTransitively(all, project.getArtifact(), remoteRepos, localRepository, artifactMetadataSource);
            for (Artifact o : (Set<Artifact>)r.getArtifacts()) {
                if (wrap(o).isPlugin()) {
                    getLog().debug("Copying "+o.getFile());

                    Artifact hpi = artifactFactory.createArtifact(o.getGroupId(),o.getArtifactId(),o.getVersion(),SCOPE_COMPILE,"hpi");
                    resolver.resolve(hpi,remoteRepos,localRepository);

                    FileUtils.copyFile(hpi.getFile(), new File(outputDirectory,hpi.getArtifactId()+".hpi"));
                    hpis.add(hpi);
                    artifactIdLength = Math.max(artifactIdLength,hpi.getArtifactId().length());
                    versionLength = Math.max(versionLength,hpi.getVersion().length());
                }
            }

            Collections.sort(hpis, new Comparator<Artifact>() {
                public int compare(Artifact o1, Artifact o2) {
                    return map(o1).compareTo(map(o2));
                }

                private String map(Artifact a) {
                    return a.getArtifactId();
                }
            });

            for (Artifact hpi : hpis) {
                getLog().info(String.format("%"+(-artifactIdLength)+"s    %"+(-versionLength)+"s    %s", hpi.getArtifactId(),hpi.getVersion(),hpi.getGroupId()));
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
