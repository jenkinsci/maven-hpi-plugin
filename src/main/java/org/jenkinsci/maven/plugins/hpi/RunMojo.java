// ========================================================================
// $Id: RunMojo.java 36037 2010-10-18 09:48:58Z kohsuke $
// Copyright 2000-2004 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================
package org.jenkinsci.maven.plugins.hpi;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import org.apache.commons.io.FileUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * Runs Jenkins with the current plugin project by forking a new Java process running {@code java -jar jenkins.war}.
 *
 * <p>
 * This only needs the source files to be compiled, so run in the compile phase.
 * </p>
 *
 * <p>
 * To specify the HTTP port, use {@code -Dport=PORT}
 * </p>
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.COMPILE)
public class RunMojo extends AbstractJenkinsMojo {

    /**
     * The location of the war file.
     *
     * <p>
     * Normally this should be left empty, in which case the plugin loads it from the repository.
     * But this parameter allows that to be overwritten.
     * </p>
     */
    @Parameter(property = "webAppFile")
    private File webAppFile;

    /**
     * Path to {@code $JENKINS_HOME}. The launched Jenkins will use this directory as the workspace.
     */
    @Parameter(property = "hudsonHome", defaultValue = "${HUDSON_HOME}")
    private File hudsonHome;

    /**
     * Path to {@code $JENKINS_HOME}. The launched Jenkins will use this directory as the workspace.
     */
    @Parameter(property = "jenkinsHome", defaultValue = "${JENKINS_HOME}")
    private File jenkinsHome;

    /**
     * Decides the level of dependency resolution.
     *
     * This controls what plugins are made available to the
     * running Jenkins.
     */
    @Parameter(defaultValue = "test")
    protected String dependencyResolution;

    /**
     * Single directory for extra files to include in the WAR.
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    protected File warSourceDirectory;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * Specifies the HTTP port number.
     */
    @Parameter(property = "port", defaultValue = "8080")
    protected int defaultPort;

    /**
     * Specifies the host (network interface) to bind to.
     */
    @Parameter(property = "host", defaultValue = "localhost")
    protected String defaultHost;

    /**
     * Optional wildcard DNS domain to help set a distinct Jenkins root URL from every plugin.
     * Just prints a URL you ought to set.
     * The domain suffix is expected to be prepended with an identifier and an IP address ({@code xxx.127.0.0.1.$suffix}).
     * Recommended: {@code nip.io} but consider {@link #wildcardLocalhostDNS} instead.
     */
    @Parameter(property = "wildcardDNS")
    protected String wildcardDNS;

    /**
     * Optional wildcard localhost DNS domain to help set a distinct Jenkins root URL from every plugin.
     * Just prints a URL you ought to set.
     * The domain suffix is expected to be prepended with an identifier ({@code xxx.$suffix}) and to resolve to localhost.
     * Recommended: {@code localtest.me}
     */
    @Parameter(property = "wildcardLocalhostDNS")
    protected String wildcardLocalhostDNS;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins war.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @since 1.68
     */
    @Parameter
    protected String jenkinsWarId;

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
     * @since 1.94
     */
    @Parameter
    protected boolean pluginFirstClassLoader = false;

    /**
     * The context path for the webapp.
     */
    @Parameter(property = "contextPath", defaultValue = "/${project.artifactId}")
    protected String contextPath;

    @Component
    protected PluginWorkspaceMap pluginWorkspaceMap;

    private Process jenkinsProcess;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        project.setArtifacts(resolveDependencies(dependencyResolution));

        File basedir = project.getBasedir();

        // compute jenkinsHome
        if (jenkinsHome == null) {
            if (hudsonHome != null) {
                getLog().warn(
                                "Please use the `jenkinsHome` configuration parameter in place of the deprecated `hudsonHome` parameter");
                jenkinsHome = hudsonHome;
            }
            String h = System.getenv("JENKINS_HOME");
            if (h == null) {
                h = System.getenv("HUDSON_HOME");
            }
            if (h != null && !h.isEmpty() && /* see pom.xml override */ !h.equals("null")) {
                jenkinsHome = new File(h);
            } else {
                jenkinsHome = new File(basedir, "work");
            }
        }

        // look for jenkins.war
        Artifacts jenkinsArtifacts = Artifacts.of(project)
                .groupIdIs("org.jenkins-ci.main", "org.jvnet.hudson.main")
                .artifactIdIsNot("remoting"); // remoting moved to its own release cycle

        if (webAppFile == null) {
            Artifact jenkinsWarArtifact =
                    MavenArtifact.resolveArtifact(getJenkinsWarArtifact(), project, session, repositorySystem);
            webAppFile = jenkinsWarArtifact.getFile();
            if (webAppFile == null || !webAppFile.isFile()) {
                throw new MojoExecutionException("Could not find " + webAppFile + " from " + jenkinsWarArtifact);
            }
        }

        // make sure all the relevant Jenkins artifacts have the same version
        for (Artifact a : jenkinsArtifacts) {
            Artifact ba = jenkinsArtifacts.get(0);
            if (!a.getVersion().equals(ba.getVersion())) {
                throw new MojoExecutionException("Version of " + a.getId() + " is inconsistent with " + ba.getId());
            }
        }

        File pluginsDir = new File(jenkinsHome, "plugins");
        try {
            Files.createDirectories(pluginsDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + pluginsDir + "'", e);
        }

        generateHpl();

        // copy other dependency Jenkins plugins
        try {
            for (MavenArtifact a : getProjectArtifacts()) {
                if (!a.isPlugin(getLog())) {
                    continue;
                }

                // find corresponding .hpi file
                Artifact hpi =
                        artifactFactory.createArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), null, "hpi");
                hpi = MavenArtifact.resolveArtifact(hpi, project, session, repositorySystem);

                // check recursive dependency. this is a rare case that happens when we split out some things from the
                // core into a plugin
                if (hasSameGavAsProject(hpi)) {
                    continue;
                }

                if (hpi.getFile().isDirectory()) {
                    throw new UnsupportedOperationException(
                            hpi.getFile() + " is a directory and not packaged yet. this isn't supported");
                }

                File upstreamHpl = pluginWorkspaceMap.read(hpi.getId());
                String actualArtifactId = a.getActualArtifactId();
                if (actualArtifactId == null) {
                    throw new MojoExecutionException(
                            "Failed to load actual artifactId from " + a + " ~ " + a.getFile());
                }
                if (upstreamHpl != null) {
                    copyHpl(upstreamHpl, pluginsDir, actualArtifactId);
                } else {
                    copyPlugin(hpi.getFile(), pluginsDir, actualArtifactId);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin", e);
        }

        // Fork and run Jenkins
        try {
            startJenkins();
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to start Jenkins", e);
        }
    }

    @SuppressFBWarnings(
            value = "COMMAND_INJECTION",
            justification = "Command is constructed from controlled sources, not user input")
    private void startJenkins() throws IOException, InterruptedException, MojoExecutionException {
        List<String> command = new ArrayList<>();

        // Get Java executable
        String javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        command.add(javaExecutable);

        // Add JVM arguments from manifest
        List<String> addOpensArgs = getAddOpensFromManifest();
        if (!addOpensArgs.isEmpty()) {
            getLog().info("Adding --add-opens arguments from jenkins.war manifest");
            command.addAll(addOpensArgs);
        }

        // Add system properties
        command.add("-DJENKINS_HOME=" + jenkinsHome.getAbsolutePath());
        command.add("-Dstapler.trace=true");
        command.add("-Dstapler.jelly.noCache=true");
        command.add("-Dhudson.hpi.run=true");
        command.add("-Djenkins.moduleRoot=" + project.getBasedir().getAbsolutePath());

        // Add resource path if exists
        List<Resource> res = project.getBuild().getResources();
        if (!res.isEmpty()) {
            Resource r = res.get(0);
            command.add("-Dstapler.resourcePath=" + r.getDirectory());
        }

        // Run the Jenkins WAR
        command.add("-jar");
        command.add(webAppFile.getAbsolutePath());

        // Add Winstone arguments
        command.add("--httpPort=" + defaultPort);
        command.add("--httpListenAddress=" + defaultHost);
        if (contextPath != null && !contextPath.isEmpty() && !contextPath.equals("/")) {
            command.add("--prefix=" + contextPath);
        }

        // Display the command
        getLog().info("Starting Jenkins with command:");
        getLog().info(String.join(" ", command));

        String browserHost;
        if (!"localhost".equals(defaultHost)) {
            browserHost = defaultHost;
        } else if (wildcardLocalhostDNS != null) {
            browserHost = project.getArtifactId() + "." + wildcardLocalhostDNS;
        } else if (wildcardDNS != null) {
            browserHost = project.getArtifactId() + ".127.0.0.1." + wildcardDNS;
        } else {
            getLog().info("Try setting -DwildcardLocalhostDNS=localtest.me in a profile");
            browserHost = defaultHost;
        }
        getLog().info("===========> Browse to: http://" + browserHost + ":" + defaultPort + contextPath + "/");

        // Start the process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        pb.directory(project.getBasedir());

        jenkinsProcess = pb.start();

        // Add shutdown hook to kill the process
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jenkinsProcess != null && jenkinsProcess.isAlive()) {
                getLog().info("Stopping Jenkins...");
                jenkinsProcess.destroy();
                try {
                    jenkinsProcess.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }));

        // Wait for the process to complete
        int exitCode = jenkinsProcess.waitFor();
        if (exitCode != 0) {
            throw new MojoExecutionException("Jenkins process exited with code " + exitCode);
        }
    }

    private List<String> getAddOpensFromManifest() throws MojoExecutionException {
        List<String> args = new ArrayList<>();
        try {
            if (webAppFile != null && webAppFile.exists()) {
                try (JarFile jarFile = new JarFile(webAppFile)) {
                    java.util.jar.Manifest manifest = jarFile.getManifest();
                    if (manifest != null) {
                        String addOpens = manifest.getMainAttributes().getValue("Add-Opens");
                        if (addOpens != null && !addOpens.trim().isEmpty()) {
                            for (String module : addOpens.split("\\s+")) {
                                if (!module.isEmpty()) {
                                    args.add("--add-opens");
                                    args.add(module + "=ALL-UNNAMED");
                                }
                            }
                            getLog().debug("Found Add-Opens in manifest: " + addOpens);
                        }
                    }
                }
            }
        } catch (IOException e) {
            getLog().warn("Failed to read Add-Opens from jenkins.war manifest: " + e.getMessage());
        }
        return args;
    }

    private boolean hasSameGavAsProject(Artifact a) {
        return project.getGroupId().equals(a.getGroupId())
                && project.getArtifactId().equals(a.getArtifactId())
                && project.getVersion().equals(a.getVersion());
    }

    private void copyPlugin(File src, File pluginsDir, String shortName) throws IOException {
        File dst = new File(pluginsDir, shortName + ".jpi");
        File hpi = new File(pluginsDir, shortName + ".hpi");
        if (Files.isRegularFile(hpi.toPath())) {
            getLog().warn("Moving historical " + hpi + " to *.jpi");
            Files.move(hpi.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        VersionNumber dstV = versionOfPlugin(dst);
        if (versionOfPlugin(src).compareTo(dstV) < 0) {
            getLog().info("will not overwrite " + dst + " with " + src + " because " + dstV + " is newer");
            return;
        }
        getLog().info("Copying dependency Jenkins plugin " + src);
        FileUtils.copyFile(src, dst);
        // pin the dependency plugin, so that even if a different version of the same plugin is bundled to Jenkins,
        // we still use the plugin as specified by the POM of the plugin.
        Files.writeString(pluginsDir.toPath().resolve(shortName + ".jpi.pinned"), "pinned", StandardCharsets.US_ASCII);
        Files.deleteIfExists(
                new File(pluginsDir, shortName + ".jpl").toPath()); // in case we used to have a snapshot dependency
    }

    private VersionNumber versionOfPlugin(File p) throws IOException {
        if (!p.isFile()) {
            return new VersionNumber("0.0");
        }
        try (JarFile j = new JarFile(p)) {
            String v = j.getManifest().getMainAttributes().getValue("Plugin-Version");
            if (v == null) {
                throw new IOException("no Plugin-Version in " + p);
            }
            try {
                return new VersionNumber(v);
            } catch (IllegalArgumentException x) {
                throw new IOException("malformed Plugin-Version in " + p + ": " + x, x);
            }
        } catch (IOException x) {
            throw new IOException("not a valid JarFile: " + p, x);
        }
    }

    private void copyHpl(File src, File pluginsDir, String shortName) throws IOException {
        File dst = new File(pluginsDir, shortName + ".jpl");
        getLog().info("Copying snapshot dependency Jenkins plugin " + src);
        FileUtils.copyFile(src, dst);
        Files.writeString(pluginsDir.toPath().resolve(shortName + ".jpi.pinned"), "pinned", StandardCharsets.US_ASCII);
    }

    /**
     * Create a dot-hpl file.
     */
    private void generateHpl() throws MojoExecutionException {
        MojoExecutor.executeMojo(
                MojoExecutor.plugin(
                        MojoExecutor.groupId("org.jenkins-ci.tools"), MojoExecutor.artifactId("maven-hpi-plugin")),
                MojoExecutor.goal("hpl"),
                MojoExecutor.configuration(
                        MojoExecutor.element(MojoExecutor.name("jenkinsHome"), jenkinsHome.toString()),
                        MojoExecutor.element(MojoExecutor.name("pluginName"), project.getName()),
                        MojoExecutor.element(MojoExecutor.name("warSourceDirectory"), warSourceDirectory.toString()),
                        MojoExecutor.element(MojoExecutor.name("jenkinsCoreId"), jenkinsCoreId),
                        MojoExecutor.element(
                                MojoExecutor.name("pluginFirstClassLoader"), Boolean.toString(pluginFirstClassLoader)),
                        MojoExecutor.element(MojoExecutor.name("maskClasses"), maskClasses)),
                MojoExecutor.executionEnvironment(project, session, pluginManager));
    }

    /**
     * Performs the equivalent of "@requiresDependencyResolution" mojo attribute,
     * so that we can choose the scope at runtime.
     */
    protected Set<Artifact> resolveDependencies(String scope) throws MojoExecutionException {
        try {
            DependencyResolutionRequest request =
                    new DefaultDependencyResolutionRequest(project, session.getRepositorySession());
            request.setResolutionFilter(getDependencyFilter(scope));
            DependencyResolutionResult result = dependenciesResolver.resolve(request);

            Set<Artifact> artifacts = new LinkedHashSet<>();
            if (result.getDependencyGraph() != null
                    && !result.getDependencyGraph().getChildren().isEmpty()) {
                RepositoryUtils.toArtifacts(
                        artifacts,
                        result.getDependencyGraph().getChildren(),
                        List.of(project.getArtifact().getId()),
                        request.getResolutionFilter());
            }
            return artifacts;
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin", e);
        }
    }

    private static DependencyFilter getDependencyFilter(String scope) {
        switch (scope) {
            case Artifact.SCOPE_COMPILE:
                return new ScopeDependencyFilter(Artifact.SCOPE_RUNTIME, Artifact.SCOPE_TEST);
            case Artifact.SCOPE_RUNTIME:
                return new ScopeDependencyFilter(Artifact.SCOPE_SYSTEM, Artifact.SCOPE_PROVIDED, Artifact.SCOPE_TEST);
            case Artifact.SCOPE_TEST:
                return null;
            default:
                throw new IllegalArgumentException("unexpected scope: " + scope);
        }
    }

    public Set<MavenArtifact> getProjectArtifacts() {
        Set<MavenArtifact> r = new HashSet<>();
        for (Artifact a : project.getArtifacts()) {
            r.add(wrap(a));
        }
        return r;
    }

    protected Artifact getJenkinsWarArtifact() throws MojoExecutionException {
        for (Artifact a : resolveDependencies("test")) {
            boolean match;
            if (jenkinsWarId != null) {
                match = (a.getGroupId() + ':' + a.getArtifactId()).equals(jenkinsWarId);
            } else {
                match = (a.getArtifactId().equals("jenkins-war")
                                || a.getArtifactId().equals("hudson-war"))
                        && (a.getType().equals("executable-war") || a.getType().equals("war"));
            }
            if (match) {
                return a;
            }
        }

        if (jenkinsWarId != null) {
            getLog().error("Unable to locate jenkins.war in '" + jenkinsWarId + "'");
        } else {
            getLog().error("Unable to locate jenkins.war. Add the following dependency in your POM:\n" + "\n"
                    + "<dependency>\n"
                    + "  <groupId>org.jenkins-ci.main</groupId>\n"
                    + "  <artifactId>jenkins-war</artifactId>\n"
                    + "  <type>war</type>\n"
                    + "  <version>1.396<!-- replace this with the version you want--></version>\n"
                    + "  <scope>test</scope>\n"
                    + "</dependency>");
        }
        throw new MojoExecutionException("Unable to find jenkins.war");
    }
}
