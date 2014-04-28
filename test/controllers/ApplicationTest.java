package controllers;
import org.junit.*;
import org.sagebionetworks.bridge.TestConstants;

import play.libs.WS;
import play.libs.F.Callback;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;
import play.test.TestBrowser;
import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

public class ApplicationTest {

    @Test
    public void test() {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                browser.goTo(TestConstants.TEST_URL);
                assertThat(browser.pageSource()).contains("Bridge: Patients");
            }
        });
    }

    @Test
    public void indexRedirectsToApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TestConstants.TEST_URL+"/index.html").setFollowRedirects(false);
				Response response = holder.get().get(TestConstants.REQUEST_TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(SEE_OTHER);
				assertThat(response.getHeader(LOCATION)).isEqualTo("/");
			}
		});
    }
    @Test
    public void canLoadApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TestConstants.TEST_URL);
				Response response = holder.get().get(TestConstants.REQUEST_TIMEOUT);
				assertThat(response.getStatus()).isEqualTo(OK);
				assertThat(response.getBody()).contains("Bridge: Patients &amp; Researchers in Partnership - Sage Bionetworks");
			}
		});
    }
}
