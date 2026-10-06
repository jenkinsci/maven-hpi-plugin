package org.jenkinsci.maven.plugins.hpi;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDependencyGraphTraversingMojo extends AbstractJenkinsMojo {

    /**
     * Traverses the whole dependency tree rooted at the project.
     */
    protected void traverseProject() throws DependencyCollectionException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

        ArtifactTypeRegistry artifactTypeRegistry =
                session.getRepositorySession().getArtifactTypeRegistry();

        List<org.eclipse.aether.graph.Dependency> dependencies = project.getDependencies().stream()
                .filter(d -> !d.isOptional())
                .filter(d ->
                        !List.of(Artifact.SCOPE_TEST, Artifact.SCOPE_PROVIDED).contains(d.getScope()))
                .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                .collect(Collectors.toList());

        List<org.eclipse.aether.graph.Dependency> managedDependencies = null;
        if (project.getDependencyManagement() != null) {
            managedDependencies = project.getDependencyManagement().getDependencies().stream()
                    .map(d -> RepositoryUtils.toDependency(d, artifactTypeRegistry))
                    .collect(Collectors.toList());
        }

        CollectRequest collectRequest =
                new CollectRequest(dependencies, managedDependencies, project.getRemoteProjectRepositories());
        collectRequest.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));

        DependencyNode node = repositorySystem
                .collectDependencies(buildingRequest.getRepositorySession(), collectRequest)
                .getRoot();

        visit(node);
    }

    /**
     * Traverses a tree rooted at the given node.
     */
    protected void visit(DependencyNode g) {
        visit(g, true);
    }

    /**
     * Traverses a tree rooted at the given node.
     * @param g the node to visit
     * @param isRoot true if this is the root node
     */
    private void visit(DependencyNode g, boolean isRoot) {
        if (accept(g, isRoot)) {
            for (DependencyNode dn : g.getChildren()) {
                visit(dn, false);
            }
        }
    }

    /**
     * Visits a node. Called at most once for any node in the dependency tree.
     *
     * @param g the node to visit
     * @param isRoot true if this is the root node
     * @return true if the children should be traversed.
     */
    protected abstract boolean accept(DependencyNode g, boolean isRoot);
}
