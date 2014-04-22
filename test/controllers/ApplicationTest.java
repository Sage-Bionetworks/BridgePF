package controllers;
import org.junit.*;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;
import test.TestConstants;
import static org.fest.assertions.Assertions.*;
import static play.test.Helpers.*;

/**
*
* Simple (JUnit) tests that can call all parts of a play app.
* If you are interested in mocking a whole application, see the wiki for more details.
*
*/
public class ApplicationTest {

    @Test
    public void simpleCheck() {
        int a = 1 + 1;
        assertThat(a).isEqualTo(2);
    }

    @Test
    public void indexRedirectsToApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TestConstants.TEST_URL+"/index.html").setFollowRedirects(false);
				Response response = holder.get().get();
				assertThat(response.getStatus()).isEqualTo(SEE_OTHER);
				assertThat(response.getHeader(LOCATION)).isEqualTo("/");
			}
		});
    	/* Result result = callAction(controllers.routes.ref.Application.redirectToApp());
        assertThat(status(result)).isEqualTo(SEE_OTHER);
        assertThat(headers(result).get(LOCATION)).isEqualTo("/");*/
    }
    
    @Test
    public void canLoadApp() {
    	running(testServer(3333), new Runnable() {
			public void run() {
				WSRequestHolder holder = WS.url(TestConstants.TEST_URL);
				Response response = holder.get().get();
				assertThat(response.getStatus()).isEqualTo(OK);
				assertThat(response.getBody()).contains("Bridge: Patients &amp; Researchers in Partnership - Sage Bionetworks");
			}
		});
    }
}
