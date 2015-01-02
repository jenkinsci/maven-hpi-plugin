package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.kohsuke.stapler.framework.io.IOException2;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.apache.maven.artifact.Artifact.SCOPE_COMPILE;

/**
 * {@link Artifact} is a bare data structure without any behavior and therefore
 * hard to write OO programs around it.
 *
 * This class wraps {@link Artifact} and adds behaviours.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact {
    public final ArtifactFactory artifactFactory;
    public final MavenProjectBuilder builder;
    public final List<ArtifactRepository> remoteRepositories;
    public final ArtifactRepository localRepository;
    public final Artifact artifact;
    public final ArtifactResolver resolver;

    public MavenArtifact(Artifact artifact, ArtifactResolver resolver, ArtifactFactory artifactFactory, MavenProjectBuilder builder, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository) {
        this.artifact = artifact;
        this.resolver = resolver;
        this.artifactFactory = artifactFactory;
        this.builder = builder;
        this.remoteRepositories = remoteRepositories;
        remoteRepositories.size(); // null check
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
            String t = artifact.getType();

            if(t.equals("hpi") || t.equals("jpi"))    return true;

            // some artifacts aren't even Java, so ignore those.
            if(!t.equals("jar"))    return false;

            // when a plugin depends on another plugin, it doesn't specify the type as hpi, so we need to resolve its POM to see it
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

    /**
     * Resolves to the jar file that contains the code of the plugin.
     */
    public File getFile() {
        if (artifact.getFile()==null)
            try {
                resolver.resolve(artifact, remoteRepositories, localRepository);
            } catch (AbstractArtifactResolutionException e) {
                throw new RuntimeException("Failed to resolve "+getId(),e);
            }
        return artifact.getFile();
    }

    /**
     * Returns {@link MavenArtifact} for the hpi variant of this artifact.
     */
    public MavenArtifact getHpi() {
        Artifact a = artifactFactory
                .createArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), SCOPE_COMPILE, "hpi");
        return new MavenArtifact(a,resolver,artifactFactory,builder,remoteRepositories,localRepository);
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

    public ArtifactVersion getVersionNumber() throws OverConstrainedVersionException {
        return artifact.getSelectedVersion();
    }

    /**
     * Returns true if this artifact has the same groupId and artifactId as the given project.
     */
    public boolean hasSameGAAs(MavenProject project) {
        return getGroupId().equals(project.getGroupId()) && getArtifactId().equals(project.getArtifactId());
    }
}
