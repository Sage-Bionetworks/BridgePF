package controllers;

import static org.apache.commons.httpclient.HttpStatus.SC_OK;
import static org.apache.commons.httpclient.HttpStatus.SC_SEE_OTHER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.mvc.Http.HeaderNames.LOCATION;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import org.junit.Test;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

public class ApplicationControllerTest {

    @Test
    public void indexRedirectsToApp() {
        running(testServer(3333), new Runnable() {
            public void run() {
                WSRequestHolder holder = WS.url(TEST_BASE_URL + "/index.html")
                        .setFollowRedirects(false);
                Response response = holder.get().get(TIMEOUT);
                assertEquals("HTTP status is SEE OTHER", SC_SEE_OTHER,
                        response.getStatus());
                assertEquals("Location header redirects to /", "/",
                        response.getHeader(LOCATION));
            }
        });
    }

    @Test
    public void canLoadApp() {
        running(testServer(3333), new Runnable() {
            public void run() {
                WSRequestHolder holder = WS.url(TEST_BASE_URL);
                Response response = holder.get().get(TIMEOUT);
                assertEquals("HTTP status is OK (200)", SC_OK,
                        response.getStatus());
                assertTrue("Page title includes expected Bridge text", response
                        .getBody().contains("Sage Bionetworks"));
            }
        });
    }
}
