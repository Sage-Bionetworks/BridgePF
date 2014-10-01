package webdriver.tests;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.models.UserSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SignInTest extends BaseIntegrationTest {
    
    @Resource
    TestUserAdminHelper helper;
    
    @Test
    public void signIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                UserSession session = null;
                try {
                    session = helper.createUser();
                    AppPage page = new AppPage(browser);
                    AppPage.SignInDialog signInDialog = page.openSignInDialog();

                    signInDialog.signIn(helper.getUserSignIn().getUsername(), helper.getUserSignIn().getPassword());
                    page.signOut();
                } finally {
                    helper.deleteUser(session);
                }
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
