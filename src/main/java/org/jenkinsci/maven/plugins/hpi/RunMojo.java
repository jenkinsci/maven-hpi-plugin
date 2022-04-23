//========================================================================
//$Id: RunMojo.java 36037 2010-10-18 09:48:58Z kohsuke $
//Copyright 2000-2004 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================
package org.jenkinsci.maven.plugins.hpi;

import hudson.util.VersionNumber;
import io.jenkins.lib.support_log_formatter.SupportLogFormatter;
import org.apache.commons.io.FileUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleDependencyResolver;
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
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.maven.plugin.MavenServerConnector;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.util.Scanner;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Runs Jenkins with the current plugin project.
 *
 * <p>
 * This only needs the source files to be compiled, so run in the compile phase.
 * </p>
 *
 * <p>
 * To specify the HTTP port, use {@code -Djetty.port=PORT}
 * </p>
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="run", requiresDependencyResolution=ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.COMPILE)
public class RunMojo extends AbstractJettyMojo {

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

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
    protected ArtifactResolver artifactResolver;

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    private ProjectDependenciesResolver dependenciesResolver;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * Specifies the HTTP port number.
     *
     * If connectors are configured in the Mojo, that'll take precedence.
     */
    @Parameter(property = "port")
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
     * Recommended: {@code nip.io}
     */
    @Parameter(property = "wildcardDNS")
    protected String wildcardDNS;

    /**
     * If true, the context will be restarted after a line feed on
     * the input console. Enabled by default.
     */
    @Parameter(property = "jetty.consoleForceReload", defaultValue = "true")
    protected boolean consoleForceReload;

    @Component
    protected ProjectBuilder projectBuilder;

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
     * @since 1.94
     */
    @Parameter
    protected boolean pluginFirstClassLoader = false;

    /**
     * List of additional System properties to set
     *
     * @since 1.85
     */
    @Parameter
    private Map<String, String> systemProperties;

    /**
     * List of loggers to define.
     * Keys are logger names (usually package or class names);
     * values are level names (such as {@code FINE}).
     * @since 1.98
     */
    @Parameter
    private Map<String,String> loggers;

    private Collection<Logger> loggerReferences; // just to prevent GC

    /**
     * Specify the minimum version of Java that this plugin requires.
     */
    @Parameter(required = true)
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getProject().setArtifacts(resolveDependencies(dependencyResolution));

        File basedir = getProject().getBasedir();

        if (webApp == null || webApp.getContextPath() == null) {
            if (contextPath != null) {
                getLog().warn("Please use `webApp/contextPath` configuration parameter in place of the deprecated `contextPath` parameter");
                if (webApp == null) {
                    try {
                        webApp = new JettyWebAppContext();
                    } catch (Exception e) {
                        throw new MojoExecutionException("Failed to initialize webApp configuration", e);
                    }
                }
                webApp.setContextPath(contextPath);
            }
        }

        // compute jenkinsHome
        if(jenkinsHome ==null) {
            if (hudsonHome != null) {
                getLog().warn("Please use the `jenkinsHome` configuration parameter in place of the deprecated `hudsonHome` parameter");
                jenkinsHome = hudsonHome;
            }
            String h = System.getenv("JENKINS_HOME");
            if (h == null) {
                h = System.getenv("HUDSON_HOME");
            }
            if (h != null && !h.isEmpty() && /* see pom.xml override */!h.equals("null")) {
                jenkinsHome = new File(h);
            } else {
                jenkinsHome = new File(basedir, "work");
            }
        }

        // auto-enable stapler trace, unless otherwise configured already.
        setSystemPropertyIfEmpty("stapler.trace", "true");
        // allow Jetty to accept a bigger form so that it can handle update center JSON post
        setSystemPropertyIfEmpty("org.eclipse.jetty.Request.maxFormContentSize","-1");
        // general-purpose system property so that we can tell from Jenkins if we are running in the hpi:run mode.
        setSystemPropertyIfEmpty("hudson.hpi.run","true");
        // expose the current top-directory of the plugin
        setSystemPropertyIfEmpty("jenkins.moduleRoot", basedir.getAbsolutePath());

        if (systemProperties != null && !systemProperties.isEmpty()) {
            for (Map.Entry<String,String> entry : systemProperties.entrySet()) {
                if (entry.getKey() != null && entry.getValue()!=null) {
                    System.setProperty( entry.getKey(), entry.getValue() );
                }
            }
        }

        // look for jenkins.war
        Artifacts jenkinsArtifacts = Artifacts.of(getProject())
                .groupIdIs("org.jenkins-ci.main","org.jvnet.hudson.main")
                .artifactIdIsNot("remoting");       // remoting moved to its own release cycle

        if (webAppFile == null) {
            Artifact jenkinsWarArtifact = getJenkinsWarArtifact();
            try {
                jenkinsWarArtifact = artifactResolver.resolveArtifact(session.getProjectBuildingRequest(), jenkinsWarArtifact).getArtifact();
            } catch (ArtifactResolverException x) {
                throw new MojoExecutionException("Could not resolve " + jenkinsWarArtifact + ": " + x, x);
            }
            webAppFile = jenkinsWarArtifact.getFile();
            if (webAppFile == null || !webAppFile.isFile()) {
                throw new MojoExecutionException("Could not find " + webAppFile + " from " + jenkinsWarArtifact);
            }
        }

        // make sure all the relevant Jenkins artifacts have the same version
        for (Artifact a : jenkinsArtifacts) {
            Artifact ba = jenkinsArtifacts.get(0);
            if(!a.getVersion().equals(ba.getVersion()))
                throw new MojoExecutionException("Version of "+a.getId()+" is inconsistent with "+ba.getId());
        }

        // set JENKINS_HOME
        setSystemPropertyIfEmpty("JENKINS_HOME",jenkinsHome.getAbsolutePath());
        File pluginsDir = new File(jenkinsHome, "plugins");
        try {
            Files.createDirectories(pluginsDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create directories for '" + pluginsDir + "'", e);
        }

        // enable view auto refreshing via stapler
        setSystemPropertyIfEmpty("stapler.jelly.noCache","true");

        List<Resource> res = getProject().getBuild().getResources();
        if(!res.isEmpty()) {
            // pick up the first one and use it
            Resource r = res.get(0);
            setSystemPropertyIfEmpty("stapler.resourcePath",r.getDirectory());
        }


        generateHpl();

        // copy other dependency Jenkins plugins
        try {
            for( MavenArtifact a : getProjectArtifacts() ) {
                if(!a.isPluginBestEffort(getLog())) {
                    continue;
                }

                // find corresponding .hpi file
                Artifact hpi = artifactFactory.createArtifact(a.getGroupId(),a.getArtifactId(),a.getVersion(),null,"hpi");
                hpi = artifactResolver.resolveArtifact(session.getProjectBuildingRequest(), hpi).getArtifact();

                // check recursive dependency. this is a rare case that happens when we split out some things from the core
                // into a plugin
                if (hasSameGavAsProject(hpi))
                    continue;

                if (hpi.getFile().isDirectory())
                    throw new UnsupportedOperationException(hpi.getFile()+" is a directory and not packaged yet. this isn't supported");

                File upstreamHpl = pluginWorkspaceMap.read(hpi.getId());
                String actualArtifactId = a.getActualArtifactId();
                if (actualArtifactId == null) {
                    throw new MojoExecutionException("Failed to load actual artifactId from " + a + " ~ " + a.getFile());
                }
                if (upstreamHpl != null) {
                    copyHpl(upstreamHpl, pluginsDir, actualArtifactId);
                } else {
                    copyPlugin(hpi.getFile(), pluginsDir, actualArtifactId);
                }
            }
        } catch (IOException | ArtifactResolverException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
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
            for (Map.Entry<String,String> logger : loggers.entrySet()) {
                Logger l = Logger.getLogger(logger.getKey());
                loggerReferences.add(l);
                l.setLevel(Level.parse(logger.getValue()));
            }
        }

        super.execute();
    }

    private boolean hasSameGavAsProject(Artifact a) {
        return getProject().getGroupId().equals(a.getGroupId())
            && getProject().getArtifactId().equals(a.getArtifactId())
            && getProject().getVersion().equals(a.getVersion());
    }

    private void setSystemPropertyIfEmpty(String name, String value) {
        if(System.getProperty(name)==null)
            System.setProperty(name, value);
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
        FileUtils.writeStringToFile(new File(dst + ".pinned"), "pinned");
        Files.deleteIfExists(new File(pluginsDir, shortName + ".jpl").toPath()); // in case we used to have a snapshot dependency
    }
    private VersionNumber versionOfPlugin(File p) throws IOException {
        if (!p.isFile()) {
            return new VersionNumber("0.0");
        }
        JarFile j;
        try {
            j = new JarFile(p);
        } catch (IOException x) {
            throw new IOException("not a valid JarFile: " + p, x);
        }
        try {
            String v = j.getManifest().getMainAttributes().getValue("Plugin-Version");
            if (v == null) {
                throw new IOException("no Plugin-Version in " + p);
            }
            try {
                return new VersionNumber(v);
            } catch (IllegalArgumentException x) {
                throw new IOException("malformed Plugin-Version in " + p + ": " + x, x);
            }
        } finally {
            j.close();
        }
    }

    private void copyHpl(File src, File pluginsDir, String shortName) throws IOException {
        File dst = new File(pluginsDir, shortName + ".jpl");
        getLog().info("Copying snapshot dependency Jenkins plugin " + src);
        FileUtils.copyFile(src, dst);
        FileUtils.writeStringToFile(new File(pluginsDir, shortName + ".jpi.pinned"), "pinned");
    }

    /**
     * Create a dot-hpl file.
     */
    private void generateHpl() throws MojoExecutionException {
        MojoExecutor.executeMojo(
                MojoExecutor.plugin(
                        MojoExecutor.groupId("org.jenkins-ci.tools"),
                        MojoExecutor.artifactId("maven-hpi-plugin")),
                MojoExecutor.goal("hpl"),
                MojoExecutor.configuration(
                        MojoExecutor.element(MojoExecutor.name("jenkinsHome"), jenkinsHome.toString()),
                        MojoExecutor.element(MojoExecutor.name("pluginName"), project.getName()),
                        MojoExecutor.element(MojoExecutor.name("warSourceDirectory"), warSourceDirectory.toString()),
                        MojoExecutor.element(MojoExecutor.name("jenkinsCoreId"), jenkinsCoreId),
                        MojoExecutor.element(MojoExecutor.name("pluginFirstClassLoader"), Boolean.toString(pluginFirstClassLoader)),
                        MojoExecutor.element(MojoExecutor.name("maskClasses"), maskClasses),
                        MojoExecutor.element(MojoExecutor.name("minimumJavaVersion"), minimumJavaVersion)),
                MojoExecutor.executionEnvironment(project, session, pluginManager));
    }

    @Override
    public void configureWebApplication() throws Exception {
        // Jetty tries to do this in WebAppContext.resolveWebApp but it failed to delete the directory.
        File t = webApp.getTempDirectory();
        if (t==null)    t = new File(getProject().getBuild().getDirectory(),"tmp");
        File extractedWebAppDir= new File(t, "webapp");
        if (isExtractedWebAppDirStale(extractedWebAppDir, webAppFile)) {
            FileUtils.deleteDirectory(extractedWebAppDir);
        }
        
        super.configureWebApplication();
        getWebAppConfig().setWar(webAppFile.getCanonicalPath());
        for (Artifact a : project.getArtifacts()) {
            if (a.getGroupId().equals("org.jenkins-ci.main") && a.getArtifactId().equals("jenkins-core")) {
                File coreBasedir = pluginWorkspaceMap.read(a.getId());
                if (coreBasedir != null) {
                    String extraCP = new File(coreBasedir, "src/main/resources").toURI() + "," + new File(coreBasedir, "target/classes").toURI();
                    getLog().info("Will load directly from " + extraCP);
                    getWebAppConfig().setExtraClasspath(extraCP);
                }
            }
        }
        HashLoginService hashLoginService = (new HashLoginService("default"));
        UserStore userStore = new UserStore();
        hashLoginService.setUserStore(userStore);
        userStore.addUser("alice", new Password("alice"), new String[] {"user", "female"});
        userStore.addUser("bob", new Password("bob"), new String[] {"user", "male"});
        userStore.addUser("charlie", new Password("charlie"), new String[] {"user", "male"});
        getWebAppConfig().getSecurityHandler().setLoginService(hashLoginService);
    }

    private static final String VERSION_PATH = "META-INF/maven/org.jenkins-ci.main/jenkins-war/pom.properties";
    private static final String VERSION_PROP = "version";
    private boolean isExtractedWebAppDirStale(File extractedWebAppDir, File webApp) throws IOException {
        if (!extractedWebAppDir.isDirectory()) {
            getLog().info(extractedWebAppDir + " does not yet exist, will receive " + webApp);
            return false;
        }
        if (extractedWebAppDir.lastModified() < webApp.lastModified()) {
            getLog().info(extractedWebAppDir + " is older than " + webApp + ", will recreate");
            return true;
        }
        File extractedPath = new File(extractedWebAppDir, VERSION_PATH);
        if (!extractedPath.isFile()) {
            getLog().warn("no such file " + extractedPath);
            return false;
        }
        InputStream is = new FileInputStream(extractedPath);
        String extractedVersion;
        try {
            extractedVersion = loadVersion(is);
        } finally {
            is.close();
        }
        if (extractedVersion == null) {
            getLog().warn("no " + VERSION_PROP + " in " + extractedPath);
            return false;
        }
        ZipFile zip = new ZipFile(webApp);
        String originalVersion;
        try {
            ZipEntry entry = zip.getEntry(VERSION_PATH);
            if (entry == null) {
                getLog().warn("no " + VERSION_PATH + " in " + webApp);
                return false;
            }
            is = zip.getInputStream(entry);
            try {
                originalVersion = loadVersion(is);
            } finally {
                is.close();
            }
        } finally {
            zip.close();
        }
        if (originalVersion == null) {
            getLog().warn("no " + VERSION_PROP + " in jar:" + webApp.toURI() + "!/" + VERSION_PATH);
            return false;
        }
        if (!extractedVersion.equals(originalVersion)) {
            getLog().info("Version " + extractedVersion + " in " + extractedWebAppDir + " does not match " + originalVersion + " in " + webApp + ", will recreate");
            return true;
        }
        getLog().info(extractedWebAppDir + " already up to date with respect to " + webApp);
        return false;
    }

    private String loadVersion(InputStream is) throws IOException {
        Properties props = new Properties();
        props.load(is);
        return props.getProperty(VERSION_PROP);
    }

    @Override
    public void configureScanner() throws MojoExecutionException {
        // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
        // this can only be done after server.start() is called, which happens in AbstractJettyMojo.startJetty()
        // and this configureScanner method is one of the few places that are run afterward.
        for (Connector con : server.getConnectors()) {
            for (ConnectionFactory cf : con.getConnectionFactories()) {
                if (cf instanceof HttpConnectionFactory) {
                    HttpConnectionFactory hcf = (HttpConnectionFactory) cf;
                    hcf.getHttpConfiguration().setResponseHeaderSize(12*1024);
                }
            }
        }

        setUpScanList();

        scannerListeners = new ArrayList<>();
        scannerListeners.add(new Scanner.BulkListener() {
            @Override
            public void filesChanged(List<String> changes) {
                try {
                    restartWebApp(changes.contains(getProject().getFile().getCanonicalPath()));
                } catch (Exception e) {
                    getLog().error("Error reconfiguring/restarting webapp after change in watched files", e);
                }
            }
        });
    }

    @Override
    public void restartWebApp(boolean reconfigureScanner) throws Exception {
        getLog().info("restarting "+webApp);
        getLog().debug("Stopping webapp ...");
        webApp.stop();
        getLog().debug("Reconfiguring webapp ...");

        checkPomConfiguration();
        configureWebApplication();

        // check if we need to reconfigure the scanner,
        // which is if the pom changes
        if (reconfigureScanner)
        {
            getLog().info("Reconfiguring scanner after change to pom.xml ...");
            generateHpl(); // regenerate hpl if POM changes.

            scanList.clear();
            if (webApp.getDescriptor() != null)
                scanList.add(new File(webApp.getDescriptor()));
            if (webApp.getJettyEnvXml() != null)
                scanList.add(new File(webApp.getJettyEnvXml()));
            scanList.add(project.getFile());
            if (webApp.getTestClasses() != null)
                scanList.add(webApp.getTestClasses());
            if (webApp.getClasses() != null)
            scanList.add(webApp.getClasses());
            scanList.addAll(webApp.getWebInfLib());
            scanner.setScanDirs(scanList);
        }

        getLog().debug("Restarting webapp ...");
        webApp.start();
        getLog().info("Restart completed at " + new Date());
    }

    private void setUpScanList() {
        scanList = new ArrayList<>();
        scanList.add(getProject().getFile());
        scanList.add(webAppFile);
        scanList.add(new File(getProject().getBuild().getOutputDirectory()));
    }

    @Override
    protected void startConsoleScanner() throws Exception {
        if (consoleForceReload) {
            getLog().info("Console reloading is ENABLED. Hit ENTER on the console to restart the context.");
            consoleScanner = new ConsoleScanner(this);
            consoleScanner.start();
        }
    }

    @Override
    public void checkPomConfiguration() throws MojoExecutionException {
    }

    @Override
    public void finishConfigurationBeforeStart() throws Exception {
        super.finishConfigurationBeforeStart();
        WebAppContext wac = getWebAppConfig();
        wac.setAttribute("org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern", ".*/classes/.*");
        // to allow the development environment to run multiple "mvn hpi:run" with different port,
        // use different session cookie names. Otherwise they can mix up. See
        // http://stackoverflow.com/questions/1612177/are-http-cookies-port-specific
        wac.getSessionHandler().getSessionCookieConfig()
            .setName(  "JSESSIONID." + UUID.randomUUID().toString().replace("-", "").substring(0, 8));

        try {
            // for Jenkins modules, swap the component from jenkins.war by target/classes
            // via classloader magic
            WebAppClassLoader wacl = new WebAppClassLoader(new JettyAndServletApiOnlyClassLoader(getPlatformClassLoader(),getClass().getClassLoader()),wac) {
                private final Pattern exclusionPattern;
                {
                    if (getProject().getPackaging().equals("jenkins-module")) {
                        // classes compiled from jenkins module should behave as if it's a part of the core
                        // load resources from source folders directly
                        for (Resource r : getProject().getResources())
                            super.addURL(new File(r.getDirectory()).toURI().toURL());
                        super.addURL(new File(getProject().getBuild().getOutputDirectory()).toURI().toURL());

                        // add all the jar dependencies of the module
                        // "provided" includes all core and others, so drop them
                        // similarly, "test" would pull in all the harness
                        // pom dependency is sometimes used so that one can depend on its transitive dependencies
                        for (Artifact a : Artifacts.of(getProject()).scopeIsNot("provided","test").typeIsNot("pom")) {
                            super.addURL(a.getFile().toURI().toURL());
                        }
                        
                        exclusionPattern = Pattern.compile("[/\\\\]\\Q"+getProject().getArtifactId()+"\\E-[0-9]([^/\\\\]+)\\.jar$");
                    } else {
                        exclusionPattern = Pattern.compile("this should never match");
                    }
                }

                @Override
                public void addClassPath(String classPath) throws IOException {
                    if (exclusionPattern != null && exclusionPattern.matcher(classPath).find()) {
                        return;
                    }
                    super.addClassPath(classPath);
                }

                @Override
                public void addJars(org.eclipse.jetty.util.resource.Resource lib) {
                    super.addJars(lib);
                }
            };
            wac.setClassLoader(wacl);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    private ClassLoader getPlatformClassLoader() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (isPostJava8()) {
            return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        }
        return null;
    }

    private boolean isPostJava8() {
        return !System.getProperty("java.version").startsWith("1.");
    }

    @Override
    public void startJetty() throws MojoExecutionException {
        if (httpConnector == null && (defaultPort != 0 || (defaultHost != null && !defaultHost.isEmpty()))) {
            httpConnector = new MavenServerConnector();
            if (defaultPort != 0) {
                httpConnector.setPort(defaultPort);
            }
            if (defaultHost != null && !defaultHost.isEmpty()) {
                httpConnector.setHost(defaultHost);
            }
            String browserHost;
            if (wildcardDNS != null && "localhost".equals(defaultHost)) {
                browserHost = getProject().getArtifactId() + ".127.0.0.1." + wildcardDNS;
            } else {
                getLog().info("Try setting -DwildcardDNS=nip.io in a profile");
                browserHost = httpConnector.getHost();
            }
            getLog().info("===========> Browse to: http://" + browserHost + ":" + (defaultPort != 0 ? defaultPort : MavenServerConnector.DEFAULT_PORT) + webApp.getContextPath() + "/");
        }
        super.startJetty();
    }

    /**
     * Performs the equivalent of "@requiresDependencyResolution" mojo attribute,
     * so that we can choose the scope at runtime.
     * @see LifecycleDependencyResolver#getDependencies(MavenProject, Collection, Collection,
     *     MavenSession, boolean, Set)
     */
    protected Set<Artifact> resolveDependencies(String scope) throws MojoExecutionException {
        try {
            DependencyResolutionRequest request =
                    new DefaultDependencyResolutionRequest(
                            getProject(), session.getRepositorySession());
            request.setResolutionFilter(getDependencyFilter(scope));
            DependencyResolutionResult result = dependenciesResolver.resolve(request);

            Set<Artifact> artifacts = new LinkedHashSet<>();
            if (result.getDependencyGraph() != null
                    && !result.getDependencyGraph().getChildren().isEmpty()) {
                RepositoryUtils.toArtifacts(
                        artifacts,
                        result.getDependencyGraph().getChildren(),
                        Collections.singletonList(getProject().getArtifact().getId()),
                        request.getResolutionFilter());
            }
            return artifacts;
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        }
    }

    private static DependencyFilter getDependencyFilter(String scope) {
        switch (scope) {
            case Artifact.SCOPE_COMPILE:
                return new ScopeDependencyFilter(Artifact.SCOPE_RUNTIME, Artifact.SCOPE_TEST);
            case Artifact.SCOPE_RUNTIME:
                return new ScopeDependencyFilter(
                        Artifact.SCOPE_SYSTEM, Artifact.SCOPE_PROVIDED, Artifact.SCOPE_TEST);
            case Artifact.SCOPE_TEST:
                return null;
            default:
                throw new IllegalArgumentException("unexpected scope: " + scope);
        }
    }

    public Set<MavenArtifact> getProjectArtifacts() {
        Set<MavenArtifact> r = new HashSet<>();
        for (Artifact a : getProject().getArtifacts()) {
            r.add(wrap(a));
        }
        return r;
    }

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(
                a,
                artifactResolver,
                artifactFactory,
                projectBuilder,
                session);
    }

    protected Artifact getJenkinsWarArtifact() throws MojoExecutionException {
        for( Artifact a : resolveDependencies("test") ) {
            boolean match;
            if (jenkinsWarId!=null)
                match = (a.getGroupId()+':'+a.getArtifactId()).equals(jenkinsWarId);
            else
                match = (a.getArtifactId().equals("jenkins-war") || a.getArtifactId().equals("hudson-war")) && (a.getType().equals("executable-war") || a.getType().equals("war"));
            if(match)
                return a;
        }

        if (jenkinsWarId!=null) {
            getLog().error("Unable to locate jenkins.war in '"+jenkinsWarId+"'");
        } else {
            getLog().error(
                "Unable to locate jenkins.war. Add the following dependency in your POM:\n" +
                "\n" +
                "<dependency>\n" +
                "  <groupId>org.jenkins-ci.main</groupId>\n" +
                "  <artifactId>jenkins-war</artifactId>\n" +
                "  <type>war</type>\n" +
                "  <version>1.396<!-- replace this with the version you want--></version>\n" +
                "  <scope>test</scope>\n" +
                "</dependency>"
            );
        }
        throw new MojoExecutionException("Unable to find jenkins.war");
    }

    protected MavenProject getProject() {
        return project;
    }


    public WebAppContext getWebAppConfig() {
        return webApp;
    }
}
