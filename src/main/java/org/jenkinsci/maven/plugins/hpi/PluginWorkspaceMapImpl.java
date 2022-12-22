package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.codehaus.plexus.component.annotations.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Default and currently the only implementation of {@link PluginWorkspaceMap}
 *
 * @author Jesse Glick
 * @author Kohsuke Kawaguchi
 */
@Component(role=PluginWorkspaceMap.class)
public class PluginWorkspaceMapImpl implements PluginWorkspaceMap {
    private final File mapFile;

    public PluginWorkspaceMapImpl(File mapFile) {
        this.mapFile = mapFile;
    }

    public PluginWorkspaceMapImpl() {
        this.mapFile = new File(System.getProperty("user.home"), ".jenkins-hpl-map");
    }

    private Properties loadMap() throws IOException {
        Properties p = new Properties();
        if (mapFile.isFile()) {
            try (InputStream is = new FileInputStream(mapFile)) {
                p.load(is);
            }
        }
        return p;
    }

    @Override
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "TODO needs triage")
    public /*@CheckForNull*/ File read(String id) throws IOException {
        return read(id, null);
    }

    @Override
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "TODO needs triage")
    public File read(String id, String workspace) throws IOException {
        File matching = null;
        for (Map.Entry<Object,Object> entry : loadMap().entrySet()) {
            if (entry.getValue().equals(id)) {
                String path = (String) entry.getKey();
                File f = new File(path);
                if (f.exists()) {
                    matching = f;
                    if (workspace == null || path.startsWith(workspace)) {
                         break;
                    }
                }
            }
        }
        return matching;
    }

    @Override
    public void write(String id, File f) throws IOException {
        Properties p = loadMap();
        p.setProperty(f.getAbsolutePath(), id);
        try (OutputStream os = new FileOutputStream(mapFile)) {
            p.store(os, " List of development files for Jenkins plugins that have been built.");
        }
    }
}
