package test;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SampleRootActionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        assertEquals(IOUtils.toString(SampleRootActionTest.class.getResource("expected.txt")),
                     r.createWebClient().goTo("sample", "text/plain").getWebResponse().getContentAsString().trim());
    }

}
