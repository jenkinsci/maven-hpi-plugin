package org.jenkinsci.maven.plugins.hpi.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Utils {

    /**
     * Return an unmodifiable set whose contents are the unions of all the specified sets.
     */
    @SafeVarargs
    public static <T> Set<T> unionOf(Set<T>... sets) {
        Set<T> unionSet = new HashSet<>(sets[0]);
        for (int i = 1; i < sets.length; i++) {
            unionSet.addAll(sets[i]);
        }
        return Collections.unmodifiableSet(unionSet);
    }
}
