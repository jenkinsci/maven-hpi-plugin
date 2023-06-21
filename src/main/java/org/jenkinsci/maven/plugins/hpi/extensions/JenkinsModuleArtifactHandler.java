package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Jenkins module artifact handler
 */
@Named("jenkins-module")
@Singleton
public class JenkinsModuleArtifactHandler extends AbstractJenkinsModuleArtifactHandler {}
