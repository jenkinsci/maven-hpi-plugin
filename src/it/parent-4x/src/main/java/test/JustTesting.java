package test;

import hudson.Main;
import hudson.init.Initializer;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.kohsuke.stapler.StaplerRequest2;

public class JustTesting {

    private JustTesting() {}

    @Initializer
    public static void shutDown() {
        if (Main.isUnitTest) {
            return;
        }
        Timer.get()
                .schedule(
                        () -> {
                            Jenkins.getInstance().doSafeExit((StaplerRequest2) null);
                            return null;
                        },
                        15,
                        TimeUnit.SECONDS);
    }
}
