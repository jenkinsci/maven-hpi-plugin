package org.jenkinsci.maven.plugins.hpi;

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
            InputStream is = new FileInputStream(mapFile);
            try {
                p.load(is);
            } finally {
                is.close();
            }
        }
        return p;
    }

    @Override
    public /*@CheckForNull*/ File read(String id) throws IOException {
        for (Map.Entry<Object,Object> entry : loadMap().entrySet()) {
            if (entry.getValue().equals(id)) {
                String path = (String) entry.getKey();
                File f = new File(path);
                if (f.exists()) {
                    return f;
                }
            }
        }
        return null;
    }

    @Override
    public void write(String id, File f) throws IOException {
        Properties p = loadMap();
        p.setProperty(f.getAbsolutePath(), id);
        OutputStream os = new FileOutputStream(mapFile);
        try {
            p.store(os, " List of development files for Jenkins plugins that have been built.");
        } finally {
            os.close();
        }
    }
}
