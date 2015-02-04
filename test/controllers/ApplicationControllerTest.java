package controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_BASE_URL;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static play.mvc.Http.HeaderNames.ACCESS_CONTROL_ALLOW_HEADERS;
import static play.mvc.Http.HeaderNames.ACCESS_CONTROL_ALLOW_METHODS;
import static play.mvc.Http.HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS;
import play.libs.WS.Response;
import play.libs.WS.WSRequestHolder;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ApplicationControllerTest {

    @Test
    public void testPreflight() {
        running(testServer(3333), new Runnable() {
            @Override
            public void run() {
                WSRequestHolder holder = WS.url(TEST_BASE_URL + "/anything");
                holder.setHeader(BridgeConstants.BRIDGE_HOST_HEADER, "api" + BridgeConfigFactory.getConfig().getStudyHostnamePostfix());
                Response response = holder.options().get(TIMEOUT);
                assertEquals(200, response.getStatus());
                assertEquals("https://assets.sagebridge.org", response.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN));
                assertEquals("HEAD, GET, OPTIONS, POST, PUT, DELETE", response.getHeader(ACCESS_CONTROL_ALLOW_METHODS));
                assertEquals("*", response.getHeader(ACCESS_CONTROL_ALLOW_HEADERS));
            }
        });
    }
}
