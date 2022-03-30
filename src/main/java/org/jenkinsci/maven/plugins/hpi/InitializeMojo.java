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
        String addOpens = getAddOpens();
        if (addOpens != null
                && JavaSpecificationVersion.forCurrentJVM().isNewerThanOrEqualTo(new JavaSpecificationVersion("9"))) {
            String argLine = project.getProperties().getProperty("argLine");
            if (argLine != null) {
                argLine += " " + buildargLine(addOpens);
            } else {
                argLine = buildargLine(addOpens);
            }
            getLog().info("Setting argLine to " + argLine);
            project.getProperties().setProperty("argLine", argLine);
        }
    }

    private static String buildargLine(String addOpens) {
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
