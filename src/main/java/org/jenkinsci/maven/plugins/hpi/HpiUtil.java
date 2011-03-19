package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
class HpiUtil {

    /**
     * @param jenkinsCoreId
     *      If null, we look for default well-known Jenkins core group/artifact ID.
     *      Otherwise this string must be "groupId:artifactId"
     */
    static String findJenkinsVersion(MavenProject project, String jenkinsCoreId) {
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
        System.err.println("** Warning: failed to determine Jenkins version this plugin depends on.");
        return null;
    }
}
