package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.util.Properties;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

/**
 * Make sure that we are running in the right environment.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class ValidateMojo extends AbstractJenkinsMojo {

    private static final String HTTP_GITHUB_COM = "http://github.com/";
    private static final String HTTPS_GITHUB_COM = "https://github.com/";

    private static final String SCM_GIT_GIT_URL_BAD = "scm:git:git://github.com/";
    private static final String SCM_GIT_HTTP_URL_BAD = "scm:git:" + HTTP_GITHUB_COM;
    private static final String SCM_GIT_SSH_URL_BAD = "scm:git:ssh://git@github.com/";

    private static final String SCM_GIT_HTTPS_URL_GOOD = "scm:git:" + HTTPS_GITHUB_COM;
    private static final String SCM_GIT_SSH_URL_GOOD = "scm:git:git@github.com:";

    private static final String GIT_URLS_ARE_DEPRECATED = "git:// URLs are deprecated";
    private static final String HTTP_URLS_ARE_INSECURE = "http:// URLs are insecure";
    private static final String SSH_URLS_DO_NOT_WORK_WELL_WITH_PCT = "ssh:// URLs do not work well with PCT";

    @Override
    public void execute() throws MojoExecutionException {
        JavaSpecificationVersion javaVersion = getMinimumJavaVersion();
        if (JavaSpecificationVersion.forCurrentJVM().isOlderThan(javaVersion)) {
            throw new MojoExecutionException("Java " + javaVersion + " or later is necessary to build this plugin.");
        }

        if (new VersionNumber(findJenkinsVersion()).compareTo(new VersionNumber("2.361")) < 0) {
            throw new MojoExecutionException("This version of maven-hpi-plugin requires Jenkins 2.361 or later");
        }

        MavenProject parent = project.getParent();
        if (parent != null
                && parent.getGroupId().equals("org.jenkins-ci.plugins")
                && parent.getArtifactId().equals("plugin")
                && !parent.getProperties().containsKey("java.level")
                && project.getProperties().containsKey("java.level")) {
            getLog().warn("Ignoring deprecated java.level property."
                    + " This property should be removed from your plugin's POM."
                    + " In the future this warning will be changed to an error and will break the build.");
        }

        Scm scm = project.getScm();
        if (scm != null) {
            String connection = scm.getConnection();
            if (connection != null) {
                check("connection", connection, SCM_GIT_GIT_URL_BAD, SCM_GIT_HTTPS_URL_GOOD, GIT_URLS_ARE_DEPRECATED);
                check(
                        "connection",
                        connection,
                        SCM_GIT_SSH_URL_BAD,
                        SCM_GIT_HTTPS_URL_GOOD,
                        SSH_URLS_DO_NOT_WORK_WELL_WITH_PCT);
                check("connection", connection, SCM_GIT_HTTP_URL_BAD, SCM_GIT_HTTPS_URL_GOOD, HTTP_URLS_ARE_INSECURE);
            }
            String developerConnection = scm.getDeveloperConnection();
            if (developerConnection != null) {
                check(
                        "developerConnection",
                        developerConnection,
                        SCM_GIT_GIT_URL_BAD,
                        SCM_GIT_SSH_URL_GOOD,
                        GIT_URLS_ARE_DEPRECATED);
                check(
                        "developerConnection",
                        developerConnection,
                        SCM_GIT_SSH_URL_BAD,
                        SCM_GIT_SSH_URL_GOOD,
                        SSH_URLS_DO_NOT_WORK_WELL_WITH_PCT);
                check(
                        "developerConnection",
                        developerConnection,
                        SCM_GIT_HTTP_URL_BAD,
                        SCM_GIT_HTTPS_URL_GOOD,
                        HTTP_URLS_ARE_INSECURE);
            }
            String url = scm.getUrl();
            if (url != null) {
                check("url", url, HTTP_GITHUB_COM, HTTPS_GITHUB_COM, HTTP_URLS_ARE_INSECURE);
            }
        }
    }

    private void check(String tag, String value, String badStart, String goodStart, String reason) {
        if (value.startsWith(badStart)) {
            String goodValue = goodStart + value.substring(badStart.length());
            getLog().warn(String.format(
                    "<%s>%s</%s> is invalid because %s."
                            + " Replace it with <%s>%s</%s>."
                            + " In the future this warning will be changed to an error and will break the build.",
                    tag, deinterpolate(value), tag, reason, tag, deinterpolate(goodValue), tag));
        }
    }

    private String deinterpolate(String interpolated) {
        Properties properties = project.getProperties();
        if (properties.containsKey("gitHubRepo")) {
            String propVal = properties.getProperty("gitHubRepo");
            return interpolated.replace(propVal, "${gitHubRepo}");
        }
        String artifactId = "jenkinsci/" + project.getArtifactId() + "-plugin.git";
        if (interpolated.endsWith(artifactId)) {
            return interpolated.substring(0, interpolated.length() - artifactId.length())
                    + "jenkinsci/${project.artifactId}-plugin.git";
        }
        return interpolated;
    }
}
