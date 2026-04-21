package org.jenkinsci.maven.plugins.hpi.enforcer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Utility for resolving BOMs and their managed dependencies.
 */
@Named
class BomResolverUtil {

    private final RepositorySystem repositorySystem;
    private final MavenSession session;
    private final ProjectBuilder projectBuilder;

    @Inject
    BomResolverUtil(RepositorySystem repositorySystem, MavenSession session, ProjectBuilder projectBuilder) {
        this.repositorySystem = Objects.requireNonNull(repositorySystem);
        this.session = Objects.requireNonNull(session);
        this.projectBuilder = Objects.requireNonNull(projectBuilder);
    }

    /**
     * Resolves a BOM and extracts its managed dependencies.
     *
     * @param bomDep the BOM dependency to resolve
     * @param project the current project (for property resolution)
     * @return map of "groupId:artifactId" to managed dependency info
     * @throws ArtifactResolutionException if BOM cannot be resolved
     * @throws ProjectBuildingException if BOM POM cannot be built
     */
    Map<String, BomManagedDependency> resolveBomManagedDependencies(Dependency bomDep, MavenProject project)
            throws ArtifactResolutionException, ProjectBuildingException {

        // Get repository session and repositories from the Maven session
        RepositorySystemSession repositorySession = session.getRepositorySession();
        java.util.List<RemoteRepository> remoteRepositories = project.getRemoteProjectRepositories();

        // Resolve the BOM artifact
        Artifact bomArtifact = new DefaultArtifact(
                bomDep.getGroupId(),
                bomDep.getArtifactId(),
                bomDep.getClassifier(),
                "pom",
                resolveProperties(bomDep.getVersion(), project));

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(bomArtifact);
        request.setRepositories(remoteRepositories);

        ArtifactResult result = repositorySystem.resolveArtifact(repositorySession, request);
        Artifact resolvedBom = result.getArtifact();

        // Build MavenProject from the BOM
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setResolveDependencies(false);
        buildingRequest.setProcessPlugins(false);
        buildingRequest.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        MavenProject bomProject =
                projectBuilder.build(resolvedBom.getFile(), buildingRequest).getProject();

        // Extract managed dependencies from the BOM
        Map<String, BomManagedDependency> bomDependencies = new LinkedHashMap<>();
        DependencyManagement bomDepMgmt = bomProject.getDependencyManagement();
        if (bomDepMgmt != null && bomDepMgmt.getDependencies() != null) {
            String bomArtifactId = bomDep.getArtifactId();
            for (Dependency dep : bomDepMgmt.getDependencies()) {
                if (dep.getVersion() != null && !dep.getVersion().isEmpty()) {
                    String key = dep.getGroupId() + ":" + dep.getArtifactId();
                    String version = resolveProperties(dep.getVersion(), bomProject);
                    // Later BOMs override earlier ones (Maven behavior)
                    bomDependencies.put(key, new BomManagedDependency(version, bomArtifactId));
                }
            }
        }

        return bomDependencies;
    }

    private String resolveProperties(String value, MavenProject project) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        String resolved = value;
        for (Map.Entry<Object, Object> entry : project.getProperties().entrySet()) {
            String key = String.valueOf(entry.getKey());
            String val = String.valueOf(entry.getValue());
            resolved = resolved.replace("${" + key + "}", val);
        }
        return resolved;
    }

    record BomManagedDependency(String version, String bomArtifactId) {}
}
