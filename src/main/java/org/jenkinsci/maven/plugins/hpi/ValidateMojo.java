package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Make sure that we are running in the right environment.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateMojo extends AbstractJenkinsMojo {
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!isMustangOrAbove())
            throw new MojoExecutionException("JDK6 or later is necessary to build a Jenkins plugin");

        if (new VersionNumber(findJenkinsVersion()).compareTo(new VersionNumber("1.419.99"))<=0)
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
