package webdriver.tests;

import static org.sagebionetworks.bridge.TestConstants.TEST2;

import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import webdriver.pages.AppPage.RequestResetPasswordDialog;

public class ResetPasswordTest extends BaseIntegrationTest {

    @Test
    public void resetPasswordCanBeCancelled() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openSignInDialog().openResetPasswordDialog();
                
                dialog.canCancel();
            }
        });
    }

    @Test
    public void resetPasswordPreventsInvalidEmailSubmission() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openSignInDialog().openResetPasswordDialog();
                
                dialog.submitInvalidEmailAddress("fooboo");
            }
        });
    }

    @Test
    public void resetPasswordSubmitsValidEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openSignInDialog().openResetPasswordDialog();
                
                dialog.submitEmailAddress(TEST2.EMAIL);
            }
        });
    }

}
