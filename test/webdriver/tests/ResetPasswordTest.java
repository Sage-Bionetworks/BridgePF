package webdriver.tests;


import javax.annotation.Resource;

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
import webdriver.pages.AppPage.RequestResetPasswordDialog;

@ContextConfiguration("file:conf/application-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ResetPasswordTest extends BaseIntegrationTest {
    
    @Resource
    public AuthenticationServiceImpl authService;

    @Resource
    public BridgeConfig bridgeConfig;
    
    @Resource
    public StudyControllerService studyControllerService;
    
    @Resource
    public StormPathUserAdminService userAdminService;

    private TestUser admin;
    private TestUser testUser = new TestUser("test2User", "test2@sagebridge.org", "P4ssword");
    private Study study;
    private UserSession adminUserSession;
    private UserSession userSession;
    
    @Before
    public void before() {
        admin =  new TestUser("administrator", bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        study = studyControllerService.getStudyByHostname("pd.sagebridge.org");
    }
    
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
                adminUserSession = authService.signIn(study, admin.getSignIn());
                userSession = userAdminService.createUser(adminUserSession.getUser(), testUser.getSignUp(), study, true, true);
                
                AppPage page = new AppPage(browser);
                RequestResetPasswordDialog dialog = page.openSignInDialog().openResetPasswordDialog();
                
                dialog.submitEmailAddress(testUser.getEmail());
                userAdminService.deleteUser(adminUserSession.getUser(), userSession.getUser(), study);
            }
        });
    }

}
