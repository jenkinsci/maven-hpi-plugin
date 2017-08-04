package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;

/**
 * Places test-dependency plugins into somewhere the test harness can pick up.
 *
 * <p>
 * See {@code TestPluginManager.loadBundledPlugins()} where the test harness uses it.
 *
 * <p>Additionally, it may adjust the classpath for {@code surefire:test} to run tests
 * against different versions of various dependencies than what was configured in the POM.
 */
@Mojo(name="resolve-test-dependencies", requiresDependencyResolution = ResolutionScope.TEST)
public class TestDependencyMojo extends AbstractHpiMojo {

    @Component
    private DependencyTreeBuilder dependencyTreeBuilder;

    @Component
    private ArtifactCollector collector;

    /**
     * List of dependency version overrides in the form {@code groupId:artifactId:version} to apply during testing.
     * Must correspond to dependencies already present in the project model.
     */
    @Parameter(property="overrideVersions")
    private List<String> overrideVersions;

    /**
     * Whether to update all transitive dependencies to the upper bounds.
     * Effectively causes same behavior as the {@code requireUpperBoundDeps} Enforcer rule would,
     * if the specified dependencies were to be written to the POM.
     * Intended for use in conjunction with {@link #overrideVersions}.
     */
    @Parameter(property="useUpperBounds")
    private boolean useUpperBounds;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Map<String, String> overrides = new HashMap<>(); // groupId:artifactId → version
        if (overrideVersions != null) {
            for (String override : overrideVersions) {
                Matcher m = Pattern.compile("([^:]+:[^:]+):([^:]+)").matcher(override);
                if (!m.matches()) {
                    throw new MojoExecutionException("illegal override: " + override);
                }
                overrides.put(m.group(1), m.group(2));
            }
        }
        File testDir = new File(project.getBuild().getTestOutputDirectory(),"test-dependencies");
        testDir.mkdirs();

        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(new File(testDir,"index")),"UTF-8");

            for (MavenArtifact a : getProjectArtfacts()) {
                if(!a.isPlugin())
                    continue;

                String artifactId = a.getActualArtifactId();
                if (artifactId == null) {
                    getLog().debug("Skipping " + artifactId + " with classifier " + a.getClassifier());
                    continue;
                }

                getLog().debug("Copying " + artifactId + " as a test dependency");
                File dst = new File(testDir, artifactId + ".hpi");
                File src;
                String version = overrides.get(a.getGroupId() + ":" + artifactId);
                if (version != null) {
                    src = replace(a.getHpi().artifact, version).getFile();
                } else {
                    src = a.getHpi().getFile();
                }
                FileUtils.copyFile(src, dst);
                w.write(artifactId + "\n");
            }

            w.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy dependency plugins",e);
        }

        if (overrideVersions != null) {
            if (useUpperBounds) {
                DependencyNode node;
                try {
                    MavenProject shadow = (MavenProject) project.clone();
                    // first pass: adjust direct dependencies in place
                    Set<String> updated = new HashSet<>();
                    @SuppressWarnings("unchecked")
                    Set<Artifact> dependencyArtifacts = shadow.getDependencyArtifacts(); // mutable; seems to be what DefaultDependencyTreeBuilder cares about
                    for (Artifact art : dependencyArtifacts) {
                        String key = art.getGroupId() + ":" + art.getArtifactId();
                        String overrideVersion = overrides.get(key);
                        if (overrideVersion != null) {
                            getLog().debug("For dependency analysis, updating " + key + " from " + art.getVersion() + " to " + overrideVersion);
                            art.setVersion(overrideVersion);
                            updated.add(key);
                        }
                    }
                    // second pass: add direct dependencies for transitive dependencies that need to be bumped
                    @SuppressWarnings("unchecked")
                    Set<Artifact> artifacts = shadow.getArtifacts();
                    Set<String> transitiveUpdated = new HashSet<>();
                    for (Artifact art : artifacts) {
                        String key = art.getGroupId() + ":" + art.getArtifactId();
                        if (updated.contains(key)) {
                            continue; // already handled above
                        }
                        String overrideVersion = overrides.get(key);
                        if (overrideVersion != null) {
                            getLog().info("For dependency analysis, updating transitive " + key + " from " + art.getVersion() + " to " + overrideVersion);
                            dependencyArtifacts.add(replace(art, overrideVersion));
                            transitiveUpdated.add(key);
                        }
                    }
                    Set<String> unapplied = new HashSet<>(overrides.keySet());
                    unapplied.removeAll(updated);
                    unapplied.removeAll(transitiveUpdated);
                    if (!unapplied.isEmpty()) {
                        throw new MojoFailureException("could not find dependencies " + unapplied);
                    }
                    getLog().debug("adjusted dependencyArtifacts: " + dependencyArtifacts);
                    node = dependencyTreeBuilder.buildDependencyTree(shadow, localRepository, artifactFactory, artifactMetadataSource, /* all scopes */null, collector);
                } catch (DependencyTreeBuilderException | CloneNotSupportedException x) {
                    throw new MojoExecutionException("could not analyze dependency tree for useUpperBounds: " + x, x);
                }
                RequireUpperBoundDepsVisitor visitor = new RequireUpperBoundDepsVisitor();
                node.accept(visitor);
                Map<String, String> upperBounds = visitor.upperBounds();
                if (!upperBounds.isEmpty()) {
                    getLog().debug("Applying upper bounds: " + upperBounds);
                    overrides.putAll(upperBounds);
                }
            }
            List<String> additionalClasspathElements = new ArrayList<>();
            List<String> classpathDependencyExcludes = new ArrayList<>();
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey();
                classpathDependencyExcludes.add(key);
                String[] groupArt = key.split(":");
                String groupId = groupArt[0];
                String artifactId = groupArt[1];
                String version = entry.getValue();
                // Cannot use MavenProject.getArtifactMap since we may have multiple dependencies of different classifiers.
                boolean found = false;
                for (Object _a : project.getArtifacts()) {
                    Artifact a = (Artifact) _a;
                    if (!a.getGroupId().equals(groupId) || !a.getArtifactId().equals(artifactId)) {
                        continue;
                    }
                    found = true;
                    if (a.getArtifactHandler().isAddedToClasspath()) { // everything is added to test CP, so no need to check scope
                        additionalClasspathElements.add(replace(a, version).getFile().getAbsolutePath());
                    }
                }
                if (!found) {
                    throw new MojoExecutionException("could not find dependency " + key);
                }
            }
            Properties properties = project.getProperties();
            getLog().info("Replacing POM-defined classpath elements " + classpathDependencyExcludes + " with " + additionalClasspathElements);
            // cf. http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html
            properties.setProperty("maven.test.additionalClasspath", StringUtils.join(additionalClasspathElements, ','));
            properties.setProperty("maven.test.dependency.excludes", StringUtils.join(classpathDependencyExcludes, ','));
        }
    }

    private Artifact replace(Artifact a, String version) throws MojoExecutionException {
        Artifact a2 = new DefaultArtifact(a.getGroupId(), a.getArtifactId(), VersionRange.createFromVersion(version), a.getScope(), a.getType(), a.getClassifier(), a.getArtifactHandler(), a.isOptional());
        try {
            artifactResolver.resolve(a2, remoteRepos, localRepository);
        } catch (AbstractArtifactResolutionException x) {
            throw new MojoExecutionException("could not find " + a + " in version " + version + ": " + x, x);
        }
        return a2;
    }

    // Adapted from RequireUpperBoundDeps @ 3.0.0-M1. TODO delete extraneous stuff and simplify to the logic we actually need here:
    private class RequireUpperBoundDepsVisitor implements DependencyNodeVisitor {

        private boolean uniqueVersions;

        public void setUniqueVersions(boolean uniqueVersions) {
            this.uniqueVersions = uniqueVersions;
        }

        private Map<String, List<DependencyNodeHopCountPair>> keyToPairsMap
                = new LinkedHashMap<String, List<DependencyNodeHopCountPair>>();

        public boolean visit(DependencyNode node) {
            DependencyNodeHopCountPair pair = new DependencyNodeHopCountPair(node);
            String key = pair.constructKey();
            List<DependencyNodeHopCountPair> pairs = keyToPairsMap.get(key);
            if (pairs == null) {
                pairs = new ArrayList<DependencyNodeHopCountPair>();
                keyToPairsMap.put(key, pairs);
            }
            pairs.add(pair);
            Collections.sort(pairs);
            return true;
        }

        public boolean endVisit(DependencyNode node) {
            return true;
        }

        // added for TestDependencyMojo in place of getConflicts/containsConflicts
        @SuppressWarnings("unchecked")
        public Map<String, String> upperBounds() {
            Map<String, String> r = new HashMap<>();
            // TODO this does not suffice; does not find that workflow-api needs to go from 2.11 to 2.16, presumably because it was not a direct dependency to begin with
            for (List<DependencyNodeHopCountPair> pairs : keyToPairsMap.values()) {
                DependencyNodeHopCountPair resolvedPair = pairs.get(0);

                // search for artifact with lowest hopCount
                for (DependencyNodeHopCountPair hopPair : pairs.subList(1, pairs.size())) {
                    if (hopPair.getHopCount() < resolvedPair.getHopCount()) {
                        resolvedPair = hopPair;
                    }
                }

                ArtifactVersion resolvedVersion = resolvedPair.extractArtifactVersion(uniqueVersions, false);

                for (DependencyNodeHopCountPair pair : pairs) {
                    ArtifactVersion version = pair.extractArtifactVersion(uniqueVersions, true);
                    if (resolvedVersion.compareTo(version) < 0) {
                        Artifact artifact = resolvedPair.node.getArtifact();
                        String key = artifact.getGroupId() + ":" + artifact.getArtifactId();
                        getLog().info("for " + key + ", upper bounds forces an upgrade from " + resolvedVersion + " to " + version);
                        r.put(key, version.toString());
                    }
                }
            }
            return r;
        }

    }

    private static class DependencyNodeHopCountPair implements Comparable<DependencyNodeHopCountPair> {

        private DependencyNode node;

        private int hopCount;

        private DependencyNodeHopCountPair(DependencyNode node) {
            this.node = node;
            countHops();
        }

        private void countHops() {
            hopCount = 0;
            DependencyNode parent = node.getParent();
            while (parent != null) {
                hopCount++;
                parent = parent.getParent();
            }
        }

        private String constructKey() {
            Artifact artifact = node.getArtifact();
            return artifact.getGroupId() + ":" + artifact.getArtifactId();
        }

        public DependencyNode getNode() {
            return node;
        }

        private ArtifactVersion extractArtifactVersion(boolean uniqueVersions, boolean usePremanagedVersion) {
            if (usePremanagedVersion && node.getPremanagedVersion() != null) {
                return new DefaultArtifactVersion(node.getPremanagedVersion());
            }

            Artifact artifact = node.getArtifact();
            String version = uniqueVersions ? artifact.getVersion() : artifact.getBaseVersion();
            if (version != null) {
                return new DefaultArtifactVersion(version);
            }
            try {
                return artifact.getSelectedVersion();
            } catch (OverConstrainedVersionException e) {
                throw new RuntimeException("Version ranges problem with " + node.getArtifact(), e);
            }
        }

        public int getHopCount() {
            return hopCount;
        }

        public int compareTo(DependencyNodeHopCountPair other) {
            return Integer.valueOf(hopCount).compareTo(Integer.valueOf(other.getHopCount()));
        }
    }

}
