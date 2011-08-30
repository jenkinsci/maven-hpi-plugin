package org.jenkinsci.maven.plugins.hpi;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * {@link ClassLoader} that hides Maven and other conflicting dependencies
 * between maven-hpi-plugin and Jenkins that runs in Maven.
 *
 * @author Kohsuke Kawaguchi
 */
final class MaskingClassLoader extends ClassLoader {
    public MaskingClassLoader(ClassLoader parent) {
        super(parent);
    }

    protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if(isMaskedClassPrefix(name))
            throw new ClassNotFoundException(name);
        return super.loadClass(name, resolve);
    }

    private boolean isMaskedClassPrefix(String name) {
        return name.startsWith("org.kohsuke")
            || name.startsWith("org.apache.maven")
            || name.startsWith("org.sonatype")
            || name.startsWith("org.cyberneko")
            || name.startsWith("org.codehaus.plexus");
    }

    public URL getResource(String name) {
        if(isMaskedResourcePrefix(name))
            return null;
        return super.getResource(name);
    }

    private boolean isMaskedResourcePrefix(String name) {
        if (name.startsWith(META_INF_SERVICES) && isMaskedClassPrefix(name.substring(META_INF_SERVICES.length())))
            return true;
        return name.startsWith("org/kohsuke")
            || name.startsWith("org/apache/maven")
            || name.startsWith("org/sonatype")
            || name.startsWith("org/codehaus/plexus")
            || name.startsWith("META-INF/plexus")
            || name.startsWith("META-INF/maven");
    }

    public Enumeration getResources(String name) throws IOException {
        if(isMaskedResourcePrefix(name))
            return EMPTY_ENUMERATION;
        return super.getResources(name);
    }

    private static final Enumeration EMPTY_ENUMERATION = new Enumeration() {
        public boolean hasMoreElements() {
            return false;
        }

        public Object nextElement() {
            throw new NoSuchElementException();
        }
    };

    private static final String META_INF_SERVICES = "META-INF/services/";
}
