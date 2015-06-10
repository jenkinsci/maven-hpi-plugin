package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.artifact.filter.collection.AbstractArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Take the current project, list up all the transitive dependencies, then copy them
 * into a specified directory.
 *
 * <p>
 * Used to assemble <tt>jenkins.war</tt> by bundling all the necessary plugins.
 *
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

    /**
     * Where to copy optional plugins into.
     * @see <a href="https://github.com/jenkinsci/optional-plugin-helper-module">optional-plugin-helper-module</a>
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}/WEB-INF/optional-plugins/")
    private File optionalOutputDirectory;

    /**
     * By default the build will fail if one of the bundled plugins has an optional dependency on a newer version
     * of another bundled plugin.
     */
    @Parameter(property = "hpi.ignoreOptionalDepenencyConflicts")
    private boolean ignoreOptionalDepenencyConflicts;

    @Component
    protected ArtifactResolver resolver;

    @Component
    protected ArtifactCollector artifactCollector;


    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<String> badDependencies = new LinkedHashSet<String>();
        for (Artifact a: (Set<Artifact>)project.getDependencyArtifacts()) {
            try {
                if (StringUtils.isBlank(a.getType()) || StringUtils.equals("jar", a.getType()) && wrap(a).isPlugin()) {
                    final String gav = String.format("%s:%s:%s", a.getGroupId(), a.getArtifactId(), a.getVersion());
                    getLog().error(String.format("Dependency on plugin %s does not include <type> tag", gav));
                    badDependencies.add(gav);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to check plugin dependencies", e);
            }
        }
        if (!badDependencies.isEmpty()) {
            throw new MojoFailureException(
                    "The following plugin dependencies are missing the <type> tag required by the bundle-plugins " 
                            + "goal:\n  " + StringUtils.join(badDependencies, "\n  "));
        }
        TypeFilter typeFilter = new TypeFilter("hpi,jpi", null);
        // the HPI packaging type is brain-dead... since nobody lists plugin dependencies with <type>hpi</type>
        // we loose all transitive information, so we need to throw away all the good stuff maven would give us
        // further we need to set <scope>provided</scope> in order to keep this off the classpath as then
        // the war plugin would suck them in anyways
        Set<Artifact> artifacts = typeFilter.filter(project.getDependencyArtifacts());
        artifacts = typeFilter.filter(artifacts);
        Set<Artifacts> nonOptionalArtifacts = new OptionalFilter(false).filter(artifacts);
        try {
            // This would be unnecessary and trivial if the hpi packaging was defined correctly
            ArtifactResolutionResult r = resolver.resolveTransitively(artifacts, project.getArtifact(), remoteRepos, localRepository, artifactMetadataSource);
            ArtifactResolutionResult noR = resolver.resolveTransitively(nonOptionalArtifacts, project.getArtifact(), remoteRepos, localRepository, artifactMetadataSource);

            List<MavenArtifact> hpis = new ArrayList<MavenArtifact>();


            // resolveTransitively resolves items in the 'all' set individually,
            // so there will be duplicates and multiple versions
            // we need to select the latest
            Map<String, MavenArtifact> selected = new HashMap<String, MavenArtifact>();

            // first we want to take all the dependencies as a complete set
            
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
            
            // now just take the non-optional ones (these may have a higher minimum versions when we exclude the optional ones)
            // (due to the ordering effect of dependencies when resolving conflicts in case you were wondering)
            
            Set<String> nonOptional = new HashSet<String>();
            for (Artifact o: (Set<Artifact>)noR.getArtifacts()) {
                MavenArtifact a = wrap(o);
                if (a.isPlugin()) {
                    nonOptional.add(o.getArtifactId());
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

            // now we just need to check if the optional dependencies have issues
            // in theory we do not need to fail the build if a transitive dependency has issues
            // we could just up-version it, but that would put us back to the start in terms of dependency resolution
            // so much simpler to force the user to explicitly resolve the conflict by adding an explicit
            // dependency
            
            Set<String> optionalDependencyIssue = new LinkedHashSet<String>();
            for (MavenArtifact a: selected.values()) {
                try {
                    final MavenProject pom = a.resolvePom();
                    for (Dependency d : (List<Dependency>)pom.getDependencies()) {
                        if (!d.isOptional()) continue;
                        if (!selected.containsKey(d.getArtifactId())) continue;
                        MavenArtifact matching = selected.get(d.getArtifactId());
                        if (!StringUtils.equals(d.getGroupId(), matching.getGroupId())) continue;
                        if (matching.getVersionNumber().compareTo(new DefaultArtifactVersion(d.getVersion())) < 0) {
                            final String message = String.format(
                                    "%s: optional dependency of %s version %s conflicts with the bundled version %s",
                                    a.getArtifactId(), d.getArtifactId(), d.getVersion(), matching.getVersion());
                            getLog().error(message);
                            optionalDependencyIssue.add(message);
                        }
                    }
                } catch (ProjectBuildingException e) {
                    getLog().warn(String.format("Could not resolve pom of %s:%s:%s to check optional dependencies",
                            a.getGroupId(), a.getArtifactId(), a.getVersion()), e);
                }
            }
            if (!optionalDependencyIssue.isEmpty()) {
                if (ignoreOptionalDepenencyConflicts) {
                    getLog().warn("Ignoring optional dependency conflicts");
                } else {
                    throw new MojoFailureException(
                            "Optional dependencies are incompatible with bundled dependencies:\n  " + StringUtils
                                    .join(optionalDependencyIssue, "\n  "));
                }
            }

            outputDirectory.mkdirs();
            if (!nonOptional.containsAll(selected.keySet())) {
                optionalOutputDirectory.mkdirs();
            }

            int artifactIdLength = "Artifact ID".length(); // how many chars does it take to print artifactId?
            int groupIdLength = "Group ID".length(); // how many chars does it take to print groupId?
            int versionLength = "Version".length();
            for (MavenArtifact a : selected.values()) {
                MavenArtifact hpi = a.getHpi();
                getLog().debug("Copying " + hpi.getFile());


                FileUtils.copyFile(hpi.getFile(), 
                        new File(nonOptional.contains(hpi.getArtifactId()) ? outputDirectory : optionalOutputDirectory, 
                                hpi.getArtifactId() + ".hpi")
                );
                hpis.add(hpi);
                artifactIdLength = Math.max(artifactIdLength, hpi.getArtifactId().length());
                groupIdLength = Math.max(groupIdLength, hpi.getGroupId().length());
                versionLength = Math.max(versionLength, hpi.getVersion().length());
            }

            Collections.sort(hpis, new Comparator<MavenArtifact>() {
                public int compare(MavenArtifact o1, MavenArtifact o2) {
                    return map(o1).compareTo(map(o2));
                }

                private String map(MavenArtifact a) {
                    return a.getArtifactId();
                }
            });

            File list = new File(project.getBuild().getOutputDirectory(), "bundled-plugins.txt");
            File manifest = new File(project.getBuild().getDirectory(), "plugin-manifest.txt");
            list.getParentFile().mkdirs();
            PrintWriter w = new PrintWriter(list);
            PrintWriter m = new PrintWriter(manifest);
            try {
                final String format = "%" + (-artifactIdLength) + "s %" + (-versionLength) + "s %-8s %"+(-groupIdLength)+"s";
                final String format2 = "%"+(-groupIdLength)+"s %" + (-artifactIdLength) + "s %" + (-versionLength) + "s %-8s%n";
                getLog().info(String.format(format, 
                        "Artifact ID", 
                        "Version", 
                        "Optional", 
                        "Group ID"));
                m.printf(format2, "Group Id", "Artifact Id", "Version", "Optional");
                m.printf(format2, StringUtils.repeat("=",groupIdLength), StringUtils.repeat("=",artifactIdLength), StringUtils.repeat("=",versionLength), StringUtils.repeat("=",8));
                getLog().info(String.format(format,
                        StringUtils.repeat("=", artifactIdLength),
                        StringUtils.repeat("=", versionLength),
                        StringUtils.repeat("=", 8),
                        StringUtils.repeat("=", groupIdLength)));
                for (MavenArtifact hpi : hpis) {
                    getLog().info(String.format(format,
                            hpi.getArtifactId(), 
                            hpi.getVersion(), 
                            nonOptional.contains(hpi.getArtifactId()) ? "" : "optional", 
                            hpi.getGroupId()));
                    m.printf(format2,
                            hpi.getGroupId(),
                            hpi.getArtifactId(),
                            hpi.getVersion(),
                            nonOptional.contains(hpi.getArtifactId()) ? "no" : "yes"
                    );
                    w.println(hpi.getId());
                }
            } finally {
                IOUtils.closeQuietly(w);
                IOUtils.closeQuietly(m);
            }

            projectHelper.attachArtifact(project, "txt", "bundled-plugins", list);
            projectHelper.attachArtifact(project, "txt", "plugin-manifest", manifest);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve plugin dependencies", e);
        }
    }
    
    private static class OptionalFilter extends AbstractArtifactsFilter {
        private final boolean optional;

        private OptionalFilter(boolean optional) {
            this.optional = optional;
        }

        public Set filter(Set artifacts) {
            Set<Artifact> result = new LinkedHashSet<Artifact>();
            for (Artifact a: (Set<Artifact>)artifacts) {
                if (optional == a.isOptional()) {
                    result.add(a);
                }
            }
            return result;
        }
    }
}
