package org.jenkinsci.maven.plugins.hpi.extensions;

import java.util.Map;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

public abstract class AbstractJenkinsModuleLifecycleMappingProvider extends AbstractLifecycleMappingProvider {
    private static Map<String, LifecyclePhase> getBindings() {
        Map<String, LifecyclePhase> bindings = HpiLifecycleMappingProvider.getBindings();
        bindings.remove("validate");
        bindings.remove("process-test-classes");
        return bindings;
    }

    protected AbstractJenkinsModuleLifecycleMappingProvider() {
        super(getBindings());
    }
}
