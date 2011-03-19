package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.kohsuke.stapler.framework.io.IOException2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * {@link Artifact} is a bare data structure without any behavior and therefore
 * hard to write OO programs around it.
 *
 * This class wraps {@link Artifact} and adds behaviours.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact {
    public final MavenProjectBuilder builder;
    public final List<ArtifactRepository> remoteRepositories;
    public final ArtifactRepository localRepository;
    public final Artifact artifact;

    public MavenArtifact(Artifact artifact, MavenProjectBuilder builder, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository) {
        this.artifact = artifact;
        this.builder = builder;
        this.remoteRepositories = remoteRepositories;
        this.localRepository = localRepository;
    }

    public MavenProject resolvePom() throws ProjectBuildingException {
        return builder.buildFromRepository(artifact,remoteRepositories,localRepository);
    }

    /**
     * Is this a Jenkins plugin?
     */
    public boolean isPlugin() throws IOException {
        try {
            // some artifacts aren't even Java, so ignore those.
            if(!artifact.getType().equals("jar"))    return false;

            return resolvePom().getPackaging().equals("hpi");
        } catch (ProjectBuildingException e) {
            throw new IOException2("Failed to open artifact "+artifact.toString()+" at "+artifact.getFile(),e);
        }
    }

    public String getId() {
        return artifact.getId();
    }

    /**
     * Converts the filename of an artifact to artifactId-version.type format.
     *
     * @return converted filename of the artifact
     */
    public String getDefaultFinalName() {
        return artifact.getArtifactId() + "-" + artifact.getVersion() + "." +
            artifact.getArtifactHandler().getExtension();
    }

    public boolean isOptional() {
        return artifact.isOptional();
    }

    public String getType() {
        return artifact.getType();
    }

    public File getFile() {
        // TODO: should we resolve?
        return artifact.getFile();
    }

    public List<String/* of IDs*/> getDependencyTrail() {
        return artifact.getDependencyTrail();
    }

    public String getGroupId() {
        return artifact.getGroupId();
    }

    public String getScope() {
        return artifact.getScope();
    }

    public String getArtifactId() {
        return artifact.getArtifactId();
    }

    public String getVersion() {
        return artifact.getVersion();
    }
}
