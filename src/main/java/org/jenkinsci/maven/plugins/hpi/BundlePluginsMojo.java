package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.ScopeArtifactFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;

/**
 * @author Stephen Connolly
 */
@Mojo(name = "bundle-plugins",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresProject = true,
        threadSafe = true
)
public class BundlePluginsMojo extends AbstractJenkinsMojo {
    /**
     * Where to copy plugins into.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}/WEB-INF/plugins/")
    private File outputDirectory;

    @Component
    protected ArtifactResolver resolver;

    @Component
    protected ArtifactCollector artifactCollector;


    public void execute() throws MojoExecutionException, MojoFailureException {
        TypeFilter typeFilter = new TypeFilter("hpi,jpi", null);
        // the HPI packaging type is brain-dead... since nobody lists plugin dependencies with <type>hpi</type>
        // we loose all transitive information, so we need to throw away all the good stuff maven would give us
        // further we need to set <scope>provided</scope> in order to keep this off the classpath as then
        // the war plugin would suck them in anyways
        Set<Artifact> artifacts = typeFilter.filter(project.getDependencyArtifacts());
        ScopeArtifactFilter scopeFilter = new ScopeArtifactFilter(DefaultArtifact.SCOPE_PROVIDED);
        artifacts = typeFilter.filter(artifacts);
        try {
            // This would be unnecessary and trivial if the hpi packaging was defined correctly
            ArtifactResolutionResult r = resolver.resolveTransitively(artifacts, project.getArtifact(), remoteRepos, localRepository, artifactMetadataSource);

            List<Artifact> hpis = new ArrayList<Artifact>();
            int artifactIdLength = 0; // how many chars does it take to print artifactId?
            int versionLength = 0;


            // resolveTransitively resolves items in the 'all' set individually,
            // so there will be duplicates and multiple versions
            // we need to select the latest
            Map<String, MavenArtifact> selected = new HashMap<String, MavenArtifact>();

            for (Artifact o : (Set<Artifact>)r.getArtifacts()) {
                MavenArtifact a = wrap(o);
                if (a.isPlugin()) {
                    MavenArtifact cur = selected.get(a.getArtifactId());
                    if (cur != null) {
                        if (cur.getVersionNumber().compareTo(a.getVersionNumber()) < 0) {
                            cur = a;
                        }
                    } else {
                        cur = a;
                    }
                    selected.put(a.getArtifactId(), cur);
                }
            }

            outputDirectory.mkdirs();

            for (MavenArtifact a : selected.values()) {
                getLog().debug("Copying " + a.getFile());

                Artifact hpi = artifactFactory
                        .createArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), SCOPE_COMPILE, "hpi");
                resolver.resolve(hpi, remoteRepos, localRepository);

                FileUtils.copyFile(hpi.getFile(), new File(outputDirectory, hpi.getArtifactId() + ".hpi"));
                hpis.add(hpi);
                artifactIdLength = Math.max(artifactIdLength, hpi.getArtifactId().length());
                versionLength = Math.max(versionLength, hpi.getVersion().length());
            }

            Collections.sort(hpis, new Comparator<Artifact>() {
                public int compare(Artifact o1, Artifact o2) {
                    return map(o1).compareTo(map(o2));
                }

                private String map(Artifact a) {
                    return a.getArtifactId();
                }
            });

            File list = new File(project.getBuild().getOutputDirectory(), "bundled-plugins.txt");
            list.getParentFile().mkdirs();
            PrintWriter w = new PrintWriter(list);
            try {
                for (Artifact hpi : hpis) {
                    getLog().info(String.format("%" + (-artifactIdLength) + "s    %" + (-versionLength) + "s    %s",
                            hpi.getArtifactId(), hpi.getVersion(), hpi.getGroupId()));
                    w.println(hpi.getId());
                }
            } finally {
                IOUtils.closeQuietly(w);
            }

            projectHelper.attachArtifact(project, "txt", "bundled-plugins", list);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        }
    }
}
