package org.jenkinsci.maven.plugins.jpi;

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

        final String jenkinsVersion = findJenkinsVersion();
		if (new VersionNumber(jenkinsVersion).compareTo(new VersionNumber("1.443.99"))<=0)
            throw new MojoExecutionException("This version of maven-jpi-plugin requires Jenkins 1.444 or later (current used: "+jenkinsVersion+")");
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
