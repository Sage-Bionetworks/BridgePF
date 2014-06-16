package webdriver.pages;

import static org.junit.Assert.*;
import static org.sagebionetworks.bridge.TestConstants.*;

import org.fluentlenium.core.domain.FluentWebElement;

import play.test.TestBrowser;

public class JoinPage extends BasePage {

    public JoinPage(TestBrowser browser) {
        super(browser);
    }
    public void enterValidData() {
        assertFalse("Submit button is initially disabled", joinButton().isEnabled());
        enterFields("bridge", "bridgeit@sagebase.org", "P4ssword", "P4ssword");
        assertTrue("Submit button is enabled", joinButton().isEnabled());
    }
    public void enterInvalidData(String username, String email, String password, String confirmPassword) {
        assertFalse("submit button is initially disabled", joinButton().isEnabled());
        enterFields(username, email, password, confirmPassword);
        assertFalse("submit button stays disabled", joinButton().isEnabled());
    }
    public void enterInvalidDataAfterValidData(String username, String email, String password, String confirmPassword) {
        enterFields(username, email, password, confirmPassword);
        assertFalse("Submit button is disabled", joinButton().isEnabled());
    }
    public void assertEmailEmailError() {
        assertTrue("email error is shown", browser.findFirst("#emailEmailError").isDisplayed());
    }
    public void assertEmailRequiredError() {
        assertTrue("email is required error is shown", browser.findFirst("#emailRequiredError").isDisplayed());
    }
    public void assertPasswordConfirmEqualError() {
        assertTrue("Password confirmation error is displayed", browser.findFirst("#passwordConfirmEqualError")
                .isDisplayed());
    }
    private void enterFields(String username, String email, String password, String confirmPassword) {
        browser.fill(USERNAME_INPUT).with(username);   
        browser.fill(EMAIL_INPUT).with(email);
        browser.fill(PASSWORD_INPUT).with(password);
        browser.fill(PASSWORD_CONFIRM_INPUT).with(confirmPassword);
    }
    private FluentWebElement joinButton() {
        waitUntilPresent(JOIN_ACT);
        return browser.findFirst(JOIN_ACT);
    }
}
