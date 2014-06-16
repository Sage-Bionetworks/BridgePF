package controllers;

import org.junit.*;


import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

public class ApplicationControllerTest {

    @Test
    public void indexRedirectsToApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TEST_URL+"/index.html").setFollowRedirects(false);
				Response response = holder.get().get(TIMEOUT);
				assertEquals("HTTP status is SEE OTHER", SEE_OTHER, response.getStatus());
				assertEquals("Location header redirects to /", "/", response.getHeader(LOCATION));
			}
		});
    }
    @Test
    public void canLoadApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TEST_URL);
				Response response = holder.get().get(TIMEOUT);
				assertEquals("HTTP status is OK (200)", OK, response.getStatus());
                assertTrue("Page title includes expected Bridge text",
                        response.getBody().contains("Bridge: Patients &amp; Researchers"));
			}
		});
    }
}
