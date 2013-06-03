package org.jenkinsci.maven.plugins.hpi;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JJavaName;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import groovy.lang.Closure;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.jelly.groovy.TagFile;
import org.kohsuke.stapler.jelly.groovy.TagLibraryUri;
import org.kohsuke.stapler.jelly.groovy.TypedTagLibrary;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;

/**
 * Generates the strongly-typed Java interfaces for Groovy taglibs.
 *
 * @author Kohsuke Kawaguchi
 * @goal generate-taglib-interface
 * @phase generate-resources
 */
public class TagLibInterfaceGeneratorMojo extends AbstractMojo {
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory for the generated WAR.
     *
     * @parameter expression="${project.basedir}/target/generated-sources/taglib-interface"
     * @required
     */
    protected File outputDirectory;

    private SAXReader saxReader = new SAXReader();

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            JCodeModel codeModel = new JCodeModel();
            for (Resource res: project.getBuild().getResources()) {
                walk(new File(res.getDirectory()),codeModel.rootPackage(),"");
            }

            outputDirectory.mkdirs();
            codeModel.build(outputDirectory);
            project.getCompileSourceRoots().add(outputDirectory.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate taglib type interface",e);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException("Duplicate class: "+e.getExistingClass().fullName(),e);
        }
    }

    private void walk(File dir,JPackage pkg,String dirName) throws JClassAlreadyExistsException, IOException {
        File[] children = dir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });
        if (children!=null) {
            for (File child : children)
                walk(child,pkg.subPackage(h2j(child.getName())),dirName+'/'+child.getName());
        }

        File taglib = new File(dir,"taglib");
        if (taglib.exists()) {
            JDefinedClass c = pkg.parent()._interface(StringUtils.capitalize(h2j(dir.getName())) + "TagLib");
            c._implements(TypedTagLibrary.class);
            c.annotate(TagLibraryUri.class).param("value",dirName);

            File[] tags = dir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jelly");
                }
            });

            long timestamp = -1;

            for (File tag : tags) {
                timestamp = Math.max(tag.lastModified(),timestamp);
                try {
                    Document dom = saxReader.read(tag);
                    Element doc = dom.getRootElement().element(QName.get("st:documentation", "jelly:stapler"));

                    String baseName = FilenameUtils.getBaseName(tag.getName());
                    String methodName;
                    if (!JJavaName.isJavaIdentifier(tag.getName())) {
                        methodName = baseName.replace('-', '_');
                        if (ReservedName.NAMES.contains(methodName))
                            methodName += '_';
                    } else {
                        methodName = baseName;
                    }

                    for (int i=0; i<4; i++) {
                        JMethod m = c.method(0, void.class, methodName);
                        if (!methodName.equals(baseName))
                            m.annotate(TagFile.class).param("value",baseName);
                        if (i%2==0)
                            m.param(Map.class,"args");
                        if ((i/2)%2==0)
                            m.param(Closure.class,"body");

                        JDocComment javadoc = m.javadoc();
                        if (doc!=null)
                            javadoc.append(doc.getText().replace("&","&amp;").replace("<","&lt;"));
                    }
                } catch (DocumentException e) {
                    throw (IOException)new IOException("Failed to parse "+tag).initCause(e);
                }
            }

            // up to date check. if the file already exists and is newer, don't regenerate it
            File dst = new File(outputDirectory, c.fullName().replace('.', '/') + ".java");
            if (dst.exists() && dst.lastModified()>timestamp)
                c.hide();
        }
    }

    private static String h2j(String s) {
        if (s.equals("hudson")) return "jenkins";
        return s;
    }
}
