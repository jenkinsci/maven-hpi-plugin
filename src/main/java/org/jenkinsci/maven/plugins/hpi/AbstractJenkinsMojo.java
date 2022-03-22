package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;

import java.util.List;

/**
 * Mojos that need to figure out the Jenkins version it's working with.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractJenkinsMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins core jar.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @since 1.65
     */
    @Parameter
    protected String jenkinsCoreId;

    /**
     * Optional string that represents the version of Jenkins core to report plugins as requiring.
     * This parameter is only used when unbundling functionality from Jenkins core and the version specified
     * will be ignored if older than the autodetected version.
     */
    @Parameter
    private String jenkinsCoreVersionOverride;


    /**
     * List of Remote Repositories used by the resolver
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}",readonly = true, required = true)
    protected List<ArtifactRepository> remoteRepos;

    @Parameter(defaultValue = "${project.pluginArtifactRepositories}", readonly = true, required = true)
    protected List<ArtifactRepository> pluginArtifactRepositories;

    @Component
    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    protected ArtifactRepository localRepository;

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ArtifactResolver artifactResolver;

    @Component
    protected ProjectBuilder projectBuilder;

    @Component
    protected MavenProjectHelper projectHelper;


    protected String findJenkinsVersion() throws MojoExecutionException {
        for(Dependency a : project.getDependencies()) {
            boolean match;
            if (jenkinsCoreId!=null)
                match = (a.getGroupId()+':'+a.getArtifactId()).equals(jenkinsCoreId);
            else
                match = (a.getGroupId().equals("org.jenkins-ci.main") || a.getGroupId().equals("org.jvnet.hudson.main"))
                     && (a.getArtifactId().equals("jenkins-core") || a.getArtifactId().equals("hudson-core"));

            if (match) {
                if (jenkinsCoreVersionOverride != null && !jenkinsCoreVersionOverride.trim().isEmpty()) {
                    VersionNumber v1 = new VersionNumber(a.getVersion());
                    VersionNumber v2 = new VersionNumber(jenkinsCoreVersionOverride);
                    if (v1.compareTo(v2) == -1) {
                        return jenkinsCoreVersionOverride;
                    }
                    getLog().warn("Ignoring 'jenkinsCoreVersionOverride' of " + jenkinsCoreVersionOverride + " as the "
                            + "autodetected version, " + a.getVersion() + ", is newer. Please remove the redundant "
                            + "version override.");
                }
                return a.getVersion();
            }
        }
        if (jenkinsCoreVersionOverride != null && !jenkinsCoreVersionOverride.trim().isEmpty()) {
            return jenkinsCoreVersionOverride;
        }
        throw new MojoExecutionException("Failed to determine Jenkins version this plugin depends on.");
    }

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(
                a,
                artifactResolver,
                artifactFactory,
                projectBuilder,
                remoteRepos,
                pluginArtifactRepositories,
                localRepository,
                session);
    }
}
