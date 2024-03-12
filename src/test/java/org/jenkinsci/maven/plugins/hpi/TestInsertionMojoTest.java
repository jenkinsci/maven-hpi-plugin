package org.jenkinsci.maven.plugins.hpi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

public class TestInsertionMojoTest {

    @Test
    public void legalizePackageName() throws Exception {
        assertEquals(
                "org.jenkinsci.maven.plugins.hpi",
                TestInsertionMojo.legalizePackageName("org.jenkinsci.maven.plugins.hpi"));
        assertEquals(
                "_123org.jenkins_ci.maven.plugins.hpi",
                TestInsertionMojo.legalizePackageName("123org.jenkins-ci.maven.plugins.hpi"));
        assertThrows(
                MojoExecutionException.class,
                () -> TestInsertionMojo.legalizePackageName("org.jenkinsci%maven.plugins.hpi"));
    }
}
