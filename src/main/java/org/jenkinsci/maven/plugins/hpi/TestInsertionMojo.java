package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\") + '"';
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
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
        try (PrintWriter w =
                new PrintWriter(new OutputStreamWriter(new FileOutputStream(javaFile), StandardCharsets.UTF_8)); ) {
            w.println("package " + packageName + ";");
            w.println("");
            w.println("import java.util.*;");
            w.println("");
            w.println("/**");
            w.println(" * Entry point to auto-generated tests (generated by maven-hpi-plugin).");
            w.println(
                    " * This code generation by configuring maven-hpi-plugin to &lt;disabledTestInjection&gt;true&lt;/disabledTestInjection&gt;.");
            w.println(" */");
            w.println("class " + injectedTestName + " {");
            w.println("  @org.junit.jupiter.api.TestFactory");
            w.println("  public List<org.junit.jupiter.api.DynamicNode> autoGeneratedTests() throws Exception {");
            w.println("    System.out.println(\"Running tests for \"+"
                    + quote(project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion()) + ");");
            w.println("    Map<String, Object> parameters = new HashMap<String, Object>();");
            w.println("    parameters.put(\"basedir\","
                    + quote(project.getBasedir().getAbsolutePath()) + ");");
            w.println("    parameters.put(\"artifactId\"," + quote(project.getArtifactId()) + ");");
            w.println("    parameters.put(\"packaging\"," + quote(project.getPackaging()) + ");");
            w.println("    parameters.put(\"outputDirectory\","
                    + quote(project.getBuild().getOutputDirectory()) + ");");
            w.println("    parameters.put(\"testOutputDirectory\","
                    + quote(project.getBuild().getTestOutputDirectory()) + ");");
            w.println("    parameters.put(\"requirePI\"," + quote(String.valueOf(requirePI)) + ");");
            w.println("    return org.jvnet.hudson.test.PluginAutomaticTestBuilder.build(parameters);");
            w.println("  }");
            w.println("}");
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
