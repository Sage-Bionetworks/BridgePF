package webdriver.tests;

import org.junit.Test;

import play.libs.F.Callback;
import play.test.TestBrowser;
import webdriver.pages.AppPage;
import webdriver.pages.AppPage.RequestResetPasswordDialog;
import webdriver.pages.AppPage.SignUpDialog;
import static org.sagebionetworks.bridge.TestConstants.*;

public class AuthenticationTest extends BaseIntegrationTest {
    
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
                
                dialog.submitEmailAddress(TEST2.EMAIL);
            }
        });
    }
    
    @Test
    public void validSignUp() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                
                dialog.enterValidData();
            }
        });
    }
    
    @Test
    public void signUpRejectsInvalidEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("bridge", "bridgeit", "P4ssword", "P4ssword");
                dialog.assertEmailEmailError();
            }
        });
    }
    
    @Test
    public void signUpRequiresEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterValidData();
                dialog.enterInvalidDataAfterValidData("bridge", "", "P4ssword", "P4ssword");
                dialog.assertEmailRequiredError();
            }
        });
    }
    
    @Test
    public void signUpRejectsMismatchedPasswords() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("bridge", "bridgeit@sagebase.org", "P4ssword", "P4ssword2");
                dialog.assertPasswordConfirmEqualError();
            }
        });
    }
    
    @Test
    public void signUpRejectsMissingUsername() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("", "bridgeit@sagebase.org", "P4ssword", "P4ssword");
            }
        });
    }
    
    @Test
    public void signUpRejectsMissingEmail() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("bridge", "", "P4ssword", "P4ssword");
            }
        });
    }
    
    public void signUpRejectsMissingPassword() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("bridge", "bridgeit@sagebase.org", "", "P4ssword");
            }
        });
    }
    
    @Test
    public void signUpRejectsMissingPasswordConfirmation() {
        call(new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                AppPage page = new AppPage(browser);
                SignUpDialog dialog = page.openSignUpDialog();
                dialog.enterInvalidData("bridge", "bridgeit@sagebase.org", "P4ssword", "");
            }
        });
    }
    
}
