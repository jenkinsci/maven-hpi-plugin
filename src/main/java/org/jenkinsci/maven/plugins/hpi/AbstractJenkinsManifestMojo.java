/*
 * Copyright 2001-2018 The Apache Software Foundation, CloudBees, Inc.
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
package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.IOUtils;
import org.apache.maven.archiver.ManifestConfiguration;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Abstract class for Mojo implementations, which produce Jenkins-style manifests.
 * The Mojo may be used to not only package plugins, but also JAR files like Jenkins modules
 * @author Oleg Nenashev
 * @since 3.0
 * @see HpiMojo
 * @see JarMojo
 * @see HplMojo
 */
public abstract class AbstractJenkinsManifestMojo extends AbstractHpiMojo {

    /**
     * Optional - the oldest version of this plugin which the current version is
     * configuration-compatible with.
     * @see <a href="https://www.jenkins.io/doc/developer/plugin-development/mark-a-plugin-incompatible/">Mark a new plugin version as incompatible with older versions</a>
     */
    @Parameter(property = "hpi.compatibleSinceVersion")
    private String compatibleSinceVersion;

    /**
     * Optional - sandbox status of this plugin.
     */
    @Parameter
    private String sandboxStatus;

    /**
     * Specify the minimum version of Java that this plugin requires.
     *
     * @deprecated removed without replacement
     */
    @Deprecated
    @Parameter
    protected String minimumJavaVersion;

    /**
     * Generates a manifest file to be included in the .hpi file
     */
    protected void generateManifest(MavenArchiveConfiguration archive, File manifestFile) throws MojoExecutionException {
        // create directory if it doesn't exist yet
        if (!Files.isDirectory(manifestFile.toPath().getParent())) {
            try {
                Files.createDirectories(manifestFile.toPath().getParent());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to create parent directories for '" + manifestFile + "'", e);
            }
        }

        getLog().info("Generating " + manifestFile);

        MavenArchiver ma = new MavenArchiver();
        ma.setOutputFile(manifestFile);

        try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(manifestFile), StandardCharsets.UTF_8))) {
            ManifestConfiguration config = archive.getManifest();
            config.setAddDefaultSpecificationEntries(true);
            config.setAddDefaultImplementationEntries(true);
            Manifest mf = ma.getManifest(project, config);
            Manifest.ExistingSection mainSection = mf.getMainSection();
            setAttributes(mainSection);

            mf.write(printWriter);
        } catch (ManifestException | IOException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        }
    }

    protected void setAttributes(Manifest.ExistingSection mainSection) throws MojoExecutionException, ManifestException, IOException {
        File pluginImpl = new File(project.getBuild().getOutputDirectory(), "META-INF/services/hudson.Plugin");
        if(pluginImpl.exists()) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(pluginImpl), StandardCharsets.UTF_8))) {
                String pluginClassName = in.readLine();
                mainSection.addAttributeAndCheck(new Manifest.Attribute("Plugin-Class",pluginClassName));
            }
        }

        mainSection.addAttributeAndCheck(new Manifest.Attribute("Group-Id",project.getGroupId()));
        mainSection.addAttributeAndCheck(new Manifest.Attribute("Artifact-Id",project.getArtifactId()));
        mainSection.addAttributeAndCheck(new Manifest.Attribute("Short-Name",project.getArtifactId()));
        mainSection.addAttributeAndCheck(new Manifest.Attribute("Long-Name",pluginName));
        String url = project.getUrl();
        if(url!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Url", url));

        if (compatibleSinceVersion!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Compatible-Since-Version", compatibleSinceVersion));

        if (this.minimumJavaVersion != null && !this.minimumJavaVersion.isEmpty()) {
            getLog().warn("Ignoring deprecated minimumJavaVersion parameter."
                    + " This property should be removed from your plugin's POM."
                    + " In the future this warning will be changed to an error and will break the build.");
        }

        if (sandboxStatus!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Sandbox-Status", sandboxStatus));

        String v = project.getVersion();
        if (v.endsWith("-SNAPSHOT") && snapshotPluginVersionOverride!=null) {
            String nonSnapshotVersion = v.substring(0, v.length() - "-SNAPSHOT".length());
            if (!snapshotPluginVersionOverride.startsWith(nonSnapshotVersion)) {
                String message = "The snapshotPluginVersionOverride of " + snapshotPluginVersionOverride
                        + " does not start with the current target release version " + v;
                // there are be some legitimate use cases for this usage:
                // for example:
                // * If the development version is 1.x-SNAPSHOT and releases are e.g. 1.423
                //   and you want to test upgrading from 1.423 to the development version from a hosted update
                //   centre then you need the version reported to be after 1.423 using the version number
                //   comparison rules, thus you would need to override the version to something like
                //   1.424-20180430.123402-6 so that this new version is visible from Jenkins
                // Ordinarily, you would only be comparing either a release with a release or a
                // SNAPSHOT with a SNAPSHOT and thus the safety checks would not be required for normal use
                // but we provide this escape hatch just in case.
                if (failOnVersionOverrideToDifferentRelease) {
                    throw new MojoExecutionException(message);
                }
                getLog().warn(message);
            }
            getLog().info("Snapshot Plugin Version Override enabled. Using " + snapshotPluginVersionOverride
                    + " in place of " + v);
            v = snapshotPluginVersionOverride;
        }
        if (v.endsWith("-SNAPSHOT") && pluginVersionDescription==null) {
            String dt = gitRevParseHead();
            if (dt == null) {
                // if SHA1 isn't available, fall back to timestamp
                dt = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(new Date());
            } else {
                dt = dt.substring(0, 8);
            }
            pluginVersionDescription = "private-"+dt+"-"+System.getProperty("user.name");
        }
        if (pluginVersionDescription!=null)
            v += " (" + pluginVersionDescription + ")";

        if (!project.getPackaging().equals("jenkins-module")) {
            // Earlier maven-hpi-plugin used to look for this attribute to determine if a jar file is a Jenkins plugin.
            // While that's fixed, people out there might be still using it, so as a precaution when building a module
            // don't put this information in there.
            // The "Implementation-Version" baked by Maven should serve the same purpose if someone needs to know the version.
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Plugin-Version",v));
        }

        String jv = findJenkinsVersion();
        mainSection.addAttributeAndCheck(new Manifest.Attribute("Hudson-Version",jv));
        mainSection.addAttributeAndCheck(new Manifest.Attribute("Jenkins-Version",jv));

        if(maskClasses!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Mask-Classes",maskClasses));

        if (globalMaskClasses!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Global-Mask-Classes",globalMaskClasses));

        if(pluginFirstClassLoader)
            mainSection.addAttributeAndCheck( new Manifest.Attribute( "PluginFirstClassLoader", "true" ) );

        String dep = findDependencyPlugins();
        if(dep.length()>0)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Plugin-Dependencies",dep));

        if (project.getDevelopers() != null) {
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Plugin-Developers",getDevelopersForManifest()));
        }

        Boolean b = isSupportDynamicLoading();
        if (b!=null)
            mainSection.addAttributeAndCheck(new Manifest.Attribute("Support-Dynamic-Loading",b.toString()));

        // Extra info attributes
        addLicenseAttributesForManifest(mainSection);
        addPropertyAttributeIfNotNull(mainSection, "Plugin-ChangelogUrl", "hpi.pluginChangelogUrl");
        addPropertyAttributeIfNotNull(mainSection, "Plugin-LogoUrl", "hpi.pluginLogoUrl");
        addAttributeIfNotNull(mainSection, "Plugin-ScmConnection", project.getScm().getConnection());
        addAttributeIfNotNull(mainSection, "Plugin-ScmTag", project.getScm().getTag());
        addAttributeIfNotNull(mainSection, "Plugin-ScmUrl", project.getScm().getUrl());

        addAttributeIfNotNull(mainSection, "Plugin-GitHash", gitRevParseHead());
        addAttributeIfNotNull(mainSection, "Plugin-ModulePath", getModulePath());
    }

    /**
     * Finds and lists dependency plugins.
     */
    private String findDependencyPlugins() throws IOException, MojoExecutionException {
        StringBuilder buf = new StringBuilder();
        for (MavenArtifact a : getDirectDependencyArtfacts()) {
            if(a.isPlugin() && scopeFilter.include(a.artifact) && !a.hasSameGAAs(project)) {
                if(buf.length()>0)
                    buf.append(',');
                buf.append(a.getActualArtifactId());
                buf.append(':');
                buf.append(a.getActualVersion());
                if (a.isOptional()) {
                    buf.append(";resolution:=optional");
                }
            }
        }

        // check any "provided" scope plugin dependencies that are probably not what the user intended.
        // see http://jenkins-ci.361315.n4.nabble.com/Classloading-problem-when-referencing-classes-from-another-plugin-during-the-initialization-phase-of-td394967.html
        for (Artifact a : project.getDependencyArtifacts())
            if ("provided".equals(a.getScope()) && wrap(a).isPlugin())
                throw new MojoExecutionException(a.getId()+" is marked as 'provided' scope dependency, but it should be the 'compile' scope.");


        return buf.toString();
    }

    /**
     * Finds and lists developers specified in POM.
     */
    private String getDevelopersForManifest() throws IOException {
        StringBuilder buf = new StringBuilder();

        for (Developer d : project.getDevelopers()) {
            if (buf.length() > 0) {
                buf.append(',');
            }
            buf.append(d.getName() != null ? d.getName() : "");
            buf.append(':');
            buf.append(d.getId() != null ? d.getId() : "");
            buf.append(':');
            buf.append(d.getEmail() != null ? d.getEmail() : "");
        }

        return buf.toString();
    }

    protected Manifest loadManifest(File f) throws IOException, ManifestException {
        try (InputStream is = Files.newInputStream(f.toPath())) {
            return new Manifest(is);
        }
    }

    private void addLicenseAttributesForManifest(Manifest.ExistingSection target) throws ManifestException {
        final List<License> licenses = project.getLicenses();
        int licenseCounter = 1;
        for (License lic : licenses) {
            String licenseSuffix = licenseCounter == 1 ? "" : ("-" + licenseCounter);
            addAttributeIfNotNull(target, "Plugin-License-Name" + licenseSuffix, lic.getName());
            addAttributeIfNotNull(target, "Plugin-License-Url" + licenseSuffix, lic.getUrl());
            //TODO(oleg_nenashev): Can be enabled later if needed
            //addAttributeIfNotNull(target, "Plugin-License-Distribution" + licenseSuffix, lic.getDistribution());
            //addAttributeIfNotNull(target, "Plugin-License-Comments" + licenseSuffix, lic.getComments());
            licenseCounter++;
        }
    }

    /**
     * If the project is on Git, return its hash
     *
     * @return {@code null} if no Git repository is found
     */
    @CheckForNull
    private String gitRevParseHead() {
        String hash = gitRevParse("HEAD");
        if (hash == null) {
            return null;
        }
        if (hash.length() < 8) {
            // Git repository present, but without commits
            return null;
        }
        return hash;
    }

    @CheckForNull
    private String getModulePath() {
        // Execute "git rev-parse --show-toplevel", then remove that from "project.baseDir" and what we have left is the path
        String topLevel = gitRevParse("--show-toplevel");
        if (topLevel == null) {
            return null;
        }
        try {
            // git rev-parse can return Unix slashes on Windows, so let's ensure everything is canonical
            String f = new File(topLevel).getCanonicalPath();
            String b = project.getBasedir().getCanonicalPath();
            // No need to be defensive - the project must be in the Git directory for git-revparse to return non-null
            String path = b.substring(f.length());
            if (path.startsWith(File.separator)) {
                // For a single module project this will be the empty string
                path = path.substring(1);
            }
            // Normalize to Unix style
            path = path.replace(File.separatorChar, '/');
            // Normalize empty to null
            if (!path.isBlank()) {
                return path;
            }
        } catch (IOException e) {
            getLog().warn("Failed to obtain project's relative location in the Git repository", e);
        }
        return null;
    }

    @CheckForNull
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "Intended behavior")
    private String gitRevParse(String... args) {
        if (!project.getScm().getConnection().startsWith("scm:git")) {
            // Project is not using Git
            return null;
        }
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("rev-parse");
        command.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(command).directory(project.getBasedir()).redirectErrorStream(true).start();
            p.getOutputStream().close();
            String result;
            try (InputStream is = p.getInputStream()) {
                result = IOUtils.toString(is, Charset.defaultCharset()).trim();
            }
            if (p.waitFor() != 0) {
                // git rev-parse failed to run
                return null;
            }
            return result;
        } catch (IOException | InterruptedException e) {
            getLog().debug("Failed to run " + String.join(" ", command), e);
            return null;
        }
    }

    private void addAttributeIfNotNull(Manifest.ExistingSection target, String attributeName, String propertyValue)
            throws ManifestException {
        if (propertyValue != null) {
            target.addAttributeAndCheck(new Manifest.Attribute(attributeName, propertyValue));
        }
    }

    private void addPropertyAttributeIfNotNull(Manifest.ExistingSection target, String attributeName, String propertyName)
            throws ManifestException {
        String propertyValue = project.getProperties().getProperty(propertyName);
        if (propertyValue != null) {
            target.addAttributeAndCheck(new Manifest.Attribute(attributeName, propertyValue));
        }
    }
}
