package org.jenkinsci.maven.plugins.hpi;

/**
 * Only exposes servlet API from Jetty to the web application, then JavaSE.
 *
 * <p>
 * This is because the classloader that loads this plugin brings in a whole lot of baggage, such as
 * Maven, Xalan, commons libraries, and etc., which can interfere with what's in Jenkins and plugins.
 *
 * @author Kohsuke Kawaguchi
 */
public class ServletApiOnlyClassLoader extends ClassLoader {
    private final ClassLoader jettyClassLoader;

    public ServletApiOnlyClassLoader(ClassLoader parent, ClassLoader jettyClassLoader) {
        super(parent);
        this.jettyClassLoader = jettyClassLoader;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith("javax."))
            return jettyClassLoader.loadClass(name);
        else
            throw new ClassNotFoundException(name);
    }
}
