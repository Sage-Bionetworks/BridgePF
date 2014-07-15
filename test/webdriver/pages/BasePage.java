package webdriver.pages;

import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import com.google.common.base.Predicate;

import play.test.TestBrowser;

public class BasePage {

    protected TestBrowser browser;

    protected BasePage(TestBrowser browser) {
        this.browser = browser;
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
