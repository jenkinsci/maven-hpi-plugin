package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleDependencyResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * Places test-dependency plugins into somewhere the test harness can pick up.
 *
 * <p>
 * See {@code TestPluginManager.loadBundledPlugins()} where the test harness uses it.
 *
 * <p>Additionally, it may adjust the classpath for {@code surefire:test} to run tests against
 * different versions of various dependencies than what was configured in the POM.
 */
@Mojo(name="resolve-test-dependencies", requiresDependencyResolution = ResolutionScope.TEST)
public class TestDependencyMojo extends AbstractHpiMojo {

    private static final String CORE_REGEX = "WEB-INF/lib/jenkins-core-([0-9.]+(?:-[0-9a-f.]+)*(?:-(?i)([a-z]+)(-)?([0-9a-f.]+)?)?(?:-(?i)([a-z]+)(-)?([0-9a-f_.]+)?)?(?:-SNAPSHOT)?)[.]jar";
    private static final String PLUGIN_REGEX = "WEB-INF/plugins/([^/.]+)[.][hj]pi";
    private static final String OVERRIDE_REGEX = "([^:]+:[^:]+):([^:]+)";

    @Component private BuildPluginManager pluginManager;

    @Component private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component private ProjectDependenciesResolver dependenciesResolver;

    /**
     * List of dependency version overrides in the form {@code groupId:artifactId:version} to apply
     * during testing. Must correspond to dependencies already present in the project model or their
     * transitive dependencies.
     */
    @Parameter(property = "overrideVersions")
    private List<String> overrideVersions;

    /**
     * Path to a Jenkins WAR file with bundled plugins to apply during testing. Dependencies already
     * present in the project model or their transitive dependencies will be updated to the versions
     * in the WAR. Dependencies not already present in the project model will be added to the
     * project model.
     * May be combined with {@code overrideVersions} so long as the results do not conflict.
     */
    @Parameter(property = "overrideWar")
    private File overrideWar;

    /**
     * Whether to update all transitive dependencies to the upper bounds. Effectively causes the
     * same behavior as the {@code requireUpperBoundDeps} Enforcer rule would, if the specified
     * dependencies were to be written to the POM. Intended for use in conjunction with {@link
     * #overrideVersions} or {@link #overrideWar}.
     */
    @Parameter(property = "useUpperBounds")
    private boolean useUpperBounds;

    @Override
    public void execute() throws MojoExecutionException {
        Map<String, String> overrides = overrideVersions != null ? parseOverrides(overrideVersions) : Collections.emptyMap();
        if (!overrides.isEmpty()) {
            getLog().info(String.format("Applying %d overrides.", overrides.size()));
        }
        if (overrides.containsKey(String.format("%s:%s", project.getGroupId(), project.getArtifactId()))) {
            throw new MojoExecutionException("Cannot override self");
        }

        Map<String, String> bundledPlugins = overrideWar != null ? scanWar(overrideWar, session, project) : Collections.emptyMap();
        if (!bundledPlugins.isEmpty()) {
            getLog().info(String.format("Scanned contents of %s with %d bundled plugins", overrideWar, bundledPlugins.size()));
        }

        // Deal with conflicts in user-provided input.
        Set<String> intersection = new HashSet<>(bundledPlugins.keySet());
        intersection.retainAll(overrides.keySet());
        for (String override : intersection) {
            if (bundledPlugins.get(override).equals(overrides.get(override))) {
                /*
                 * Not really a conflict since the versions are the same. Remove it from one of the
                 * two lists to simplify the implementation later. We pick the former since the
                 * semantics for the latter are looser.
                 */
                overrides.remove(override);
            } else {
                throw new MojoExecutionException(String.format(
                        "Failed to override %s: conflict between %s in overrideVersions and %s in jth.jenkins-war.path",
                        override, bundledPlugins.get(override), overrides.get(override)));
            }
        }

        // The effective artifacts to be used when building the plugin index and test classpath.
        Set<MavenArtifact> effectiveArtifacts;

        // Track changes to the classpath when the user has overridden dependency versions.
        Map<String, String> additions = new HashMap<>();
        Map<String, String> deletions = new HashMap<>();
        Map<String, String> updates = new HashMap<>();

        if (overrides.isEmpty() && overrideWar == null) {
            effectiveArtifacts = getProjectArtfacts();
        } else {
            /*
             * WARNING: Under no circumstances should this code ever be executed when performing a
             * release.
             */

            // Create a shadow project for dependency analysis.
            MavenProject shadow = project.clone();

            // Stash the original resolution for use later.
            Map<String, String> originalResolution = new HashMap<>();
            for (Artifact artifact : shadow.getArtifacts()) {
                originalResolution.put(toKey(artifact), artifact.getVersion());
            }

            // First pass: apply the overrides specified by the user.
            applyOverrides(overrides, bundledPlugins, shadow, getLog());

            if (useUpperBounds) {
                /*
                 * Do upper bounds analysis. Upper bounds analysis consumes the model directly and
                 * not the resolution of that model, so it is fine to invoke it at this point with
                 * the model having been updated and the resolution having been cleared.
                 */
                DependencyNode node;
                try {
                    ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                    buildingRequest.setProject(shadow);
                    ArtifactFilter filter = null; // Evaluate all scopes
                    node = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, filter);
                } catch (DependencyCollectorBuilderException e) {
                    throw new MojoExecutionException("Failed to analyze dependency tree for useUpperBounds", e);
                }
                RequireUpperBoundDepsVisitor visitor = new RequireUpperBoundDepsVisitor();
                node.accept(visitor);
                Map<String, String> upperBounds = visitor.upperBounds();

                // Second pass: apply the results of the upper bounds analysis.
                applyOverrides(upperBounds, Collections.emptyMap(), shadow, getLog());
            }

            /*
             * At this point, the model has been updated as the user has requested. We now redo
             * resolution and compare the new resolution to the original in order to account for
             * updates to transitive dependencies that are not present in the model. Anything that
             * was updated in the new resolution needs to be updated in the test classpath. Anything
             * that was removed in the new resolution needs to be removed from the test classpath.
             * Anything that was added in the new resolution needs to be added to the test
             * classpath.
             */
            Set<Artifact> resolved = resolveDependencies(shadow);
            effectiveArtifacts = wrap(new Artifacts(resolved));
            Map<String, String> newResolution = new HashMap<>();
            for (Artifact artifact : resolved) {
                newResolution.put(toKey(artifact), artifact.getVersion());
            }
            for (Map.Entry<String, String> entry : newResolution.entrySet()) {
                if (originalResolution.containsKey(entry.getKey())) {
                    // Present in both old and new resolution: check for update.
                    String originalVersion = originalResolution.get(entry.getKey());
                    String newVersion = entry.getValue();
                    if (!newVersion.equals(originalVersion)) {
                        updates.put(entry.getKey(), newVersion);
                    }
                } else {
                    // Present in new resolution but not old: addition.
                    additions.put(entry.getKey(), entry.getValue());
                }
            }
            for (Map.Entry<String, String> entry : originalResolution.entrySet()) {
                if (!newResolution.containsKey(entry.getKey())) {
                    // Present in old resolution but not new: deletion.
                    deletions.put(entry.getKey(), entry.getValue());
                }
            }
            getLog().info("New dependency tree:");
            MavenSession shadowSession = session.clone();
            shadowSession.setCurrentProject(shadow);
            shadow.setArtifacts(resolved);
            MojoExecutor.executeMojo(
                    MojoExecutor.plugin(
                            MojoExecutor.groupId("org.apache.maven.plugins"),
                            MojoExecutor.artifactId("maven-dependency-plugin")),
                    MojoExecutor.goal("tree"),
                    MojoExecutor.configuration(
                            MojoExecutor.element(MojoExecutor.name("scope"), Artifact.SCOPE_TEST)),
                    MojoExecutor.executionEnvironment(shadow, shadowSession, pluginManager));
            if (getLog().isDebugEnabled()) {
                getLog().debug("after re-resolving, additions: " + additions);
                getLog().debug("after re-resolving, deletions: " + deletions);
                getLog().debug("after re-resolving, updates: " + updates);
            }
        }

        File testDir = new File(project.getBuild().getTestOutputDirectory(), "test-dependencies");
        try {
            Files.createDirectories(testDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + testDir + "'", e);
        }

        try (FileOutputStream fos = new FileOutputStream(new File(testDir, "index")); Writer w = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            for (MavenArtifact a : effectiveArtifacts) {
                if (!a.isPluginBestEffort(getLog()))
                    continue;

                String artifactId = a.getActualArtifactId();
                if (artifactId == null) {
                    getLog().debug("Skipping " + artifactId + " with classifier " + a.getClassifier());
                    continue;
                }

                getLog().debug("Copying " + artifactId + " as a test dependency");
                File dst = new File(testDir, artifactId + ".hpi");
                FileUtils.copyFile(a.getHpi().getFile(),dst);
                w.write(artifactId + "\n");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy dependency plugins",e);
        }

        if (!additions.isEmpty() || !deletions.isEmpty() || !updates.isEmpty()) {
            List<String> additionalClasspathElements = new LinkedList<>();
            NavigableMap<String, String> includes = new TreeMap<>();
            includes.putAll(additions);
            includes.putAll(updates);
            for (Map.Entry<String, String> entry : includes.entrySet()) {
                String key = entry.getKey();
                String[] groupArt = key.split(":");
                String groupId = groupArt[0];
                String artifactId = groupArt[1];
                String version = entry.getValue();
                /*
                 * We cannot use MavenProject.getArtifactMap since we may have multiple dependencies
                 * of different classifiers.
                 */
                boolean found = false;
                // Yeah, this is O(n²)... deal with it!
                for (MavenArtifact a : effectiveArtifacts) {
                    if (!a.getGroupId().equals(groupId) || !a.getArtifactId().equals(artifactId)) {
                        continue;
                    }
                    if (!a.getVersion().equals(version)) {
                        throw new AssertionError("should never happen");
                    }
                    found = true;
                    if (a.getArtifactHandler().isAddedToClasspath()) {
                        /*
                         * Everything is added to the test classpath, so there is no need to check
                         * scope.
                         */
                        additionalClasspathElements.add(a.getFile().getAbsolutePath());
                    }
                }
                if (!found) {
                    throw new MojoExecutionException("could not find dependency " + key);
                }
            }

            NavigableSet<String> classpathDependencyExcludes = new TreeSet<>();
            classpathDependencyExcludes.addAll(deletions.keySet());
            classpathDependencyExcludes.addAll(updates.keySet());

            Properties properties = project.getProperties();
            if (getLog().isDebugEnabled()) {
                getLog().debug(String.format("Replacing POM-defined classpath elements %s with %s", classpathDependencyExcludes, additionalClasspathElements));
            }
            // cf. http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html
            properties.setProperty("maven.test.additionalClasspath", String.join(",", additionalClasspathElements));
            properties.setProperty("maven.test.dependency.excludes", String.join(",", classpathDependencyExcludes));
        }
    }

    /**
     * Scan a WAR file, accumulating plugin information.
     *
     * @param war The WAR to scan.
     * @return The bundled plugins in the WAR.
     */
    @SuppressFBWarnings(value = "REDOS", justification = "trusted code")
    private static Map<String, String> scanWar(File war, MavenSession session, MavenProject project) throws MojoExecutionException {
        Map<String, String> overrides = new HashMap<>();
        try (JarFile jf = new JarFile(war)) {
            Enumeration<JarEntry> entries = jf.entries();
            String coreVersion = null;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                Matcher m = Pattern.compile(CORE_REGEX).matcher(name);
                if (m.matches()) {
                    if (coreVersion != null) {
                        throw new MojoExecutionException("More than 1 jenkins-core JAR in " + war);
                    }
                    coreVersion = m.group(1);
                }
                m = Pattern.compile(PLUGIN_REGEX).matcher(name);
                if (m.matches()) {
                    try (InputStream is = jf.getInputStream(entry); JarInputStream jis = new JarInputStream(is)) {
                        Manifest manifest = jis.getManifest();
                        String groupId = manifest.getMainAttributes().getValue("Group-Id");
                        if (groupId == null) {
                            throw new IllegalArgumentException("Failed to determine group ID for " + name);
                        }
                        String artifactId = manifest.getMainAttributes().getValue("Short-Name");
                        if (artifactId == null) {
                            throw new IllegalArgumentException("Failed to determine artifact ID for " + name);
                        }
                        String version = manifest.getMainAttributes().getValue("Plugin-Version");
                        if (version == null) {
                            throw new IllegalArgumentException("Failed to determine version for " + name);
                        }
                        String key = String.format("%s:%s", groupId, artifactId);
                        String self = String.format("%s:%s", project.getGroupId(), project.getArtifactId());
                        if (!key.equals(self)) {
                            overrides.put(key, version);
                        }
                    }
                }
            }

            /*
             * It is tempting to try and avoid the requirement for jenkins.version here and simply
             * override the core dependencies in the tree; however, this fails to take into account
             * that core's provided dependencies are already being managed at their original
             * versions.
             */
            if (coreVersion == null) {
                throw new MojoExecutionException("no jenkins-core.jar in " + war);
            }
            String jenkinsVersion = session.getSystemProperties().getProperty("jenkins.version");
            if (jenkinsVersion == null) {
                jenkinsVersion = project.getProperties().getProperty("jenkins.version");
            }
            if (jenkinsVersion == null) {
                throw new MojoExecutionException("jenkins.version must be set when using overrideWar");
            } else if (!jenkinsVersion.equals(coreVersion)) {
                throw new MojoExecutionException("jenkins.version must match the version specified by overrideWar: " + coreVersion);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan " + war, e);
        }
        return overrides;
    }

    private static Map<String, String> parseOverrides(List<String> overrideVersions) throws MojoExecutionException {
        Map<String, String> overrides = new HashMap<>();
        for (String override : overrideVersions) {
            Matcher m = Pattern.compile(OVERRIDE_REGEX).matcher(override);
            if (!m.matches()) {
                throw new MojoExecutionException("illegal override: " + override);
            }
            overrides.put(m.group(1), m.group(2));
        }
        return overrides;
    }

    /**
     * Apply the overrides specified by the user or upper bounds analysis to the model (i.e.,
     * dependency management or dependencies) in the shadow project. This clears the existing
     * resolution that was done because of the {@code @requiresDependencyResolution} Mojo attribute,
     * as it is now invalid. It is possible to perform such a pass manually on a plugin and compare
     * the results with this algorithm to verify that the logic in this method is correct.
     */
    private static void applyOverrides(
            Map<String, String> overrides,
            Map<String, String> bundledPlugins,
            MavenProject project,
            Log log)
            throws MojoExecutionException {
        Set<String> appliedOverrides = new HashSet<>();
        Set<String> appliedBundledPlugins = new HashSet<>();

        // Update existing dependency entries in the model.
        for (Dependency dependency : project.getDependencies()) {
            String key = toKey(dependency);
            if (updateDependency(dependency, overrides, "direct dependency", log)) {
                appliedOverrides.add(key);
            }
            if (updateDependency(dependency, bundledPlugins, "direct dependency", log)) {
                appliedBundledPlugins.add(key);
            }
        }

        // Update existing dependency management entries in the model.
        if (project.getDependencyManagement() != null) {
            for (Dependency dependency : project.getDependencyManagement().getDependencies()) {
                String key = toKey(dependency);
                if (updateDependency(dependency, overrides, "dependency management entry", log)) {
                    appliedOverrides.add(key);
                }
                if (updateDependency(dependency, bundledPlugins, "dependency management entry", log)) {
                    appliedBundledPlugins.add(key);
                }
            }
        }

        /*
         * If an override was requested for a transitive dependency that is not in the model, add a
         * dependency management entry to the model.
         */
        Set<String> unappliedOverrides = new HashSet<>(overrides.keySet());
        unappliedOverrides.removeAll(appliedOverrides);
        Set<String> overrideAdditions = new HashSet<>();
        for (Artifact artifact : project.getArtifacts()) {
            String key = toKey(artifact);
            if (unappliedOverrides.contains(key)) {
                String version = overrides.get(key);
                Dependency dependency = new Dependency();
                dependency.setGroupId(artifact.getGroupId());
                dependency.setArtifactId(artifact.getArtifactId());
                dependency.setVersion(version);
                dependency.setScope(artifact.getScope());
                dependency.setType(artifact.getType());
                dependency.setClassifier(artifact.getClassifier());
                DependencyManagement dm = project.getDependencyManagement();
                if (dm != null) {
                    log.info(String.format("Adding dependency management entry %s:%s", key, dependency.getVersion()));
                    dm.addDependency(dependency);
                } else {
                    throw new MojoExecutionException(String.format("Failed to add dependency management entry %s:%s because the project does not have a dependency management section", key, version));
                }
                overrideAdditions.add(key);
            }
        }
        unappliedOverrides.removeAll(overrideAdditions);

        // By now, we should have applied the entire override request. If not, fail.
        if (!unappliedOverrides.isEmpty()) {
            throw new MojoExecutionException("Failed to apply the following overrides: " + unappliedOverrides);
        }

        /*
         * If a bundled plugin was added that is neither in the model nor the transitive dependency
         * chain, add a test-scoped direct dependency to the model. This is necessary in order for
         * us to be able to correctly populate target/test-dependencies/ later on.
         */
        Set<String> unappliedBundledPlugins = new HashSet<>(bundledPlugins.keySet());
        unappliedBundledPlugins.removeAll(appliedBundledPlugins);
        for (String key : unappliedBundledPlugins) {
            String[] groupArt = key.split(":");
            String groupId = groupArt[0];
            String artifactId = groupArt[1];
            String version = bundledPlugins.get(key);
            Dependency dependency = new Dependency();
            dependency.setGroupId(groupId);
            dependency.setArtifactId(artifactId);
            dependency.setVersion(version);
            dependency.setScope(Artifact.SCOPE_TEST);
            log.info(String.format("Adding test-scoped direct dependency %s:%s", key, version));
            project.getDependencies().add(dependency);
        }

        log.debug("adjusted dependencies: " + project.getDependencies());
        if (project.getDependencyManagement() != null) {
            log.debug("adjusted dependency management: " + project.getDependencyManagement().getDependencies());
        }

        /*
         * With our changes to the model, the existing resolution is now invalid, so clear it lest
         * anything accidentally use the invalid values. We will perform resolution again after all
         * passes are complete.
         */
        project.setDependencyArtifacts(null);
        project.setArtifacts(null);
    }

    private static boolean updateDependency(Dependency dependency, Map<String, String> overrides, String type, Log log) {
        String key = toKey(dependency);
        String overrideVersion = overrides.get(key);
        if (overrideVersion != null) {
            log.info(String.format("Updating %s %s from %s to %s", type, key, dependency.getVersion(), overrideVersion));
            dependency.setVersion(overrideVersion);
            return true;
        }
        return false;
    }

    /**
     * Performs the equivalent of the "@requiresDependencyResolution" mojo attribute.
     *
     * @see LifecycleDependencyResolver#getDependencies(MavenProject, Collection, Collection,
     *     MavenSession, boolean, Set)
     */
    private Set<Artifact> resolveDependencies(MavenProject project) throws MojoExecutionException {
        try {
            DependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, session.getRepositorySession());
            DependencyResolutionResult result = dependenciesResolver.resolve(request);

            Set<Artifact> artifacts = new LinkedHashSet<>();
            if (result.getDependencyGraph() != null && !result.getDependencyGraph().getChildren().isEmpty()) {
                RepositoryUtils.toArtifacts(
                        artifacts,
                        result.getDependencyGraph().getChildren(),
                        Collections.singletonList(project.getArtifact().getId()),
                        request.getResolutionFilter());
            }
            return artifacts;
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Unable to resolve dependencies", e);
        }
    }

    // Adapted from RequireUpperBoundDeps @ 731ea7a693a0986f2054b6a73a86a31373df59ec.
    private class RequireUpperBoundDepsVisitor implements DependencyNodeVisitor {

        private Map<String, List<DependencyNodeHopCountPair>> keyToPairsMap = new LinkedHashMap<>();

        public boolean visit(DependencyNode node) {
            DependencyNodeHopCountPair pair = new DependencyNodeHopCountPair(node);
            String key = pair.constructKey();
            List<DependencyNodeHopCountPair> pairs = keyToPairsMap.get(key);
            if (pairs == null) {
                pairs = new ArrayList<>();
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
        public Map<String, String> upperBounds() {
            Map<String, String> r = new HashMap<>();
            for (List<DependencyNodeHopCountPair> pairs : keyToPairsMap.values()) {
                DependencyNodeHopCountPair resolvedPair = pairs.get(0);

                // search for artifact with lowest hopCount
                for (DependencyNodeHopCountPair hopPair : pairs.subList(1, pairs.size())) {
                    if (hopPair.getHopCount() < resolvedPair.getHopCount()) {
                        resolvedPair = hopPair;
                    }
                }

                ArtifactVersion resolvedVersion = resolvedPair.extractArtifactVersion(false);

                for (DependencyNodeHopCountPair pair : pairs) {
                    ArtifactVersion version = pair.extractArtifactVersion(true);
                    if (resolvedVersion.compareTo(version) < 0) {
                        Artifact artifact = resolvedPair.node.getArtifact();
                        String key = toKey(artifact);
                        if (!r.containsKey(key) || new ComparableVersion(version.toString()).compareTo(new ComparableVersion(r.get(key))) > 1) {
                            getLog().info(String.format("for %s, upper bounds forces an upgrade from %s to %s", key, resolvedVersion, version));
                            r.put(key, version.toString());
                        }
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
            return toKey(artifact);
        }

        public DependencyNode getNode() {
            return node;
        }

        private ArtifactVersion extractArtifactVersion(boolean usePremanagedVersion) {
            if (usePremanagedVersion && node.getPremanagedVersion() != null) {
                return new DefaultArtifactVersion(node.getPremanagedVersion());
            }

            Artifact artifact = node.getArtifact();
            String version = artifact.getBaseVersion();
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

        @SuppressFBWarnings(
                value = "EQ_COMPARETO_USE_OBJECT_EQUALS",
                justification = "Silly check; it is perfectly reasonable to implement Comparable by writing a compareTo without an equals.")
        public int compareTo(DependencyNodeHopCountPair other) {
            return Integer.compare(hopCount, other.getHopCount());
        }
    }

    private static String toKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    private static String toKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId();
    }
}
