package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

/**
 * Configure Maven for the desired version of Java.
 *
 * @author Basil Crow
 */
@Mojo(name = "initialize", defaultPhase = LifecyclePhase.INITIALIZE)
public class InitializeMojo extends AbstractJenkinsMojo {

    @Override
    public void execute() throws MojoExecutionException {
        setSurefireProperties();
    }

    private void setSurefireProperties() throws MojoExecutionException {
        if (JavaSpecificationVersion.forCurrentJVM().isOlderThan(new JavaSpecificationVersion("9"))) {
            // nothing to do
            return;
        }

        String manifestEntry = getManifestEntry();
        if (manifestEntry == null) {
            // core older than 2.339, ignore
            return;
        }

        String argLine = buildArgLine(manifestEntry);
        getLog().info("Setting jenkins.addOpens to " + argLine);
        project.getProperties().setProperty("jenkins.addOpens", argLine);
    }

    @CheckForNull
    private String getManifestEntry() throws MojoExecutionException {
        Artifact artifact = resolveJenkinsWar();
        File war = wrap(artifact).getFile();
        try (JarFile jarFile = new JarFile(war)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new MojoExecutionException("No manifest found in " + war);
            }
            return manifest.getMainAttributes().getValue("Add-Opens");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read MANIFEST.MF from " + war, e);
        }
    }

    private Artifact resolveJenkinsWar() throws MojoExecutionException {
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId("org.jenkins-ci.main");
        artifactCoordinate.setArtifactId("jenkins-war");
        artifactCoordinate.setVersion(findJenkinsVersion());
        artifactCoordinate.setExtension("war");

        try {
            return artifactResolver
                    .resolveArtifact(session.getProjectBuildingRequest(), artifactCoordinate)
                    .getArtifact();
        } catch (ArtifactResolverException e) {
            throw new MojoExecutionException("Couldn't download artifact: ", e);
        }
    }

    private static String buildArgLine(String manifestEntry) {
        List<String> arguments = new ArrayList<>();
        for (String module : manifestEntry.split("\\s+")) {
            if (!module.isEmpty()) {
                arguments.add("--add-opens");
                arguments.add(module + "=ALL-UNNAMED");
            }
        }
        return String.join(" ", arguments);
    }
}
