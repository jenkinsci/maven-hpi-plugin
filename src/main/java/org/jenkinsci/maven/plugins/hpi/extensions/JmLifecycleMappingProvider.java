package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Like {@link JenkinsModuleLifecycleMappingProvider} but for backward compatibility
 *
 * @deprecated use {@link JenkinsModuleLifecycleMappingProvider}
 */
@Deprecated
@Named("jm")
@Singleton
public final class JmLifecycleMappingProvider extends AbstractJenkinsModuleLifecycleMappingProvider {}
