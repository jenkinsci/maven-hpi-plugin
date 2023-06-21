package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Like {@link JenkinsModuleArtifactHandler} but for backward compatibility
 *
 * @deprecated use {@link JenkinsModuleArtifactHandler}
 */
@Deprecated
@Named("jm")
@Singleton
public class JmArtifactHandler extends AbstractJenkinsModuleArtifactHandler {}
