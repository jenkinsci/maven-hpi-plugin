package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import java.util.List;

/**
 * Mojos that need to figure out the Jenkins version it's working with.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractJenkinsMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins core jar.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @parameter
     * @since 1.65
     */
    protected String jenkinsCoreId;


    /**
     * List of Remote Repositories used by the resolver
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     */
    protected List<ArtifactRepository> remoteRepos;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    protected ArtifactRepository localRepository;

    /**
     * @component
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @component
     */
    protected MavenProjectBuilder projectBuilder;


    protected String findJenkinsVersion() throws MojoExecutionException {
        for(Dependency a : (List<Dependency>)project.getDependencies()) {
            boolean match;
            if (jenkinsCoreId!=null)
                match = (a.getGroupId()+':'+a.getArtifactId()).equals(jenkinsCoreId);
            else
                match = (a.getGroupId().equals("org.jenkins-ci.main") || a.getGroupId().equals("org.jvnet.hudson.main"))
                     && (a.getArtifactId().equals("jenkins-core") || a.getArtifactId().equals("hudson-core"));

            if (match)
                return a.getVersion();
        }
        throw new MojoExecutionException("Failed to determine Jenkins version this plugin depends on.");
    }

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(a,projectBuilder,remoteRepos,localRepository);
    }
}
