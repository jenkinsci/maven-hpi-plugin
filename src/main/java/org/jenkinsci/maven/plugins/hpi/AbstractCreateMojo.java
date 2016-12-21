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
package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.io.FileUtils;
import org.apache.maven.archetype.Archetype;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.components.interactivity.Prompter;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Abstract create Mojo.
 */
public abstract class AbstractCreateMojo extends AbstractMojo {
    @Component
    private Archetype archetype;

    @Component
    Prompter prompter;

    @Component
    private ArtifactRepositoryFactory artifactRepositoryFactory;

    @Component
    private ArtifactRepositoryLayout defaultArtifactRepositoryLayout;


    @Parameter(defaultValue = "${localRepository}", readonly = true, required = true)
    private ArtifactRepository localRepository;

    @Parameter(property = "groupId")
    String groupId;

    @Parameter(property = "artifactId")
    String artifactId;

    @Parameter(property = "version", defaultValue = "1.0-SNAPSHOT")
    String version;

    @Parameter(property = "packageName", alias = "package")
    String packageName;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
    private List pomRemoteRepositories;

    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    @Component
    private MavenProject project;

    String basedir = System.getProperty("user.dir");
    char sep = File.separatorChar;

    public void execute() throws MojoExecutionException {
        try {
            // ----------------------------------------------------------------------
            // archetypeGroupId
            // archetypeArtifactId
            // archetypeVersion
            //
            // localRepository
            // remoteRepository
            // parameters
            // ----------------------------------------------------------------------

            if (project.getFile() != null && groupId == null) {
                groupId = project.getGroupId();
            }

            if(groupId==null) {
                groupId = prompter.prompt("Enter the groupId of your plugin ["+DEFAULT_GROUPID+']');
                if (StringUtils.isEmpty(groupId))  groupId = DEFAULT_GROUPID;
            }

            String basedir = System.getProperty("user.dir");

            if(artifactId==null) {
                artifactId = prompter.prompt("Enter the artifactId of your plugin (normally without '-plugin' suffix)");
            }

            if (packageName == null) {
                getLog().info("Defaulting package to group ID + artifact ID: " + groupId+'.'+artifactId);

                packageName = replaceInvalidPackageNameChars(groupId+'.'+artifactId);
            }

            // TODO: context mojo more appropriate?
            Map<String, String> map = new HashMap<String, String>();

            map.put("basedir", basedir);
            map.put("package", packageName);
            map.put("groupId", groupId);
            map.put("artifactId", artifactId);
            map.put("version", version);

            List archetypeRemoteRepositories = new ArrayList(pomRemoteRepositories);

            if (remoteRepositories != null) {
                getLog().info("We are using command line specified remote repositories: " + remoteRepositories);

                archetypeRemoteRepositories = new ArrayList();

                String[] s = StringUtils.split(remoteRepositories, ",");

                for (int i = 0; i < s.length; i++) {
                    archetypeRemoteRepositories.add(createRepository(s[i], "id" + i));
                }
            }

            Properties props = new Properties();
            props.load(getClass().getResourceAsStream("plugin.properties"));

            archetype.createArchetype(
                props.getProperty("groupId"),
                props.getProperty("artifactId"),
                props.getProperty("version"), localRepository,
                archetypeRemoteRepositories, map);

            injectPOMDependencies();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create a new Jenkins plugin",e);
        }
    }

    private void injectPOMDependencies() throws IOException {
        File pomFile = new File(getOutDir(), "pom.xml");
        String pomString = FileUtils.readFileToString(pomFile, "UTF-8");

        pomString = pomString.replace("<!-- @PROPERIES_FRAG@ -->", getPropertiesPOMFrag());
        pomString = pomString.replace("<!-- @DEPENDENCIES_FRAG@ -->", getDependenciesPOMFrag());

        FileUtils.writeStringToFile(pomFile, pomString, "UTF-8");
    }

    protected String getPropertiesPOMFrag() {
        return "<!-- Baseline Jenkins version you use to build the plugin. Users must have this version or newer to run. -->\n" +
                "    <jenkins.version>1.625.3</jenkins.version>";
    }

    protected String getDependenciesPOMFrag() {
        return "<!-- If you want to depend on other plugins:\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>org.jenkins-ci.plugins</groupId>\n" +
                "      <artifactId>credentials</artifactId>\n" +
                "      <version>1.9.4</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>\n" +
                "  -->\n";
    }

    File getOutDir() {
        return new File( basedir, artifactId );
    }

    void copyTextResources(Collection<String> fileNames, String fromPath, File toDir) throws IOException {
        toDir.mkdirs();
        for( String s : fileNames ) {
            String filePath = fromPath + s;
            InputStream in = getClass().getResourceAsStream(filePath);

            if (in == null) {
                getLog().error("Failed to copy file '" + filePath + "'. File cannot be read.");
                continue;
            }

            FileWriter out = new FileWriter(new File(toDir, s));
            out.write(IOUtil.toString(in).replace("@artifactId@", artifactId));
            in.close();
            out.close();
        }
    }

    void copyBinaryResources(Collection<String> fileNames, String fromPath, File toDir) throws IOException {
        toDir.mkdirs();
        for( String s : fileNames ) {
            String filePath = fromPath + s;
            InputStream in = getClass().getResourceAsStream(filePath);

            if (in == null) {
                getLog().error("Failed to copy file '" + filePath + "'. File cannot be read.");
                continue;
            }

            FileOutputStream out = new FileOutputStream(new File(toDir, s));
            out.write(IOUtil.toByteArray(in));
            in.close();
            out.close();
        }
    }

    /**
     * Replaces all characters which are invalid in package names.
     *
     * TODO: Currently this only handles '-'
     */
    private String replaceInvalidPackageNameChars(String packageName) {
        StringBuilder buf = new StringBuilder(packageName);
        boolean changed = false;
        int i = 0;
        while (i < buf.length()) {
            char c = buf.charAt(i);
            if (c == '-') {
                buf.deleteCharAt(i);
                changed = true;
            } else {
                i++;
            }
        }

        if (changed && !packageName.equals(DEFAULT_GROUPID)) {
            getLog().warn("Package name contains invalid characters. Replacing it with '" + buf + "'");
        }
        return buf.toString();
    }

    //TODO: this should be put in John's artifact utils and used from there instead of being repeated here. Creating
    // artifact repositories is somewhat cumbersome atm.
    public ArtifactRepository createRepository(String url, String repositoryId) {
        // snapshots vs releases
        // offline = to turning the update policy off

        //TODO: we'll need to allow finer grained creation of repositories but this will do for now

        String updatePolicyFlag = ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS;

        String checksumPolicyFlag = ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN;

        ArtifactRepositoryPolicy snapshotsPolicy =
            new ArtifactRepositoryPolicy(true, updatePolicyFlag, checksumPolicyFlag);

        ArtifactRepositoryPolicy releasesPolicy =
            new ArtifactRepositoryPolicy(true, updatePolicyFlag, checksumPolicyFlag);

        return artifactRepositoryFactory.createArtifactRepository(repositoryId, url, defaultArtifactRepositoryLayout,
            snapshotsPolicy, releasesPolicy);
    }

    private static final String DEFAULT_GROUPID = "org.jenkins-ci.plugins";
}
