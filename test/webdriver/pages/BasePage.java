package webdriver.pages;

import java.util.concurrent.TimeUnit;

import play.test.TestBrowser;

public class BasePage {

    protected TestBrowser browser;
    
    protected BasePage(TestBrowser browser) {
        this.browser = browser;
    }

    protected void screenshot(String name) {
        browser.takeScreenShot("screenshots/"+ name + ".png");
    }
    protected void waitUntilPresent(String cssSelector) {
        browser.await().atMost(10, TimeUnit.SECONDS).until(cssSelector).isPresent();
    }
    
    protected void waitUntilNotPresent(String cssSelector) {
        browser.await().atMost(10, TimeUnit.SECONDS).until(cssSelector).isNotPresent();
    }
    
    protected void waitUntilDisplayed(String cssSelector) {
        browser.await().atMost(10, TimeUnit.SECONDS).until(cssSelector).areDisplayed();
    }
    
}
