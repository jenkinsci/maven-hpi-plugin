package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.groovy.control.io.NullWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * List up all the plugin dependencies.
 *
 * @goal list-plugin-dependencies
 * @requiresDependencyResolution compile
 * @author Kohsuke Kawaguchi
 */
public class ListPluginDependenciesMojo extends AbstractHpiMojo {
    /**
     * If non-null, the output will be sent to a file
     *
     * @parameter expression="${outputFile}"
     */
    protected File outputFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Writer w = outputFile==null ? new NullWriter() : new OutputStreamWriter(new FileOutputStream(outputFile),"UTF-8");

            for (MavenArtifact a : getProjectArtfacts()) {
                if(!a.isPlugin())
                    continue;

                String line = String.format("%s:%s:%s", a.getGroupId(), a.getArtifactId(), a.getVersion());
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

