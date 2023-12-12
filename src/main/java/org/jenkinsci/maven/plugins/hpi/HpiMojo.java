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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.jenkinsci.maven.plugins.hpi.util.Utils;

/**
 * Build a war/webapp.
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: HpiMojo.java 33552 2010-08-03 23:28:55Z olamy $
 */
@Mojo(name = "hpi", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class HpiMojo extends AbstractJenkinsManifestMojo {

    private static final String WEB_INF = "WEB-INF";

    private static final String META_INF = "META-INF";

    private static final String[] EMPTY_STRING_ARRAY = {};

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
     * The comma separated list of tokens to include in the WAR.
     */
    @Parameter(alias = "includes", defaultValue = "**")
    private String warSourceIncludes;

    /**
     * The comma separated list of tokens to exclude from the WAR.
     */
    @Parameter(alias = "excludes")
    private String warSourceExcludes;

    /**
     * The comma separated list of tokens to include when doing
     * a war overlay.
     */
    @Parameter(defaultValue = "**")
    private String dependentWarIncludes;

    /**
     * The comma separated list of tokens to exclude when doing
     * a way overlay.
     */
    @Parameter
    private String dependentWarExcludes;

    /**
     * The path to the context.xml file to use.
     */
    @Parameter(defaultValue = "${maven.war.containerConfigXML}")
    private File containerConfigXML;

    @Parameter(defaultValue = "${project.build.filters}")
    private List<String> filters;

    /**
     * The list of webResources we want to transfer.
     */
    @Parameter
    private Resource[] webResources;

    /**
     * Directory to unpack dependent WARs into if needed
     */
    @Parameter(defaultValue = "${project.build.directory}/war/work")
    private File workDirectory;

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

    public File getContainerConfigXML() {
        return containerConfigXML;
    }

    public void setContainerConfigXML(File containerConfigXML) {
        this.containerConfigXML = containerConfigXML;
    }

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
        File manifestFile = new File(webappDirectory, "META-INF/MANIFEST.MF");
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
            MavenArchiver archiver = new MavenArchiver();
            archiver.setArchiver(jarArchiver);
            archiver.setOutputFile(jarFile);
            jarArchiver.addConfiguredManifest(manifest);
            File indexJelly = new File(classesDirectory, "index.jelly");
            if (!indexJelly.isFile()) {
                throw new MojoFailureException("Missing " + indexJelly
                        + ". Delete any <description> from pom.xml and create src/main/resources/index.jelly:\n"
                        + "<?jelly escape-by-default='true'?>\n"
                        + "<div>\n"
                        + "    The description here…\n"
                        + "</div>");
            }
            jarArchiver.addDirectory(classesDirectory);
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
        buildExplodedWebapp(webappDirectory, jarFile);

        File hpiFile = getOutputFile(".hpi");
        getLog().info("Generating hpi " + hpiFile.getAbsolutePath());

        MavenArchiver archiver = new MavenArchiver();

        archiver.setArchiver(hpiArchiver);
        archiver.setOutputFile(hpiFile);

        hpiArchiver.addConfiguredManifest(manifest);
        hpiArchiver.addDirectory(webappDirectory, getIncludes(), getExcludes());

        // create archive
        archiver.createArchive(session, project, archive);
        project.getArtifact().setFile(hpiFile);
    }

    private void buildExplodedWebapp(File webappDirectory, File jarFile) throws MojoExecutionException {
        getLog().info("Exploding webapp...");

        try {
            Files.createDirectories(webappDirectory.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + webappDirectory + "'", e);
        }

        File webinfDir = new File(webappDirectory, WEB_INF);
        try {
            Files.createDirectories(webinfDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + webinfDir + "'", e);
        }

        File metainfDir = new File(webappDirectory, META_INF);
        try {
            Files.createDirectories(metainfDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + metainfDir + "'", e);
        }

        try {
            List<Resource> webResources = this.webResources != null ? List.of(this.webResources) : null;
            if (webResources != null && webResources.size() > 0) {
                copyResourcesWithFiltering(webResources, webappDirectory);
            }

            copyResources(warSourceDirectory, webappDirectory);

            if (containerConfigXML != null && StringUtils.isNotEmpty(containerConfigXML.getName())) {
                metainfDir = new File(webappDirectory, META_INF);
                String xmlFileName = containerConfigXML.getName();
                FileUtils.copyFileIfModified(containerConfigXML, new File(metainfDir, xmlFileName));
            }

            buildWebapp(project, webappDirectory);

            FileUtils.copyFileIfModified(jarFile, new File(webappDirectory, "WEB-INF/lib/" + jarFile.getName()));
        } catch (IOException e) {
            throw new MojoExecutionException("Could not explode webapp...", e);
        } catch (MavenFilteringException e) {
            throw new MojoExecutionException("Could not copy webResources...", e);
        }
    }

    /**
     * Builds the webapp for the specified project.
     * <p>
     * Classes, libraries and tld files are copied to
     * the {@code webappDirectory} during this phase.
     *
     * @param project         the maven project
     * @throws java.io.IOException if an error occurred while building the webapp
     */
    private void buildWebapp(MavenProject project, File webappDirectory) throws MojoExecutionException, IOException {
        getLog().info("Assembling webapp " + project.getArtifactId() + " in " + webappDirectory);

        File libDirectory = new File(webappDirectory, WEB_INF + "/lib");

        File tldDirectory = new File(webappDirectory, WEB_INF + "/tld");

        Set<MavenArtifact> artifacts = getProjectArtfacts();

        // Also capture test dependencies
        Set<MavenArtifact> dependencyArtifacts = getDirectDependencyArtfacts();

        List<String> duplicates = findDuplicates(artifacts);

        List<File> dependentWarDirectories = new ArrayList<>();

        // List up IDs of Jenkins plugin dependencies
        Set<String> jenkinsPlugins = new HashSet<>();
        Set<String> excludedArtifacts = new HashSet<>();
        for (MavenArtifact artifact : Utils.unionOf(artifacts, dependencyArtifacts)) {
            if (artifact.isPluginBestEffort(getLog())) {
                jenkinsPlugins.add(artifact.getId());
            }
            // Exclude dependency if it comes from test or provided trail.
            // Most likely a plugin transitive dependency but the trail through (test,provided) dep is shorter
            if (artifact.hasScope("test", "provided")) {
                excludedArtifacts.add(artifact.getId());
            }
        }

        OUTER:
        for (MavenArtifact artifact : artifacts) {
            getLog().debug("Considering artifact trail " + artifact.getDependencyTrail());
            if (jenkinsPlugins.contains(artifact.getId())) {
                continue; // plugin dependency need not be WEB-INF/lib
            }
            if (artifact.getDependencyTrail().size() >= 1) {
                if (jenkinsPlugins.contains(artifact.getDependencyTrail().get(1))) {
                    continue; // no need to have transitive dependencies through plugins in WEB-INF/lib.
                }
                if (excludedArtifacts.contains(artifact.getDependencyTrail().get(1))) {
                    continue; // exclude artifacts resolved through a test or provided scope
                }
            }
            // if the dependency goes through jenkins core, we don't need to bundle it in the war
            // because jenkins-core comes in the <provided> scope, I think this is a bug in Maven that it puts such
            // dependencies into the artifact list.
            for (String trail : artifact.getDependencyTrail()) {
                if (trail.contains(":hudson-core:") || trail.contains(":jenkins-core:")) {
                    continue OUTER;
                }
            }

            String targetFileName = artifact.getDefaultFinalName();

            getLog().debug("Processing: " + targetFileName);

            if (duplicates.contains(targetFileName)) {
                getLog().debug("Duplicate found: " + targetFileName);
                targetFileName = artifact.getGroupId() + "-" + targetFileName;
                getLog().debug("Renamed to: " + targetFileName);
            }

            // TODO: utilise appropriate methods from project builder
            ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact.artifact)) {
                if (artifact.getDependencyTrail().size() > 2) {
                    getLog().warn("Bundling transitive dependency " + targetFileName + " (via "
                            + artifact.getDependencyTrail().get(1).replaceAll("[^:]+:([^:]+):.+", "$1") + ")");
                } else {
                    getLog().info("Bundling direct dependency " + targetFileName);
                }
                String type = artifact.getType();
                if ("tld".equals(type)) {
                    FileUtils.copyFileIfModified(artifact.getFile(), new File(tldDirectory, targetFileName));
                } else {
                    if ("jar".equals(type) || "ejb".equals(type) || "ejb-client".equals(type)) {
                        FileUtils.copyFileIfModified(artifact.getFile(), new File(libDirectory, targetFileName));
                    } else {
                        if ("par".equals(type)) {
                            targetFileName = targetFileName.substring(0, targetFileName.lastIndexOf('.')) + ".jar";

                            getLog().debug("Copying " + artifact.getFile() + " to "
                                    + new File(libDirectory, targetFileName));

                            FileUtils.copyFileIfModified(artifact.getFile(), new File(libDirectory, targetFileName));
                        } else {
                            if ("war".equals(type)) {
                                dependentWarDirectories.add(unpackWarToTempDirectory(artifact));
                            } else {
                                getLog().debug("Skipping artifact of type " + type + " for WEB-INF/lib");
                            }
                        }
                    }
                }
            }
        }

        if (dependentWarDirectories.size() > 0) {
            getLog().info("Overlaying " + dependentWarDirectories.size() + " war(s).");

            // overlay dependent wars
            for (File dependentWarDirectory : dependentWarDirectories) {
                copyDependentWarContents(dependentWarDirectory, webappDirectory);
            }
        }
    }

    /**
     * Searches a set of artifacts for duplicate filenames and returns a list of duplicates.
     *
     * @param artifacts set of artifacts
     * @return List of duplicated artifacts
     */
    private List<String> findDuplicates(Set<MavenArtifact> artifacts) {
        List<String> duplicates = new ArrayList<>();
        List<String> identifiers = new ArrayList<>();
        for (MavenArtifact artifact : artifacts) {
            String candidate = artifact.getDefaultFinalName();
            if (identifiers.contains(candidate)) {
                duplicates.add(candidate);
            } else {
                identifiers.add(candidate);
            }
        }
        return duplicates;
    }

    /**
     * Unpacks war artifacts into a temporary directory inside {@code workDirectory}
     * named with the name of the war.
     *
     * @param artifact War artifact to unpack.
     * @return Directory containing the unpacked war.
     */
    private File unpackWarToTempDirectory(MavenArtifact artifact) throws MojoExecutionException {
        String name = artifact.getFile().getName();
        File tempLocation = new File(workDirectory, name.substring(0, name.length() - 4));

        boolean process = false;
        if (!Files.isDirectory(tempLocation.toPath())) {
            try {
                Files.createDirectories(tempLocation.toPath());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create directories for '" + tempLocation + "'", e);
            }
            process = true;
        } else if (artifact.getFile().lastModified() > tempLocation.lastModified()) {
            process = true;
        }

        if (process) {
            File file = artifact.getFile();
            try {
                unpack(file, tempLocation);
            } catch (NoSuchArchiverException e) {
                this.getLog().info("Skip unpacking dependency file with unknown extension: " + file.getPath());
            }
        }

        return tempLocation;
    }

    /**
     * Recursively copies contents of {@code srcDir} into {@code targetDir}.
     * This will not overwrite any existing files.
     *
     * @param srcDir    Directory containing unpacked dependent war contents
     * @param targetDir Directory to overlay srcDir into
     */
    private void copyDependentWarContents(File srcDir, File targetDir) throws MojoExecutionException {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(srcDir);
        scanner.setExcludes(getDependentWarExcludes());
        scanner.addDefaultExcludes();

        scanner.setIncludes(getDependentWarIncludes());

        scanner.scan();

        for (String dir : scanner.getIncludedDirectories()) {
            File includeDir = new File(targetDir, dir);
            try {
                Files.createDirectories(includeDir.toPath());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create directories for '" + includeDir + "'", e);
            }
        }

        for (String file : scanner.getIncludedFiles()) {
            File targetFile = new File(targetDir, file);

            // Do not overwrite existing files.
            if (!Files.exists(targetFile.toPath())) {
                try {
                    Files.createDirectories(targetFile.toPath().getParent());
                } catch (IOException e) {
                    throw new MojoExecutionException("Failed to create parent directories for '" + targetFile + "'", e);
                }
                try {
                    FileUtils.copyFileIfModified(new File(srcDir, file), targetFile);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error copying file '" + file + "' to '" + targetFile + "'", e);
                }
            }
        }
    }

    /**
     * Unpacks the archive file.
     *
     * @param file     File to be unpacked.
     * @param location Location where to put the unpacked files.
     */
    private void unpack(File file, File location) throws MojoExecutionException, NoSuchArchiverException {
        String archiveExt = FileUtils.getExtension(file.getAbsolutePath()).toLowerCase();

        try {
            UnArchiver unArchiver = archiverManager.getUnArchiver(archiveExt);
            unArchiver.setSourceFile(file);
            unArchiver.setDestDirectory(location);
            unArchiver.extract();
        } catch (ArchiverException e) {
            throw new MojoExecutionException("Error unpacking file: " + file + "to: " + location, e);
        }
    }

    /**
     * Copies webapp webResources from the specified directory.
     * <p>
     * Note that the {@code webXml} parameter could be null and may
     * specify a file which is not named {@code web.xml}. If the file
     * exists, it will be copied to the {@code META-INF} directory and
     * renamed accordingly.
     *
     * @param sourceDirectory the source directory
     * @param webappDirectory the target directory
     * @throws java.io.IOException if an error occurred while copying webResources
     */
    private void copyResources(File sourceDirectory, File webappDirectory) throws IOException {
        if (!sourceDirectory.equals(webappDirectory)) {
            getLog().info("Copy webapp webResources to " + webappDirectory.getAbsolutePath());
            if (warSourceDirectory.exists()) {
                String[] fileNames = getWarFiles(sourceDirectory);
                for (String fileName : fileNames) {
                    FileUtils.copyFileIfModified(
                            new File(sourceDirectory, fileName), new File(webappDirectory, fileName));
                }
            }
        }
    }

    /**
     * Returns a list of filenames that should be copied
     * over to the destination directory.
     *
     * @param sourceDir the directory to be scanned
     * @return the array of filenames, relative to the sourceDir
     */
    private String[] getWarFiles(File sourceDir) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDir);
        scanner.setExcludes(getExcludes());
        scanner.addDefaultExcludes();

        scanner.setIncludes(getIncludes());

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Returns a string array of the excludes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to exclude
     */
    protected String[] getExcludes() {
        List<String> excludeList = new ArrayList<>();
        if (StringUtils.isNotEmpty(warSourceExcludes)) {
            excludeList.addAll(List.of(StringUtils.split(warSourceExcludes, ",")));
        }

        // if contextXML is specified, omit the one in the source directory
        if (containerConfigXML != null && StringUtils.isNotEmpty(containerConfigXML.getName())) {
            excludeList.add("**/" + META_INF + "/" + containerConfigXML.getName());
        }

        return excludeList.toArray(EMPTY_STRING_ARRAY);
    }

    /**
     * Returns a string array of the includes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to include
     */
    protected String[] getIncludes() {
        return StringUtils.split(Objects.toString(warSourceIncludes), ",");
    }

    /**
     * Returns a string array of the excludes to be used
     * when adding dependent wars as an overlay onto this war.
     *
     * @return an array of tokens to exclude
     */
    protected String[] getDependentWarExcludes() {
        String[] excludes;
        if (StringUtils.isNotEmpty(dependentWarExcludes)) {
            excludes = StringUtils.split(dependentWarExcludes, ",");
        } else {
            excludes = EMPTY_STRING_ARRAY;
        }
        return excludes;
    }

    /**
     * Returns a string array of the includes to be used
     * when adding dependent wars as an overlay onto this war.
     *
     * @return an array of tokens to include
     */
    protected String[] getDependentWarIncludes() {
        return StringUtils.split(Objects.toString(dependentWarIncludes), ",");
    }

    /**
     * Copies webapp webResources from the specified directory.
     * <p>
     * Note that the {@code webXml} parameter could be null and may
     * specify a file which is not named {@code web.xml}. If the file
     * exists, it will be copied to the {@code META-INF} directory and
     * renamed accordingly.
     *
     * @param resources        the resources to copy
     * @param webappDirectory  the target directory
     * @throws MavenFilteringException if an error occurred while copying webResources
     */
    public void copyResourcesWithFiltering(List<Resource> resources, File webappDirectory)
            throws MavenFilteringException {
        MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(
                resources,
                webappDirectory,
                project,
                StandardCharsets.UTF_8.name(),
                filters,
                Collections.emptyList(),
                session);
        mavenResourcesExecution.setEscapeString("\\");
        mavenResourcesFiltering.filterResources(mavenResourcesExecution);
    }

}
