package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

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
    public final ProjectBuilder builder;
    public final Artifact artifact;
    public final RepositorySystem repositorySystem;
    public final MavenSession session;
    public final MavenProject project;

    public MavenArtifact(
            Artifact artifact,
            RepositorySystem repositorySystem,
            ArtifactFactory artifactFactory,
            ProjectBuilder builder,
            MavenSession session,
            MavenProject project) {
        this.artifact = artifact;
        this.repositorySystem = repositorySystem;
        this.artifactFactory = artifactFactory;
        this.builder = builder;
        this.session = Objects.requireNonNull(session);
        this.project = Objects.requireNonNull(project);
    }

    public MavenProject resolvePom() throws ProjectBuildingException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProcessPlugins(false); // improve performance
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return builder.build(artifact, buildingRequest).getProject();
    }

    /**
     * Is this a Jenkins plugin?
     * The preferred check is for {@code hpi} or {@code jpi} packaging.
     * If the project model cannot be resolved
     * (for example when an indirect dependency has a bogus {@code systemPath} that is only rejected in some environments)
     * then the fallback logic is to look for a JAR manifest with entries typical of plugins.
     */
    public boolean isPlugin(Log log) {
        try {
            String type = getResolvedType();
            return type.equals("hpi") || type.equals("jpi");
        } catch (IOException x) {
            if (log.isDebugEnabled()) {
                log.debug(x);
            } else {
                log.warn("While inspecting " + artifact + ": " + x.getCause().getMessage());
            }
        }
        var f = artifact.getFile();
        if (f.getName().endsWith(".jar") && f.isFile()) {
            try (var jf = new JarFile(f)) {
                var mani = jf.getManifest();
                if (mani != null) {
                    var attr = mani.getMainAttributes();
                    return attr.getValue("Jenkins-Version") != null && attr.getValue("Plugin-Version") != null;
                }
            } catch (IOException x) {
                if (log.isDebugEnabled()) {
                    log.debug(x);
                } else {
                    log.warn(
                            "While inspecting " + artifact + ": " + x.getCause().getMessage());
                }
            }
        }
        return false;
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
        StringBuilder path = new StringBuilder();
        path.append(artifact.getArtifactId());
        path.append('-');
        path.append(artifact.getVersion());
        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            path.append('-');
            path.append(artifact.getClassifier());
        }
        if (!artifact.getArtifactHandler().getExtension().isEmpty()) {
            path.append('.');
            path.append(artifact.getArtifactHandler().getExtension());
        }
        return path.toString();
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
    public File getFile() throws MojoExecutionException {
        if (artifact.getFile() == null) {
            return resolveArtifact(artifact, project, session, repositorySystem).getFile();
        }
        return artifact.getFile();
    }

    static Artifact resolveArtifact(
            Artifact artifact, MavenProject project, MavenSession session, RepositorySystem repositorySystem)
            throws MojoExecutionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        List<RemoteRepository> remoteRepositories = RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories());
        RepositorySystemSession repositorySession = buildingRequest.getRepositorySession();

        // use descriptor to respect relocation
        ArtifactDescriptorRequest descriptorRequest =
                new ArtifactDescriptorRequest(RepositoryUtils.toArtifact(artifact), remoteRepositories, null);

        ArtifactDescriptorResult descriptorResult;
        try {
            descriptorResult = repositorySystem.readArtifactDescriptor(repositorySession, descriptorRequest);
        } catch (ArtifactDescriptorException e) {
            throw new MojoExecutionException("Failed to read artifact descriptor: " + artifact, e);
        }

        ArtifactRequest request = new ArtifactRequest(descriptorResult.getArtifact(), remoteRepositories, null);
        Artifact resolved;
        try {
            resolved = RepositoryUtils.toArtifact(
                    repositorySystem.resolveArtifact(repositorySession, request).getArtifact());
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve artifact: " + artifact, e);
        }

        /*
         * If the result is a directory rather than a file, we must be in a multi-module project
         * where one plugin depends on another plugin in the same multi-module project. Try again
         * without the workspace reader to force Maven to look for released artifacts rather than in
         * the target/ directory of another module.
         */
        if (resolved.getFile().isDirectory()
                && buildingRequest.getRepositorySession() instanceof DefaultRepositorySystemSession) {
            DefaultRepositorySystemSession oldRepositorySession =
                    (DefaultRepositorySystemSession) buildingRequest.getRepositorySession();
            DefaultRepositorySystemSession newRepositorySession =
                    new DefaultRepositorySystemSession(oldRepositorySession);
            newRepositorySession.setWorkspaceReader(null);
            newRepositorySession.setReadOnly();
            try {
                resolved = RepositoryUtils.toArtifact(repositorySystem
                        .resolveArtifact(newRepositorySession, request)
                        .getArtifact());
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException("Failed to resolve artifact: " + artifact, e);
            }
        }

        return resolved;
    }

    /**
     * Returns {@link MavenArtifact} for the hpi variant of this artifact.
     */
    public MavenArtifact getHpi() throws IOException {
        Artifact a = artifactFactory.createArtifact(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                Artifact.SCOPE_COMPILE,
                getResolvedType());
        return new MavenArtifact(a, repositorySystem, artifactFactory, builder, session, project);
    }

    public List<String /* of IDs*/> getDependencyTrail() {
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
            if (s == null && artifact.getScope() == null) {
                return true;
            }
            if (s != null && s.equals(artifact.getScope())) {
                return true;
            }
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

    /**
     * Get the artifact handler.
     */
    public ArtifactHandler getArtifactHandler() {
        return artifact.getArtifactHandler();
    }

    /** For a plugin artifact, unlike {@link #getArtifactId} this parses the plugin manifest. */
    public String getActualArtifactId() throws IOException, MojoExecutionException {
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
    public String getActualVersion() throws IOException, MojoExecutionException {
        File file = getFile();
        if (file != null && file.isFile()) {
            try (JarFile jf = new JarFile(file)) {
                // e.g. " (private-abcd1234-username)"; Implementation-Version is clean but seems less portable
                return jf.getManifest()
                        .getMainAttributes()
                        .getValue("Plugin-Version")
                        .replaceFirst(" [(].+[)]$", "");
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

            // only resolve the POM if the packaging type is jar, because that's the default if no type has been
            // specified
            if (!type.equals("jar")) {
                return type;
            }
            // also ignore core-assets, tests, etc.
            if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
                return type;
            }

            // when a plugin depends on another plugin, it doesn't specify the type as hpi or jpi, so we need to resolve
            // its POM to see it
            return resolvePom().getPackaging();
        } catch (ProjectBuildingException e) {
            throw new IOException("Failed to open artifact " + artifact + " at " + artifact.getFile() + ": " + e, e);
        }
    }
}
