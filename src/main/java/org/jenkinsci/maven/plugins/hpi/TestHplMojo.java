package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.IOException;

/**
 * Generate .hpl file in the test class directory so that test harness can locate the plugin.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="test-hpl", requiresDependencyResolution = ResolutionScope.TEST)
public class TestHplMojo extends HplMojo {

    @Component
    protected PluginWorkspaceMap pluginWorkspaceMap;

    /**
     * Generates the hpl file in a known location.
     */
    @Override
    protected File computeHplFile() throws MojoExecutionException {
        File testDir = new File(project.getBuild().getTestOutputDirectory());
        testDir.mkdirs();
        File theHpl = new File(testDir,"the.hpl");
        if (project.getArtifact().isSnapshot()) {
            try {
                pluginWorkspaceMap.write(project.getArtifact().getId(), theHpl);
            } catch (IOException x) {
                getLog().error(x);
            }
        }
        return theHpl;
    }
}
