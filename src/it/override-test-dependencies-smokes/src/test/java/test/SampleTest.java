package test;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SampleTest {

//    @Rule
//    public JenkinsRule r = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        System.err.println(System.getProperty("java.class.path"));
    }

}
