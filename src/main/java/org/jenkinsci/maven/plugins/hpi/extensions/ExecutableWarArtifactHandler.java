package org.jenkinsci.maven.plugins.hpi.extensions;

import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;

/**
 * Executable WAR artifact handler
 *
 * <p>See JENKINS-24064 for background; used to be {@code war-for-test} classifier
 */
@Named("executable-war")
@Singleton
public class ExecutableWarArtifactHandler extends DefaultArtifactHandler {
    public ExecutableWarArtifactHandler() {
        super("executable-war");
        setExtension("war");
        setLanguage("java");
        setAddedToClasspath(true);
    }
}
