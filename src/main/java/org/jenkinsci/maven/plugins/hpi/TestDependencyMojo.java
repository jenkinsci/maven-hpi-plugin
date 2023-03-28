package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Collectors;
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
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
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
@SuppressFBWarnings(value = "REDOS", justification = "trusted code")
public class TestDependencyMojo extends AbstractHpiMojo {

    private static final Pattern CORE_REGEX = Pattern.compile("WEB-INF/lib/jenkins-core-([0-9.]+(?:-[0-9a-f.]+)*(?:-(?i)([a-z]+)(-)?([0-9a-f.]+)?)?(?:-(?i)([a-z]+)(-)?([0-9a-f_.]+)?)?(?:-SNAPSHOT)?)[.]jar");
    private static final Pattern PLUGIN_REGEX = Pattern.compile("WEB-INF/plugins/([^/.]+)[.][hj]pi");
    private static final Pattern OVERRIDE_REGEX = Pattern.compile("([^:]+:[^:]+):([^:]+)");

    @Component private BuildPluginManager pluginManager;

    @Component private DependencyCollectorBuilder dependencyCollectorBuilder;

    @Component private ProjectDependenciesResolver dependenciesResolver;

    @Component private RepositorySystem repositorySystem;

    /**
     * List of dependency version overrides in the form {@code groupId:artifactId:version} to apply
     * during testing. Must correspond to dependencies already present in the project model or their
     * transitive dependencies.
     */
    @Parameter(property = "overrideVersions")
    private List<String> overrideVersions;

    /**
     * Path to a Jenkins WAR file with bundled plugins to apply during testing.
     * <p>Dependencies already present in the project model or their transitive dependencies will be updated to the versions in the WAR.
     * <p>May be combined with {@code overrideVersions} so long as the results do not conflict.
     * <p>The version of the WAR must be identical to {@code jenkins.version}.
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

    /**
     * List of exclusions to upper bound updates in the form {@code groupId:artifactId}.
     * Must not be provided when {@link #useUpperBounds} is false.
     */
    @Parameter(property = "upperBoundsExcludes")
    private List<String> upperBoundsExcludes;

    @Override
    public void execute() throws MojoExecutionException {
        Map<String, String> overrides = overrideVersions != null ? parseOverrides(overrideVersions) : Map.of();
        if (!overrides.isEmpty()) {
            getLog().info(String.format("Applying %d overrides.", Integer.valueOf(overrides.size())));
        }
        if (overrides.containsKey(String.format("%s:%s", project.getGroupId(), project.getArtifactId()))) {
            throw new MojoExecutionException("Cannot override self");
        }

        Map<String, String> bundledPlugins = overrideWar != null ? scanWar(overrideWar, session, project) : Map.of();
        if (!bundledPlugins.isEmpty()) {
            getLog().info(String.format("Scanned contents of %s with %d bundled plugins", overrideWar, Integer.valueOf(bundledPlugins.size())));
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
                        "Failed to override %s: conflict between %s in overrideVersions and %s in overrideWar",
                        override, overrides.get(override), bundledPlugins.get(override)));
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
            // Under no circumstances should this code ever be executed when performing a release.
            for (String goal : session.getGoals()) {
                if (goal.contains("deploy")) {
                    throw new MojoExecutionException("Cannot override dependencies when doing a release");
                }
            }

            // Create a shadow project for dependency analysis.
            MavenProject shadow = project.clone();

            // Stash the original resolution for use later.
            Map<String, String> originalResolution = new HashMap<>();
            for (Artifact artifact : shadow.getArtifacts()) {
                originalResolution.put(toKey(artifact), artifact.getVersion());
            }

            // First pass: apply the overrides specified by the user.
            applyOverrides(overrides, bundledPlugins, false, shadow, getLog());

            if (useUpperBounds) {
                boolean converged = false;
                int i = 0;
                Map<String, String> upperBounds = null;

                while (!converged) {
                    if (i++ > 10) {
                        throw new MojoExecutionException("Failed to iterate to convergence during upper bounds analysis: " + upperBounds);
                    }

                    /*
                     * Do upper bounds analysis. Upper bounds analysis consumes the model directly and
                     * not the resolution of that model, so it is fine to invoke it at this point with
                     * the model having been updated and the resolution having been cleared.
                     */
                    DependencyNode node;
                    try {
                        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                        buildingRequest.setProject(shadow);
                        buildingRequest.setRemoteRepositories(shadow.getRemoteArtifactRepositories());
                        ArtifactFilter filter = null; // Evaluate all scopes
                        node = dependencyCollectorBuilder.collectDependencyGraph(buildingRequest, filter);
                    } catch (DependencyCollectorBuilderException e) {
                        throw new MojoExecutionException("Failed to analyze dependency tree for useUpperBounds", e);
                    }
                    RequireUpperBoundDepsVisitor visitor = new RequireUpperBoundDepsVisitor();
                    node.accept(visitor);
                    String self = String.format("%s:%s", shadow.getGroupId(), shadow.getArtifactId());
                    upperBounds = visitor.upperBounds(upperBoundsExcludes, self);

                    if (upperBounds.isEmpty()) {
                        converged = true;
                    } else {
                        // Second pass: apply the results of the upper bounds analysis.

                        /*
                         * applyOverrides depends on resolution, so resolve again between the first pass
                         * and the second.
                         */
                        Set<Artifact> resolved = resolveDependencies(shadow);
                        shadow.setArtifacts(resolved);

                        applyOverrides(upperBounds, Map.of(), true, shadow, getLog());
                    }
                }
            } else if (!upperBoundsExcludes.isEmpty()) {
                throw new MojoExecutionException("Cannot provide upper bounds excludes when not using upper bounds");
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
            Map<String, String> newResolution = new HashMap<>();
            Set<Artifact> self = new HashSet<>();
            for (Artifact artifact : resolved) {
                if (artifact.getGroupId().equals(project.getGroupId()) && artifact.getArtifactId().equals(project.getArtifactId())) {
                    self.add(artifact);
                } else {
                    newResolution.put(toKey(artifact), artifact.getVersion());
                }
            }
            resolved.removeAll(self);
            effectiveArtifacts = wrap(new Artifacts(resolved));
            for (Map.Entry<String, String> entry : newResolution.entrySet()) {
                if (originalResolution.containsKey(entry.getKey())) {
                    // Present in both old and new resolution: check for update.
                    String originalVersion = originalResolution.get(entry.getKey());
                    String newVersion = entry.getValue();
                    /*
                     * We check that the new version is not equal to the original version rather
                     * than newer than the original version for the following reason. Suppose we
                     * depend on A:1.0 which depends on B:1.2. Now suppose a problem is discovered
                     * in B:1.2 that results in A:1.1 rolling back to B:1.1. We only ever directly
                     * depended on A:1.0, but now we override A:1.0 to A:1.1. B:1.2 was in our
                     * transitive tree before, but now for correctness we must change B from 1.2 to
                     * 1.1.
                     */
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
            getLog().info("After resolving, additions: " + additions);
            getLog().info("After resolving, deletions: " + deletions);
            getLog().info("After resolving, updates: " + updates);
            if (getLog().isDebugEnabled()) {
                getLog().debug("New dependency tree:");
                MavenSession shadowSession = session.clone();
                shadowSession.setCurrentProject(shadow);
                shadow.setArtifacts(resolved);
                MojoExecutor.executeMojo(
                        MojoExecutor.plugin(
                                MojoExecutor.groupId("org.apache.maven.plugins"),
                                MojoExecutor.artifactId("maven-dependency-plugin")),
                        MojoExecutor.goal("tree"),
                        MojoExecutor.configuration(
                                MojoExecutor.element(
                                        MojoExecutor.name("scope"), Artifact.SCOPE_TEST)),
                        MojoExecutor.executionEnvironment(shadow, shadowSession, pluginManager));
            }
        }

        copyTestDependencies(effectiveArtifacts);

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
            appendEntries("maven.test.additionalClasspath", additionalClasspathElements, properties);
            appendEntries("maven.test.dependency.excludes", classpathDependencyExcludes, properties);
        }
    }

    private void copyTestDependencies(Set<MavenArtifact> mavenArtifacts) throws MojoExecutionException {
        File testDir = new File(project.getBuild().getTestOutputDirectory(), "test-dependencies");
        try {
            Files.createDirectories(testDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + testDir + "'", e);
        }

        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        List<RemoteRepository> remoteRepositories = RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories());

        List<ArtifactRequest> artifactRequests = new ArrayList<>();
        boolean ignoreWorkspaceRepository = false;
        for (MavenArtifact mavenArtifact : mavenArtifacts) {
            if (!mavenArtifact.isPluginBestEffort(getLog())) {
                continue;
            }

            String artifactId;
            try {
                artifactId = mavenArtifact.getActualArtifactId();
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to resolve " + mavenArtifact.getId(), e);
            }
            if (artifactId == null) {
                getLog().debug("Skipping null artifactID with classifier " + mavenArtifact.getClassifier());
                continue;
            }

            // Use the descriptor to respect relocations.
            ArtifactDescriptorRequest descriptorRequest;
            try {
                descriptorRequest = new ArtifactDescriptorRequest(RepositoryUtils.toArtifact(mavenArtifact.getHpi().artifact), remoteRepositories, null);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to resolve " + mavenArtifact.getId(), e);
            }
            ArtifactDescriptorResult descriptorResult;
            try {
                descriptorResult = repositorySystem.readArtifactDescriptor(buildingRequest.getRepositorySession(), descriptorRequest);
            } catch (ArtifactDescriptorException e) {
                throw new MojoExecutionException("Failed to read artifact descriptor for " + mavenArtifact.getId(), e);
            }
            ArtifactRequest artifactRequest = new ArtifactRequest(descriptorResult.getArtifact(), remoteRepositories, null);
            artifactRequests.add(artifactRequest);
        }

        List<ArtifactResult> artifactResults;
        try {
            artifactResults = repositorySystem.resolveArtifacts(buildingRequest.getRepositorySession(), artifactRequests);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve artifacts: " + artifactRequests.stream().map(ArtifactRequest::getArtifact).map(Object::toString).collect(Collectors.joining(", ")), e);
        }

        /*
         * If the result is a directory rather than a file, we must be in a multi-module
         * project where one plugin depends on another plugin in the same multi-module
         * project. Try again without the workspace reader to force Maven to look for
         * released artifacts rather than in the target/ directory of another module.
         */
        artifactRequests = new ArrayList<>();
        Map<String, Artifact> artifactMap = new LinkedHashMap<>();
        for (ArtifactResult artifactResult : artifactResults) {
            Artifact artifact = RepositoryUtils.toArtifact(artifactResult.getArtifact());
            artifactMap.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), artifact);
            if (artifact.getFile().isDirectory()) {
                ArtifactRequest artifactRequest = new ArtifactRequest(RepositoryUtils.toArtifact(artifact), remoteRepositories, null);
                artifactRequests.add(artifactRequest);
            }
        }
        if (!artifactRequests.isEmpty() && buildingRequest.getRepositorySession() instanceof DefaultRepositorySystemSession) {
            DefaultRepositorySystemSession oldRepositorySession = (DefaultRepositorySystemSession) buildingRequest.getRepositorySession();
            DefaultRepositorySystemSession newRepositorySession = new DefaultRepositorySystemSession(oldRepositorySession);
            newRepositorySession.setWorkspaceReader(null);
            newRepositorySession.setReadOnly();
            try {
                artifactResults = repositorySystem.resolveArtifacts(newRepositorySession, artifactRequests);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException("Failed to resolve artifacts: " + artifactRequests.stream().map(ArtifactRequest::getArtifact).map(Object::toString).collect(Collectors.joining(", ")), e);
            }
            for (ArtifactResult artifactResult : artifactResults) {
                Artifact artifact = RepositoryUtils.toArtifact(artifactResult.getArtifact());
                artifactMap.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), artifact);
            }
        }

        try (BufferedWriter w = Files.newBufferedWriter(testDir.toPath().resolve("index"), StandardCharsets.UTF_8)) {
            for (Artifact artifact : artifactMap.values()) {
                getLog().debug("Copying " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + " as a test dependency");
                File dst = new File(testDir, artifact.getArtifactId() + ".hpi");
                FileUtils.copyFile(artifact.getFile(), dst);
                w.write(artifact.getArtifactId());
                w.newLine();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy dependency plugins", e);
        }
    }

    private static void appendEntries(String property, Collection<String> additions, Properties properties) {
        String existing = properties.getProperty(property);
        List<String> newEntries = new ArrayList<>();
        if (existing != null && !existing.isEmpty()) {
            for (String entry : existing.split(",")) {
                entry = entry.trim();
                if (!entry.isEmpty()) {
                    newEntries.add(entry);
                }
            }
        }
        newEntries.addAll(additions);
        properties.setProperty(property, String.join(",", newEntries));
    }

    /**
     * Scan a WAR file, accumulating plugin information.
     *
     * @param war The WAR to scan.
     * @return The bundled plugins in the WAR.
     */
    private static Map<String, String> scanWar(File war, MavenSession session, MavenProject project) throws MojoExecutionException {
        Map<String, String> overrides = new HashMap<>();
        try (JarFile jf = new JarFile(war)) {
            Enumeration<JarEntry> entries = jf.entries();
            String coreVersion = null;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                Matcher m = CORE_REGEX.matcher(name);
                if (m.matches()) {
                    if (coreVersion != null) {
                        throw new MojoExecutionException("More than 1 jenkins-core JAR in " + war);
                    }
                    coreVersion = m.group(1);
                }
                m = PLUGIN_REGEX.matcher(name);
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
            Matcher m = OVERRIDE_REGEX.matcher(override);
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
            boolean upperBounds,
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
                if (dependency.getGroupId().equals(project.getGroupId()) && dependency.getArtifactId().equals(project.getArtifactId())) {
                    throw new MojoExecutionException("Cannot add self to dependency management section");
                }
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
            if (upperBounds) {
                /*
                 * An upper bounds override that could not be found in the transitive tree is most likely a
                 * provided transitive dependency of a test-scoped dependency. We could ignore these, but
                 * we add them to the dependency management section just to be safe.
                 */
                for (String key : unappliedOverrides) {
                    String[] groupArt = key.split(":");
                    Dependency dependency = new Dependency();
                    dependency.setGroupId(groupArt[0]);
                    dependency.setArtifactId(groupArt[1]);
                    dependency.setVersion(overrides.get(key));
                    if (dependency.getGroupId().equals(project.getGroupId()) && dependency.getArtifactId().equals(project.getArtifactId())) {
                        throw new MojoExecutionException("Cannot add self to dependency management section");
                    }
                    DependencyManagement dm = project.getDependencyManagement();
                    if (dm == null) {
                        dm = new DependencyManagement();
                        project.getModel().setDependencyManagement(dm);
                    }
                    log.info(String.format("Adding dependency management entry %s:%s", key, dependency.getVersion()));
                    dm.addDependency(dependency);
                    overrideAdditions.add(key);
                }
            } else {
                throw new MojoExecutionException("Failed to apply the following overrides: " + unappliedOverrides);
            }
        }

        /*
         * If a bundled plugin was added that is neither in the model nor the transitive dependency
         * chain, add a dependency management entry to the model. This is necessary in order for us
         * to be able to correctly populate target/test-dependencies/ later on.
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
            if (dependency.getGroupId().equals(project.getGroupId()) && dependency.getArtifactId().equals(project.getArtifactId())) {
                throw new MojoExecutionException("Cannot add self as dependency management entry");
            }
            log.info(String.format("Adding dependency management entry %s:%s", key, version));
            project.getDependencyManagement().getDependencies().add(dependency);
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
            String classifier = dependency.getClassifier();
            log.info(String.format("Updating %s %s%s from %s to %s", type, key, classifier != null ? ":" + classifier : "", dependency.getVersion(), overrideVersion));
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
                        List.of(project.getArtifact().getId()),
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
            List<DependencyNodeHopCountPair> pairs = keyToPairsMap.computeIfAbsent(key, unused -> new ArrayList<>());
            pairs.add(pair);
            Collections.sort(pairs);
            return true;
        }

        public boolean endVisit(DependencyNode node) {
            return true;
        }

        // added for TestDependencyMojo in place of getConflicts/containsConflicts
        public Map<String, String> upperBounds(List<String> upperBoundsExcludes, String self) {
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
                        if (!key.equals(self) && (!r.containsKey(key) || new ComparableVersion(version.toString()).compareTo(new ComparableVersion(r.get(key))) > 1)) {
                            if (upperBoundsExcludes.contains(key)) {
                                getLog().info( "Ignoring requireUpperBoundDeps in " + key);
                            } else {
                                getLog().info(buildErrorMessage(pairs.stream().map(DependencyNodeHopCountPair::getNode).collect(Collectors.toList())).trim());
                                getLog().info(String.format("for %s, upper bounds forces an upgrade from %s to %s", key, resolvedVersion, version));
                                r.put(key, version.toString());
                            }
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

    private static String buildErrorMessage(List<DependencyNode> conflict) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append(
                "Require upper bound dependencies error for "
                        + getFullArtifactName(conflict.get(0), false)
                        + " paths to dependency are:"
                        + System.lineSeparator());
        if (conflict.size() > 0) {
            errorMessage.append(buildTreeString(conflict.get(0)));
        }
        for (DependencyNode node : conflict.subList(1, conflict.size())) {
            errorMessage.append("and" + System.lineSeparator());
            errorMessage.append(buildTreeString(node));
        }
        return errorMessage.toString();
    }

    private static StringBuilder buildTreeString(DependencyNode node) {
        List<String> loc = new ArrayList<>();
        DependencyNode currentNode = node;
        while (currentNode != null) {
            StringBuilder line = new StringBuilder(getFullArtifactName(currentNode, false));

            if (currentNode.getPremanagedVersion() != null) {
                line.append(" (managed) <-- ");
                line.append(getFullArtifactName(currentNode, true));
            }

            loc.add(line.toString());
            currentNode = currentNode.getParent();
        }
        Collections.reverse(loc);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < loc.size(); i++) {
            builder.append("  ".repeat(i));
            builder.append("+-").append(loc.get(i));
            builder.append(System.lineSeparator());
        }
        return builder;
    }

    private static String getFullArtifactName(DependencyNode node, boolean usePremanaged) {
        Artifact artifact = node.getArtifact();

        String version = node.getPremanagedVersion();
        if (!usePremanaged || version == null) {
            version = artifact.getBaseVersion();
        }
        String result = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + version;

        String classifier = artifact.getClassifier();
        if (classifier != null && !classifier.isEmpty()) {
            result += ":" + classifier;
        }

        String scope = artifact.getScope();
        if (scope != null) {
            result += " [" + scope + ']';
        }

        return result;
    }

    private static String toKey(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    private static String toKey(Dependency dependency) {
        return dependency.getGroupId() + ":" + dependency.getArtifactId();
    }
}
