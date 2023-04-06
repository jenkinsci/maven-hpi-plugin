package org.jenkinsci.maven.plugins.hpi;

import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.graph.DependencyFilter;

public class DependencyFilterProvider {
    Map<String, DependencyFilterFactory> FACTORIES = 
        Map.of(
            Artifact.SCOPE_COMPILE, new CompileDependencyFilterFactory(),
            Artifact.SCOPE_RUNTIME, new RuntimeDependencyFilterFactory(),
            Artifact.SCOPE_TEST, new TestDependencyFilterFactory()
        );

    public DependencyFilter createFilter(String scope) {
        DependencyFilterFactory factory = FACTORIES.get(scope);
        if (factory == null) {
            throw new IllegalArgumentException("unexpected scope: " + scope);
        }
        return factory.createFilter();
    }
}