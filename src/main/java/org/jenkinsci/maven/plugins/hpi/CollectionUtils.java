package org.jenkinsci.maven.plugins.hpi;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Taken from jdk7 Collections
 */
class CollectionUtils {

    public static <T> Enumeration<T> emptyEnumeration() {
        return (Enumeration<T>) EmptyEnumeration.EMPTY_ENUMERATION;
    }

    private static class EmptyEnumeration<E> implements Enumeration<E> {
        static final EmptyEnumeration<Object> EMPTY_ENUMERATION = new EmptyEnumeration<>();

        @Override
        public boolean hasMoreElements() { return false; }
        @Override
        public E nextElement() { throw new NoSuchElementException(); }
    }
}
