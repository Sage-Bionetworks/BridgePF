package webdriver.pages;

import static org.fest.assertions.Assertions.assertThat;

import static org.sagebionetworks.bridge.TestConstants.*;

import org.fluentlenium.core.domain.FluentWebElement;

import play.test.TestBrowser;

public class AppPage {

    private TestBrowser browser;
    
    public AppPage(TestBrowser browser) {
        this.browser = browser;
        browser.goTo(TEST_URL);
        assertThat(browser.pageSource()).contains("Bridge: Patients");
        waitForSignInLinks();
    }
    
    public SignInDialog openSignInDialog() {
        signInLink().click();
        browser.await().until(SIGN_IN_DIALOG);
        return new SignInDialog(browser);
    }
    public RequestResetPasswordDialog openResetPasswordDialog() {
        resetPasswordLink().click();
        browser.await().until(RESET_PASSWORD_DIALOG);
        return new RequestResetPasswordDialog(browser);
    }
    public void signOut() {
        signOutLink().click();
        waitForSignInLinks();
    }
    private void waitForSignInLinks() {
        browser.await().until(SIGN_IN_LINK).isPresent();
        browser.await().until(RESET_PASSWORD_LINK).isPresent();
    }
    private FluentWebElement resetPasswordLink() {
        // Test fail in Firefox
        // TODO: These may not be correct, is this better?
        browser.await().until(RESET_PASSWORD_LINK).isPresent();
        return browser.$(RESET_PASSWORD_LINK).first();
        //return browser.findFirst(RESET_PASSWORD_LINK);
    }
    private FluentWebElement signInLink() {
        browser.await().until(SIGN_IN_LINK).isPresent();
        return browser.$(SIGN_IN_LINK).first();
        //return browser.findFirst(SIGN_IN_LINK);
    }
    private FluentWebElement signOutLink() {
        browser.await().until(SIGN_OUT_LINK).isPresent();
        return browser.$(SIGN_OUT_LINK).first();
        //return browser.findFirst(SIGN_OUT_LINK);
    }
    
    public class SignInDialog {
       
        private TestBrowser browser;
        
        public SignInDialog(TestBrowser browser) {
            this.browser = browser;
        }
        public void signInWrong(String username, String password) {
            enterCredentials(username, password);
            signInAction().click();
            assertThat(signInMessage().isDisplayed()).isTrue();
            close();
        }
        public void signIn(String username, String password) {
            enterCredentials(username, password);
            signInAction().click();
            browser.await().until(USERNAME_LABEL).isPresent();
            assertThat(userLabel().getText()).isEqualTo(username);
            close();
        }
        public void close() {
            browser.click(".close");
            browser.await().until(SIGN_IN_DIALOG).isNotPresent();
        }
        private void enterCredentials(String username, String password) {
            assertThat(signInMessage().isDisplayed()).isFalse();
            assertThat(signInAction().isEnabled()).isFalse();
            browser.fill(USERNAME_INPUT).with(username);
            browser.fill(PASSWORD_INPUT).with(password);
            assertThat(signInAction().isEnabled()).isTrue();
        }
        private FluentWebElement signInMessage() {
            browser.await().until(SIGN_IN_MESSAGE).isPresent();
            return browser.$(SIGN_IN_MESSAGE).first();
            //return browser.findFirst(SIGN_IN_MESSAGE);
        }
        private FluentWebElement signInAction() {
            browser.await().until(SIGN_IN_ACT).isPresent();
            return browser.$(SIGN_IN_ACT).first();
            //return browser.findFirst(SIGN_IN_ACT);
        }
        private FluentWebElement userLabel() {
            browser.await().until(USERNAME_LABEL).isPresent();
            return browser.$(USERNAME_LABEL).first();
            //return browser.findFirst(USERNAME_LABEL);
        }
    }
    
    public class RequestResetPasswordDialog {
        private TestBrowser browser;
        
        public RequestResetPasswordDialog(TestBrowser browser) {
            this.browser = browser;
        }
        public void canCancel() {
            browser.await().until(RESET_PASSWORD_DIALOG).isPresent();
            cancelButton().click();
            browser.await().until(RESET_PASSWORD_DIALOG).isNotPresent();
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
            browser.await().until(RESET_PASSWORD_DIALOG).isNotPresent();
            assertThat(messagePopup().getText()).contains("Please look for further instructions in your email inbox.");
            close();
        }
        public void close() {
            browser.click(".close");
            browser.await().until(SIGN_IN_DIALOG).isNotPresent();
        }
        private FluentWebElement messagePopup() {
            browser.await().until(".humane").isPresent();
            return browser.$(".humane").first();
            // return browser.findFirst(".humane");
        }
        private FluentWebElement sendEmailButton() {
            browser.await().until(SEND_ACTION).isPresent();
            return browser.$(SEND_ACTION).first();
            //return browser.findFirst(SEND_ACTION);
        }
        private FluentWebElement cancelButton() {
            browser.await().until(CANCEL_ACTION).isPresent();
            return browser.$(CANCEL_ACTION).first();
            //return browser.findFirst(CANCEL_ACTION);
        }
    }

}
