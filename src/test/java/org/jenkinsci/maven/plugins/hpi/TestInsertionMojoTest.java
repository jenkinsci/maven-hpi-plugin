package org.jenkinsci.maven.plugins.hpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

class TestInsertionMojoTest {

    @Test
    void legalizePackageName() throws Exception {
        assertEquals(
                "org.jenkinsci.maven.plugins.hpi",
                TestInsertionMojo.legalizePackageName("org.jenkinsci.maven.plugins.hpi"));
        assertEquals(
                "_123org.jenkins_ci.maven.plugins.hpi",
                TestInsertionMojo.legalizePackageName("123org.jenkins-ci.maven.plugins.hpi"));
        assertThrows(
                MojoFailureException.class,
                () -> TestInsertionMojo.legalizePackageName("org.jenkinsci%maven.plugins.hpi"));
    }
}
