package webdriver.tests;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants.TestUser;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.AuthenticationServiceImpl;
import org.sagebionetworks.bridge.services.StormPathUserAdminService;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import controllers.StudyControllerService;
import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import static org.sagebionetworks.bridge.TestConstants.*;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SignInTest extends BaseIntegrationTest {
    
    @Resource
    AuthenticationServiceImpl authService;

    @Resource
    BridgeConfig bridgeConfig;
    
    @Resource
    StudyControllerService studyControllerService;
    
    @Resource
    StormPathUserAdminService userAdminService;

    private Study study;
    
    private TestUser testUser = new TestUser("test2User", "test2@sagebridge.org", "P4ssword");
    private TestUser admin;

    private UserSession adminUserSession;
    private UserSession userSession;
    
    
    @Before
    public void before() {
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
        admin = new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
    }
    
    @Test
    public void signIn() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                adminUserSession = authService.signIn(study, admin.getSignIn());
                userSession = userAdminService.createUser(adminUserSession.getUser(), testUser.getSignUp(), study, true, true);
                
                AppPage page = new AppPage(browser);
                AppPage.SignInDialog signInDialog = page.openSignInDialog();

                signInDialog.signIn(testUser.getUsername(), testUser.getPassword());
                page.signOut();
                
                userAdminService.deleteUser(adminUserSession.getUser(), userSession.getUser(), study);
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
