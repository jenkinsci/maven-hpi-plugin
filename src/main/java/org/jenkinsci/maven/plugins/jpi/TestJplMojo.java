package org.jenkinsci.maven.plugins.jpi;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;

/**
 * Generate .jpl file in the test class directory so that test harness can locate the plugin.
 *
 * @goal test-jpl
 * @requiresDependencyResolution test
 * @author Kohsuke Kawaguchi
 */
public class TestJplMojo extends JplMojo {
    /**
     * Generates the jpl file in a known location.
     */
    @Override
    protected File computeJplFile() throws MojoExecutionException {
        File testDir = new File(project.getBuild().getTestOutputDirectory());
        testDir.mkdirs();
        return new File(testDir,"the.jpl");
    }
}
