package webdriver.tests;

import static play.test.Helpers.fakeApplication;

import static play.test.Helpers.inMemoryDatabase;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.concurrent.TimeUnit;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver.Window;
import org.sagebionetworks.bridge.TestConstants;

import play.libs.F.Callback;
import play.test.TestBrowser;

public class BaseIntegrationTest {

    private TestBrowser configureDriver(TestBrowser browser) {
        Window window = browser.manage().window();
        window.setSize(new Dimension(1024,1400));
        browser.manage().deleteAllCookies();
        browser.manage().timeouts().pageLoadTimeout(300, TimeUnit.SECONDS);
        return browser;
    }
    
    private Callback<TestBrowser> wrapAndConfigureDriver(final Callback<TestBrowser> callback) {
        return new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) throws Throwable{
                callback.invoke(configureDriver(browser));
            }
        };
    }
    
    protected void call(Callback<TestBrowser> callback) {
        running(testServer(3333, fakeApplication(inMemoryDatabase())), TestConstants.FIREFOX_DRIVER, wrapAndConfigureDriver(callback));
    }

}
