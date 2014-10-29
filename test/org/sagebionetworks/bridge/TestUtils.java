package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.libs.WS;
import play.libs.WS.WSRequestHolder;

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

    public static WSRequestHolder getURL(String sessionToken, String path) {
        return getURL(sessionToken, path, null);
    }
    
    public static WSRequestHolder getURL(String sessionToken, String path, Map<String,String> queryMap) {
        WSRequestHolder request = WS.url(TEST_BASE_URL + path).setHeader(BridgeConstants.SESSION_TOKEN_HEADER, sessionToken);
        if (queryMap != null) {
            for (Map.Entry<String,String> entry : queryMap.entrySet()) {
                request.setQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }
    
    // Waiting for that eventual consistency to ensure the test passes every time.
    // 3x with the correct answer is assumed to be propagated.
    public static void waitFor(Callable<Boolean> callable) throws Exception {
        int delay = 200;
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
    
    /* This waited for one right answer, then slept, then continued, it was pretty reliable
     * but not 100% reliable.
    public static void waitForOriginal(Callable<Boolean> callable) throws Exception {
        int countdown = 20;
        boolean processing = true;
        while(countdown-- > 0 && processing) {
            Thread.sleep(200);
            processing = !callable.call();
        }
        // And then, it seems there's an issue with eventual consistency, even after
        // the system returns at least one desired state change. Possible with DynamoDB?
        System.out.println("Waiting 2s after condition was true for eventual consistency(?)");
        Thread.sleep(2000);
    }*/

 }
