package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyNode;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDependencyGraphTraversingMojo extends AbstractJenkinsMojo {

    /**
     * Traverses the whole dependency tree rooted at the project.
     */
    protected void traverseProject() {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setProject(project);
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

        try {
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));
            collectRequest.setRepositories(RepositoryUtils.toRepos(project.getRemoteArtifactRepositories()));

            // Get dependencies from the project
            for (org.apache.maven.model.Dependency dependency : project.getDependencies()) {
                collectRequest.addDependency(RepositoryUtils.toDependency(dependency, null));
            }

            // Get managed dependencies if any
            if (project.getDependencyManagement() != null) {
                for (org.apache.maven.model.Dependency dependency : project.getDependencyManagement()
                        .getDependencies()) {
                    collectRequest.addManagedDependency(RepositoryUtils.toDependency(dependency, null));
                }
            }

            DependencyNode rootNode = repositorySystem
                    .collectDependencies(session.getRepositorySession(), collectRequest)
                    .getRoot();

            visit(rootNode);
        } catch (Exception e) {
            getLog().error("Failed to resolve dependencies", e);
        }
    }

    /**
     * Traverses a tree rooted at the given node.
     */
    protected void visit(DependencyNode g) {
        if (accept(g)) {
            for (DependencyNode dn : g.getChildren()) {
                visit(dn);
            }
        }
    }

    /**
     * Visits a node. Called at most once for any node in the dependency tree.
     *
     * @return true
     *         if the children should be traversed.
     */
    protected abstract boolean accept(DependencyNode g);
}
