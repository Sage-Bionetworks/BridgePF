package org.sagebionetworks.bridge;

import static org.sagebionetworks.bridge.TestConstants.*;

import java.util.Map;

import play.libs.WS;
import play.libs.WS.WSRequestHolder;

public class TestUtils {
    
    public abstract static class FailableRunnable implements Runnable {
        public abstract void testCode() throws Exception;
        @Override
        public void run() {
            try {
                testCode();
            } catch (Exception e) {
                // there is no fail(e);
                throw new RuntimeException(e);
            }
        }
    }

    public static WSRequestHolder getURL(String sessionToken, String path) {
        System.out.println("**** " + path);
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
    
    // Useful in tests because Play Framework seems to disable all logging of the server code 
    // during integration tests. This is a quick and dirty way to ping what is happening from 
    // the integration tests. Remove references before committing code.
    public static void output(String tag, String output) {
        String str = String.format("\033[31m%s: \033[0m%s", tag, output);
        System.out.println(str);
    }

 }
