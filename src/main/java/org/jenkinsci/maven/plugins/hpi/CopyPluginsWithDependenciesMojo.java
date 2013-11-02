package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.IOUtils;
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
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

            List<Artifact> hpis = new ArrayList<Artifact>();
            int artifactIdLength=0; // how many chars does it take to print artifactId?
            int versionLength=0;


            // resolveTransitively resolves items in the 'all' set individually, so there will be duplicates and multiple versions
            // we need to select the latest
            Map<String,MavenArtifact> selected = new HashMap<String, MavenArtifact>();

            for (Artifact o : (Set<Artifact>)r.getArtifacts()) {
                MavenArtifact a = wrap(o);
                if (a.isPlugin()) {
                    MavenArtifact cur = selected.get(a.getArtifactId());
                    if (cur!=null) {
                        if (cur.getVersionNumber().compareTo(a.getVersionNumber())<0)
                            cur = a;
                    } else
                        cur = a;
                    selected.put(a.getArtifactId(),cur);
                }
            }

            for (MavenArtifact a : selected.values()) {
                getLog().debug("Copying "+a.getFile());

                Artifact hpi = artifactFactory.createArtifact(a.getGroupId(),a.getArtifactId(),a.getVersion(),SCOPE_COMPILE,"hpi");
                resolver.resolve(hpi,remoteRepos,localRepository);

                FileUtils.copyFile(hpi.getFile(), new File(outputDirectory,hpi.getArtifactId()+".hpi"));
                hpis.add(hpi);
                artifactIdLength = Math.max(artifactIdLength,hpi.getArtifactId().length());
                versionLength = Math.max(versionLength,hpi.getVersion().length());
            }

            Collections.sort(hpis, new Comparator<Artifact>() {
                public int compare(Artifact o1, Artifact o2) {
                    return map(o1).compareTo(map(o2));
                }

                private String map(Artifact a) {
                    return a.getArtifactId();
                }
            });

            File list = new File(project.getBuild().getOutputDirectory(),"bundled-plugins.txt");
            PrintWriter w = new PrintWriter(list);
            try {
                for (Artifact hpi : hpis) {
                    getLog().info(String.format("%" + (-artifactIdLength) + "s    %" + (-versionLength) + "s    %s", hpi.getArtifactId(), hpi.getVersion(), hpi.getGroupId()));
                    w.println(hpi.getId());
                }
            } finally {
                IOUtils.closeQuietly(w);
            }

            projectHelper.attachArtifact(project,"txt","bundled-plugins",list);
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
