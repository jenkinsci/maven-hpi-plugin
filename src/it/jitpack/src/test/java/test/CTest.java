package test;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class CTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void displayName() throws Exception {
        assertEquals("Test", new C().action().getObjectDisplayName());
    }

}
