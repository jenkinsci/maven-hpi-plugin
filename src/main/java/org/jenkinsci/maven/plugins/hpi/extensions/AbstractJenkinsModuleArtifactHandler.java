package org.jenkinsci.maven.plugins.hpi.extensions;

import org.apache.maven.artifact.handler.DefaultArtifactHandler;

public abstract class AbstractJenkinsModuleArtifactHandler extends DefaultArtifactHandler {
    protected AbstractJenkinsModuleArtifactHandler() {
        super("jenkins-module");
        setExtension("jm");
        setLanguage("java");
        setAddedToClasspath(true);
    }
}
