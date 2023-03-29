package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * List all plugins their version, and directrory that are in the reactor.
 */
@Mojo(name="list-plugins", aggregator = true)
public class ListPluginsMojo extends AbstractHpiMojo{

    /**
     * If not {@code null}, the output will written to this file in addition to the log.
     */
    @Parameter(property = "output")
    protected File output;

    /**
     * The projects in the current build. 
     */
    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> projects;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try (Writer w = new OutputStreamWriter(output == null ? OutputStream.nullOutputStream() : new FileOutputStream(output), StandardCharsets.UTF_8)) {
            for (MavenProject p : projects) {
                if ("hpi".equals(p.getPackaging())) {
                    String line = String.format("%s\t%s\t%s", p.getArtifactId(), p.getVersion(), p.getBasedir());
                    w.write(line);
                    w.write('\n');
                    getLog().info(line);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list plugin dependencies",e);
        }
    }

}
