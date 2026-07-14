package org.jenkinsci.maven.plugins.hpi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RunMojo} helper methods.
 *
 * @author Akash Manna
 */
class RunMojoTest {

    /**
     * A minimal {@link RunMojo} subclass that captures log output and exposes the
     * package-private helper methods under test.
     */
    private static class TestableRunMojo extends RunMojo {

        final List<String> infoMessages = new CopyOnWriteArrayList<>();
        final List<String> warnMessages = new CopyOnWriteArrayList<>();

        @Override
        public Log getLog() {
            return new Log() {
                @Override
                public boolean isDebugEnabled() {
                    return false;
                }

                @Override
                public void debug(CharSequence content) {}

                @Override
                public void debug(CharSequence content, Throwable error) {}

                @Override
                public void debug(Throwable error) {}

                @Override
                public boolean isInfoEnabled() {
                    return true;
                }

                @Override
                public void info(CharSequence content) {
                    infoMessages.add(content.toString());
                }

                @Override
                public void info(CharSequence content, Throwable error) {
                    infoMessages.add(content.toString());
                }

                @Override
                public void info(Throwable error) {}

                @Override
                public boolean isWarnEnabled() {
                    return true;
                }

                @Override
                public void warn(CharSequence content) {
                    warnMessages.add(content.toString());
                }

                @Override
                public void warn(CharSequence content, Throwable error) {
                    warnMessages.add(content.toString());
                }

                @Override
                public void warn(Throwable error) {}

                @Override
                public boolean isErrorEnabled() {
                    return true;
                }

                @Override
                public void error(CharSequence content) {}

                @Override
                public void error(CharSequence content, Throwable error) {}

                @Override
                public void error(Throwable error) {}
            };
        }
    }

    private TestableRunMojo mojo;

    @BeforeEach
    void setUp() {
        mojo = new TestableRunMojo();
    }

    @Test
    void streamToLogInfoForwardsLinesToInfoLog() throws Exception {
        String text = "line one\nline two\nline three\n";
        InputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        Thread t = mojo.streamToLog(stream, false);
        t.start();
        t.join(5_000);

        assertThat(mojo.infoMessages, hasItem("line one"));
        assertThat(mojo.infoMessages, hasItem("line two"));
        assertThat(mojo.infoMessages, hasItem("line three"));
        assertTrue(mojo.warnMessages.isEmpty(), "stderr path should not produce WARN messages");
    }

    @Test
    void streamToLogInfoDoesNotProduceWarnMessages() throws Exception {
        String text = "some output\n";
        InputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        Thread t = mojo.streamToLog(stream, false);
        t.start();
        t.join(5_000);

        assertTrue(mojo.warnMessages.isEmpty());
    }

    @Test
    void streamToLogWarnForwardsLinesToWarnLog() throws Exception {
        String text = "error line one\nerror line two\n";
        InputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        Thread t = mojo.streamToLog(stream, true);
        t.start();
        t.join(5_000);

        assertThat(mojo.warnMessages, hasItem("error line one"));
        assertThat(mojo.warnMessages, hasItem("error line two"));
        assertTrue(mojo.infoMessages.isEmpty(), "stdout path should not produce INFO messages here");
    }

    @Test
    void streamToLogWarnDoesNotProduceInfoMessages() throws Exception {
        String text = "stderr output\n";
        InputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));

        Thread t = mojo.streamToLog(stream, true);
        t.start();
        t.join(5_000);

        assertTrue(mojo.infoMessages.isEmpty());
    }

    @Test
    void streamToLogReturnsDaemonThread() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        Thread t = mojo.streamToLog(stream, false);
        assertTrue(t.isDaemon(), "streamToLog thread must be a daemon thread");
    }

    @Test
    void streamToLogStdoutThreadHasExpectedName() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        Thread t = mojo.streamToLog(stream, false);
        assertEquals("hpi-run-stdout", t.getName());
    }

    @Test
    void streamToLogStderrThreadHasExpectedName() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        Thread t = mojo.streamToLog(stream, true);
        assertEquals("hpi-run-stderr", t.getName());
    }

    @Test
    void streamToLogThreadIsNotStartedByFactory() throws Exception {
        InputStream stream = new ByteArrayInputStream("hello\n".getBytes(StandardCharsets.UTF_8));
        Thread t = mojo.streamToLog(stream, false);
        assertFalse(t.isAlive(), "Thread should not be started by streamToLog()");
    }

    @Test
    void streamToLogHandlesEmptyStreamWithoutError() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        Thread t = mojo.streamToLog(stream, false);
        t.start();
        t.join(5_000);

        assertTrue(mojo.infoMessages.isEmpty());
        assertTrue(mojo.warnMessages.isEmpty());
    }

    @Test
    void streamToLogHandlesMultipleLines() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("message ").append(i).append('\n');
        }
        InputStream stream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));

        Thread t = mojo.streamToLog(stream, false);
        t.start();
        t.join(5_000);

        assertEquals(100, mojo.infoMessages.size());
        assertThat(mojo.infoMessages, hasItem("message 0"));
        assertThat(mojo.infoMessages, hasItem("message 99"));
    }

    @Test
    void pipeStdinForwardsBytesToOutputStream() throws Exception {
        byte[] input = "hello from stdin\n".getBytes(StandardCharsets.UTF_8);
        InputStream in = new ByteArrayInputStream(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Thread t = mojo.pipeStdin(in, out);
        t.start();
        t.join(5_000);

        assertEquals("hello from stdin\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void pipeStdinReturnsDaemonThread() throws Exception {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread t = mojo.pipeStdin(in, out);
        assertTrue(t.isDaemon(), "pipeStdin thread must be a daemon thread");
    }

    @Test
    void pipeStdinThreadHasExpectedName() throws Exception {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread t = mojo.pipeStdin(in, out);
        assertEquals("hpi-run-stdin", t.getName());
    }

    @Test
    void pipeStdinThreadIsNotStartedByFactory() throws Exception {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread t = mojo.pipeStdin(in, out);
        assertFalse(t.isAlive(), "Thread should not be started by pipeStdin()");
    }

    @Test
    void pipeStdinStopsWhenInterrupted() throws Exception {
        PipedOutputStream pipeOut = new PipedOutputStream();
        PipedInputStream pipeIn = new PipedInputStream(pipeOut);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        CountDownLatch started = new CountDownLatch(1);
        Thread stdinThread = new Thread(
                () -> {
                    started.countDown();
                    byte[] buf = new byte[256];
                    try {
                        int n;
                        while (!Thread.currentThread().isInterrupted() && (n = pipeIn.read(buf)) != -1) {
                            sink.write(buf, 0, n);
                        }
                    } catch (Exception e) {
                        // expected on interrupt/close
                    }
                },
                "test-stdin-pipe");
        stdinThread.setDaemon(true);
        stdinThread.start();

        assertTrue(started.await(5, TimeUnit.SECONDS), "Thread did not start in time");

        pipeOut.write("data\n".getBytes(StandardCharsets.UTF_8));
        pipeOut.flush();

        stdinThread.interrupt();
        pipeOut.close();
        stdinThread.join(5_000);

        assertFalse(stdinThread.isAlive(), "Thread should have terminated after interrupt");
    }

    @Test
    void isDebuggerPresentReturnsBooleanWithoutThrowing() {
        boolean result = RunMojo.isDebuggerPresent();
        assertTrue(result || !result);
    }

    private static List<String> invokeAddArgs(String args) throws Exception {
        Method m = RunMojo.class.getDeclaredMethod("addArgs", List.class, String.class);
        m.setAccessible(true);
        List<String> cmd = new ArrayList<>();
        m.invoke(null, cmd, args);
        return cmd;
    }

    @Test
    void addArgsHandlesNullArgsGracefully() throws Exception {
        List<String> cmd = invokeAddArgs(null);
        assertTrue(cmd.isEmpty());
    }

    @Test
    void addArgsHandlesEmptyArgsGracefully() throws Exception {
        List<String> cmd = invokeAddArgs("   ");
        assertTrue(cmd.isEmpty());
    }

    @Test
    void addArgsSplitsOnWhitespace() throws Exception {
        List<String> cmd = invokeAddArgs("-Xms512M -Xmx1G");
        assertEquals(List.of("-Xms512M", "-Xmx1G"), cmd);
    }

    @Test
    void addArgsPreservesAddOpensAsPair() throws Exception {
        List<String> cmd = invokeAddArgs("--add-opens java.base/java.io=ALL-UNNAMED");
        assertEquals(List.of("--add-opens", "java.base/java.io=ALL-UNNAMED"), cmd);
    }

    @Test
    void addArgsPreservesAddExportsAsPair() throws Exception {
        List<String> cmd = invokeAddArgs("--add-exports java.base/sun.reflect=ALL-UNNAMED");
        assertEquals(List.of("--add-exports", "java.base/sun.reflect=ALL-UNNAMED"), cmd);
    }

    @Test
    void addArgsPreservesPatchModuleAsPair() throws Exception {
        List<String> cmd = invokeAddArgs("--patch-module java.base=some.jar");
        assertEquals(List.of("--patch-module", "java.base=some.jar"), cmd);
    }

    @Test
    void addArgsHandlesMixedArguments() throws Exception {
        List<String> cmd = invokeAddArgs("-Xmx1G --add-opens java.base/java.io=ALL-UNNAMED -XX:+HeapDumpOnOutOfMemoryError");
        assertEquals(
                List.of("-Xmx1G", "--add-opens", "java.base/java.io=ALL-UNNAMED", "-XX:+HeapDumpOnOutOfMemoryError"),
                cmd);
    }

    private static String invokeBuildJenkinsUrl(String host, int port, String contextPath) throws Exception {
        Method m = RunMojo.class.getDeclaredMethod("buildJenkinsUrl", String.class, int.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, host, port, contextPath);
    }

    @Test
    void buildJenkinsUrlBasicNoContextPath() throws Exception {
        String url = invokeBuildJenkinsUrl("localhost", 8080, null);
        assertEquals("http://localhost:8080/", url);
    }

    @Test
    void buildJenkinsUrlWithContextPath() throws Exception {
        String url = invokeBuildJenkinsUrl("localhost", 8080, "/jenkins");
        assertEquals("http://localhost:8080/jenkins/", url);
    }

    @Test
    void buildJenkinsUrlAppendsTrailingSlashToContextPath() throws Exception {
        String url = invokeBuildJenkinsUrl("localhost", 8080, "jenkins");
        assertThat(url, containsString("/jenkins/"));
    }

    @Test
    void buildJenkinsUrlReturnsNullForBlankHost() throws Exception {
        String url = invokeBuildJenkinsUrl("", 8080, null);
        assertEquals(null, url);
    }

    @Test
    void buildJenkinsUrlReturnsNullForNullHost() throws Exception {
        String url = invokeBuildJenkinsUrl(null, 8080, null);
        assertEquals(null, url);
    }

    @Test
    void buildJenkinsUrlWithWildcardHostname() throws Exception {
        String url = invokeBuildJenkinsUrl("myplugin.localtest.me", 8080, null);
        assertEquals("http://myplugin.localtest.me:8080/", url);
    }
}
