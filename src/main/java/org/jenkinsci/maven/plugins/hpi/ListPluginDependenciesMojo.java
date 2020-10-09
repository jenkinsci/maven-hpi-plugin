package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.groovy.control.io.NullWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * List up all plugin dependencies declared in the project.
 * Transitive plugin dependencies will not be listed.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="list-plugin-dependencies", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ListPluginDependenciesMojo extends AbstractHpiMojo {
    /**
     * If non-null, the output will be sent to a file
     */
    @Parameter(property = "outputFile")
    protected File outputFile;

    // TODO(oleg_nenashev): Add support for transitive plugin dependencies.
    // Might require reusing/refactoring the plugin dependency tree resolution code in plugin installation mojos.
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Writer w = outputFile==null ? new NullWriter() : new OutputStreamWriter(new FileOutputStream(outputFile),"UTF-8");

            for (MavenArtifact a : getDirectDependencyArtfacts()) {
                if(!a.isPlugin())
                    continue;

                String line = String.format("%s:%s:%s", a.getGroupId(), a.getArtifactId(), a.artifact.getBaseVersion());
                w.write(line);
                w.write('\n');
                getLog().info(line);
            }

            w.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to list plugin dependencies",e);
        }
    }
}

