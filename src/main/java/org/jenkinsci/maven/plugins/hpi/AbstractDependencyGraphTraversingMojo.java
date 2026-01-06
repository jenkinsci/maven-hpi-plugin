package org.jenkinsci.maven.plugins.hpi;

import java.util.*;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.*;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Migrate from maven-dependency-tree to direct use of Resolver API
 * @author Kohsuke Kawaguchi, flowerlake
 */
public abstract class AbstractDependencyGraphTraversingMojo extends AbstractJenkinsMojo {

    /**
     * Resolves and traverses the project's dependency tree.
     * <p>
     * This method uses the Maven Resolver API to resolve dependencies of the project
     *
     * @throws DependencyResolutionException if dependency resolution fails
     */
    protected void traverseProject() throws DependencyResolutionException {
        RepositorySystemSession repoSession = session.getRepositorySession();
        MavenProject shadow = project.clone();

        ArtifactTypeRegistry artifactTypeRegistry = repoSession.getArtifactTypeRegistry();
        List<Dependency> projectDependencies = shadow.getDependencies().stream()
                .map(dep -> RepositoryUtils.toDependency(dep, artifactTypeRegistry))
                .toList();

        List<Dependency> managedDependencies = new ArrayList<>();
        if (shadow.getDependencyManagement() != null) {
            managedDependencies = shadow.getDependencyManagement().getDependencies().stream()
                    .map(dep -> RepositoryUtils.toDependency(dep, artifactTypeRegistry))
                    .toList();
        }

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRootArtifact(RepositoryUtils.toArtifact(shadow.getArtifact()));
        collectRequest.setRepositories(shadow.getRemoteProjectRepositories());
        collectRequest.setDependencies(projectDependencies);
        collectRequest.setManagedDependencies(managedDependencies);

        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        DependencyNode root = repositorySystem
                .resolveDependencies(repoSession, dependencyRequest)
                .getRoot();
        visit(root, true);
    }

    /**
     * Traverses a tree rooted at the given node.
     */
    protected void visit(DependencyNode g, boolean isRoot) {
        if (accept(g, isRoot)) {
            for (DependencyNode dn : g.getChildren()) {
                visit(dn, false);
            }
        }
    }

    /**
     * Visits a node. Called at most once for any node in the dependency tree.
     *
     * @return true
     * if the children should be traversed.
     */
    protected abstract boolean accept(DependencyNode g, boolean isRoot);
}
