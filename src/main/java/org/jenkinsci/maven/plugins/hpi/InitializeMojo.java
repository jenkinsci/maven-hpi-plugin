package org.jenkinsci.maven.plugins.hpi;

import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.util.ArrayList;
import java.util.List;
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
