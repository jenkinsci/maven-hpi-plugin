package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Make sure that we are running in the right environment.
 *
 * @author Kohsuke Kawaguchi
 * @goal validate
 * @phase validate
 */
public class ValidateMojo extends AbstractJenkinsMojo {
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!isMustangOrAbove())
            throw new MojoExecutionException("JDK6 or later is necessary to build a Jenkins plugin");

        if (new VersionNumber(findJenkinsVersion()).compareTo(new VersionNumber("1.419.*"))<=0)
            throw new MojoExecutionException("This version of maven-hpi-plugin requires Jenkins 1.420 or later");
    }

    /**
     * Are we running on JDK6 or above?
     */
    private static boolean isMustangOrAbove() {
        try {
            Class.forName("javax.annotation.processing.Processor");
            return true;
        } catch(ClassNotFoundException e) {
            return false;
        }
    }
}
