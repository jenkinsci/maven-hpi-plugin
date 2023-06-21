package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@code jenkins-module} packaging plugin bindings provider for {@code default} lifecycle
 *
 * <p>No longer used in official components as of <a href="https://jenkins.io/jep/230">JEP-230</a>
 */
@Named("jenkins-module")
@Singleton
public final class JenkinsModuleLifecycleMappingProvider extends AbstractJenkinsModuleLifecycleMappingProvider {}
