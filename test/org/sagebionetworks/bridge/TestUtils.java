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
    
    public static void waitFor(Callable<Boolean> callable) throws Exception {
        int countdown = 200;
        boolean processing = true;
        while(countdown-- > 0 && processing) {
            logger.info("    sleeping 200ms");
            Thread.sleep(200);
            processing = !callable.call();
        }
    }

 }
