package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractDependencyGraphTraversingMojo extends AbstractJenkinsMojo {
    @Component
    protected DependencyGraphBuilder graphBuilder;

    /**
     * Traverses the whole dependency tree rooted at the project.
     */
    protected void traverseProject() throws DependencyGraphBuilderException {
        visit(graphBuilder.buildDependencyGraph(project, null));
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
     *      if the children should be traversed.
     */
    protected abstract boolean accept(DependencyNode g);
}
