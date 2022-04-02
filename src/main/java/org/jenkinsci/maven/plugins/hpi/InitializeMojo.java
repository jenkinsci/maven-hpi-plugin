package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Configure Maven for the desired version of Java.
 *
 * @author Basil Crow
 */
@Mojo(name = "initialize", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitializeMojo extends AbstractJenkinsMojo {

    @Override
    public void execute() throws MojoExecutionException {
        setCompilerProperties();
    }

    private void setCompilerProperties() throws MojoExecutionException {
        if (!project.getProperties().containsKey("maven.compiler.source")
                && !project.getProperties().containsKey("maven.compiler.release")) {
            // On an older plugin parent POM that predates the setting of these values as Maven properties.
            return;
        }

        JavaSpecificationVersion javaVersion = getMinimumJavaVersion();
        if (JavaSpecificationVersion.forCurrentJVM().isOlderThan(new VersionNumber("9"))) {
            // Should always be set already, but just in case...
            setProperty("maven.compiler.source", javaVersion.toString());
            setProperty("maven.compiler.target", javaVersion.toString());
            setProperty("maven.compiler.testSource", javaVersion.toString());
            setProperty("maven.compiler.testTarget", javaVersion.toString());
            // Should never be set already, but just in case...
            unsetProperty("maven.compiler.release");
            unsetProperty("maven.compiler.testRelease");
        } else {
            /*
             * When compiling with a Java 9+ compiler, we always rely on "release" in favor of "source" and "target",
             * even when compiling to Java 8 bytecode.
             */
            setProperty("maven.compiler.release", Integer.toString(javaVersion.toReleaseVersion()));
            setProperty("maven.compiler.testRelease", Integer.toString(javaVersion.toReleaseVersion()));

            // "release" serves the same purpose as Animal Sniffer.
            setProperty("animal.sniffer.skip", "true");

            /*
             * While it does not hurt to have these set to the Java specification version, it is also not needed when
             * "release" is in use.
             */
            unsetProperty("maven.compiler.source");
            unsetProperty("maven.compiler.target");
            unsetProperty("maven.compiler.testSource");
            unsetProperty("maven.compiler.testTarget");
        }
    }

    private void setProperty(String key, String value) {
        String currentValue = project.getProperties().getProperty(key);
        if (currentValue == null || !currentValue.equals(value)) {
            getLog().info("Setting " + key + " to " + value);
            project.getProperties().setProperty(key, value);
        }
    }

    private void unsetProperty(String key) {
        String currentValue = project.getProperties().getProperty(key);
        if (currentValue != null && !currentValue.isEmpty()) {
            getLog().info("Unsetting " + key);
            project.getProperties().remove(key);
        }
    }
}
