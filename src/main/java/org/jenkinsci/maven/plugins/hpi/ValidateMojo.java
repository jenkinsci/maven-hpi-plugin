package org.jenkinsci.maven.plugins.hpi;

import com.google.common.collect.Sets;
import hudson.util.VersionNumber;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * Make sure that we are running in the right environment.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class ValidateMojo extends AbstractHpiMojo {
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!isMustangOrAbove()) {
            throw new MojoExecutionException("JDK6 or later is necessary to build a Jenkins plugin");
        }
        if (new VersionNumber(findJenkinsVersion()).compareTo(new VersionNumber("1.419.99"))<=0) {
            throw new MojoExecutionException("This version of maven-hpi-plugin requires Jenkins 1.420 or later");
        }
        validateCoreVersion();
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

    private void validateCoreVersion() throws MojoExecutionException {
        try {
            VersionNumber coreVersion = new VersionNumber(findJenkinsVersion());
            MavenArtifact maxCoreVersionArtifact = null;
            VersionNumber maxCoreVersion = new VersionNumber("0");

            for (MavenArtifact artifact : Sets.union(getProjectArtfacts(), getDirectDependencyArtfacts())) {
                if (artifact.isPluginBestEffort(getLog())) {
                    VersionNumber dependencyCoreVersion = getDependencyCoreVersion(artifact);
                    if (dependencyCoreVersion.compareTo(maxCoreVersion) > 0) {
                        maxCoreVersionArtifact = artifact;
                        maxCoreVersion = dependencyCoreVersion;
                    }
                }
            }
            if (coreVersion.compareTo(maxCoreVersion) < 0) {
                throw new MojoExecutionException("Dependency " + maxCoreVersionArtifact + " requires Jenkins " + maxCoreVersion + " or higher.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to retrieve manifest", e);
        }
    }

    private VersionNumber getDependencyCoreVersion(MavenArtifact artifact) throws IOException, MojoExecutionException {
        Attributes mainAttributes = new JarFile(artifact.getFile()).getManifest().getMainAttributes();
        Attributes.Name jName = new Attributes.Name("Jenkins-Version");
        if (mainAttributes.containsKey(jName)) {
            return new VersionNumber(mainAttributes.getValue(jName));
        } else {
            Attributes.Name hName = new Attributes.Name("Hudson-Version");
            if (mainAttributes.containsKey(hName)) {
                return new VersionNumber(mainAttributes.getValue(hName));
            } else {
                throw new MojoExecutionException("Could not find Jenkins Version in manifest for " + artifact);
            }
        }
    }
}
