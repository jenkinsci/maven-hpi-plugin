package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Places test-dependency plugins into somewhere the test harness can pick up.
 *
 * <p>
 * See {@code TestPluginManager.loadBundledPlugins()} where the test harness uses it.
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
        System.err.println("TODO overrideVersions=" + overrideVersions + " useUpperBounds=" + useUpperBounds);
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
                // TODO adjust according to revised version
                FileUtils.copyFile(a.getHpi().getFile(),dst);
                w.write(artifactId + "\n");
            }

            w.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy dependency plugins",e);
        }
    }
}
