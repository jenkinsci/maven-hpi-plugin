package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.artifact.AttachedArtifact;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.ZipFileSet;

import java.io.File;
import java.io.IOException;

import static org.apache.maven.plugins.annotations.LifecyclePhase.*;
import static org.apache.maven.plugins.annotations.ResolutionScope.*;

/**
 * Builds a custom Jenkins war that includes all the additional plugins referenced in this POM.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="custom-war", defaultPhase = PACKAGE, requiresDependencyResolution = RUNTIME)
public class WarMojo extends RunMojo {
    /**
     * Optional string that represents "groupId:artifactId" of Jenkins war.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     */
    @Parameter
    protected String jenkinsWarId;

    @Parameter
    protected File outputFile;

    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * Executes the WarMojo on the current project.
     *
     * @throws MojoExecutionException if an error occurred while building the webapp
     */
    public void execute() throws MojoExecutionException {
        try {
            if (outputFile==null)
                outputFile = new File(getProject().getBasedir(),"target/"+getProject().getArtifactId()+".war");

            File war = getJenkinsWarArtifact().getFile();

            Zip rezip = new Zip();
            rezip.setDestFile(outputFile);
            rezip.setProject(new Project());
            ZipFileSet z = new ZipFileSet();
            z.setSrc(war);
            rezip.addZipfileset(z);

            getProject().setArtifacts(resolveDependencies(dependencyResolution));
            for( MavenArtifact a : getProjectArtifacts() ) {
                if(!a.isPlugin())
                    continue;

                // find corresponding .hpi file
                Artifact hpi = artifactFactory.createArtifact(a.getGroupId(),a.getArtifactId(),a.getVersion(),null,"hpi");
                artifactResolver.resolve(hpi,getProject().getRemoteArtifactRepositories(), localRepository);

                if (hpi.getFile().isDirectory())
                    throw new UnsupportedOperationException(hpi.getFile()+" is a directory and not packaged yet. this isn't supported");

                z = new ZipFileSet();
                z.setFile(hpi.getFile());
                z.setFullpath("/WEB-INF/plugins/"+hpi.getArtifactId()+".hpi");
                rezip.addZipfileset(z);
            }

            rezip.execute();
            getLog().info("Generated "+outputFile);

            projectHelper.attachArtifact(getProject(), "war", outputFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to package war",e);
        } catch (AbstractArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to package war",e);
        }
    }

}
