package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import javax.lang.model.SourceVersion;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Insert default test suite.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(
        name = "insert-test",
        defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
        requiresDependencyResolution = ResolutionScope.TEST)
public class TestInsertionMojo extends AbstractJenkinsMojo {

    /**
     * If true, the automatic test injection will be skipped.
     */
    @Parameter(property = "maven-hpi-plugin.disabledTestInjection", defaultValue = "false")
    private boolean disabledTestInjection;

    /**
     * Package name of the injected test.
     * <p>
     * This is used to determine the package name of the generated test.
     * <p>
     * For compatibility with Java package names,
     * <ul>
     * <li><code>"-"</code> gets replaced by <code>"_"</code>
     * <li>If starting with a digit, a <code>"_"</code> is prepended.
     */
    @Parameter(
            property = "maven-hpi-plugin.injectedTestPackage",
            defaultValue = "${project.groupId}.${project.artifactId}")
    private String injectedTestPackage;

    /**
     * Name of the injected test.
     *
     * You may change this to "InjectIT" to get the test running during phase integration-test.
     */
    @Parameter(property = "maven-hpi-plugin.injectedTestName", defaultValue = "InjectedTest")
    private String injectedTestName;

    /**
     * If true, verify that all the jelly scripts have the Jelly XSS PI in them.
     */
    @Parameter(property = "jelly.requirePI", defaultValue = "true")
    private boolean requirePI;

    /**
     * Optional string that represents "groupId:artifactId" of the Jenkins test harness.
     * If left unspecified, the default groupId/artifactId pair for the Jenkins test harness is looked for.
     *
     * @since 1.119
     */
    @Parameter(defaultValue = "org.jenkins-ci.main:jenkins-test-harness")
    protected String jenkinsTestHarnessId;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (disabledTestInjection) {
            getLog().info("Skipping auto-test generation");
            return;
        }

        String target = findJenkinsVersion();
        if (new VersionNumber(target).compareTo(new VersionNumber("1.327")) < 0) {
            getLog().info("Skipping auto-test generation because we are targeting Jenkins " + target
                    + " (at least 1.327 is required).");
            return;
        }

        if (!project.getPackaging().equals("hpi")) {
            Artifact jenkinsTestHarness = null;
            if (jenkinsTestHarnessId != null) {
                for (Artifact b : project.getTestArtifacts()) {
                    if (jenkinsTestHarnessId.equals(b.getGroupId() + ":" + b.getArtifactId())) {
                        jenkinsTestHarness = b;
                        break;
                    }
                }
            }
            if (jenkinsTestHarness != null) {
                try {
                    ArtifactVersion version = jenkinsTestHarness.getSelectedVersion();
                    if (version == null || version.compareTo(new DefaultArtifactVersion("2.14")) < 0) {
                        getLog().info("Skipping " + project.getName()
                                + " because it's not <packaging>hpi</packaging> and the " + jenkinsTestHarnessId
                                + ", " + version + ", is less than 2.14");
                        return;
                    }
                } catch (OverConstrainedVersionException e) {
                    throw new MojoFailureException(
                            "Build should be failed before we get here if there is an over-constrained version", e);
                }
            } else {
                getLog().info("Skipping " + project.getName()
                        + " because it's not <packaging>hpi</packaging> and we could not determine the version of "
                        + jenkinsTestHarnessId + " used by this project");
                return;
            }
        }

        File f = new File(project.getBasedir(), "target/generated-test-sources/injected");
        try {
            Files.createDirectories(f.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create injected test directory", e);
        }

        String packageName = legalizePackageName(injectedTestPackage);
        File javaFile =
                new File(f, packageName.replace(".", File.separator) + File.separator + injectedTestName + ".java");
        var parentFile = javaFile.getParentFile();
        if (!parentFile.mkdirs()) {
            getLog().debug(parentFile + " already existed.");
        }

        try {
            String content =
                    isJUnitJupiterSupported() ? getJUnitJupiterSuite(packageName) : getJUnitTestCase(packageName);
            Files.writeString(javaFile.toPath(), content);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create injected tests", e);
        }

        project.addTestCompileSourceRoot(f.getAbsolutePath());

        try {
            // always set the same time stamp on this file, so that Maven will not re-compile this
            // every time we run this mojo.
            Files.setLastModifiedTime(javaFile.toPath(), FileTime.fromMillis(0L));
        } catch (IOException e) {
            // Ignore, as this is an optimization for performance rather than correctness.
            getLog().warn("Failed to clear last modified time on " + javaFile, e);
        }
    }

    private boolean isJUnitJupiterSupported() {
        Artifact jenkinsTestHarness = null;
        if (jenkinsTestHarnessId != null) {
            for (Artifact b : project.getTestArtifacts()) {
                if (jenkinsTestHarnessId.equals(b.getGroupId() + ":" + b.getArtifactId())) {
                    jenkinsTestHarness = b;
                    break;
                }
            }
        }
        if (jenkinsTestHarness != null) {
            try {
                ArtifactVersion version = jenkinsTestHarness.getSelectedVersion();
                return version != null && version.compareTo(new DefaultArtifactVersion("2533.vb_b_4e14045e01")) > 0;
            } catch (OverConstrainedVersionException e) {
                // ignore
            }
        }
        return false;
    }

    @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
    private String getJUnitTestCase(String packageName) {
        return """
            package %s;

            import java.util.*;

            /**
             * Entry point to auto-generated tests (generated by maven-hpi-plugin).
             * If this fails to compile, you are probably using Hudson &lt; 1.327. If so, disable
             * this code generation by configuring maven-hpi-plugin to &lt;disabledTestInjection&gt;true&lt;/disabledTestInjection&gt;.
             */
            public class %s extends junit.framework.TestCase {
              public static junit.framework.Test suite() throws Exception {
                System.out.println("Running tests for %s:%s:%s");
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("basedir", "%s");
                parameters.put("artifactId", "%s");
                parameters.put("packaging", "%s");
                parameters.put("outputDirectory", "%s");
                parameters.put("testOutputDirectory", "%s");
                parameters.put("requirePI", "%s");
                return org.jvnet.hudson.test.PluginAutomaticTestBuilder.build(parameters);
              }
            }
            """.formatted(
                        packageName,
                        injectedTestName,
                        project.getGroupId(),
                        project.getArtifactId(),
                        project.getVersion(),
                        escape(project.getBasedir().getAbsolutePath()),
                        project.getArtifactId(),
                        project.getPackaging(),
                        escape(project.getBuild().getOutputDirectory()),
                        escape(project.getBuild().getTestOutputDirectory()),
                        String.valueOf(requirePI));
    }

    @SuppressFBWarnings("VA_FORMAT_STRING_USES_NEWLINE")
    private String getJUnitJupiterSuite(String packageName) {
        return """
            package %s;

            import org.junit.platform.suite.api.ConfigurationParameter;
            import org.junit.platform.suite.api.SelectPackages;
            import org.junit.platform.suite.api.Suite;

            /**
             * Entry point for auto-generated tests (generated by maven-hpi-plugin).
             * You can disable the code generation by configuring maven-hpi-plugin to &lt;disabledTestInjection&gt;true&lt;/disabledTestInjection&gt;.
             */
            @Suite
            @ConfigurationParameter(key = "InjectedTest.basedir", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.groupId", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.artifactId", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.version", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.packaging", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.outputDirectory", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.testOutputDirectory", value = "%s")
            @ConfigurationParameter(key = "InjectedTest.requirePI", value = "%s")
            @SelectPackages("org.jvnet.hudson.test.injected")
            class %s {

            }
            """.formatted(
                        packageName,
                        escape(project.getBasedir().getAbsolutePath()),
                        project.getGroupId(),
                        project.getArtifactId(),
                        project.getVersion(),
                        project.getPackaging(),
                        escape(project.getBuild().getOutputDirectory()),
                        escape(project.getBuild().getTestOutputDirectory()),
                        String.valueOf(requirePI),
                        injectedTestName);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\");
    }

    static String legalizePackageName(@NonNull String input) throws MojoFailureException {
        String result = input.replace('-', '_');
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "_" + result;
        }
        if (!SourceVersion.isName(result)) {
            throw new MojoFailureException(
                    "Could not convert " + input
                            + " to a legal java package name. Please override \"injectedTestPackage\" with a valid java package name.");
        }
        return result;
    }
}
