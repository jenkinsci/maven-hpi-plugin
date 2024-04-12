package org.jenkinsci.maven.plugins.hpi;

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

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;

/**
 * Build a war/webapp.
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: HpiMojo.java 33552 2010-08-03 23:28:55Z olamy $
 */
@Mojo(name = "hpi", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class HpiMojo extends AbstractJenkinsManifestMojo {

    /**
     * The name of the generated hpi.
     */
    @Parameter(defaultValue = "${project.build.finalName}")
    private String hpiName;

    /**
     * The classifier to use when searching for the jar artifact.
     * @since 1.115
     */
    @Parameter(defaultValue = "")
    private String jarClassifier;

    /**
     * Used to create .jar archive.
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    /**
     * Used to create .hpi archive.
     */
    @Component(role = Archiver.class, hint = "jar")
    private JarArchiver hpiArchiver;

    /**
     * The maven archive configuration to use.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    // ----------------------------------------------------------------------
    // Implementation
    // ----------------------------------------------------------------------

    protected File getOutputFile(String extension) {
        return new File(new File(outputDirectory), hpiName + extension);
    }

    /**
     * Executes the WarMojo on the current project.
     *
     * @throws MojoExecutionException if an error occurred while building the webapp
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            performPackaging();
        } catch (IOException | ArchiverException | ManifestException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error assembling hpi: " + e.getMessage(), e);
        }
    }

    /**
     * Generates the webapp according to the {@code mode} attribute.
     */
    private void performPackaging()
            throws IOException, ArchiverException, ManifestException, DependencyResolutionRequiredException,
                    MojoExecutionException, MojoFailureException {

        // generate a manifest
        File manifestFile = new File(getWebappDirectory(), "META-INF/MANIFEST.MF");
        generateManifest(archive, manifestFile);
        Manifest manifest = loadManifest(manifestFile);

        getLog().info("Checking for attached .jar artifact "
                + (jarClassifier == null || jarClassifier.trim().isEmpty()
                        ? "..."
                        : "with classifier " + jarClassifier + "..."));
        File jarFile = null;
        for (Artifact artifact : project.getAttachedArtifacts()) {
            if (Objects.equals(project.getGroupId(), artifact.getGroupId())
                    && Objects.equals(project.getArtifactId(), artifact.getArtifactId())
                    && project.getArtifact().getVersionRange().equals(artifact.getVersionRange())
                    && Objects.equals("jar", artifact.getType())
                    && (jarClassifier == null || jarClassifier.trim().isEmpty()
                            ? !artifact.hasClassifier()
                            : Objects.equals(jarClassifier, artifact.getClassifier()))
                    && artifact.getFile() != null
                    && artifact.getFile().isFile()) {
                jarFile = artifact.getFile();
                getLog().info("Found attached .jar artifact: " + jarFile.getAbsolutePath());
                break;
            }
        }
        if (jarFile == null) {
            // create a jar file to be used when other plugins depend on this plugin.
            jarFile = getOutputFile(".jar");
            getLog().info("Generating jar " + jarFile.getAbsolutePath());
            MavenArchiver archiver = newMavenArchiver(jarArchiver, jarFile);
            jarArchiver.addConfiguredManifest(manifest);
            File indexJelly = new File(getClassesDirectory(), "index.jelly");
            if (!indexJelly.isFile()) {
                throw new MojoFailureException("Missing " + indexJelly
                        + ". Delete any <description> from pom.xml and create src/main/resources/index.jelly:\n"
                        + "<?jelly escape-by-default='true'?>\n"
                        + "<div>\n"
                        + "    The description here…\n"
                        + "</div>");
            }
            jarArchiver.addDirectory(getClassesDirectory());
            archiver.createArchive(session, project, archive);
        }
        // HACK Alert... due to how this plugin hacks the maven dependency model (by using a dependency on the
        // jar file and then rewriting them for hpi projects) we need to add the jar as an attached artifact
        // without a classifier. We do this even if the jarClassifier is non-blank as otherwise we would break
        // things. The use case of a non-blank jarClassifier is where you are using e.g. maven-shade-plugin
        // which will attach the shaded jar with a different classifier (though as of maven-shade-plugin:2.4.1
        // you cannot use the shade plugin to process anything other than the main artifact... but when
        // that gets fixed then this will make sense)
        projectHelper.attachArtifact(project, "jar", null, jarFile);

        // generate war file
        buildExplodedWebapp(getWebappDirectory(), jarFile);

        File hpiFile = getOutputFile(".hpi");
        getLog().info("Generating hpi " + hpiFile.getAbsolutePath());

        MavenArchiver archiver = newMavenArchiver(hpiArchiver, hpiFile);

        hpiArchiver.addConfiguredManifest(manifest);
        hpiArchiver.addDirectory(getWebappDirectory(), getIncludes(), getExcludes());

        // create archive
        archiver.createArchive(session, project, archive);
        project.getArtifact().setFile(hpiFile);
    }
}
