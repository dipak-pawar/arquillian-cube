package org.arquillian.cube.docker.impl.await;

import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.dsl.OutputHandle;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.arquillian.cube.docker.impl.client.config.Await;
import org.arquillian.cube.docker.impl.docker.DockerClientExecutor;
import org.arquillian.cube.spi.Cube;

public class LogScanningAwaitStrategy extends SleepingAwaitStrategyBase {

    public static final String TAG = "log";

    private static final Logger logger = Logger.getLogger(LogScanningAwaitStrategy.class.getName());
    private static final String REGEXP_PREFIX = "regexp:";
    private final LogMatcher matcher;
    private int timeout = 15;
    private boolean stdOut;
    private boolean stdErr;
    private int occurrences = 1;
    private Cube<?> cube;
    private DockerClientExecutor dockerClientExecutor;

    public LogScanningAwaitStrategy(Cube<?> cube, DockerClientExecutor dockerClientExecutor, Await params) {
        super(params.getSleepPollingTime());

        this.cube = cube;
        this.dockerClientExecutor = dockerClientExecutor;

        this.stdOut = params.isStdOut();
        this.stdErr = params.isStdErr();

        this.occurrences = params.getOccurrences();

        if (params.getMatch().startsWith(REGEXP_PREFIX)) {
            matcher = new RegexpLogMatcher(params.getMatch().substring(REGEXP_PREFIX.length()));
        } else {
            matcher = new ContainsLogMatcher(params.getMatch());
        }

        if (params.getTimeout() != null) {
            this.timeout = params.getTimeout();
        }
    }

    public int getTimeout() {
        return timeout;
    }

    public boolean isStdOut() {
        return stdOut;
    }

    public boolean isStdErr() {
        return stdErr;
    }

    public int getOccurrences() {
        return occurrences;
    }

    @Override
    public boolean await() {
        final DockerClient client = dockerClientExecutor.getDockerClient();
        final CountDownLatch containerUp = new CountDownLatch(1);
        try {
            final LogContainerResultCallback callback = new LogContainerResultCallback(containerUp, this.occurrences);
            final OutputHandle follow = client.container().withName(cube.getId()).logs().writingOutput(System.out)
                .usingListener(callback).follow();

            final boolean await = containerUp.await(this.timeout, TimeUnit.SECONDS);
            follow.close();
            return await;
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, String.format("Log Await Strategy failed with %s", e.getMessage()));
            return false;
        }
    }

    private interface LogMatcher {

        boolean match(String line);
    }

    private static final class ContainsLogMatcher implements LogMatcher {

        private String substring;

        public ContainsLogMatcher(String substring) {
            this.substring = substring;
        }

        @Override
        public boolean match(String line) {
            return line.contains(substring);
        }
    }

    private static final class RegexpLogMatcher implements LogMatcher {

        private Pattern regex;

        public RegexpLogMatcher(String pattern) {
            regex = Pattern.compile(pattern, Pattern.DOTALL);
        }

        @Override
        public boolean match(String line) {
            return regex.matcher(line).matches();
        }
    }

    private class LogContainerResultCallback implements io.fabric8.docker.dsl.EventListener {

        private CountDownLatch containerUp;
        private int occurrences;

        public LogContainerResultCallback(CountDownLatch containerUp, int occurrences) {
            this.containerUp = containerUp;
            this.occurrences = occurrences;
        }

        @Override
        public void onSuccess(String s) {

        }

        @Override
        public void onError(String s) {

        }

        @Override
        public void onEvent(String s) {
            if (matcher.match(s)) {
                this.occurrences--;
                if (this.occurrences == 0) {
                    this.containerUp.countDown();
                }
            }
        }

        /*@Override
        public void onNext(Frame item) {
            String line = new String(item.getPayload());

            if (matcher.match(line)) {
                this.occurrences--;
                if (this.occurrences == 0) {
                    this.containerUp.countDown();
                }
            }
        }*/
    }
}
