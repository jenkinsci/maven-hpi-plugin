package org.jenkinsci.maven.plugins.jpi;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.Manifest.Section;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.IOUtil;
import sun.misc.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

/**
 * Build a war/webapp.
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: HpiMojo.java 33552 2010-08-03 23:28:55Z olamy $
 * @goal jpi
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class JpiMojo extends AbstractJpiMojo {

    /**
     * The name of the generated jpi.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     */
    private String jpiName;

    /**
     * Used to create .jar archive.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" role-hint="jar"
     * @required
     */
    private JarArchiver jarArchiver;

    /**
     * Used to create .jpi archive.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" role-hint="jar"
     * @required
     */
    private JarArchiver jpiArchiver;

    /**
     * The maven archive configuration to use.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * @component
     */
    private MavenProjectHelper projectHelper;

    // ----------------------------------------------------------------------
    // Implementation
    // ----------------------------------------------------------------------

    protected File getOutputFile(String extension) {
        return new File(new File(outputDirectory), jpiName + extension);
    }

    /**
     * Executes the WarMojo on the current project.
     *
     * @throws MojoExecutionException if an error occurred while building the webapp
     */
    public void execute() throws MojoExecutionException {
        try {
            performPackaging();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error assembling jpi: " + e.getMessage(), e);
        } catch (ManifestException e) {
            throw new MojoExecutionException("Error assembling jpi", e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error assembling jpi", e);
        } catch (ArchiverException e) {
            throw new MojoExecutionException("Error assembling jpi: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the webapp according to the <tt>mode</tt> attribute.
     *
     * @throws IOException
     * @throws ArchiverException
     * @throws ManifestException
     * @throws DependencyResolutionRequiredException
     *
     */
    private void performPackaging()
        throws IOException, ArchiverException, ManifestException, DependencyResolutionRequiredException, MojoExecutionException {

        File jpiFile = getOutputFile(".jpi");
        buildExplodedWebapp(getWebappDirectory());

        //generate war file
        getLog().info("Generating jpi " + jpiFile.getAbsolutePath());

        MavenArchiver archiver = new MavenArchiver();

        archiver.setArchiver(jpiArchiver);
        archiver.setOutputFile(jpiFile);

        File manifestFile = new File(getWebappDirectory(), "META-INF/MANIFEST.MF");
        generateManifest(manifestFile);
        Manifest manifest = loadManifest(manifestFile);

        jpiArchiver.addConfiguredManifest(manifest);
        jpiArchiver.addDirectory(getWebappDirectory(), getIncludes(), getExcludes());

        // create archive
        archiver.createArchive(project, archive);
        project.getArtifact().setFile(jpiFile);

        // also creates a jar file to be used when other plugins depend on this plugin.
        File jarFile = getOutputFile(".jar");
        archiver = new MavenArchiver();
        archiver.setArchiver(jarArchiver);
        archiver.setOutputFile(jarFile);
        jarArchiver.addConfiguredManifest(manifest);
        jarArchiver.addDirectory(getClassesDirectory());
        archiver.createArchive(project,archive);
        projectHelper.attachArtifact(project, "jar", null, jarFile);
    }

    private Manifest loadManifest(File f) throws IOException, ManifestException {
        InputStreamReader r = new InputStreamReader(new FileInputStream(f), "UTF-8");
        try {
            return new Manifest(r);
        } finally {
            IOUtil.close(r);
        }
    }

    /**
     * Generates a manifest file to be included in the .jpi file
     */
    private void generateManifest(File manifestFile) throws MojoExecutionException {
        // create directory if it doesn't exist yet
        if(!manifestFile.getParentFile().exists())
            manifestFile.getParentFile().mkdirs();

        getLog().info("Generating "+manifestFile);

        MavenArchiver ma = new MavenArchiver();
        ma.setOutputFile(manifestFile);

        PrintWriter printWriter = null;
        try {
            Manifest mf = ma.getManifest(project, archive.getManifest());
            Section mainSection = mf.getMainSection();
            setAttributes(mainSection);

            printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(manifestFile),"UTF-8"));
            mf.write(printWriter);
        } catch (ManifestException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } finally {
            IOUtil.close(printWriter);
        }
    }
}
