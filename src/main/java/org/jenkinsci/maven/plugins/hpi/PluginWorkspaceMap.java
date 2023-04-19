package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import java.io.IOException;

/**
 * The contract part of {@code ~/.jenkins-hpl-map} that stores
 * the local workspaces of plugins under development.
 *
 * <p>
 * The contract offers read/write access to a virtual {@code String -> File} map.
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
public interface PluginWorkspaceMap {
    File read(String id) throws IOException;

    void write(String id, File dir) throws IOException;
}
