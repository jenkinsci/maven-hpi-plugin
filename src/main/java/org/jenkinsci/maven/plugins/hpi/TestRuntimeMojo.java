package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

/**
 * Configure Surefire for the desired version of Java.
 *
 * @author Basil Crow
 */
@Mojo(name = "test-runtime", requiresDependencyResolution = ResolutionScope.TEST)
public class TestRuntimeMojo extends AbstractJenkinsMojo {

    /**
     * Set to {@code true} when we are compiling the tests but not running them.
     */
    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    /**
     * Set to {@code true} when we are neither compiling nor running the tests.
     */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Directory where unpacked patch modules should be cached.
     */
    @Parameter(defaultValue = "${project.build.directory}/patch-modules")
    private File patchModuleDir;

    @Override
    public void execute() throws MojoExecutionException {
        if (skipTests || skip) {
            getLog().info("Tests are skipped.");
            return;
        }
        setAddOpensProperty();
        setInsaneHookProperty();
    }

    private void setAddOpensProperty() throws MojoExecutionException {
        String manifestEntry = getManifestEntry(wrap(resolveJenkinsWar()));
        if (manifestEntry == null) {
            getLog().warn("Add-Opens missing from MANIFEST.MF");
            return;
        }

        String argLine = buildArgLine(manifestEntry);
        getLog().info("Setting jenkins.addOpens to " + argLine);
        project.getProperties().setProperty("jenkins.addOpens", argLine);
    }

    @NonNull
    private Artifact resolveJenkinsWar() throws MojoExecutionException {
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        artifactCoordinate.setGroupId("org.jenkins-ci.main");
        artifactCoordinate.setArtifactId("jenkins-war");
        artifactCoordinate.setVersion(findJenkinsVersion());
        artifactCoordinate.setExtension("war");

        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
            return artifactResolver
                    .resolveArtifact(buildingRequest, artifactCoordinate)
                    .getArtifact();
        } catch (ArtifactResolverException e) {
            throw new MojoExecutionException("Couldn't download artifact: ", e);
        }
    }

    @CheckForNull
    private static String getManifestEntry(MavenArtifact artifact) throws MojoExecutionException {
        File war = artifact.getFile();
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

    @NonNull
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

    private void setInsaneHookProperty() throws MojoExecutionException {
        Artifact insane = project.getArtifactMap().get("org.netbeans.modules:org-netbeans-insane");
        if (insane == null || Integer.parseInt(insane.getVersion().substring("RELEASE".length())) < 130) {
            // older versions of insane do not need a hook
            return;
        }

        Artifact jth = project.getArtifactMap().get("org.jenkins-ci.main:jenkins-test-harness");
        if (jth == null) {
            return;
        }

        Path insaneHook = getInsaneHook(wrap(jth), patchModuleDir.toPath());

        String argLine = String.format(
                "--patch-module='java.base=%s' --add-exports=java.base/org.netbeans.insane.hook=ALL-UNNAMED",
                insaneHook);
        getLog().info("Setting jenkins.insaneHook to " + argLine);
        project.getProperties().setProperty("jenkins.insaneHook", argLine);
    }

    @NonNull
    private static Path getInsaneHook(MavenArtifact artifact, Path patchModuleDir) throws MojoExecutionException {
        File jar = artifact.getFile();
        try (JarFile jarFile = new JarFile(jar)) {
            ZipEntry entry = jarFile.getEntry("netbeans/harness/modules/ext/org-netbeans-insane-hook.jar");
            if (entry == null) {
                throw new MojoExecutionException("Failed to find org-netbeans-insane-hook.jar in " + jar);
            }
            Files.createDirectories(patchModuleDir);
            Path insaneHook = patchModuleDir.resolve("org-netbeans-insane-hook.jar");
            try (InputStream is = jarFile.getInputStream(entry)) {
                Files.copy(is, insaneHook, StandardCopyOption.REPLACE_EXISTING);
            }
            return insaneHook.toAbsolutePath();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read org-netbeans-insane-hook.jar from " + jar, e);
        }
    }
}
