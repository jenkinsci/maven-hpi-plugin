package test;

import com.google.common.collect.ImmutableMap;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Manifest;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SampleTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        Map<String, String> expectedVersions = ImmutableMap.of("workflow-cps", "2.32", "workflow-api", "2.17");
        Enumeration<URL> manifests = SampleTest.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
        while (manifests.hasMoreElements()) {
            URL url = manifests.nextElement();
            try (InputStream is = url.openStream()) {
                Manifest mf = new Manifest(is);
                String pluginName = mf.getMainAttributes().getValue("Short-Name");
                String expectedVersion = expectedVersions.get(pluginName);
                if (expectedVersion != null) {
                    assertEquals("wrong version for " + pluginName + " as classpath entry", expectedVersion, mf.getMainAttributes().getValue("Plugin-Version"));
                }
            }
        }
        for (Map.Entry<String, String> entry : expectedVersions.entrySet()) {
            assertEquals("wrong version for " + entry.getKey() + " as plugin", entry.getValue(), r.jenkins.pluginManager.getPlugin(entry.getKey()).getVersion());
        }
    }

}
