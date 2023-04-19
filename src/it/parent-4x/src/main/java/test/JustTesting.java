package test;

import hudson.Main;
import hudson.init.Initializer;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;

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
                            Jenkins.getInstance().doSafeExit(null);
                            return null;
                        },
                        15,
                        TimeUnit.SECONDS);
    }
}
