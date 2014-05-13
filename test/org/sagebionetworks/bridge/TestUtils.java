package org.sagebionetworks.bridge;

import static org.junit.Assert.fail;

public class TestUtils {

    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;
        @Override
        public void run() {
            try {
                testCode();
            } catch(Exception e) {
                fail(e.getMessage());
            }       
        }
    }

}
