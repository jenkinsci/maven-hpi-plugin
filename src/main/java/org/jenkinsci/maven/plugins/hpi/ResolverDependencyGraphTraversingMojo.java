package org.jenkinsci.maven.plugins.hpi;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Base class for Mojos that need to traverse the dependency graph using the
 * Maven Resolver API.
 * This replaces the old AbstractDependencyGraphTraversingMojo that used
 * maven-dependency-tree.
 */
public abstract class ResolverDependencyGraphTraversingMojo extends AbstractJenkinsMojo {

    @Component
    protected RepositorySystem resolverRepositorySystem;

    /**
     * Traverses the whole dependency tree rooted at the project.
     */
    protected void traverseProject() throws MojoExecutionException {
        try {
            RepositorySystemSession repositorySystemSession = session.getRepositorySession();

            // Convert Maven project to Resolver dependencies
            List<Dependency> dependencies = project.getDependencies().stream()
                    .map(dep -> RepositoryUtils.toDependency(
                            dep, session.getRepositorySession().getArtifactTypeRegistry()))
                    .collect(Collectors.toList());

            // Create collect request
            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(new Dependency(RepositoryUtils.toArtifact(project.getArtifact()), null));
            collectRequest.setDependencies(dependencies);
            if (project.getDependencyManagement() != null) {
                collectRequest.setManagedDependencies(project.getDependencyManagement().getDependencies().stream()
                        .map(dep -> RepositoryUtils.toDependency(
                                dep, session.getRepositorySession().getArtifactTypeRegistry()))
                        .collect(Collectors.toList()));
            }
            collectRequest.setRepositories(RepositoryUtils.toRepos(project.getRemoteArtifactRepositories()));

            // Create dependency request
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

            // Resolve dependencies
            org.eclipse.aether.resolution.DependencyResult result =
                    resolverRepositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

            // Traverse the resolved dependency tree
            visit(result.getRoot());

        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve dependencies", e);
        }
    }

    /**
     * Traverses a tree rooted at the given node.
     */
    protected void visit(DependencyNode node) {
        visit(node, null);
    }

    /**
     * Traverses a tree rooted at the given node with parent context.
     */
    protected void visit(DependencyNode node, DependencyNode parent) {
        if (accept(node, parent)) {
            for (DependencyNode child : node.getChildren()) {
                visit(child, node);
            }
        }
    }

    /**
     * Visits a node. Called at most once for any node in the dependency tree.
     *
     * @param node   the current node
     * @param parent the parent node (null for root)
     * @return true if the children should be traversed.
     */
    protected abstract boolean accept(DependencyNode node, DependencyNode parent);
}
