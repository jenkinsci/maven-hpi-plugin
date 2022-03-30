package org.jenkinsci.maven.plugins.hpi;

import com.google.common.collect.Iterables;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.tools.ant.util.StringUtils;

/**
 * Configure Maven for the desired version of Java.
 *
 * @author Basil Crow
 */
@Mojo(name = "initialize", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.INITIALIZE)
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

        String addOpens = getAddOpens();
        if (addOpens == null) {
            // core older than 2.339, ignore
            return;
        }

        String argLine = project.getProperties().getProperty("argLine");
        if (argLine != null) {
            argLine += " " + buildArgLine(addOpens);
        } else {
            argLine = buildArgLine(addOpens);
        }

        // TODO fetch the org-netbeans-insane-hook.jar artifact from Maven Central, once it is published there (see apache/netbeans#3743)
        // for now hard-coding /tmp/org-netbeans-insane-hook.jar
        boolean insaneHook;
        List<Artifact> insaneArtifacts = project.getArtifacts().stream()
                 .filter(artifact -> artifact.getGroupId().equals("org.netbeans.modules") && artifact.getArtifactId().equals("org-netbeans-insane"))
                 .collect(Collectors.toList());
        if (!insaneArtifacts.isEmpty()) {
            Artifact insane = Iterables.getOnlyElement(insaneArtifacts);
            insaneHook = Integer.parseInt(StringUtils.removePrefix(insane.getVersion(), "RELEASE")) >= 130;
        } else {
            insaneHook = false;
        }
        if (insaneHook) {
            if (JavaSpecificationVersion.forCurrentJVM().isNewerThanOrEqualTo(new JavaSpecificationVersion("9"))) {
                argLine += " --patch-module=java.base=/tmp/org-netbeans-insane-hook.jar --add-exports=java.base/org.netbeans.insane.hook=ALL-UNNAMED";
            } else {
                argLine += " -Xbootclasspath/p:/tmp/org-netbeans-insane-hook.jar";
            }
        }

        getLog().info("Setting argLine to " + argLine);
        project.getProperties().setProperty("argLine", argLine);
    }

    private static String buildArgLine(String addOpens) {
        List<String> arguments = new ArrayList<>();
        for (String module : addOpens.split("\\s+")) {
            if (!module.isEmpty()) {
                arguments.add("--add-opens");
                arguments.add(module + "=ALL-UNNAMED");
            }
        }
        return String.join(" ", arguments);
    }
}
