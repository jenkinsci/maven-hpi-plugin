package org.jenkinsci.maven.plugins.hpi;
import org.eclipse.aether.graph.DependencyFilter;

public interface DependencyFilterFactory {
    DependencyFilter createFilter();
}
