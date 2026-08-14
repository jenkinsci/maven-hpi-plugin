package org.jenkinsci.maven.plugins.hpi;

import java.io.File;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * A single override in the shadow web root built by {@link RunMojo} when {@code explodedWar} is set.
 *
 * <p>
 * {@link #path} is a {@code /}-separated location relative to the web root; {@link #target} is the
 * directory or file the symbolic link at that location should point at. This is how a project whose
 * web root is a build output (such as Jenkins core itself) can have parts of it resolve back to the
 * source tree, so that edits are picked up without repackaging.
 * </p>
 */
public class WebrootLink {

    @Parameter(required = true)
    private String path;

    @Parameter(required = true)
    private File target;

    public WebrootLink() {
        // required for Plexus instantiation
    }

    public String getPath() {
        return path;
    }

    public File getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return path + " -> " + target;
    }
}
