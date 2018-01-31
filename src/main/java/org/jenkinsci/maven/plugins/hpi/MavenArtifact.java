package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
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
import java.util.jar.JarFile;

import static org.apache.maven.artifact.Artifact.*;

/**
 * {@link Artifact} is a bare data structure without any behavior and therefore
 * hard to write OO programs around it.
 *
 * This class wraps {@link Artifact} and adds behaviours.
 *
 * @author Kohsuke Kawaguchi
 */
public class MavenArtifact implements Comparable<MavenArtifact> {
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
        String type = getResolvedType();
        return type.equals("hpi") || type.equals("jpi");
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
     * 
     * @return the artifact file
     * @throws RuntimeException
     *             if the artifact can't be resolved
     * @throws NullPointerException
     *             if the artifact file can't be resolved
     */
    public File getFile() {
        if (artifact.getFile()==null) {
            if (artifact.isResolved()) {
                // artifacts can be set as resolved and file be null, is this a bug in Maven 3.5.0 ?
                artifact.setResolved(false);
            }
            try {
                resolver.resolve(artifact, remoteRepositories, localRepository);
            } catch (AbstractArtifactResolutionException e) {
                throw new RuntimeException("Failed to resolve "+getId(),e);
            }
        }
        if (artifact.getFile()==null) {
            throw new NullPointerException("Unable to resolve file for artifact " + artifact);
        }
        return artifact.getFile();
    }

    /**
     * Returns {@link MavenArtifact} for the hpi variant of this artifact.
     */
    public MavenArtifact getHpi() throws IOException {
        Artifact a = artifactFactory
                .createArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), SCOPE_COMPILE, getResolvedType());
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

    /**
     * Returns true if the artifacts has one of the given scopes (including null.)
     */
    public boolean hasScope(String... scopes) {
        for (String s : scopes) {
            if (s==null && artifact.getScope()==null)
                return true;
            if (s!=null && s.equals(artifact.getScope()))
                return true;
        }
        return false;
    }

    /** Gets the {@code artifactId} as used in a dependency declaration. For a plugin artifact this need not be be a plugin short name. */
    public String getArtifactId() {
        return artifact.getArtifactId();
    }

    /** Gets the {@code version} as used in a dependency declaration. For a plugin artifact this need not be be a plugin version number. */
    public String getVersion() {
        return artifact.getVersion();
    }

    public String getClassifier() {
        return artifact.getClassifier();
    }

    /** For a plugin artifact, unlike {@link #getArtifactId} this parses the plugin manifest. */
    public String getActualArtifactId() throws IOException {
        File file = getFile();
        if (file != null && file.isFile()) {
            try (JarFile jf = new JarFile(file)) {
                return jf.getManifest().getMainAttributes().getValue("Short-Name");
            }
        } else {
            return getArtifactId();
        }
    }

    /** For a plugin artifact, unlike {@link #getVersion} this parses the plugin manifest. */
    public String getActualVersion() throws IOException {
        File file = getFile();
        if (file != null && file.isFile()) {
            try (JarFile jf = new JarFile(file)) {
                return jf.getManifest().getMainAttributes().getValue("Plugin-Version").replaceFirst(" [(].+[)]$", ""); // e.g. " (private-abcd1234-username)"; Implementation-Version is clean but seems less portable
            }
        } else {
            return getVersion();
        }
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

    public boolean isNewerThan(MavenArtifact rhs) {
        return new VersionNumber(this.getVersion()).compareTo(new VersionNumber(rhs.getVersion())) > 0;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public int compareTo(MavenArtifact o) {
        return getId().compareTo(o.getId());
    }

    /**
     * Tries to detect the original packaging type by eventually resolving the POM.
     * This is necessary when a plugin depends on another plugin and it doesn't specify the type as hpi or jpi.
     */
    private String getResolvedType() throws IOException {
        try {
            String type = artifact.getType();

            // only resolve the POM if the packaging type is jar, because that's the default if no type has been specified
            if(!type.equals("jar")) {
                return type;
            }

            // when a plugin depends on another plugin, it doesn't specify the type as hpi or jpi, so we need to resolve its POM to see it
            return resolvePom().getPackaging();
        } catch (ProjectBuildingException e) {
            throw new IOException2("Failed to open artifact "+artifact.toString()+" at "+artifact.getFile(),e);
        }
    }
}
