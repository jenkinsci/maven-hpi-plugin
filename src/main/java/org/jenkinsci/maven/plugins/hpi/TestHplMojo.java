package org.jenkinsci.maven.plugins.hpi;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Generate .hpl file in the test class directory so that test harness can locate the plugin.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="test-hpl", requiresDependencyResolution = ResolutionScope.TEST)
public class TestHplMojo extends HplMojo {
    /**
     * Generates the hpl file in a known location.
     */
    @Override
    protected File computeHplFile() throws MojoExecutionException {
        File testDir = new File(project.getBuild().getTestOutputDirectory());
        testDir.mkdirs();
        File theHpl = new File(testDir,"the.hpl");
        String id = project.getArtifact().getId();
        try {
            writeMap(id, theHpl);
        } catch (IOException x) {
            getLog().error(x);
        }
        return theHpl;
    }

    private static File mapFile() {
        return new File(System.getProperty("user.home"), ".jenkins-hpl-map");
    }
    
    private static Properties loadMap(File mapFile) throws IOException {
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

    static /*@CheckForNull*/ File readMap(String id) throws IOException {
        String path = loadMap(mapFile()).getProperty(id);
        if (path == null) {
            return null;
        }
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    private void writeMap(String id, File theHpl) throws IOException {
        File mapFile = mapFile();
        Properties p = loadMap(mapFile());
        p.setProperty(id, theHpl.getAbsolutePath());
        OutputStream os = new FileOutputStream(mapFile);
        try {
            p.store(os, "List of development files for Jenkins plugins that have been built.");
        } finally {
            os.close();
        }
    }

}
