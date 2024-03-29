package org.jenkinsci.maven.plugins.hpi.extensions;

import java.util.List;
import java.util.Map;
import javax.inject.Provider;
import org.apache.maven.lifecycle.mapping.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

/**
 * Base lifecycle provider
 */
public abstract class AbstractLifecycleMappingProvider implements Provider<LifecycleMapping> {
    private static final String DEFAULT_LIFECYCLE_KEY = "default";

    private final Lifecycle defaultLifecycle;
    private final LifecycleMapping lifecycleMapping;

    protected AbstractLifecycleMappingProvider(Map<String, LifecyclePhase> bindings) {
        defaultLifecycle = new Lifecycle();
        defaultLifecycle.setId(DEFAULT_LIFECYCLE_KEY);
        defaultLifecycle.setLifecyclePhases(bindings);

        lifecycleMapping = new DefaultLifecycleMapping(defaultLifecycle);
    }

    @Override
    public LifecycleMapping get() {
        return lifecycleMapping;
    }

    private static class DefaultLifecycleMapping implements LifecycleMapping {
        private final Lifecycle lifecycle;

        DefaultLifecycleMapping(Lifecycle lifecycle) {
            this.lifecycle = lifecycle;
        }

        @Override
        public Map<String, Lifecycle> getLifecycles() {
            return Map.of(DEFAULT_LIFECYCLE_KEY, lifecycle);
        }

        @Deprecated
        @Override
        public List<String> getOptionalMojos(String lifecycle) {
            return null;
        }

        @Deprecated
        @Override
        public Map<String, String> getPhases(String lifecycle) {
            if (DEFAULT_LIFECYCLE_KEY.equals(lifecycle)) {
                return this.lifecycle.getPhases();
            } else {
                return null;
            }
        }
    }
}
