package org.jenkinsci.maven.plugins.hpi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;

/**
 * Utility methods to convert between Maven and Aether repository objects.
 */
public class RepositoryUtils {

    /**
     * Converts a Maven {@link Artifact} to an Aether
     * {@link org.eclipse.aether.artifact.Artifact}.
     *
     * @param artifact Maven artifact
     * @return Aether artifact
     */
    public static org.eclipse.aether.artifact.Artifact toArtifact(Artifact artifact) {
        if (artifact == null) {
            return null;
        }

        StringBuilder coords = new StringBuilder();
        coords.append(artifact.getGroupId())
                .append(':')
                .append(artifact.getArtifactId())
                .append(':')
                .append(artifact.getType());

        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            coords.append(':').append(artifact.getClassifier());
        }

        coords.append(':').append(artifact.getVersion());

        org.eclipse.aether.artifact.Artifact result = new DefaultArtifact(coords.toString());

        if (artifact.getFile() != null) {
            result = result.setFile(artifact.getFile());
        }

        return result;
    }

    /**
     * Converts a Maven {@link Dependency} to an Aether
     * {@link org.eclipse.aether.graph.Dependency}.
     *
     * @param dependency Maven dependency
     * @param scope      dependency scope (may be {@code null})
     * @return Aether dependency
     */
    public static org.eclipse.aether.graph.Dependency toDependency(Dependency dependency, String scope) {
        if (dependency == null) {
            return null;
        }

        StringBuilder coords = new StringBuilder();
        coords.append(dependency.getGroupId())
                .append(':')
                .append(dependency.getArtifactId())
                .append(':')
                .append(dependency.getType() != null ? dependency.getType() : "jar");

        if (dependency.getClassifier() != null && !dependency.getClassifier().isEmpty()) {
            coords.append(':').append(dependency.getClassifier());
        }

        coords.append(':').append(dependency.getVersion());

        org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(coords.toString());

        String effectiveScope = (scope != null) ? scope : dependency.getScope();

        return new org.eclipse.aether.graph.Dependency(artifact, effectiveScope, dependency.isOptional());
    }

    /**
     * Converts a collection of Maven {@link ArtifactRepository} to a list of Aether
     * {@link RemoteRepository}.
     *
     * @param repositories Maven repositories
     * @return Aether repositories
     */
    public static List<RemoteRepository> toRepos(Collection<ArtifactRepository> repositories) {
        if (repositories == null) {
            return null;
        }

        List<RemoteRepository> results = new ArrayList<>(repositories.size());

        for (ArtifactRepository repository : repositories) {
            Builder builder = new RemoteRepository.Builder(
                    repository.getId(), repository.getLayout().getId(), repository.getUrl());

            RemoteRepository result = builder.build();
            results.add(result);
        }

        return results;
    }
}
