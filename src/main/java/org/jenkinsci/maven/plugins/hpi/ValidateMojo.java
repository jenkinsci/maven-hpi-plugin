package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import org.apache.maven.plugin.MojoExecutionException;
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
    public void execute() throws MojoExecutionException {
        JavaSpecificationVersion javaVersion = getMinimumJavaVersion();
        if (JavaSpecificationVersion.forCurrentJVM().isOlderThan(javaVersion)) {
            throw new MojoExecutionException("Java " + javaVersion + " or later is necessary to build this plugin.");
        }

        if (new VersionNumber(findJenkinsVersion()).compareTo(new VersionNumber("2.204")) < 0) {
            throw new MojoExecutionException("This version of maven-hpi-plugin requires Jenkins 2.204 or later");
        }
    }
}
