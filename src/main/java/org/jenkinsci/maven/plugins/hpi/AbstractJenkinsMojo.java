package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import io.jenkins.lib.versionnumber.JavaSpecificationVersion;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;

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

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ArtifactResolver artifactResolver;

    @Component
    protected ProjectBuilder projectBuilder;

    @Component
    protected MavenProjectHelper projectHelper;

    protected String findJenkinsVersion() throws MojoExecutionException {
        for (Dependency a : project.getDependencies()) {
            boolean match;
            if (jenkinsCoreId != null) {
                match = (a.getGroupId() + ':' + a.getArtifactId()).equals(jenkinsCoreId);
            } else {
                match = (a.getGroupId().equals("org.jenkins-ci.main")
                                || a.getGroupId().equals("org.jvnet.hudson.main"))
                        && (a.getArtifactId().equals("jenkins-core")
                                || a.getArtifactId().equals("hudson-core"));
            }

            if (match) {
                if (jenkinsCoreVersionOverride != null
                        && !jenkinsCoreVersionOverride.trim().isEmpty()) {
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
        if (jenkinsCoreVersionOverride != null
                && !jenkinsCoreVersionOverride.trim().isEmpty()) {
            return jenkinsCoreVersionOverride;
        }
        throw new MojoExecutionException("Failed to determine Jenkins version this plugin depends on.");
    }

    protected JavaSpecificationVersion getMinimumJavaVersion() throws MojoExecutionException {
        Artifact core = resolveJenkinsCore();
        File jar = wrap(core).getFile();
        try (JarFile jarFile = new JarFile(jar)) {
            ZipEntry entry = jarFile.getEntry("jenkins/model/Jenkins.class");
            if (entry == null) {
                throw new MojoExecutionException("Failed to find Jenkins.class in " + jar);
            }
            try (InputStream is = jarFile.getInputStream(entry);
                    DataInputStream dis = new DataInputStream(is)) {
                int magic = dis.readInt();
                if (magic != 0xcafebabe) {
                    throw new MojoExecutionException("Jenkins.class is not a valid class file in " + jar);
                }
                dis.readUnsignedShort(); // discard minor version
                return JavaSpecificationVersion.fromClassVersion(dis.readUnsignedShort());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read minimum Java version from " + jar, e);
        }
    }

    private Artifact resolveJenkinsCore() throws MojoExecutionException {
        DefaultArtifactCoordinate artifactCoordinate = new DefaultArtifactCoordinate();
        if (jenkinsCoreId != null) {
            String[] parts = jenkinsCoreId.split(":");
            artifactCoordinate.setGroupId(parts[0]);
            artifactCoordinate.setArtifactId(parts[1]);
        } else {
            artifactCoordinate.setGroupId("org.jenkins-ci.main");
            artifactCoordinate.setArtifactId("jenkins-core");
        }
        artifactCoordinate.setVersion(findJenkinsVersion());

        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
            return artifactResolver
                    .resolveArtifact(buildingRequest, artifactCoordinate)
                    .getArtifact();
        } catch (ArtifactResolverException e) {
            throw new MojoExecutionException("Couldn't download artifact: ", e);
        }
    }

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(a, artifactResolver, artifactFactory, projectBuilder, session, project);
    }
}
