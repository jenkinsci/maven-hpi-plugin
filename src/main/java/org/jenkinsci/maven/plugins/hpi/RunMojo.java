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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.commons.io.FileUtils;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.plugin.Jetty6PluginServer;
import org.mortbay.jetty.plugin.util.JettyPluginServer;
import org.mortbay.jetty.plugin.util.Scanner;
import org.mortbay.jetty.plugin.util.Scanner.Listener;
import org.mortbay.jetty.plugin.util.SystemProperty;
import org.mortbay.jetty.webapp.WebAppClassLoader;
import org.mortbay.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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
 * To specify the HTTP port, use <tt>-Djetty.port=<i>PORT</i></tt>
 * </p>
 * 
 * @goal run
 * @execute phase=compile
 * @description Runs Jenkins with the current plugin
 * @author Kohsuke Kawaguchi
 */
public class RunMojo extends AbstractJetty6Mojo {

    /**
     * The location of the war file.
     *
     * <p>
     * Normally this should be left empty, in which case the plugin loads it from the repository.
     * But this parameter allows that to be overwritten.
     * </p>
     * 
     * @parameter
     */
    private File webApp;

    /**
     * Path to <tt>$JENKINS_HOME</tt>. The launched Jenkins will use this directory as the workspace.
     *
     * @parameter expression="${HUDSON_HOME}"
     */
    private File jenkinsHome;

    /**
     * Decides the level of dependency resolution.
     *
     * This controls what plugins are made available to the
     * running Jenkins.
     *
     * @parameter
     */
    private String dependencyResolution = "test";

    /**
     * Single directory for extra files to include in the WAR.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     * @required
     */
    protected File warSourceDirectory;

    /**
     * @component
     */
    protected ArtifactResolver artifactResolver;

    /**
     * @component
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    protected ArtifactRepository localRepository;

    /**
     * @component
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * Specifies the HTTP port number.
     *
     * If connectors are configured in the Mojo, that'll take precedence.
     *
     * @parameter expression="${port}"
     */
    protected String defaultPort;

    /**
     * If true, the context will be restarted after a line feed on
     * the input console. Disabled by default.
     *
     * @parameter expression="${jetty.consoleForceReload}" default-value="true"
     */
    protected boolean consoleForceReload;

    /**
     * @component
     */
    protected MavenProjectBuilder projectBuilder;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins core jar.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @parameter
     * @since 1.65
     */
    protected String jenkinsCoreId;

    /**
     * Optional string that represents "groupId:artifactId" of Jenkins war.
     * If left unspecified, the default groupId/artifactId pair for Jenkins is looked for.
     *
     * @parameter
     * @since 1.68
     */
    protected String jenkinsWarId;

    /**
     * [ws|tab|CR|LF]+ separated list of package prefixes that your plugin doesn't want to see
     * from the core.
     *
     * <p>
     * Tokens in this list is prefix-matched against the fully-qualified class name, so add
     * "." to the end of each package name, like "com.foo. com.bar."
     *
     * @parameter
     */
    protected String maskClasses;

    /**
     * @since 1.94
     * @parameter
     */
    protected boolean pluginFirstClassLoader = false;

    /**
     * List of additionnal System properties to set
     *
     * @parameter
     * @since 1.85
     */
    private Map<String, String> systemProperties;

    /**
     * List of loggers to define.
     * Keys are logger names (usually package or class names);
     * values are level names (such as {@code FINE}).
     * @parameter
     * @since 1.98
     */
    private Map<String,String> loggers;

    private Collection<Logger> loggerReferences; // just to prevent GC

    public void execute() throws MojoExecutionException, MojoFailureException {
        getProject().setArtifacts(resolveDependencies(dependencyResolution));

        // compute hudsonHome
        if(jenkinsHome ==null) {
            String h = System.getenv("HUDSON_HOME");
            if(h!=null)
                jenkinsHome = new File(h);
            else
                jenkinsHome = new File("./work");
        }

        // auto-enable stapler trace, unless otherwise configured already.
        setSystemPropertyIfEmpty("stapler.trace", "true");
        // run YUI in the debug mode, unless otherwise configured
        setSystemPropertyIfEmpty("debug.YUI","true");
        // allow Jetty to accept a bigger form so that it can handle update center JSON post
        setSystemPropertyIfEmpty("org.mortbay.jetty.Request.maxFormContentSize","-1");
        // general-purpose system property so that we can tell from Jenkins if we are running in the hpi:run mode.
        setSystemPropertyIfEmpty("hudson.hpi.run","true");
        // this adds 3 secs to the shutdown time. Skip it.
        setSystemPropertyIfEmpty("hudson.DNSMultiCast.disabled","true");
        // expose the current top-directory of the plugin
        setSystemPropertyIfEmpty("jenkins.moduleRoot",new File(".").getAbsolutePath());

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

        webApp = getJenkinsWarArtifact().getFile();

        // make sure all the relevant Jenkins artifacts have the same version
        for (Artifact a : jenkinsArtifacts) {
            Artifact ba = jenkinsArtifacts.get(0);
            if(!a.getVersion().equals(ba.getVersion()))
                throw new MojoExecutionException("Version of "+a.getId()+" is inconsistent with "+ba.getId());
        }

        // set HUDSON_HOME
        SystemProperty sp = new SystemProperty();
        sp.setName("HUDSON_HOME");
        sp.setValue(jenkinsHome.getAbsolutePath());
        sp.setIfNotSetAlready();
        File pluginsDir = new File(jenkinsHome, "plugins");
        pluginsDir.mkdirs();

        // enable view auto refreshing via stapler
        sp = new SystemProperty();
        sp.setName("stapler.jelly.noCache");
        sp.setValue("true");
        sp.setIfNotSetAlready();

        List res = getProject().getBuild().getResources();
        if(!res.isEmpty()) {
            // pick up the first one and use it
            Resource r = (Resource) res.get(0);
            sp = new SystemProperty();
            sp.setName("stapler.resourcePath");
            sp.setValue(r.getDirectory());
            sp.setIfNotSetAlready();
        }


        generateHpl();

        // copy other dependency Jenkins plugins
        try {
            for( MavenArtifact a : getProjectArtifacts() ) {
                if(!a.isPlugin())
                    continue;

                // find corresponding .hpi file
                Artifact hpi = artifactFactory.createArtifact(a.getGroupId(),a.getArtifactId(),a.getVersion(),null,"hpi");
                artifactResolver.resolve(hpi,getProject().getRemoteArtifactRepositories(), localRepository);

                // check recursive dependency. this is a rare case that happens when we split out some things from the core
                // into a plugin
                if (hasSameGavAsProject(hpi))
                    continue;

                if (hpi.getFile().isDirectory())
                    throw new UnsupportedOperationException(hpi.getFile()+" is a directory and not packaged yet. this isn't supported");

                copyPlugin(hpi.getFile(), pluginsDir, a.getArtifactId());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        }

        if (loggers != null) {
            for (Handler h : LogManager.getLogManager().getLogger("").getHandlers()) {
                if (h instanceof ConsoleHandler) {
                    h.setLevel(Level.ALL);
                }
            }
            loggerReferences = new LinkedList<Logger>();
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
        if (hpi.isFile()) {
            getLog().warn("Moving historical " + hpi + " to *.jpi");
            hpi.renameTo(dst);
        }
        if (versionOfPlugin(src).compareTo(versionOfPlugin(dst)) < 0) {
            getLog().warn("will not overwrite " + dst + " with " + src + " because it is newer");
            return;
        }
        getLog().info("Copying dependency Jenkins plugin " + src);
        FileUtils.copyFile(src, dst);
        // pin the dependency plugin, so that even if a different version of the same plugin is bundled to Jenkins,
        // we still use the plugin as specified by the POM of the plugin.
        FileUtils.writeStringToFile(new File(dst + ".pinned"), "pinned");
    }
    private VersionNumber versionOfPlugin(File p) throws IOException {
        if (!p.isFile()) {
            return new VersionNumber("0.0");
        }
        JarFile j = new JarFile(p);
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

    /**
     * Create a dot-hpl file.
     *
     * <p>
     * All I want to do here is to invoke the hpl target.
     * there must be a better way to do this!
     *
     * <p>
     * Besides, if the user wants to change the plugin name, etc,
     * this forces them to do it in two places.
     */
    private void generateHpl() throws MojoExecutionException, MojoFailureException {
        HplMojo hpl = new HplMojo();
        hpl.project = getProject();
        hpl.setHudsonHome(jenkinsHome);
        hpl.setLog(getLog());
        hpl.pluginName = getProject().getName();
        hpl.warSourceDirectory = warSourceDirectory;
        hpl.scopeFilter = new ScopeArtifactFilter(dependencyResolution);
        hpl.projectBuilder = this.projectBuilder;
        hpl.localRepository = this.localRepository;
        hpl.jenkinsCoreId = this.jenkinsCoreId;
        hpl.pluginFirstClassLoader = this.pluginFirstClassLoader;
        hpl.maskClasses = this.maskClasses;
        hpl.execute();
    }

    public void configureWebApplication() throws Exception {
        // Jetty tries to do this in WebAppContext.resolveWebApp but it failed to delete the directory.
        File extractedWebAppDir= new File(getTmpDirectory(), "webapp");
        if (isExtractedWebAppDirStale(extractedWebAppDir, webApp)) {
            FileUtils.deleteDirectory(extractedWebAppDir);
        }
        
        super.configureWebApplication();
        getWebApplication().setWebAppSrcDir(webApp);
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

    public void configureScanner() throws MojoExecutionException {
        setUpScanList(new ArrayList());

        ArrayList<Listener> listeners = new ArrayList<Listener>();
        listeners.add(new Listener() {
            public void changesDetected(Scanner scanner, List changes) {
                try {
                    getLog().info("Restarting webapp ...");
                    getLog().debug("Stopping webapp ...");
                    getWebApplication().stop();
                    getLog().debug("Reconfiguring webapp ...");

                    checkPomConfiguration();

                    // check if we need to reconfigure the scanner,
                    // which is if the pom changes
                    if (changes.contains(getProject().getFile().getCanonicalPath())) {
                        getLog().info("Reconfiguring scanner after change to pom.xml ...");
                        generateHpl(); // regenerate hpl if POM changes.
                        ArrayList scanList = getScanList();
                        scanList.clear();
                        setUpScanList(scanList);
                        scanner.setRoots(scanList);
                    }

                    getLog().debug("Restarting webapp ...");
                    getWebApplication().start();
                    getLog().info("Restart completed.");
                } catch (Exception e) {
                    getLog().error("Error reconfiguring/restarting webapp after change in watched files", e);
                }
            }
        });
        setScannerListeners(listeners);

    }

    private void setUpScanList(ArrayList scanList) {
        scanList.add(getProject().getFile());
        scanList.add(webApp);
        scanList.add(new File(getProject().getBuild().getOutputDirectory()));
        setScanList(scanList);
    }

    @Override
    protected void startScanner() {
        super.startScanner();

        if (consoleForceReload) {
            getLog().info("Console reloading is ENABLED. Hit ENTER on the console to restart the context.");
            new ConsoleScanner(this).start();
        }
    }

    public void checkPomConfiguration() throws MojoExecutionException {
    }

    public void finishConfigurationBeforeStart() {
        // working around JETTY-1226. This bug affects those who use Axis from plugins, for example.
        WebAppContext wac = (WebAppContext)getWebApplication().getProxiedObject();
        List<String> sc = new ArrayList<String>(Arrays.asList(wac.getSystemClasses()));
        sc.add("javax.activation.");
        wac.setSystemClasses(sc.toArray(new String[sc.size()]));

        // to allow the development environment to run multiple "mvn hpi:run" with different port,
        // use different session cookie names. Otherwise they can mix up. See
        // http://stackoverflow.com/questions/1612177/are-http-cookies-port-specific
        wac.getSessionHandler().getSessionManager().setSessionCookie("JSESSIONID."+UUID.randomUUID().toString().replace("-","").substring(0,8));

        try {
            // for Jenkins modules, swap the component from jenkins.war by target/classes
            // via classloader magic
            WebAppClassLoader wacl = new WebAppClassLoader(new ServletApiOnlyClassLoader(null,getClass().getClassLoader()),wac) {
                private final Pattern exclusionPattern;
                {
                    if (getProject().getPackaging().equals("jenkins-module")) {
                        // classes compiled from jenkins module should behave as if it's a part of the core
                        // load resources from source folders directly
                        for (Resource r : (List<Resource>)getProject().getResources())
                            super.addURL(new File(r.getDirectory()).toURL());
                        super.addURL(new File(getProject().getBuild().getOutputDirectory()).toURL());

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
                    if (exclusionPattern.matcher(classPath).find())
                        return;
                    super.addClassPath(classPath);
                }

                @Override
                public void addJars(org.mortbay.resource.Resource lib) {
                    super.addJars(lib);
                }
            };
            wac.setClassLoader(wacl);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    @Override
    protected String getDefaultHttpPort() {
        if (defaultPort!=null)
            return defaultPort;
        return super.getDefaultHttpPort();
    }

    public JettyPluginServer createServer() throws Exception {
        return new Jetty6PluginServer() {
            @Override
            public Object createDefaultConnector(String portnum) throws Exception {
                SelectChannelConnector con = (SelectChannelConnector)super.createDefaultConnector(portnum);

                con.setHeaderBufferSize(12*1024); // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL

                return con;
            }
        };
    }

    /**
     * Performs the equivalent of "@requireDependencyResolution" mojo attribute,
     * so that we can choose the scope at runtime.
     * @param scope
     */
    private Set<Artifact> resolveDependencies(String scope) throws MojoExecutionException {
        try {
            ArtifactResolutionResult result = artifactResolver.resolveTransitively(
                    getProject().getDependencyArtifacts(),
                    getProject().getArtifact(),
                    getProject().getManagedVersionMap(),
                    localRepository,
                    getProject().getRemoteArtifactRepositories(),
                    artifactMetadataSource,
                    new ScopeArtifactFilter(scope));
            return result.getArtifacts();
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Unable to copy dependency plugin",e);
        }
    }

    public Set<MavenArtifact> getProjectArtifacts() {
        Set<MavenArtifact> r = new HashSet<MavenArtifact>();
        for (Artifact a : (Collection<Artifact>)getProject().getArtifacts()) {
            r.add(wrap(a));
        }
        return r;
    }

    protected MavenArtifact wrap(Artifact a) {
        return new MavenArtifact(a,projectBuilder,getProject().getRemoteArtifactRepositories(),localRepository);
    }

    protected Artifact getJenkinsWarArtifact() throws MojoExecutionException {
        for( Artifact a : resolveDependencies("test") ) {
            boolean match;
            if (jenkinsWarId!=null)
                match = (a.getGroupId()+':'+a.getArtifactId()).equals(jenkinsWarId);
            else
                match = (a.getArtifactId().equals("jenkins-war") || a.getArtifactId().equals("hudson-war")) && a.getType().equals("war");
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
}
