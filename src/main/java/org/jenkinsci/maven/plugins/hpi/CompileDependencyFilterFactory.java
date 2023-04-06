package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

public class CompileDependencyFilterFactory implements DependencyFilterFactory {
    @Override
    public DependencyFilter createFilter() {
        return new ScopeDependencyFilter(Artifact.SCOPE_RUNTIME, Artifact.SCOPE_TEST);
    }
}