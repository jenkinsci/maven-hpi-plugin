package org.jenkinsci.maven.plugins.hpi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
class ReservedName {
    static Set<String> NAMES = new HashSet<String>(Arrays.asList(
        "abstract",
        "assert",
        "boolean",
        "break",
        "byte",
        "case",
        "catch",
        "char",
        "class",
        "const",
        "continue",
        "default",
        "do",
        "double",
        "else",
        "enum",
        "extends",
        "final",
        "finally",
        "float",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "int",
        "interface",
        "long",
        "native",
        "new",
        "package",
        "private",
        "protected",
        "public",
        "return",
        "short",
        "static",
        "strictfp",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "try",
        "void",
        "volatile",
        "while",

        // technically these are not reserved words but they cannot be used as identifiers.
        "true",
        "false",
        "null"
    ));
}
