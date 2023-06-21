package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

/**
 * HPI artifact handler
 */
@Named("hpi")
@Singleton
public class HpiArtifactHandler extends DefaultArtifactHandler {
    public HpiArtifactHandler() {
        super("hudson-plugin");
        setExtension("hpi");
        setLanguage("java");
        setAddedToClasspath(true);
    }
}
