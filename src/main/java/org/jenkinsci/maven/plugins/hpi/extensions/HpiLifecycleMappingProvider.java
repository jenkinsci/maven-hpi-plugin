package org.jenkinsci.maven.plugins.hpi.extensions;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;

/**
 * {@code hpi} packaging plugin bindings provider for {@code default} lifecycle
 */
@Named("hpi")
@Singleton
public final class HpiLifecycleMappingProvider extends AbstractLifecycleMappingProvider {
    /**
     * Note: The current plugin does NOT have to have a version specified, as the version should be
     * specified in the effective POM (otherwise this lifecycle mapping would not be loaded at all).
     * Therefore, the version of the current plugin (in this case {@code maven-hpi-plugin}) is NEVER
     * considered and will come from the effective POM of the project using this plugin.
     *
     * <p>For all other versions, we use the version defined in the current Maven baseline.
     */
    static Map<String, LifecyclePhase> getBindings() {
        Map<String, LifecyclePhase> bindings = new LinkedHashMap<>();
        bindings.put(
                "validate",
                new LifecyclePhase(
                        "org.jenkins-ci.tools:maven-hpi-plugin:validate,org.jenkins-ci.tools:maven-hpi-plugin:validate-hpi"));
        bindings.put(
                "process-resources",
                new LifecyclePhase("org.apache.maven.plugins:maven-resources-plugin:2.6:resources"));
        bindings.put("compile", new LifecyclePhase("org.apache.maven.plugins:maven-compiler-plugin:3.1:compile"));
        bindings.put("process-classes", new LifecyclePhase("org.kohsuke:access-modifier-checker:1.31:enforce"));
        bindings.put("generate-test-sources", new LifecyclePhase("org.jenkins-ci.tools:maven-hpi-plugin:insert-test"));
        bindings.put(
                "process-test-resources",
                new LifecyclePhase("org.apache.maven.plugins:maven-resources-plugin:2.6:testResources"));
        bindings.put(
                "test-compile",
                new LifecyclePhase(
                        "org.apache.maven.plugins:maven-compiler-plugin:3.1:testCompile,org.jenkins-ci.tools:maven-hpi-plugin:test-hpl"));
        bindings.put("process-test-classes", new LifecyclePhase("org.jenkins-ci.tools:maven-hpi-plugin:test-runtime"));
        bindings.put(
                "test",
                new LifecyclePhase(
                        "org.jenkins-ci.tools:maven-hpi-plugin:resolve-test-dependencies,org.apache.maven.plugins:maven-surefire-plugin:2.12.4:test"));
        bindings.put("package", new LifecyclePhase("org.jenkins-ci.tools:maven-hpi-plugin:hpi"));
        bindings.put("install", new LifecyclePhase("org.apache.maven.plugins:maven-install-plugin:2.4:install"));
        bindings.put("deploy", new LifecyclePhase("org.apache.maven.plugins:maven-deploy-plugin:2.7:deploy"));
        return bindings;
    }

    public HpiLifecycleMappingProvider() {
        super(getBindings());
    }
}
