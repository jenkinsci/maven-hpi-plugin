package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
        setAddOpensProperty(resolveJenkinsWar());
        setInsaneHookProperty();
        setJavaAgentProperty();
    }

    @NonNull
    private Artifact resolveJenkinsWar() throws MojoExecutionException {
        Artifact artifact =
                artifactFactory.createArtifact("org.jenkins-ci.main", "jenkins-war", findJenkinsVersion(), null, "war");
        return MavenArtifact.resolveArtifact(artifact, project, session, repositorySystem);
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

    private void setJavaAgentProperty() {
        Artifact mockito = project.getArtifactMap().get("org.mockito:mockito-core");
        if (mockito == null) {
            // Mockito not in use; no need to set Java agent property.
            return;
        }

        String javaAgent = String.format("-javaagent:'%s'", mockito.getFile());
        getLog().info("Setting jenkins.javaAgent to " + javaAgent);
        project.getProperties().setProperty("jenkins.javaAgent", javaAgent);
    }
}
