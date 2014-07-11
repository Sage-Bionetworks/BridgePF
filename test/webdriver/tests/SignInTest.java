package webdriver.tests;

import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import static org.sagebionetworks.bridge.TestConstants.*;

public class SignInTest extends BaseIntegrationTest {

    @Test
    public void signIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                AppPage.SignInDialog signInDialog = page.openSignInDialog();

                signInDialog.signIn(TEST2.USERNAME, TEST2.PASSWORD);
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

}
