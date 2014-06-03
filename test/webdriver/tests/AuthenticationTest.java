package webdriver.tests;

import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import webdriver.pages.AppPage.RequestResetPasswordDialog;

public class AuthenticationTest extends BaseIntegrationTest {
    
    @Test
    public void signIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                AppPage.SignInDialog signInDialog = page.openSignInDialog();

                signInDialog.signIn("test2", "password");
                page.signOut();
            }
        });
    }
    
    @Test
    public void signInDialogDoesClose() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                AppPage.SignInDialog signInDialog = page.openSignInDialog();
                signInDialog.close();
            }
        });
    }
    
    @Test
    public void failToSignIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                AppPage.SignInDialog signInDialog = page.openSignInDialog();
                
                signInDialog.signInWrong("test43000", "notMyPassword");
            }
        });
    }
    
    @Test
    public void resetPasswordCanBeCancelled() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openResetPasswordDialog();
                
                dialog.canCancel();
            }
        });
    }
    
    @Test
    public void resetPasswordPreventsInvalidEmailSubmission() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openResetPasswordDialog();
                
                dialog.submitInvalidEmailAddress("fooboo");
            }
        });
    }

    @Test
    public void resetPasswordSubmitsValidEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openResetPasswordDialog();
                
                dialog.submitEmailAddress("test2@sagebase.org");
            }
        });
    }
}
