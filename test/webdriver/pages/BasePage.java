package webdriver.pages;

import static org.sagebionetworks.bridge.TestConstants.TOAST_DIALOG;

import java.util.concurrent.TimeUnit;

import org.fluentlenium.core.domain.FluentWebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import play.test.TestBrowser;

import com.google.common.base.Predicate;

public class BasePage {

    protected TestBrowser browser;

    protected BasePage(TestBrowser browser) {
        this.browser = browser;
    }
    
    protected void click(String cssSelector) {
        waitUntilPresent(cssSelector);
        browser.findFirst(cssSelector).click();
    }

    protected void screenshot(String name) {
        browser.takeScreenShot("screenshots/" + name + ".png");
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
    
    protected void waitUntilEnabled(String cssSelector) {
        browser.await().atMost(10, TimeUnit.SECONDS).until(cssSelector).areEnabled();
    }
    
    protected FluentWebElement messagePopup() {
        waitUntilPresent(TOAST_DIALOG);
        return browser.findFirst(TOAST_DIALOG);
    }

    /**
     * If you force the focus to shift to the first element of a form, this can interfere
     * with test scripting. So wait for that to happen before you proceed (pass the css
     * selector of the element that should have the focus before you begin to this method).
     */
    protected void waitUntilScriptHasFocused(final String cssSelector) {
        browser.await().atMost(10, TimeUnit.SECONDS).until(new Predicate<WebDriver>() {
            public boolean apply(WebDriver driver) {
                WebElement element = driver.findElement(By.cssSelector(cssSelector));
                return (element.equals(driver.switchTo().activeElement()));
            }
        });        
    }
    
}
