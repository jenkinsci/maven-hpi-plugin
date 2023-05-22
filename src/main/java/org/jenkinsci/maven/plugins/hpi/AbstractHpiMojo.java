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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import jenkins.YesNoMaybe;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.jenkinsci.maven.plugins.hpi.util.Utils;

public abstract class AbstractHpiMojo extends AbstractJenkinsMojo {

    @Component(role = MavenResourcesFiltering.class, hint = "default")
    protected MavenResourcesFiltering mavenResourcesFiltering;

    /**
     * The directory for the generated WAR.
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected String outputDirectory;

    /**
     * The directory containing generated classes.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private File classesDirectory;

    /**
     * Name of the plugin that Jenkins uses for display purpose.
     * It should be one line text.
     */
    @Parameter(defaultValue = "${project.name}")
    protected String pluginName;

    /**
     * Additional information that accompanies the version number of the plugin.
     *
     * Useful to distinguish snapshot builds.
     * For example, if you are building snapshot plugins from Jenkins, you can
     * put the build number in here by running Maven with "-Dplugin.version.description=$BUILD_TAG"
     */
    @Parameter(defaultValue = "${plugin.version.description}")
    protected String pluginVersionDescription;

    /**
     * Optional - the version to use in place of the project version in the event that the project version is a
     * -SNAPSHOT. If specified, the value <strong>must</strong> start with the project version after removing the
     * terminal {@code -SNAPSHOT}, for example if the project version is {@code 1.2.3-SNAPSHOT} then the
     * {@code snapshotPluginVersionOverride} could be {@code 1.2.3-rc45.cafebabe-SNAPSHOT} or
     * {@code 1.2.3-20180430.123233-56}, etc, but it could not be {@code 1.2.4} as that does not start with the
     * project version.
     *
     * When testing plugin builds on a locally hosted update centre, in order to be allowed to update the plugin,
     * the update centre must report the plugin version as greater than the currently installed plugin version.
     * If you are using a Continuous Delivery model for your plugin (i.e. where the master branch stays on a
     * version like {@code 1.x-SNAPSHOT} and releases are tagged but not merged back to master (in order to
     * prevent merge conflicts) then you will find that you cannot update the plugin when building locally as
     * {@code 1.x-SNAPSHOT} is never less than {@code 1.x-SNAPSHOT}. Thus in order to test plugin upgrade (in say
     * an acceptance test), you need to override the plugin version for non-releases. Typically you would use
     * something like <a href="https://github.com/stephenc/git-timestamp-maven-plugin">git-timestamp-maven-plugin</a>
     * to populate a property with the version and then use this configuration to provide the version.
     *
     * @see #failOnVersionOverrideToDifferentRelease
     * @since 2.4
     */
    @Parameter
    protected String snapshotPluginVersionOverride;

    /**
     * Controls the safety check that prevents a {@link #snapshotPluginVersionOverride} from switching to a different
     * release version.
     * @since 2.4
     */
    @Parameter(defaultValue = "true")
    protected boolean failOnVersionOverrideToDifferentRelease = true;

    /**
     * The directory where the webapp is built.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private File webappDirectory;

    /**
     * Single directory for extra files to include in the WAR.
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    protected File warSourceDirectory;

    /**
     * The list of webResources we want to transfer.
     */
    @Parameter
    private Resource[] webResources;

    @Parameter(defaultValue = "${project.build.filters}")
    private List<String> filters;

    /**
     * The path to the context.xml file to use.
     */
    @Parameter(defaultValue = "${maven.war.containerConfigXML}")
    private File containerConfigXML;

    /**
     * Directory to unpack dependent WARs into if needed
     */
    @Parameter(defaultValue = "${project.build.directory}/war/work")
    private File workDirectory;

    /**
     * To look up Archiver/UnArchiver implementations
     */
    @Component
    protected ArchiverManager archiverManager;

    private static final String WEB_INF = "WEB-INF";

    private static final String META_INF = "META-INF";

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
     * [ws|tab|CR|LF]+ separated list of package prefixes that your plugin doesn't want to see
     * from the core.
     *
     * <p>
     * Tokens in this list is prefix-matched against the fully-qualified class name, so add
     * "." to the end of each package name, like "com.foo. com.bar."
     */
    @Parameter
    protected String maskClasses;

    /**
     * Like the {@code maskClasses} parameter, but it applies at the boundary between core and
     * all the plugins.
     *
     * <p>
     * This mechanism is intended for those plugins that bring JavaEE APIs (such as the database plugin,
     * which brings in the JPA API.) Other plugins that depend on the database plugin can still see
     * the JPA API through the container classloader, so to make them all resolve to the JPA API in the
     * database plugin, the database plugin needs to rely on this mechanism.
     * @since 1.92
     */
    @Parameter
    protected String globalMaskClasses;

    /**
     * Change the classloader preference such that classes locally bundled in this plugin
     * will take precedence over those that are defined by the dependency plugins.
     *
     * <p>
     * This is useful if the plugins that you want to depend on exposes conflicting versions
     * of the libraries you are using, but enabling this switch makes your code
     * susceptible to classloader constraint violations.
     *
     * @since 1.53
     */
    @Parameter
    protected boolean pluginFirstClassLoader = false;

    /**
     * If true, test scope dependencies count as if they are normal dependencies.
     * This is only useful during hpi:run, so not exposing it as a configurable parameter.
     */
    ScopeArtifactFilter scopeFilter = new ScopeArtifactFilter("runtime");

    private static final String[] EMPTY_STRING_ARRAY = {};

    public File getClassesDirectory() {
        return classesDirectory;
    }

    public void setClassesDirectory(File classesDirectory) {
        this.classesDirectory = classesDirectory;
    }

    public File getWebappDirectory() {
        return webappDirectory;
    }

    public void setWebappDirectory(File webappDirectory) {
        this.webappDirectory = webappDirectory;
    }

    public void setWarSourceDirectory(File warSourceDirectory) {
        this.warSourceDirectory = warSourceDirectory;
    }

    public File getContainerConfigXML() {
        return containerConfigXML;
    }

    public void setContainerConfigXML(File containerConfigXML) {
        this.containerConfigXML = containerConfigXML;
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

    public void buildExplodedWebapp(File webappDirectory, File jarFile) throws MojoExecutionException {
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

            FileUtils.copyFileIfModified(jarFile, new File(getWebappDirectory(), "WEB-INF/lib/" + jarFile.getName()));
        } catch (IOException e) {
            throw new MojoExecutionException("Could not explode webapp...", e);
        } catch (MavenFilteringException e) {
            throw new MojoExecutionException("Could not copy webResources...", e);
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
    public void copyResources(File sourceDirectory, File webappDirectory) throws IOException {
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
     * Returns all the transitive dependencies.
     */
    public Set<MavenArtifact> getProjectArtfacts() {
        return wrap(Artifacts.of(project));
    }

    /**
     * Returns just the direct dependencies.
     */
    public Set<MavenArtifact> getDirectDependencyArtfacts() {
        return wrap(Artifacts.ofDirectDependencies(project));
    }

    protected Set<MavenArtifact> wrap(Iterable<Artifact> artifacts) {
        Set<MavenArtifact> r = new TreeSet<>();
        for (Artifact a : artifacts) {
            r.add(wrap(a));
        }
        return r;
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
    public void buildWebapp(MavenProject project, File webappDirectory) throws MojoExecutionException, IOException {
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
     * If the project is on Git, figure out Git SHA1.
     *
     * @return null if no git repository is found
     */
    public String getGitHeadSha1() {
        if (!project.getScm().getConnection().startsWith("scm:git")) {
            // Project is not using Git
            return null;
        }

        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(project.getBasedir())
                    .redirectErrorStream(true)
                    .start();
            p.getOutputStream().close();
            String v;
            try (InputStream is = p.getInputStream()) {
                v = IOUtils.toString(is, Charset.defaultCharset()).trim();
            }
            if (p.waitFor() != 0) {
                return null; // git rev-parse failed to run
            }

            if (v.length() < 8) {
                return null; // git repository present, but without commits
            }

            return v;
        } catch (IOException | InterruptedException e) {
            getLog().debug("Failed to run git rev-parse HEAD", e);
            return null;
        }
    }

    /**
     * Is the dynamic loading supported?
     *
     * False, if the answer is known to be "No". Otherwise null, if there are some extensions
     * we don't know we can dynamic load. Otherwise, if everything is known to be dynamic loadable, return true.
     */
    @CheckForNull
    protected Boolean isSupportDynamicLoading() throws IOException {
        try (URLClassLoader cl = new URLClassLoader(
                new URL[] {
                    new File(project.getBuild().getOutputDirectory()).toURI().toURL()
                },
                getClass().getClassLoader())) {

            EnumSet<YesNoMaybe> e = EnumSet.noneOf(YesNoMaybe.class);
            for (IndexItem<Extension, Object> i : Index.load(Extension.class, Object.class, cl)) {
                e.add(i.annotation().dynamicLoadable());
            }

            if (e.contains(YesNoMaybe.NO)) {
                return Boolean.FALSE;
            }
            if (e.contains(YesNoMaybe.MAYBE)) {
                return null;
            }
            return Boolean.TRUE;
        }
    }
}
