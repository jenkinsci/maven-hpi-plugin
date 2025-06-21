package org.jenkinsci.maven.plugins.hpi;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.RepositoryUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDependencyGraphTraversingMojo extends AbstractJenkinsMojo {

    /**
     * Traverses the whole dependency tree rooted at the project.
     */
    protected void traverseProject() throws DependencyResolutionException {
        RepositorySystemSession repoSession = newRepositorySystemSession();
        DefaultArtifact rootArtifact = new DefaultArtifact(
                project.getGroupId(), project.getArtifactId(), project.getPackaging(), project.getVersion());
        Dependency rootDependency = new Dependency(rootArtifact, "compile");
        List<RemoteRepository> remoteRepos = project.getRemoteArtifactRepositories().stream()
                .map(RepositoryUtils::toRepo)
                .collect(Collectors.toList());
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(rootDependency);
        collectRequest.setRepositories(remoteRepos);
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        DependencyResult result = repositorySystem.resolveDependencies(repoSession, dependencyRequest);
        DependencyNode rootNode = result.getRoot();
        visit(rootNode, true);
    }

    private RepositorySystemSession newRepositorySystemSession() {
        DefaultRepositorySystemSession repoSession = new DefaultRepositorySystemSession(session.getRepositorySession());
        LocalRepository localRepo =
                new LocalRepository(session.getLocalRepository().getBasedir());
        repoSession.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(repoSession, localRepo));
        return repoSession;
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
