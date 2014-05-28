package controllers;

import org.junit.*;

import play.libs.F.Callback;
import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;
import play.test.TestBrowser;
import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;

public class ApplicationControllerTest {

    @Test
    public void indexRedirectsToApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TEST_URL+"/index.html").setFollowRedirects(false);
				Response response = holder.get().get(TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(SEE_OTHER);
				assertThat(response.getHeader(LOCATION)).isEqualTo("/");
			}
		});
    }
    @Test
    public void canLoadApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TEST_URL);
				Response response = holder.get().get(TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(OK);
				assertThat(response.getBody()).contains("Bridge: Patients &amp; Researchers in Partnership - Sage Bionetworks");
			}
		});
    }
}
