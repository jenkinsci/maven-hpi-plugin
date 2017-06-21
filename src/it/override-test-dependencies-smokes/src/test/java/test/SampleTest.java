package test;

import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class SampleTest {

//    @Rule
//    public JenkinsRule r = new JenkinsRule();

    @Test
    public void smokes() throws Exception {
        assertThat(System.getProperty("java.class.path"), endsWith(":extra:stuff:"));
    }

}
