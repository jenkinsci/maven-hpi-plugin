package org.jenkinsci.maven.plugins.hpi;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Only exposes Servlet API and Jetty to the web application, then JavaSE.
 *
 * <p>
 * This is because the classloader that loads this plugin brings in a whole lot of baggage, such as
 * Maven, Xalan, commons libraries, and etc., which can interfere with what's in Jenkins and plugins.
 *
 * @author Kohsuke Kawaguchi
 */
public class JettyAndServletApiOnlyClassLoader extends ClassLoader {
    private final ClassLoader jettyClassLoader;

    public JettyAndServletApiOnlyClassLoader(ClassLoader parent, ClassLoader jettyClassLoader) {
        super(parent);
        this.jettyClassLoader = jettyClassLoader;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      if (name.equals("jndi.properties")) {
        return jettyClassLoader.getResources(name);
      }
      return Collections.emptyEnumeration();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith("javax.")
            || name.startsWith("org.eclipse.jetty."))
            return jettyClassLoader.loadClass(name);
        else
            throw new ClassNotFoundException(name);
    }
}
