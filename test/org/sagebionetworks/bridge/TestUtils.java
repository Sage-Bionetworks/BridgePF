package org.sagebionetworks.bridge;

import java.util.concurrent.Callable;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestUtils {

    private static Logger logger = LoggerFactory.getLogger(TestUtils.class);
    
    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;
        @Override
        public void run() {
            try {
                testCode();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    // Waiting for that eventual consistency to ensure the test passes every time.
    // 3x with the correct answer is assumed to be propagated.
    public static void waitFor(Callable<Boolean> callable) throws Exception {
        int delay = 250;
        int loopLimit = 40;
        int successesLimit = 3;
        int loops = 0;
        int successes = 0;
        while (successes < successesLimit && loops < loopLimit) {
            if (callable.call()) {
                successes++;
            } else {
                successes = 0;
            }
            loops++;
            String msg = String.format("waitFor sleeping %sms (%s/%s successes after loop %s/%s)", delay, successes,
                    successesLimit, loops, loopLimit);
            logger.info(msg);
            System.out.println(msg);
            Thread.sleep(delay);
        }
    }
    
    public static String randomName() {
        return "test-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

 }
