package org.jenkinsci.maven.plugins.hpi;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.Manifest.Attribute;
import org.codehaus.plexus.archiver.jar.Manifest.Section;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generate .hpl file.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="hpl", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class HplMojo extends AbstractHpiMojo {
    /**
     * Path to {@code $JENKINS_HOME}. A .hpl file will be generated to this location.
     * @deprecated Use {@link #jenkinsHome}.
     */
    @Deprecated
    @Parameter(property = "hudsonHome")
    private File hudsonHome;

    /**
     * Path to {@code $JENKINS_HOME}. A .hpl file will be generated to this location.
     */
    @Parameter(property = "jenkinsHome")
    private File jenkinsHome;

    @Deprecated
    public void setHudsonHome(File hudsonHome) {
        this.hudsonHome = null;
        this.jenkinsHome = hudsonHome;
    }

    public void setJenkinsHome(File jenkinsHome) {
        this.hudsonHome = null;
        this.jenkinsHome = jenkinsHome;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(!project.getPackaging().equals("hpi")) {
            getLog().info("Skipping "+project.getName()+" because it's not <packaging>hpi</packaging>");
            return;
        }
        if (jenkinsHome == null) {
            if (hudsonHome != null) {
                getLog().warn("Please use the `jenkinsHome` configuration parameter in place of the deprecated `hudsonHome` parameter");
            }
        }

        File hplFile = computeHplFile();
        getLog().info("Generating "+hplFile);

        PrintWriter printWriter = null;
        try {
            Manifest mf = new Manifest();
            Section mainSection = mf.getMainSection();
            setAttributes(mainSection);

            // compute Libraries entry
            List<String> paths = new ArrayList<String>();

            // we want resources to be picked up before target/classes,
            // so that the original (not in the copy) will be picked up first.
            for (Resource r : (List<Resource>) project.getBuild().getResources()) {
                File dir = new File(r.getDirectory());
                if (!dir.isAbsolute())
                    dir = new File(project.getBasedir(),r.getDirectory());
                if(dir.exists()) {
                    paths.add(dir.getPath());
                }
            }

            paths.add(project.getBuild().getOutputDirectory());

            buildLibraries(paths);

            mainSection.addAttributeAndCheck(new Attribute("Libraries", StringUtils.join(paths, ",")));

            // compute Resource-Path entry
            mainSection.addAttributeAndCheck(new Attribute("Resource-Path",warSourceDirectory.getAbsolutePath()));

            printWriter = new PrintWriter(new FileWriter(hplFile));
            mf.write(printWriter);
        } catch (ManifestException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error preparing the manifest: " + e.getMessage(), e);
        } finally {
            IOUtil.close(printWriter);
        }
    }

    /**
     * Compute library dependencies.
     *
     * <p>
     * The list produced by this function and the list of jars that the 'hpi' mojo
     * puts into WEB-INF/lib should be the same so that the plugins see consistent
     * environment.
     */
    private void buildLibraries(List<String> paths) throws IOException {
        Set<MavenArtifact> artifacts = getProjectArtfacts();

        // List up IDs of Jenkins plugin dependencies
        Set<String> jenkinsPlugins = new HashSet<String>();
        for (MavenArtifact artifact : artifacts) {
            if(artifact.isPlugin())
                jenkinsPlugins.add(artifact.getId());
        }

        OUTER:
        for (MavenArtifact artifact : artifacts) {
            if(jenkinsPlugins.contains(artifact.getId()))
                continue;   // plugin dependencies
            if(artifact.getDependencyTrail().size() >= 1 && jenkinsPlugins.contains(artifact.getDependencyTrail().get(1)))
                continue;   // no need to have transitive dependencies through plugins

            // if the dependency goes through jenkins core, that's not a library
            for (String trail : artifact.getDependencyTrail()) {
                if (trail.contains(":hudson-core:") || trail.contains(":jenkins-core:"))
                    continue OUTER;
            }

            //Skip artifacts of type pom
            if(artifact.getType().equals("pom"))
                continue;

            ScopeArtifactFilter filter = new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact.artifact)) {
                paths.add(artifact.getFile().getPath());
            }
        }
    }

    /**
     * Determine where to produce the .hpl file.
     */
    protected File computeHplFile() throws MojoExecutionException {
        if (jenkinsHome == null) {
            jenkinsHome = hudsonHome;
        }
        if(jenkinsHome==null) {
            throw new MojoExecutionException(
                "Property jenkinsHome needs to be set to $JENKINS_HOME. Please use 'mvn -DjenkinsHome=...' or " +
                "put <settings><profiles><profile><properties><property><jenkinsHome>...</...>"
            );
        }

        File hplFile = new File(jenkinsHome, "plugins/" + project.getBuild().getFinalName() + ".hpl");
        return hplFile;
    }
}
