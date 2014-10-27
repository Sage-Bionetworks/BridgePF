package webdriver.tests;

import javax.annotation.Resource;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
@Ignore
public class SignInTest extends BaseIntegrationTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private StudyServiceImpl studyService;
    
    @Test
    public void signIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                TestUser testUser = null;
                try {
                    SignUp signUp = new SignUp("test", "test@sagebridge.org", "P4ssword");
                    Study study = studyService.getStudyByKey("neurod");
                    testUser = helper.createUser(signUp, study, true, true);
                    
                    AppPage page = new AppPage(browser);
                    AppPage.SignInDialog signInDialog = page.openSignInDialog();

                    signInDialog.signIn(testUser.getUser().getUsername(), "P4ssword");
                    page.signOut();
                } finally {
                    helper.deleteUser(testUser);
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
