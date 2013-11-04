package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Generate .hpl file in the test class directory so that test harness can locate the plugin.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="test-hpl", requiresDependencyResolution = ResolutionScope.TEST)
public class TestHplMojo extends HplMojo {
    /**
     * Generates the hpl file in a known location.
     */
    @Override
    protected File computeHplFile() throws MojoExecutionException {
        File testDir = new File(project.getBuild().getTestOutputDirectory());
        testDir.mkdirs();
        return new File(testDir,"the.hpl");
    }
}
