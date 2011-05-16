package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

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
}
