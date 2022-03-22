package org.jenkinsci.maven.plugins.hpi;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JDocComment;
import com.sun.codemodel.JJavaName;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.fmt.JBinaryFile;
import com.sun.codemodel.writer.FileCodeWriter;
import com.sun.codemodel.writer.FilterCodeWriter;
import groovy.lang.Closure;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.kohsuke.stapler.jelly.groovy.TagFile;
import org.kohsuke.stapler.jelly.groovy.TagLibraryUri;
import org.kohsuke.stapler.jelly.groovy.TypedTagLibrary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Generates the strongly-typed Java interfaces for Groovy taglibs.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name="generate-taglib-interface", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class TagLibInterfaceGeneratorMojo extends AbstractMojo {
    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    /**
     * The directory for the generated WAR.
     */
    @Parameter(defaultValue = "${project.basedir}/target/generated-sources/taglib-interface")
    protected File outputDirectory;

    /**
     * The encoding to use for generated files.
     */
    @Parameter(property = "project.build.sourceEncoding")
    protected String encoding;

    private SAXReader saxReader = new SAXReader();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            JCodeModel codeModel = new JCodeModel();
            for (Resource res: project.getBuild().getResources()) {
                walk(new File(res.getDirectory()),codeModel.rootPackage(),"");
            }

            Files.createDirectories(outputDirectory.toPath());
            CodeWriter w = new FilterCodeWriter(encoding != null ? new FileCodeWriter(outputDirectory, encoding) : new FileCodeWriter(outputDirectory)) {
                // Cf. ProgressCodeWriter:
                @Override public Writer openSource(JPackage pkg, String fileName) throws IOException {
                    report(pkg, fileName);
                    return super.openSource(pkg, fileName);
                }
                @Override public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
                    report(pkg, fileName);
                    return super.openBinary(pkg, fileName);
                }
                private void report(JPackage pkg, String fileName) {
                    if (pkg.isUnnamed()) {
                        getLog().info(fileName);
                    } else {
                        getLog().info(pkg.name().replace('.', File.separatorChar) + File.separatorChar + fileName);
                    }
               }
            };
            codeModel.build(w);
            project.getCompileSourceRoots().add(outputDirectory.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate taglib type interface",e);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException("Duplicate class: "+e.getExistingClass().fullName(),e);
        }
    }

    private void walk(File dir,JPackage pkg,String dirName) throws JClassAlreadyExistsException, IOException {
        File[] children = dir.listFiles(File::isDirectory);
        if (children!=null) {
            for (File child : children)
                walk(child,pkg.subPackage(h2j(child.getName())),dirName+'/'+child.getName());
        }

        if (isTagLibDir(dir)) {
            String taglib = h2j(dir.getName());
            JDefinedClass c = pkg.parent()._interface(taglib.substring(0, 1).toUpperCase() + taglib.substring(1) + "TagLib");
            c._implements(TypedTagLibrary.class);
            c.annotate(TagLibraryUri.class).param("value",dirName);

            JBinaryFile _gdsl = new JBinaryFile(c.name()+".gdsl");
            PrintWriter gdsl = new PrintWriter(new BufferedWriter(new OutputStreamWriter(_gdsl.getDataStore(), StandardCharsets.UTF_8)));
            gdsl.printf("package %s;\n",pkg.parent().name());
            gdsl.printf("contributor(context(ctype:'%s')) {\n",c.fullName());

            File[] tags = dir.listFiles((unused, name) -> name.endsWith(".jelly"));

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

                    // add 4 overload variants
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
                            javadoc.append(doc.getText().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
                    }

                    // generate Groovy DSL
                    if (doc!=null) {
                        gdsl.printf("  method name:'%s', type:void, params:[args:[\n", methodName);
                        List<Element> atts = doc.elements(QName.get("st:attribute", "jelly:stapler"));
                        for (Element a : atts) {
    //                                    parameter(name: 'param1', type: String, doc: 'My doc'),
                            gdsl.printf("    parameter(name:'%s',type:'%s', doc:\"\"\"\n%s\n\"\"\"),\n",
                                    a.attributeValue("name"),
                                    a.attributeValue("type","java.lang.Object"),
                                    a.getTextTrim().replace("$", "\\$").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
                        }

                        // see http://youtrack.jetbrains.com/issue/IDEA-108355 for why
                        // we add the bogus 'dummy' parameter
                        gdsl.printf("  ], dummy:void, c:Closure]\n");
                    }
                } catch (DocumentException e) {
                    throw new IOException("Failed to parse " + tag, e);
                }
            }

            gdsl.printf("}\n");
            gdsl.close();

            // up to date check. if the file already exists and is newer, don't regenerate it
            File dst = new File(outputDirectory, c.fullName().replace('.', '/') + ".java");
            if (dst.exists() && dst.lastModified()>timestamp)
                c.hide();
            else
                pkg.parent().addResourceFile(_gdsl);
        }
    }

    private boolean isTagLibDir(File dir) {
        return new File(dir,"taglib").exists();
    }

    private static String h2j(String s) {
        if (s.equals("hudson")) return "jenkins";
        return s;
    }
}
