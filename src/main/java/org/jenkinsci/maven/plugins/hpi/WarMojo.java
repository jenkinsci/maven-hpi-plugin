package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.ZipFileSet;

/**
 * Builds a custom Jenkins war that includes all the additional plugins referenced in this POM.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(
        name = "custom-war",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class WarMojo extends RunMojo {
    /**
     * Optional string that represents "groupId:artifactId" of Jenkins war.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     */
    @Parameter
    protected String jenkinsWarId;

    @Parameter
    protected File outputFile;

    /**
     * Add this plugin to custom war
     */
    @Parameter(property = "addThisPluginToCustomWar", defaultValue = "false")
    private boolean addThisPluginToCustomWar = false;

    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * Executes the WarMojo on the current project.
     *
     * @throws MojoExecutionException if an error occurred while building the webapp
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            if (outputFile == null) {
                outputFile = new File(
                        getProject().getBasedir(), "target/" + getProject().getArtifactId() + ".war");
            }

            File war = getJenkinsWarArtifact().getFile();

            Zip rezip = new Zip();
            rezip.setDestFile(outputFile);
            rezip.setProject(new Project());
            ZipFileSet z = new ZipFileSet();
            z.setSrc(war);
            rezip.addZipfileset(z);

            getProject().setArtifacts(resolveDependencies(dependencyResolution));

            Set<MavenArtifact> projectArtifacts = new LinkedHashSet<>(getProjectArtifacts());
            if (getProject().getPackaging().equals("hpi") && addThisPluginToCustomWar) {
                Optional.ofNullable(getProject()).map(MavenProject::getArtifact).map(a -> {
                    projectArtifacts.add(wrap(a)); // side effect
                    getLog().debug("This plugin " + a + "to be added to custom war");
                    return projectArtifacts; // have to return something from multiline lambda inside map()
                });
            }
            for (MavenArtifact a : projectArtifacts) {
                if (!a.isPlugin()) {
                    continue;
                }

                // find corresponding .hpi file
                Artifact hpi =
                        artifactFactory.createArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), null, "hpi");
                ProjectBuildingRequest buildingRequest =
                        new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
                buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
                hpi = artifactResolver.resolveArtifact(buildingRequest, hpi).getArtifact();

                if (hpi.getFile().isDirectory()) {
                    throw new UnsupportedOperationException(
                            hpi.getFile() + " is a directory and not packaged yet. this isn't supported");
                }

                z = new ZipFileSet();
                z.setFile(hpi.getFile());
                z.setFullpath("WEB-INF/plugins/" + hpi.getArtifactId() + ".hpi");
                rezip.addZipfileset(z);
            }

            rezip.execute();
            getLog().info("Generated " + outputFile);

            projectHelper.attachArtifact(getProject(), "war", outputFile);
        } catch (IOException | ArtifactResolverException e) {
            throw new MojoExecutionException("Failed to package war", e);
        }
    }
}
