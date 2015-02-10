package org.sagebionetworks.bridge;

import org.apache.commons.lang3.RandomStringUtils;

public class TestUtils {

    //private static Logger logger = LoggerFactory.getLogger(TestUtils.class);
    
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
    
    public static String randomName() {
        return "test-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
    }

 }
