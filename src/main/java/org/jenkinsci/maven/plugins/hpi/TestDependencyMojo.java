package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugins.annotations.Parameter;

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
                throw new MojoExecutionException("TODO useUpperBounds not yet supported");
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

}
