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
     * To look up Archiver/UnArchiver implementations
     */
    @Component
    protected ArchiverManager archiverManager;

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

}
