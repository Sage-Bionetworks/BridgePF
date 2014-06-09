package webdriver.pages;

import static org.fest.assertions.Assertions.assertThat;
import static org.sagebionetworks.bridge.TestConstants.*;

import java.util.concurrent.TimeUnit;

import org.fluentlenium.core.domain.FluentWebElement;

import play.test.TestBrowser;

public class AppPage {

    private TestBrowser browser;
    
    public AppPage(TestBrowser browser) {
        this.browser = browser;
        browser.goTo(TEST_URL);
        assertThat(browser.pageSource()).contains("Bridge: Patients");
        if (browser.findFirst(SIGN_OUT_LINK).isDisplayed()) {
            signOut();
        }
    }
    
    public SignInDialog openSignInDialog() {
        signInLink().click();
        browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_IN_DIALOG).isPresent();
        return new SignInDialog(browser);
    }
    public RequestResetPasswordDialog openResetPasswordDialog() {
        resetPasswordLink().click();
        browser.await().atMost(20, TimeUnit.SECONDS).until(RESET_PASSWORD_DIALOG).isPresent();
        return new RequestResetPasswordDialog(browser);
    }
    public SignUpDialog openSignUpDialog() {
        signUpLink().click();
        browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_UP_DIALOG).isPresent();
        return new SignUpDialog(browser);
    }

    public void signOut() {
        signOutLink().click();
        browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_IN_LINK).isPresent();
        browser.await().atMost(20, TimeUnit.SECONDS).until(RESET_PASSWORD_LINK).isPresent();
    }
    private FluentWebElement resetPasswordLink() {
        return browser.findFirst(RESET_PASSWORD_LINK);
    }
    private FluentWebElement signInLink() {
        return browser.findFirst(SIGN_IN_LINK);
    }
    private FluentWebElement signOutLink() {
        return browser.findFirst(SIGN_OUT_LINK);
    }
    private FluentWebElement signUpLink() {
        return browser.findFirst(SIGN_UP_LINK);
    }
    
    public class SignInDialog {
       
        private TestBrowser browser;
        
        public SignInDialog(TestBrowser browser) {
            this.browser = browser;
        }
        public void signInWrong(String username, String password) {
            enterCredentials(username, password);
            signInAction().click();
            browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_IN_MESSAGE).areDisplayed();
            close();
        }
        public void signIn(String username, String password) {
            enterCredentials(username, password);
            signInAction().click();
            browser.await().atMost(20, TimeUnit.SECONDS).until(USERNAME_LABEL).areDisplayed();
            assertThat(userLabel().getText()).isEqualTo(username);
        }
        public void close() {
            browser.click(CLOSE_ACTION);
            browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_IN_DIALOG).isNotPresent();
        }
        private void enterCredentials(String username, String password) {
            assertThat(signInMessage().isDisplayed()).isFalse();
            assertThat(signInAction().isEnabled()).isFalse();
            browser.fill(USERNAME_INPUT).with(username);
            browser.fill(PASSWORD_INPUT).with(password);
            assertThat(signInAction().isEnabled()).isTrue();
        }
        private FluentWebElement signInMessage() {
            return browser.findFirst(SIGN_IN_MESSAGE);
        }
        private FluentWebElement signInAction() {
            return browser.findFirst(SIGN_IN_ACT);
        }
        private FluentWebElement userLabel() {
            return browser.findFirst(USERNAME_LABEL);
        }
    }
    
    public class RequestResetPasswordDialog {
        private TestBrowser browser;
        
        public RequestResetPasswordDialog(TestBrowser browser) {
            this.browser = browser;
        }
        public void canCancel() {
            browser.await().atMost(20, TimeUnit.SECONDS).until(RESET_PASSWORD_DIALOG).isPresent();
            cancelButton().click();
            browser.await().atMost(20, TimeUnit.SECONDS).until(RESET_PASSWORD_DIALOG).isNotPresent();
            close();
        }
        public void submitInvalidEmailAddress(String email) {
            assertThat(sendEmailButton().isEnabled()).isFalse();
            browser.fill(EMAIL_INPUT).with(email);
            assertThat(sendEmailButton().isEnabled()).isFalse();
            close();
        }
        public void submitEmailAddress(String email) {
            assertThat(sendEmailButton().isEnabled()).isFalse();
            browser.fill(EMAIL_INPUT).with(email);
            assertThat(sendEmailButton().isEnabled()).isTrue();
            sendEmailButton().click();
            browser.await().atMost(20, TimeUnit.SECONDS).until(RESET_PASSWORD_DIALOG).isNotPresent();
            assertThat(messagePopup().getText()).contains("Please look for further instructions in your email inbox.");
        }
        public void close() {
            browser.click(CLOSE_ACTION);
            browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_IN_DIALOG).isNotPresent();
        }
        private FluentWebElement messagePopup() {
            return browser.findFirst(".humane");
        }
        private FluentWebElement sendEmailButton() {
            return browser.findFirst(SEND_ACTION);
        }
        private FluentWebElement cancelButton() {
            return browser.findFirst(CANCEL_ACTION);
        }
    }
    
    public class SignUpDialog {
        private TestBrowser browser;
        
        public SignUpDialog(TestBrowser browser) {
            this.browser = browser;
        }
        public void enterValidData() {
            assertThat(submitButton().isEnabled()).isFalse();
            enterFields("bridge", "bridgeit@sagebase.org", "P4ssword", "P4ssword");
            assertThat(submitButton().isEnabled()).isTrue();
        }
        public void enterInvalidData(String username, String email, String password, String confirmPassword) {
            assertThat(submitButton().isEnabled()).isFalse();
            enterFields(username, email, password, confirmPassword);
            assertThat(submitButton().isEnabled()).isFalse();
        }
        public void enterInvalidDataAfterValidData(String username, String email, String password, String confirmPassword) {
            enterFields(username, email, password, confirmPassword);
            assertThat(submitButton().isEnabled()).isFalse();
        }
        public void assertEmailEmailError() {
            assertThat(browser.findFirst("#emailEmailError").isDisplayed()).isTrue();
        }
        public void assertEmailRequiredError() {
            assertThat(browser.findFirst("#emailRequiredError").isDisplayed()).isTrue();
        }
        public void assertPasswordPatternError() {
            assertThat(browser.findFirst("#passwordPatternError").isDisplayed()).isTrue();
        }
        public void assertPasswordConfirmEqualError() {
            assertThat(browser.findFirst("#passwordConfirmEqualError").isDisplayed()).isTrue();
        }
        public void close() {
            browser.click(CLOSE_ACTION);
            browser.await().atMost(20, TimeUnit.SECONDS).until(SIGN_UP_DIALOG).isNotPresent();
        }
        private void enterFields(String username, String email, String password, String confirmPassword) {
            browser.fill(USERNAME_INPUT).with(username);   
            browser.fill(EMAIL_INPUT).with(email);
            browser.fill(PASSWORD_INPUT).with(password);
            browser.fill(CONFIRM_PASSWORD_INPUT).with(confirmPassword);
        }
        private FluentWebElement submitButton() {
            return browser.findFirst(SIGN_UP_ACT);
        }
        
    }

}
