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

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.VersionNumber;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
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
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

/**
 * Runs Jenkins with the current plugin project using Winstone (java -jar jenkins.war).
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.COMPILE)
public class RunMojo extends AbstractHpiMojo {
    private static final Map<String, String> REQUIRED_PACKAGES_TO_TEST_CLASSES =
            Map.of("java.lang", "String$CaseInsensitiveComparator", "java.util", "UUID$Holder");

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

    @Component
    private BuildPluginManager pluginManager;

    /**
     * Specifies the HTTP port number.
     *
     * If connectors are configured in the Mojo, that'll take precedence.
     */
    @Parameter(property = "port", defaultValue = "8080")
    protected int defaultPort;

    /**
     * Specifies the host (network interface) to bind to.
     *
     * If connectors are configured in the Mojo, that'll take precedence.
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
     * If true, the context will be restarted after a line feed on
     * the input console. Enabled by default.
     *
     * @deprecated do not use
     */
    @Deprecated
    @Parameter(property = "jetty.consoleForceReload", defaultValue = "true")
    protected boolean consoleForceReload;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins core jar.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @since 1.65
     */
    @Parameter
    protected String jenkinsCoreId;

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
     * List of loggers to define.
     * Keys are logger names (usually package or class names);
     * values are level names (such as {@code FINE}).
     * @since 1.98
     */
    @Parameter
    private Map<String, String> loggers;

    private Collection<Logger> loggerReferences; // just to prevent GC

    /**
     * Specify the minimum version of Java that this plugin requires.
     *
     * @deprecated removed without replacement
     */
    @Deprecated
    @Parameter
    private String minimumJavaVersion;

    /**
     * The context path for the webapp. Defaults to the
     * name of the webapp's artifact.
     *
     * @deprecated Use &lt;webApp&gt;&lt;contextPath&gt; instead.
     */
    @Deprecated
    @Parameter(readonly = true, required = true, defaultValue = "/${project.artifactId}")
    protected String contextPath;

    @Component
    protected PluginWorkspaceMap pluginWorkspaceMap;

    /**
     * Compatibility shim for older configurations which used Jetty's <webApp> config.
     * We currently use it only for the contextPath/prefix mapping.
     */
    @Parameter
    private WebApp webApp;

    /**
     * Simple bean used for parsing <webApp> configuration.
     * Must be public for Plexus to instantiate reliably.
     */
    public static final class WebApp {
        /**
         * Maps to <webApp><contextPath>...</contextPath></webApp>.
         */
        @Parameter
        public String contextPath;

        public WebApp() {
            // required for Plexus instantiation
        }

        public String getContextPath() {
            return contextPath;
        }
    }

    /**
     * Compatibility shim for older configurations which used Jetty's <systemProperties> config.
     * These will be passed to the forked Jenkins JVM as {@code -Dkey=value}.
     */
    @Parameter
    private Map<String, String> systemProperties;

    @Override
    @SuppressFBWarnings(
            value = "COMMAND_INJECTION",
            justification =
                    "ProcessBuilder arguments are constructed from internal plugin configuration and not user input.")
    public void execute() throws MojoExecutionException, MojoFailureException {
        getProject().setArtifacts(resolveDependencies(dependencyResolution));

        File basedir = getProject().getBasedir();

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
        Artifacts jenkinsArtifacts = Artifacts.of(getProject())
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

        // set JENKINS_HOME
        setSystemPropertyIfEmpty("JENKINS_HOME", jenkinsHome.getAbsolutePath());
        File pluginsDir = new File(jenkinsHome, "plugins");
        try {
            Files.createDirectories(pluginsDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + pluginsDir + "'", e);
        }

        // enable view auto refreshing via stapler
        setSystemPropertyIfEmpty("stapler.jelly.noCache", "true");

        List<Resource> res = getProject().getBuild().getResources();
        if (!res.isEmpty()) {
            // pick up the first one and use it
            Resource r = res.get(0);
            setSystemPropertyIfEmpty("stapler.resourcePath", r.getDirectory());
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

        if (System.getProperty("java.util.logging.config.file") == null) {
            // see org.apache.juli.logging.DirectJDKLog
            System.setProperty("org.apache.juli.formatter", SupportLogFormatter.class.getName());
        }

        if (loggers != null) {
            for (Handler h : LogManager.getLogManager().getLogger("").getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    h.setLevel(Level.ALL);
                }
            }
            loggerReferences = new LinkedList<>();
            for (Map.Entry<String, String> logger : loggers.entrySet()) {
                Logger l = Logger.getLogger(logger.getKey());
                loggerReferences.add(l);
                l.setLevel(Level.parse(logger.getValue()));
            }
        }

        // Determine context path / prefix
        String effectiveContextPath = null;
        if (webApp != null
                && webApp.getContextPath() != null
                && !webApp.getContextPath().trim().isEmpty()) {
            effectiveContextPath = webApp.getContextPath().trim();
        } else if (contextPath != null && !contextPath.trim().isEmpty()) {
            // legacy parameter kept for compatibility
            effectiveContextPath = contextPath.trim();
        }

        // Determine the effective bind host (used for Winstone and URL hinting)
        String effectiveHost = (defaultHost == null || defaultHost.trim().isEmpty()) ? "localhost" : defaultHost.trim();
        // If wildcard DNS is enabled, Jenkins will be accessed via a hostname different from the bind host.
        // In that case, default to listening on all interfaces unless the user explicitly configured a host.
        boolean wildcardEnabled =
                (wildcardLocalhostDNS != null && !wildcardLocalhostDNS.trim().isEmpty())
                        || (wildcardDNS != null && !wildcardDNS.trim().isEmpty());
        if (wildcardEnabled
                && (defaultHost == null || defaultHost.trim().isEmpty() || "localhost".equals(effectiveHost))) {
            effectiveHost = "0.0.0.0";
        }

        // Decide what Jenkins URL should be (producing the host users will browse to).
        String externalHost = effectiveHost;
        if (wildcardEnabled) {
            String id = getProject().getArtifactId();
            if (wildcardLocalhostDNS != null && !wildcardLocalhostDNS.trim().isEmpty()) {
                // expected: <id>.<suffix> -> resolves to localhost
                externalHost = id + "." + wildcardLocalhostDNS.trim();
            } else {
                // historical expected: <id>.127.0.0.1.<suffix>
                externalHost = id + ".127.0.0.1." + wildcardDNS.trim();
            }
        }

        String jenkinsUrl = buildJenkinsUrl(externalHost, defaultPort, effectiveContextPath);
        getLog().info("===========> Browse to: " + jenkinsUrl);
        setSystemPropertyIfEmpty("JENKINS_URL", jenkinsUrl);

        // Prepare JVM arguments
        String argLine = getProject().getProperties().getProperty("argLine", "");
        String addOpens = getProject().getProperties().getProperty("jenkins.addOpens", "");
        String insaneHook = getProject().getProperties().getProperty("jenkins.insaneHook", "");
        String javaAgent = getProject().getProperties().getProperty("jenkins.javaAgent", "");

        // The test harness may store these as Surefire-style argfile placeholders like "@{jenkins.addOpens}".
        // When launching `java` directly, such tokens are interpreted as @argfiles and will fail.
        // Expand them to the underlying property values before building the command.
        argLine = expandAtPropertyToken(argLine);
        addOpens = expandAtPropertyToken(addOpens);
        insaneHook = expandAtPropertyToken(insaneHook);
        javaAgent = expandAtPropertyToken(javaAgent);

        List<String> cmd = new ArrayList<>();
        String javaExe = System.getProperty("java.home") + "/bin/java";
        cmd.add(javaExe);

        // Add configured system properties early
        if (systemProperties != null && !systemProperties.isEmpty()) {
            for (Map.Entry<String, String> e : systemProperties.entrySet()) {
                if (e.getKey() == null || e.getKey().trim().isEmpty()) {
                    continue;
                }
                String key = e.getKey().trim();
                String val = e.getValue() == null ? "" : e.getValue();
                cmd.add("-D" + key + "=" + val);
            }
        }

        addArgs(cmd, argLine);
        addArgs(cmd, addOpens);
        addArgs(cmd, insaneHook);
        addArgs(cmd, javaAgent);

        cmd.add("-jar");
        cmd.add(webAppFile.getAbsolutePath());

        // Winstone options must come after the WAR path.
        // Make the configured host/port effective.
        if (!effectiveHost.isEmpty()) {
            cmd.add("--httpListenAddress=" + effectiveHost);
        }
        if (defaultPort > 0) {
            cmd.add("--httpPort=" + defaultPort);
        }

        // Pass context path to Winstone
        if (effectiveContextPath != null) {
            String prefix = effectiveContextPath.trim();
            // Winstone expects --prefix=<value> (no space). Keep the leading slash.
            cmd.add("--prefix=" + prefix);
        }

        getLog().info("Launching Jenkins: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(jenkinsHome);
        pb.inheritIO();
        pb.environment().put("JENKINS_HOME", jenkinsHome.getAbsolutePath());
        try {
            Process proc = pb.start();
            int exitCode = proc.waitFor();
            if (exitCode != 0) {
                throw new MojoExecutionException("Jenkins exited with code " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to launch Jenkins", e);
        }
    }

    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "workaround JDK11")
    private static List<String> unavailableRequiredPackages() {
        final List<String> packages = new ArrayList<>();
        for (Map.Entry<String, String> e : REQUIRED_PACKAGES_TO_TEST_CLASSES.entrySet()) {
            final String key = e.getKey();
            final String value = e.getValue();
            try {
                final Class<?> clazz = Class.forName(key + "." + value);
                if (clazz.isEnum()) {
                    clazz.getMethod("values").invoke(null);
                } else {
                    Constructor<?> c = clazz.getDeclaredConstructor();
                    c.setAccessible(true);
                    c.newInstance();
                }
            } catch (InaccessibleObjectException ex) {
                packages.add(key);
            } catch (Exception ignore) {
                // in old versions of JDK some classes could be unavailable
            }
        }
        return packages;
    }

    private static void openPackages(Collection<String> packagesToOpen) throws Throwable {
        // Reflection-based opening of packages is not portable and not supported in all JVMs.
        // Instead, log a warning to the user.
        if (!packagesToOpen.isEmpty()) {
            System.err.println("WARNING: The following packages may require --add-opens: " + packagesToOpen);
        }
    }

    @Nullable
    @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "workaround JDK11")
    private static Collection<?> allModules() {
        // calling ModuleLayer.boot().modules() by reflection
        try {
            final Object boot =
                    Class.forName("java.lang.ModuleLayer").getMethod("boot").invoke(null);
            if (boot == null) {
                return null;
            }
            final Object modules = boot.getClass().getMethod("modules").invoke(boot);
            return (Collection<?>) modules;
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean hasSameGavAsProject(Artifact a) {
        return getProject().getGroupId().equals(a.getGroupId())
                && getProject().getArtifactId().equals(a.getArtifactId())
                && getProject().getVersion().equals(a.getVersion());
    }

    private void setSystemPropertyIfEmpty(String name, String value) {
        if (System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
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
        // TODO skip .pinned file creation if Jenkins version is >= 2.0
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

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(a, repositorySystem, artifactFactory, projectBuilder, session, project);
    }

    protected Artifact getJenkinsWarArtifact() throws MojoExecutionException {
        // First try to find an explicitly declared Jenkins WAR dependency (historical behavior).
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

        // Fallback: resolve jenkins-war based on the Jenkins core version from dependencies.
        // This makes `hpi:run` work even when the consuming plugin doesn't declare a test-scoped jenkins-war.
        String inferredVersion = findJenkinsVersion();
        String[] gav =
                Optional.ofNullable(jenkinsWarId).map(id -> id.split(":", 2)).orElse(null);
        String groupId = (gav != null && gav.length == 2) ? gav[0] : "org.jenkins-ci.main";
        String artifactId = (gav != null && gav.length == 2) ? gav[1] : "jenkins-war";

        getLog().info("No Jenkins WAR dependency found; resolving " + groupId + ":" + artifactId + ":war:"
                + inferredVersion);
        Artifact war = artifactFactory.createArtifact(groupId, artifactId, inferredVersion, null, "war");
        return MavenArtifact.resolveArtifact(war, project, session, repositorySystem);
    }

    protected MavenProject getProject() {
        return project;
    }

    /**
     * Expands tokens of the form "@{some.prop}" into their resolved property values.
     *
     * <p>Surefire may inject such placeholders into argLine (or store them in properties) and later expand them.
     * When we launch {@code java} directly we must do the expansion ourselves, otherwise the JVM interprets
     * leading {@code @} as an argument file reference.
     */
    private String expandAtPropertyToken(String value) {
        if (value == null) {
            return "";
        }
        String v = value;

        // Fast path
        if (!v.contains("@{")) {
            return v;
        }

        // Replace all occurrences of @{key}
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("@\\{([^}]+)}").matcher(v);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String resolved = project.getProperties().getProperty(key);
            if (resolved == null) {
                resolved = session.getSystemProperties().getProperty(key);
            }
            if (resolved == null) {
                resolved = System.getProperty(key);
            }
            if (resolved == null) {
                getLog().warn("Unable to resolve placeholder @{" + key + "}; skipping");
                resolved = "";
            }
            // Ensure any $ or \ in resolved values don't get interpreted by Matcher
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(resolved.trim()));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    /**
     * Adds a whitespace-separated string of arguments to the command list.
     * This is intentionally simple since these properties are expected to be JVM args without quoting.
     */
    private static void addArgs(List<String> cmd, String args) {
        if (args == null) {
            return;
        }
        String trimmed = args.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String part : trimmed.split("\\s+")) {
            if (!part.isEmpty()) {
                cmd.add(part);
            }
        }
    }

    /**
     * Builds a suggested Jenkins root URL when wildcard DNS settings are used.
     */
    @Nullable
    private static String buildJenkinsUrl(String host, int port, @Nullable String contextPath) {
        if (host == null || host.isBlank()) {
            return null;
        }
        StringBuilder url = new StringBuilder();
        url.append("http://").append(host.trim());
        if (port != 80 && port > 0) {
            url.append(":").append(port);
        }
        if (contextPath != null && !contextPath.isBlank()) {
            String cp = contextPath.trim();
            if (!cp.startsWith("/")) {
                url.append('/');
            }
            url.append(cp);
        }
        if (url.charAt(url.length() - 1) != '/') {
            url.append('/');
        }
        return url.toString();
    }
}
