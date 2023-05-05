package org.jenkins_ci.tools.hpi.its.override_war_with_snapshot;

import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public class Version {

    public static String getVersion() throws IOException {
        try (InputStream is = Version.class.getResourceAsStream("/META-INF/maven/org.jenkins-ci.tools.hpi.its/override-war-with-snapshot/pom.properties")) {
            Properties p = new Properties();
            p.load(is);
            return p.getProperty("version");
        }
    }
}