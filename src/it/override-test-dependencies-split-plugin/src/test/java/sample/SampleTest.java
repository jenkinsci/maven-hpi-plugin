package sample;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.jenkins.plugins.sample.Util;
import org.junit.Test;

public class SampleTest {

    @Test
    public void smokes() throws Exception {
        // just load some org.apache.commons.compress types:
        assertThat(Util.x(), is(259L));
    }
}
