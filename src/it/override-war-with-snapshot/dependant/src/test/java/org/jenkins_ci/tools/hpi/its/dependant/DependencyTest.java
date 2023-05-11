package org.jenkins_ci.tools.hpi.its.dependant;

import java.io.IOException;
import org.jenkins_ci.tools.hpi.its.override_war_with_snapshot.Version;
import org.junit.Assert;
import org.junit.Test;

public class DependencyTest {

    @Test
    public void testVersionOfDependency() throws IOException {
        Assert.assertEquals("2.0.0-SNAPSHOT", Version.getVersion());
    }
}