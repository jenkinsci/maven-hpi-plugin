package org.jenkinsci.maven.plugins.hpi;

import com.google.common.io.ByteStreams;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

/**
 * Configure Maven for the desired version of Java.
 *
 * @author Basil Crow
 */
@Mojo(name = "initialize", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.INITIALIZE)
public class InitializeMojo extends AbstractJenkinsMojo {

    @Override
    public void execute() throws MojoExecutionException {
        setCompilerProperties();
        setAddOpensProperty();
        setInsaneHookProperty();
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
        if (project.getProperties().containsKey(key)) {
            getLog().info("Unsetting " + key);
            project.getProperties().remove(key);
        }
    }

    private void setAddOpensProperty() throws MojoExecutionException {
        if (JavaSpecificationVersion.forCurrentJVM().isOlderThan(new JavaSpecificationVersion("9"))) {
            // nothing to do prior to JEP 261
            return;
        }

        String manifestEntry = getManifestEntry(wrap(resolveJenkinsWar()));
        if (manifestEntry == null) {
            // core older than 2.339, ignore
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
            return artifactResolver
                    .resolveArtifact(session.getProjectBuildingRequest(), artifactCoordinate)
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
            throw new MojoExecutionException("Failed to find the Jenkins test harness on the test classpath");
        }

        Path insaneHook = getInsaneHook(wrap(jth));

        String argLine;
        if (JavaSpecificationVersion.forCurrentJVM().isNewerThanOrEqualTo(new JavaSpecificationVersion("9"))) {
            argLine = String.format("--patch-module=java.base=%s --add-exports=java.base/org.netbeans.insane.hook=ALL-UNNAMED", insaneHook);
        } else {
            argLine = String.format("-Xbootclasspath/p:%s", insaneHook);
        }
        getLog().info("Setting jenkins.insaneHook to " + argLine);
        project.getProperties().setProperty("jenkins.insaneHook", argLine);
    }

    @NonNull
    private static Path getInsaneHook(MavenArtifact artifact) throws MojoExecutionException {
        File jar = artifact.getFile();
        try (JarFile jarFile = new JarFile(jar)) {
            ZipEntry entry = jarFile.getEntry("netbeans/harness/modules/ext/org-netbeans-insane-hook.jar");
            if (entry == null) {
                throw new MojoExecutionException("Failed to find org-netbeans-insane-hook.jar in " + jar);
            }
            Path tempFile = Files.createTempFile("org-netbeans-insane-hook", ".jar");
            try (InputStream is = jarFile.getInputStream(entry); OutputStream os = Files.newOutputStream(tempFile)) {
                ByteStreams.copy(is, os);
            }
            return tempFile.toAbsolutePath();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read org-netbeans-insane-hook.jar from " + jar, e);
        }
    }
}
