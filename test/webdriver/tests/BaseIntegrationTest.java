package webdriver.tests;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.inMemoryDatabase;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import org.sagebionetworks.bridge.TestConstants;

import play.libs.F.Callback;
import play.test.TestBrowser;

public class BaseIntegrationTest {

    protected void call(Callback<TestBrowser> callback) {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), TestConstants.FIREFOX_DRIVER, callback);
    }

}
