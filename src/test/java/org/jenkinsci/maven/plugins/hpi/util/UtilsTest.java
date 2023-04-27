package org.jenkinsci.maven.plugins.hpi.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.Collections;
import java.util.Set;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void setUnionCalculation() {
        checkUnion("union of a set is itself", new String[] {"one", "two", "three"}, Set.of("one", "two", "three"));

        checkUnion(
                "union of 2 sets is correct",
                new String[] {"one", "two", "three", "four", "five", "six"},
                Set.of("one", "two", "three"),
                Set.of("four", "five", "six"));

        checkUnion(
                "union of 3 sets is correct",
                new String[] {"one", "two", "three", "four", "five", "six", "seven"},
                Set.of("one", "three", "five"),
                Set.of("two", "four", "six"),
                Set.of("one", "seven"));

        checkUnion(
                "union of overlapping sets",
                new String[] {"one", "two", "three"},
                Set.of("one", "two", "three"),
                Set.of("one", "two", "three"),
                Set.of("one", "two", "three"),
                Set.of("one", "two", "three"),
                Set.of("one", "two", "three"),
                Set.of("one", "two", "three"));

        checkUnion(
                "union of an empty set and something is something",
                new String[] {"four", "five", "six"},
                Collections.emptySet(),
                Set.of("four", "five", "six"));

        checkUnion(
                "union of something and an empty set and is somethine",
                new String[] {"four", "five", "six"},
                Set.of("four", "five", "six"),
                Collections.emptySet());

        checkUnion("union of 2 empty sets is empty", new String[] {}, Collections.emptySet(), Collections.emptySet());
    }

    @SafeVarargs
    public static <T> void checkUnion(String reason, T[] expected, Set<T>... unions) {
        assertThat(reason, Utils.unionOf(unions), containsInAnyOrder(expected));
    }
}
